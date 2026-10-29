package org.teavm.backend.wasm.debug.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.teavm.backend.wasm.debug.info.ArrayLayout;
import org.teavm.backend.wasm.debug.info.ClassInfo;
import org.teavm.backend.wasm.debug.info.ClassLayout;
import org.teavm.backend.wasm.debug.info.ClassLayoutInfo;
import org.teavm.backend.wasm.debug.info.FieldInfo;
import org.teavm.backend.wasm.debug.info.FieldType;
import org.teavm.backend.wasm.debug.info.InterfaceLayout;
import org.teavm.backend.wasm.debug.info.PrimitiveLayout;
import org.teavm.backend.wasm.debug.info.TypeLayout;
import org.teavm.backend.wasm.debug.info.UnknownLayout;
import org.teavm.model.PrimitiveType;

public class DebugClassLayoutParser extends DebugSectionParser {
   private DebugStringParser strings;
   private DebugClassParser classes;
   private ArrayList<DebugClassLayoutParser.TypeLayoutImpl> types = new ArrayList<>();
   private List<List<Consumer<DebugClassLayoutParser.TypeLayoutImpl>>> forwardReferences = new ArrayList<>();
   private int lastAddress;
   private int lastFieldOffset;
   private DebugClassLayoutParser.ClassLayoutInfoImpl classLayoutInfo;

   public DebugClassLayoutParser(DebugStringParser strings, DebugClassParser classes) {
      super("teavm_cll", strings, classes);
      this.strings = strings;
      this.classes = classes;
   }

   @Override
   protected void doParse() {
      while (this.ptr < this.data.length) {
         byte kind = this.data[this.ptr++];
         switch (kind) {
            case 0:
               this.parseRootClass();
               break;
            case 1:
               this.parseClass();
               break;
            case 2:
               this.parseInterface();
               break;
            case 3:
               this.parseArray();
               break;
            case 4:
               this.parsePrimitive(PrimitiveType.BOOLEAN);
               break;
            case 5:
               this.parsePrimitive(PrimitiveType.BYTE);
               break;
            case 6:
               this.parsePrimitive(PrimitiveType.SHORT);
               break;
            case 7:
               this.parsePrimitive(PrimitiveType.CHARACTER);
               break;
            case 8:
               this.parsePrimitive(PrimitiveType.INTEGER);
               break;
            case 9:
               this.parsePrimitive(PrimitiveType.LONG);
               break;
            case 10:
               this.parsePrimitive(PrimitiveType.FLOAT);
               break;
            case 11:
               this.parsePrimitive(PrimitiveType.DOUBLE);
               break;
            case 12:
               this.parseUnknown();
         }
      }

      this.types.trimToSize();
      this.classLayoutInfo = new DebugClassLayoutParser.ClassLayoutInfoImpl(Collections.unmodifiableList(this.types));
   }

   public ClassLayoutInfo getInfo() {
      return this.classLayoutInfo;
   }

   private void parseRootClass() {
      ClassInfo classPtr = this.classes.getClass(this.readLEB());
      int address = this.readAddress();
      int size = this.readLEB();
      DebugClassLayoutParser.ClassLayoutImpl type = new DebugClassLayoutParser.ClassLayoutImpl(address, classPtr, size);
      this.addType(type);
      this.parseClassFields(type);
   }

   private void parseClass() {
      ClassInfo classPtr = this.classes.getClass(this.readLEB());
      int superclassIndex = this.types.size() - this.readSignedLEB();
      int address = this.readAddress();
      int size = this.readLEB();
      DebugClassLayoutParser.ClassLayoutImpl type = new DebugClassLayoutParser.ClassLayoutImpl(address, classPtr, size);
      this.addType(type);
      this.ref(superclassIndex, superclass -> type.superclass = (DebugClassLayoutParser.ClassLayoutImpl)superclass);
      this.parseClassFields(type);
   }

   private void parseClassFields(DebugClassLayoutParser.ClassLayoutImpl type) {
      this.lastFieldOffset = 0;
      ArrayList<DebugClassLayoutParser.FieldInfoImpl> staticFields = new ArrayList<>();

      while (true) {
         byte fieldType = this.data[this.ptr++];
         if (fieldType == 0) {
            staticFields.trimToSize();
            type.staticFields = staticFields.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(staticFields);
            type.instanceFields = Collections.emptyList();
            return;
         }

         if (fieldType == 1) {
            this.lastFieldOffset = 0;
            ArrayList<DebugClassLayoutParser.FieldInfoImpl> instanceFields = new ArrayList<>();

            while (true) {
               byte fieldTypex = this.data[this.ptr++];
               if (fieldTypex == 0 || fieldTypex == 1) {
                  staticFields.trimToSize();
                  instanceFields.trimToSize();
                  type.staticFields = staticFields.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(staticFields);
                  type.instanceFields = instanceFields.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(instanceFields);
                  return;
               }

               instanceFields.add(this.parseField(fieldTypex));
            }
         }

         staticFields.add(this.parseField(fieldType));
      }
   }

   private DebugClassLayoutParser.FieldInfoImpl parseField(byte type) {
      FieldType fieldType = switch (type) {
         case 2 -> FieldType.BOOLEAN;
         case 3 -> FieldType.BYTE;
         case 4 -> FieldType.SHORT;
         case 5 -> FieldType.CHAR;
         case 6 -> FieldType.INT;
         case 7 -> FieldType.LONG;
         case 8 -> FieldType.FLOAT;
         case 9 -> FieldType.DOUBLE;
         case 10 -> FieldType.OBJECT;
         case 11 -> FieldType.ADDRESS;
         default -> FieldType.UNDEFINED;
      };
      String name = this.strings.getString(this.readLEB());
      int offset = this.lastFieldOffset + this.readSignedLEB();
      this.lastFieldOffset = offset;
      return new DebugClassLayoutParser.FieldInfoImpl(offset, name, fieldType);
   }

