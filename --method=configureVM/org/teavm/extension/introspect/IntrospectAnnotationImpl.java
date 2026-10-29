package org.teavm.extension.introspect;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReader;
import org.teavm.model.ValueType;

public class IntrospectAnnotationImpl<T extends Annotation> implements IntrospectAnnotation<T> {
   private Introspection introspection;
   private ClassHierarchy hierarchy;
   private AnnotationReader reader;
   private IntrospectClass<T> annotationType;
   private Map<IntrospectMethod, Object> cache = new HashMap<>();

   IntrospectAnnotationImpl(Introspection introspection, AnnotationReader reader) {
      this.introspection = introspection;
      this.hierarchy = introspection.getHierarchy();
      this.reader = reader;
      this.annotationType = (IntrospectClass<T>)introspection.findClass(reader.getType());
   }

   public IntrospectClass<T> type() {
      return this.annotationType;
   }

   public Object value(IntrospectMethod method) {
      if (!method.declaringClass().equals(this.annotationType)) {
         throw new IllegalArgumentException("Method " + method + " is not declared by " + this.annotationType);
      } else {
         return this.cache.computeIfAbsent(method, m -> {
            MethodReader methodReader = ((IntrospectMethodImpl)m).method;
            AnnotationValue value = this.reader.getValue(methodReader.getName());
            if (value == null) {
               value = methodReader.getAnnotationDefault();
            }

            try {
               return this.convertValue(value, methodReader.getResultType());
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });
      }
   }

   public Object value(String attributeName) {
      return this.value(this.annotationType.declaredMethod(attributeName, new IntrospectClass[0]));
   }

   private Object convertValue(AnnotationValue value, ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
               return value.getBoolean();
            case BYTE:
               return value.getByte();
            case SHORT:
               return value.getShort();
            case INTEGER:
               return value.getInt();
            case LONG:
               return value.getLong();
            case FLOAT:
               return value.getFloat();
            case DOUBLE:
               return value.getDouble();
            case CHARACTER:
               return value.getChar();
         }
      } else {
         if (type.isObject(String.class)) {
            return value.getString();
         }

         if (type instanceof ValueType.Array) {
            List<AnnotationValue> array = value.getList();
            ValueType itemType = ((ValueType.Array)type).getItemType();
            Object result = this.createArray(itemType, array.size());

            for (int i = 0; i < array.size(); i++) {
               Array.set(result, i, this.convertValue(array.get(i), itemType));
            }

            return result;
         }

         if (type.isObject(Class.class)) {
            return this.introspection.getClass(value.getJavaClass());
         }

         if (this.hierarchy.isSuperType(ValueType.parse(Enum.class), type, false)) {
            FieldReference fieldRef = value.getEnumValue();
            IntrospectClassImpl<?> cls = this.introspection.findClass(fieldRef.getClassName());
            return cls.declaredField(fieldRef.getFieldName());
         }

         if (this.hierarchy.isSuperType(ValueType.parse(Annotation.class), type, false)) {
            return new IntrospectAnnotationImpl(this.introspection, value.getAnnotation());
         }
      }

      throw new AssertionError("Unsupported type: " + type);
   }

   private Object createArray(ValueType type, int size) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
               return new boolean[size];
            case BYTE:
               return new byte[size];
            case SHORT:
               return new short[size];
            case INTEGER:
               return new int[size];
            case LONG:
               return new long[size];
            case FLOAT:
               return new float[size];
            case DOUBLE:
               return new double[size];
            case CHARACTER:
               return new char[size];
         }
      } else if (type instanceof ValueType.Object) {
         String name = ((ValueType.Object)type).getClassName();
         if (name.equals("java.lang.String")) {
            return new String[size];
         }

         if (name.equals("java.lang.Class")) {
            return new IntrospectClass[size];
         }

         ClassReader cls = this.introspection.getClassSource().get(name);
         if (cls != null) {
            if (cls.hasModifier(ElementModifier.ENUM)) {
               return new IntrospectField[size];
            }

            if (cls.hasModifier(ElementModifier.ANNOTATION)) {
               return new IntrospectAnnotation[size];
            }
         }
      }

      throw new AssertionError("Unsupported type: " + type);
   }
}
