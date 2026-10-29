package org.teavm.debugging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.teavm.backend.wasm.debug.info.ArrayLayout;
import org.teavm.backend.wasm.debug.info.ClassLayout;
import org.teavm.backend.wasm.debug.info.ClassLayoutInfo;
import org.teavm.backend.wasm.debug.info.DebugInfo;
import org.teavm.backend.wasm.debug.info.FieldInfo;
import org.teavm.backend.wasm.debug.info.FieldType;
import org.teavm.backend.wasm.debug.info.InterfaceLayout;
import org.teavm.backend.wasm.debug.info.PrimitiveLayout;
import org.teavm.backend.wasm.debug.info.TypeLayout;
import org.teavm.common.Promise;
import org.teavm.debugging.javascript.JavaScriptCallFrame;
import org.teavm.debugging.javascript.JavaScriptValue;
import org.teavm.model.PrimitiveType;

class WasmValueImpl extends Value {
   private static final String CLASS_PROP = "__class";
   private static final String ADDRESS_PROP = "__address";
   private DebugInfo debugInfo;
   private FieldType type;
   private JavaScriptCallFrame callFrame;
   private long longValue;
   private Promise<TypeLayout> calculatedType;

   WasmValueImpl(Debugger debugger, DebugInfo debugInfo, FieldType type, JavaScriptCallFrame callFrame, long longValue) {
      super(debugger);
      this.debugInfo = debugInfo;
      this.type = type;
      this.callFrame = callFrame;
      this.longValue = longValue;
   }

   @Override
   public Promise<String> getRepresentation() {
      switch (this.type) {
         case BOOLEAN:
            return Promise.of(this.longValue != 0L ? "true" : "false");
         case BYTE:
            return Promise.of(Byte.toString((byte)this.longValue));
         case SHORT:
            return Promise.of(Short.toString((short)this.longValue));
         case CHAR:
            StringBuilder sb = new StringBuilder("'");
            this.appendChar(sb, (char)this.longValue);
            sb.append("'");
            return Promise.of(sb.toString());
         case INT:
            return Promise.of(Integer.toString((int)this.longValue));
         case LONG:
            return Promise.of(Long.toString(this.longValue));
         case FLOAT:
            return Promise.of(Float.toString(Float.intBitsToFloat((int)this.longValue)));
         case DOUBLE:
            return Promise.of(Double.toString(Double.longBitsToDouble(this.longValue)));
         case OBJECT:
            return this.buildObjectRepresentation();
         case ADDRESS:
            return Promise.of("0x" + Integer.toHexString((int)this.longValue));
         default:
            return Promise.of("undefined");
      }
   }

   private void appendChar(StringBuilder sb, char c) {
      switch (c) {
         case '\b':
            sb.append("\\b");
            break;
         case '\t':
            sb.append("\\t");
            break;
         case '\n':
            sb.append("\\n");
            break;
         case '\f':
            sb.append("\\f");
            break;
         case '\r':
            sb.append("\\r");
            break;
         case '"':
            sb.append("\\\"");
            break;
         case '\'':
            sb.append("\\'");
            break;
         case '\\':
            sb.append("\\\\");
            break;
         default:
            if (c < ' ') {
               sb.append("\\u00").append(Character.forDigit(c / 16, 16)).append(Character.forDigit(c % 16, 16));
            } else {
               sb.append(c);
            }
      }
   }

   private Promise<String> buildObjectRepresentation() {
      return this.longValue == 0L
         ? Promise.of("null")
         : this.getCalculatedType().thenAsync(cls -> cls == null ? Promise.of("error") : this.typeRepresentation(cls, (int)this.longValue));
   }

   private Promise<String> typeRepresentation(TypeLayout type, int address) {
      switch (type.kind()) {
         case CLASS:
            return this.objectRepresentation((ClassLayout)type, address);
         case ARRAY:
            return this.arrayRepresentation((ArrayLayout)type, address);
         default:
            return Promise.of(this.classToString(type));
      }
   }

   private Promise<String> objectRepresentation(ClassLayout cls, int address) {
      if (cls.classRef().fullName().equals("java.lang.String")) {
         Promise<String> stringRepr = this.decodeString(cls, address);
         if (stringRepr != null) {
            return stringRepr.then(result -> result != null ? result : this.classToString(cls));
         }
      } else if (cls.classRef().fullName().equals("java.lang.Class")) {
         String stringRepr = this.decodeClass(address);
         if (stringRepr != null) {
            return Promise.of(stringRepr);
         }
      }

      return Promise.of(this.classToString(cls));
   }

   private Promise<String> arrayRepresentation(ArrayLayout arrayType, int address) {
      return this.callFrame.getMemory(address + 8, 4).then(data -> {
         if (data == null) {
            return this.classToString(arrayType);
         }

         int length = this.readInt(data, 0);
         return this.classToString(arrayType.elementType()) + "[" + length + "]";
      });
   }