   private void parseInterface() {
      ClassInfo classRef = this.classes.getClass(this.readLEB());
      int address = this.readAddress();
      this.addType(new DebugClassLayoutParser.InterfaceLayoutImpl(address, classRef));
   }

   private void parseArray() {
      int elementPtr = this.types.size() - this.readSignedLEB();
      int address = this.readAddress();
      DebugClassLayoutParser.ArrayLayoutImpl type = new DebugClassLayoutParser.ArrayLayoutImpl(address);
      this.addType(type);
      this.ref(elementPtr, element -> type.elementType = element);
   }

   private void parsePrimitive(PrimitiveType primitiveType) {
      int address = this.readAddress();
      this.addType(new DebugClassLayoutParser.PrimitiveLayoutImpl(address, primitiveType));
   }

   private void parseUnknown() {
      int address = this.readAddress();
      this.addType(new DebugClassLayoutParser.UnknownLayoutImpl(address));
   }

   private int readAddress() {
      int result = this.readSignedLEB() + this.lastAddress;
      this.lastAddress = result;
      return result;
   }

   private void addType(DebugClassLayoutParser.TypeLayoutImpl type) {
      int index = this.types.size();
      this.types.add(type);
      if (index < this.forwardReferences.size()) {
         List<Consumer<DebugClassLayoutParser.TypeLayoutImpl>> refs = this.forwardReferences.get(index);
         this.forwardReferences.set(index, null);

         for (Consumer<DebugClassLayoutParser.TypeLayoutImpl> ref : refs) {
            ref.accept(type);
         }
      }
   }

   private void ref(int index, Consumer<DebugClassLayoutParser.TypeLayoutImpl> handler) {
      if (index < this.types.size()) {
         handler.accept(this.types.get(index));
      } else {
         if (index >= this.forwardReferences.size()) {
            this.forwardReferences.addAll(Collections.nCopies(index + 1 - this.forwardReferences.size(), null));
         }

         List<Consumer<DebugClassLayoutParser.TypeLayoutImpl>> refs = this.forwardReferences.get(index);
         if (refs == null) {
            refs = new ArrayList<>();
            this.forwardReferences.set(index, refs);
         }

         refs.add(handler);
      }
   }

   private static class ArrayLayoutImpl extends DebugClassLayoutParser.TypeLayoutImpl implements ArrayLayout {
      private DebugClassLayoutParser.TypeLayoutImpl elementType;

      ArrayLayoutImpl(int address) {
         super(address);
      }

      @Override
      public TypeLayout elementType() {
         return this.elementType;
      }
   }

   private static class ClassLayoutImpl extends DebugClassLayoutParser.TypeLayoutImpl implements ClassLayout {
      private ClassInfo classRef;
      private List<? extends DebugClassLayoutParser.FieldInfoImpl> instanceFields;
      private List<? extends DebugClassLayoutParser.FieldInfoImpl> staticFields;
      private DebugClassLayoutParser.ClassLayoutImpl superclass;
      private int size;

      ClassLayoutImpl(int address, ClassInfo classRef, int size) {
         super(address);
         this.classRef = classRef;
         this.size = size;
      }

      @Override
      public ClassInfo classRef() {
         return this.classRef;
      }

      @Override
      public ClassLayout superclass() {
         return this.superclass;
      }

      @Override
      public Collection<? extends FieldInfo> instanceFields() {
         return this.instanceFields;
      }

      @Override
      public Collection<? extends FieldInfo> staticFields() {
         return this.staticFields;
      }

      @Override
      public int size() {
         return this.size;
      }
   }

   private static class ClassLayoutInfoImpl extends ClassLayoutInfo {
      private List<DebugClassLayoutParser.TypeLayoutImpl> types;

      ClassLayoutInfoImpl(List<DebugClassLayoutParser.TypeLayoutImpl> types) {
         this.types = types;
      }

      @Override
      public List<? extends TypeLayout> types() {
         return this.types;
      }
   }

   private static class FieldInfoImpl extends FieldInfo {
      private int address;
      private String name;
      private FieldType type;

      FieldInfoImpl(int address, String name, FieldType type) {
         this.address = address;
         this.name = name;
         this.type = type;
      }

      @Override
      public int address() {
         return this.address;
      }

      @Override
      public String name() {
         return this.name;
      }

      @Override
      public FieldType type() {
         return this.type;
      }
   }

   private static class InterfaceLayoutImpl extends DebugClassLayoutParser.TypeLayoutImpl implements InterfaceLayout {
      private ClassInfo classRef;

      InterfaceLayoutImpl(int address, ClassInfo classRef) {
         super(address);
         this.classRef = classRef;
      }

      @Override
      public ClassInfo classRef() {
         return this.classRef;
      }
   }

   private static class PrimitiveLayoutImpl extends DebugClassLayoutParser.TypeLayoutImpl implements PrimitiveLayout {
      private PrimitiveType primitiveType;

      PrimitiveLayoutImpl(int address, PrimitiveType primitiveType) {
         super(address);
         this.primitiveType = primitiveType;
      }

      @Override
      public PrimitiveType primitiveType() {
         return this.primitiveType;
      }
   }

   private abstract static class TypeLayoutImpl implements TypeLayout {
      private int address;

      private TypeLayoutImpl(int address) {
         this.address = address;
      }

      @Override
      public int address() {
         return this.address;
      }
   }

   private static class UnknownLayoutImpl extends DebugClassLayoutParser.TypeLayoutImpl implements UnknownLayout {
      UnknownLayoutImpl(int address) {
         super(address);
      }
   }
}
