package org.teavm.backend.wasm.generate.classes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.reflection.ReflectionTypes;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmPackedType;
import org.teavm.backend.wasm.model.WasmStorageType;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.ValueType;
import org.teavm.runtime.reflect.AnnotationData;

public class WasmGCTypeMapper {
   private ClassReaderSource classes;
   private WasmGCClassInfoProvider classInfoProvider;
   private WasmFunctionTypes functionTypes;
   private ReflectionTypes reflectionTypes;
   private List<WasmGCCustomTypeMapper> customTypeMappers;
   private Map<String, WasmType> typeCache = new HashMap<>();

   WasmGCTypeMapper(ClassReaderSource classes, WasmGCClassInfoProvider classInfoProvider, WasmFunctionTypes functionTypes, ReflectionTypes reflectionTypes) {
      this.classes = classes;
      this.classInfoProvider = classInfoProvider;
      this.functionTypes = functionTypes;
      this.reflectionTypes = reflectionTypes;
   }

   void setCustomTypeMappers(List<WasmGCCustomTypeMapper> customTypeMappers) {
      this.customTypeMappers = List.copyOf(customTypeMappers);
   }

   public WasmStorageType mapStorageType(ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BYTE:
            case BOOLEAN:
               return WasmStorageType.packed(WasmPackedType.INT8);
            case SHORT:
            case CHARACTER:
               return WasmStorageType.packed(WasmPackedType.INT16);
            case INTEGER:
               return WasmType.INT32.asStorage();
            case LONG:
               return WasmType.INT64.asStorage();
            case FLOAT:
               return WasmType.FLOAT32.asStorage();
            case DOUBLE:
               return WasmType.FLOAT64.asStorage();
            default:
               throw new IllegalArgumentException();
         }
      } else {
         return this.mapType(type).asStorage();
      }
   }

   public WasmType mapType(ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BYTE:
            case BOOLEAN:
            case SHORT:
            case CHARACTER:
            case INTEGER:
               return WasmType.INT32;
            case LONG:
               return WasmType.INT64;
            case FLOAT:
               return WasmType.FLOAT32;
            case DOUBLE:
               return WasmType.FLOAT64;
            default:
               throw new IllegalArgumentException();
         }
      } else {
         if (type instanceof ValueType.Void) {
            return null;
         }

         if (type instanceof ValueType.Object) {
            return this.mapClassType(((ValueType.Object)type).getClassName());
         }

         if (!(type instanceof ValueType.Array)) {
            throw new IllegalArgumentException();
         }

         int degree;
         for (degree = 0; type instanceof ValueType.Array; degree++) {
            type = ((ValueType.Array)type).getItemType();
         }

         if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object)type).getClassName();
            ClassReader cls = this.classes.get(className);
            if (cls == null) {
               type = ValueType.object("java.lang.Object");
            }
         }

         while (degree-- > 0) {
            type = ValueType.arrayOf(type);
         }

         return this.classInfoProvider.getClassInfo(type).getType();
      }
   }

   private WasmType mapClassType(String className) {
      WasmType result = this.typeCache.get(className);
      if (result == null) {
         for (WasmGCCustomTypeMapper customMapper : this.customTypeMappers) {
            result = customMapper.map(className);
            if (result != null) {
               break;
            }
         }

         if (result == null) {
            switch (className) {
               case "org.teavm.interop.Address":
                  result = WasmType.INT32;
                  break;
               case "org.teavm.runtime.reflect.ClassInfo":
                  result = this.reflectionTypes.classInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.ClassReflectionInfo":
                  result = this.reflectionTypes.classReflectionInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.DerivedClassInfo":
                  result = this.reflectionTypes.derivedClassInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.AnnotationInfo":
                  result = this.reflectionTypes.annotationInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.AnnotationData":
                  result = WasmType.STRUCT;
                  break;
               case "org.teavm.runtime.reflect.AnnotationConstructor":
                  result = this.reflectionTypes.annotationInfo().constructorType().getReference();
                  break;
               case "org.teavm.runtime.reflect.AnnotationValueArray":
                  result = WasmType.ARRAY;
                  break;
               case "org.teavm.runtime.reflect.FieldInfo":
                  result = this.reflectionTypes.fieldInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.FieldReflectionInfo":
                  result = this.reflectionTypes.fieldReflectionInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.MethodInfo":
                  result = this.reflectionTypes.methodInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.MethodReflectionInfo":
                  result = this.reflectionTypes.methodReflectionInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.ParameterInfo":
                  result = this.reflectionTypes.parameterInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.TypeVariableInfo":
                  result = this.reflectionTypes.typeVariableInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.GenericTypeInfo":
                  result = WasmType.STRUCT;
                  break;
               case "org.teavm.runtime.reflect.ParameterizedTypeInfo":
                  result = this.reflectionTypes.parameterizedTypeInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.TypeVariableReference":
                  result = this.reflectionTypes.typeVariableReference().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.GenericArrayInfo":
                  result = this.reflectionTypes.genericArrayInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.WildcardTypeInfo":
                  result = this.reflectionTypes.wildcardTypeInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.reflect.RawTypeInfo":
                  result = this.reflectionTypes.derivedClassInfo().structure().getReference();
                  break;
               case "org.teavm.runtime.StringInfo":
                  result = this.classInfoProvider.getClassInfo("java.lang.String").getType();
                  break;
               default:
                  ClassReader cls = this.classes.get(className);
                  if (cls == null) {
                     className = "java.lang.Object";
                  } else if (Objects.equals(cls.getParent(), AnnotationData.class.getName())) {
                     String annotClassName = cls.getName().substring(0, cls.getName().length() - "$$_nativeData".length());
                     return this.classInfoProvider.reflectionTypes().annotationData(annotClassName).structure().getReference();
                  }

                  WasmGCClassInfo classInfo = this.classInfoProvider.getClassInfo(className);
                  if (classInfo.isHeapStructure()) {
                     result = WasmType.INT32;
                  } else {
                     result = classInfo.getType();
                  }
            }

            this.typeCache.put(className, result);
         }
      }

      return result;
   }

   public WasmFunctionType getFunctionType(WasmType receiverType, MethodDescriptor methodDesc) {
      WasmType returnType = this.mapType(methodDesc.getResultType());
      ValueType[] javaParamTypes = methodDesc.getParameterTypes();
      WasmType[] paramTypes = new WasmType[javaParamTypes.length + 1];
      paramTypes[0] = receiverType;

      for (int i = 0; i < javaParamTypes.length; i++) {
         paramTypes[i + 1] = this.mapType(javaParamTypes[i]);
      }

      return this.functionTypes.of(returnType, paramTypes);
   }

   public WasmFunctionType getFunctionType(String className, MethodDescriptor methodDesc) {
      return this.getFunctionType(this.classInfoProvider.getClassInfo(className).getType(), methodDesc);
   }
}