   private Promise<String> decodeString(ClassLayout cls, int address) {
      for (FieldInfo field : cls.instanceFields()) {
         if (field.name().equals("characters") && field.type() == FieldType.OBJECT) {
            return this.callFrame.getMemory(address + field.address(), 4).thenAsync(data -> {
               int charsAddress = this.readInt(data, 0);
               return this.decodeChars(charsAddress);
            });
         }
      }

      return null;
   }

   private Promise<String> decodeChars(int address) {
      return this.callFrame.getMemory(address, 12).thenAsync(data -> {
         if (data == null) {
            return null;
         }

         int classPtr = this.readInt(data, 0) << 3;
         TypeLayout type = this.debugInfo.classLayoutInfo().find(classPtr);
         if (!(type instanceof ArrayLayout)) {
            return null;
         }

         TypeLayout elementType = ((ArrayLayout)type).elementType();
         if (!(elementType instanceof PrimitiveLayout)) {
            return null;
         }

         PrimitiveType primitiveType = ((PrimitiveLayout)elementType).primitiveType();
         if (primitiveType != PrimitiveType.CHARACTER) {
            return null;
         }

         int length = this.readInt(data, 8);
         return this.callFrame.getMemory(address + 12, length * 2).then(charsData -> {
            if (charsData == null) {
               return null;
            }

            StringBuilder sb = new StringBuilder("\"");

            for (int i = 0; i < length; i++) {
               this.appendChar(sb, (char)this.readShort(charsData, i * 2));
            }

            sb.append("\"");
            return sb.toString();
         });
      });
   }

   private String decodeClass(int address) {
      TypeLayout type = this.debugInfo.classLayoutInfo().find(address);
      return type != null ? this.classToString(type) : null;
   }

   @Override
   Promise<String> prepareType() {
      switch (this.type) {
         case BOOLEAN:
            return Promise.of("boolean");
         case BYTE:
            return Promise.of("byte");
         case SHORT:
            return Promise.of("short");
         case CHAR:
            return Promise.of("char");
         case INT:
            return Promise.of("int");
         case LONG:
            return Promise.of("long");
         case FLOAT:
            return Promise.of("float");
         case DOUBLE:
            return Promise.of("double");
         case OBJECT:
            return this.fetchObjectType();
         case ADDRESS:
            return Promise.of("address");
         default:
            return Promise.of("undefined");
      }
   }

   private Promise<TypeLayout> getCalculatedType() {
      if (this.calculatedType == null) {
         this.calculatedType = this.callFrame.getMemory((int)this.longValue, 4).then(data -> {
            if (data == null) {
               return null;
            }

            int header = this.readInt(data, 0);
            int classPtr = header << 3;
            ClassLayoutInfo classes = this.debugInfo.classLayoutInfo();
            return classes == null ? null : classes.find(classPtr);
         });
      }

      return this.calculatedType;
   }

   private Promise<String> fetchObjectType() {
      return this.longValue == 0L ? Promise.of("null") : this.getCalculatedType().then(cls -> cls == null ? "error" : this.classToString(cls));
   }

   private String classToString(TypeLayout type) {
      switch (type.kind()) {
         case CLASS:
            return ((ClassLayout)type).classRef().fullName();
         case ARRAY:
            return this.classToString(((ArrayLayout)type).elementType()) + "[]";
         case PRIMITIVE:
            switch (((PrimitiveLayout)type).primitiveType()) {
               case BOOLEAN:
                  return "boolean";
               case BYTE:
                  return "byte";
               case SHORT:
                  return "short";
               case CHARACTER:
                  return "char";
               case INTEGER:
                  return "int";
               case LONG:
                  return "long";
               case FLOAT:
                  return "float";
               case DOUBLE:
                  return "double";
            }
         default:
            return "unknown";
         case INTERFACE:
            return ((InterfaceLayout)type).classRef().fullName();
      }
   }

   @Override
   Promise<Map<String, Variable>> prepareProperties() {
      return this.getCalculatedType().thenAsync(cls -> {
         if (cls != null) {
            switch (cls.kind()) {
               case CLASS:
                  return this.fetchObjectProperties((ClassLayout)cls);
               case ARRAY:
                  return this.fetchArrayProperties((ArrayLayout)cls);
            }
         }

         return Promise.of(Collections.emptyMap());
      });
   }

   private Promise<Map<String, Variable>> fetchObjectProperties(ClassLayout cls) {
      return this.longValue == 0L ? Promise.of(Collections.emptyMap()) : this.callFrame.getMemory((int)this.longValue, cls.size()).then(data -> {
         if (data == null) {
            return Collections.emptyMap();
         }

         LinkedHashMap<String, Variable> properties = new LinkedHashMap<>();

         for (ClassLayout ancestorCls = cls; ancestorCls != null; ancestorCls = ancestorCls.superclass()) {
            for (FieldInfo field : ancestorCls.instanceFields()) {
               WasmValueImpl value = new WasmValueImpl(this.debugger, this.debugInfo, field.type(), this.callFrame, switch (field.type()) {
                  case BOOLEAN, BYTE -> data[field.address()];
                  case SHORT, CHAR -> this.readShort(data, field.address());
                  case INT, FLOAT, OBJECT, ADDRESS -> this.readInt(data, field.address());
                  case LONG, DOUBLE -> this.readLong(data, field.address());
                  default -> 0L;
               });
               properties.put(field.name(), new Variable(field.name(), value));
            }
         }

         this.addCommonProperties(properties, cls);
         return properties;
      });
   }

   private Promise<Map<String, Variable>> fetchArrayProperties(ArrayLayout type) {
      return this.longValue == 0L ? Promise.of(Collections.emptyMap()) : this.callFrame.getMemory((int)this.longValue + 8, 4).thenAsync(data -> {
         if (data == null) {
            return Promise.of(Collections.emptyMap());
         }

         int length = this.readInt(data, 0);
         int offset = 12;
         int itemSize;
         FieldType elementType;
         if (type.elementType() instanceof PrimitiveLayout) {
            switch (((PrimitiveLayout)type.elementType()).primitiveType()) {
               case BOOLEAN:
                  elementType = FieldType.BOOLEAN;
                  itemSize = 0;
                  break;
               case BYTE:
                  elementType = FieldType.BYTE;
                  itemSize = 0;
                  break;
               case SHORT:
                  elementType = FieldType.SHORT;
                  itemSize = 1;
                  break;
               case CHARACTER:
                  elementType = FieldType.CHAR;
                  itemSize = 1;
                  break;
               case INTEGER:
                  elementType = FieldType.INT;
                  itemSize = 2;
                  break;
               case LONG:
                  elementType = FieldType.LONG;
                  itemSize = 3;
                  break;
               case FLOAT:
                  elementType = FieldType.FLOAT;
                  itemSize = 2;
                  break;
               case DOUBLE:
                  elementType = FieldType.DOUBLE;
                  offset = 16;
                  itemSize = 3;
                  break;
               default:
                  itemSize = 1;
                  elementType = FieldType.UNDEFINED;
            }
         } else {
            elementType = FieldType.OBJECT;
            itemSize = 2;
         }

         return this.callFrame.getMemory((int)this.longValue + offset, length << itemSize).then(arrayData -> {
            LinkedHashMap<String, Variable> properties = new LinkedHashMap<>();

            for (int i = 0; i < length; i++) {
               String name = String.valueOf(i);

               WasmValueImpl value = new WasmValueImpl(this.debugger, this.debugInfo, elementType, this.callFrame, switch (itemSize) {
                  case 0 -> arrayData[i];
                  case 1 -> this.readShort(arrayData, i * 2);
                  case 2 -> this.readInt(arrayData, i * 4);
                  default -> this.readLong(arrayData, i * 8);
               });
               properties.put(name, new Variable(name, value));
            }

            properties.put("length", new Variable("length", new WasmValueImpl(this.debugger, this.debugInfo, FieldType.INT, this.callFrame, length)));
            this.addCommonProperties(properties, type);
            return properties;
         });
      });
   }

   private void addCommonProperties(Map<String, Variable> properties, TypeLayout cls) {
      properties.put("__class", new Variable("__class", new WasmValueImpl(this.debugger, this.debugInfo, FieldType.OBJECT, this.callFrame, cls.address())));
      properties.put(
         "__address", new Variable("__address", new WasmValueImpl(this.debugger, this.debugInfo, FieldType.ADDRESS, this.callFrame, this.longValue))
      );
   }

   private int readInt(byte[] data, int offset) {
      return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8 | (data[offset + 2] & 0xFF) << 16 | (data[offset + 3] & 0xFF) << 24;
   }

   private int readShort(byte[] data, int offset) {
      return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8;
   }

   private long readLong(byte[] data, int offset) {
      return data[offset] & 0xFF | (data[offset + 1] & 255) << 8 | (data[offset + 2] & 255) << 16 | (data[offset + 3] & 255) << 24
         | (data[offset + 4] & 255L) << 32
         | (data[offset + 5] & 255) << 40
         | (data[offset + 6] & 255L) << 48
         | (data[offset + 7] & 255) << 56;
   }

   @Override
   public Promise<Boolean> hasInnerStructure() {
      return Promise.of(this.type == FieldType.OBJECT && this.longValue != 0L);
   }

   @Override
   public Promise<String> getInstanceId() {
      return Promise.of(null);
   }

   @Override
   public JavaScriptValue getOriginalValue() {
      return null;
   }
}
