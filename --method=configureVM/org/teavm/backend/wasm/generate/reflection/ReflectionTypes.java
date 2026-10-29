package org.teavm.backend.wasm.generate.reflection;

import java.util.HashMap;
import java.util.Map;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStorageType;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.dependency.DependencyInfo;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ClassMetadataRequirements;

public class ReflectionTypes {
   private WasmGCNameProvider names;
   private WasmModule module;
   private DependencyInfo dependency;
   private ClassReaderSource classes;
   private WasmFunctionTypes functionTypes;
   private WasmGCClassInfoProvider classInfoProvider;
   private BaseWasmFunctionRepository functions;
   private ClassMetadataRequirements metadataRequirements;
   private ClassInfoStruct classInfoStruct;
   private ClassReflectionInfoStruct classReflectionInfoStruct;
   private AnnotationInfoStruct annotationInfoStruct;
   private DerivedClassInfoStruct derivedClassInfoStruct;
   private FieldInfoStruct fieldInfoStruct;
   private FieldReflectionInfoStruct fieldReflectionInfoStruct;
   private MethodInfoStruct methodInfoStruct;
   private MethodReflectionInfoStruct methodReflectionInfoStruct;
   private ParameterInfoStruct parameterInfoStruct;
   private TypeVariableInfoStruct typeVariableInfoStruct;
   private ParameterizedTypeInfoStruct parameterizedTypeInfoStruct;
   private TypeVariableReferenceStruct typeVariableReferenceStruct;
   private GenericArrayInfoStruct genericArrayInfoStruct;
   private WildcardTypeInfoStruct wildcardTypeInfoStruct;
   private Map<String, AnnotationDataStruct> annotationDataStructMap = new HashMap<>();
   private Map<WasmStorageType, WasmArray> annotationTypesForArrays = new HashMap<>();

   public ReflectionTypes(
      WasmGCNameProvider names,
      WasmModule module,
      DependencyInfo dependency,
      ClassReaderSource classes,
      WasmFunctionTypes functionTypes,
      WasmGCClassInfoProvider classInfoProvider,
      BaseWasmFunctionRepository functions,
      ClassMetadataRequirements metadataRequirements
   ) {
      this.names = names;
      this.module = module;
      this.dependency = dependency;
      this.classes = classes;
      this.functionTypes = functionTypes;
      this.classInfoProvider = classInfoProvider;
      this.functions = functions;
      this.metadataRequirements = metadataRequirements;
   }

   public ClassInfoStruct classInfo() {
      if (this.classInfoStruct == null) {
         this.classInfoStruct = new ClassInfoStruct(
            this.names, this.module, this.functionTypes, this.classInfoProvider, this.functions, this.metadataRequirements, this.dependency, this
         );
      }

      return this.classInfoStruct;
   }

   public ClassReflectionInfoStruct classReflectionInfo() {
      if (this.classReflectionInfoStruct == null) {
         this.classReflectionInfoStruct = new ClassReflectionInfoStruct(this.names, this.module, this.dependency, this.metadataRequirements, this);
      }

      return this.classReflectionInfoStruct;
   }

   public DerivedClassInfoStruct derivedClassInfo() {
      if (this.derivedClassInfoStruct == null) {
         this.derivedClassInfoStruct = new DerivedClassInfoStruct(this.names, this.module, this);
      }

      return this.derivedClassInfoStruct;
   }

   public AnnotationInfoStruct annotationInfo() {
      if (this.annotationInfoStruct == null) {
         this.annotationInfoStruct = new AnnotationInfoStruct(this.names, this.module, this.functionTypes, this.classInfoProvider);
      }

      return this.annotationInfoStruct;
   }

   public AnnotationDataStruct annotationData(String annotationClassName) {
      return this.annotationDataStructMap
         .computeIfAbsent(
            annotationClassName,
            k -> new AnnotationDataStruct(k, k + "$$_nativeData", this.names, this.module, this.classes, this.functions, this.classInfoProvider, this)
         );
   }

   public FieldInfoStruct fieldInfo() {
      if (this.fieldInfoStruct == null) {
         this.fieldInfoStruct = new FieldInfoStruct(this.names, this.module, this.functionTypes, this.dependency, this.classInfoProvider);
      }

      return this.fieldInfoStruct;
   }

   public FieldReflectionInfoStruct fieldReflectionInfo() {
      if (this.fieldReflectionInfoStruct == null) {
         this.fieldReflectionInfoStruct = new FieldReflectionInfoStruct(this.names, this.module, this.dependency, this.classInfoProvider);
      }

      return this.fieldReflectionInfoStruct;
   }

   public MethodInfoStruct methodInfo() {
      if (this.methodInfoStruct == null) {
         this.methodInfoStruct = new MethodInfoStruct(this.names, this.module, this.functionTypes, this.dependency, this.classInfoProvider);
      }

      return this.methodInfoStruct;
   }

   public MethodReflectionInfoStruct methodReflectionInfo() {
      if (this.methodReflectionInfoStruct == null) {
         this.methodReflectionInfoStruct = new MethodReflectionInfoStruct(this.names, this.module, this.dependency, this.classInfoProvider);
      }

      return this.methodReflectionInfoStruct;
   }

   public ParameterInfoStruct parameterInfo() {
      if (this.parameterInfoStruct == null) {
         this.parameterInfoStruct = new ParameterInfoStruct(this.names, this.module, this.dependency, this.classInfoProvider);
      }

      return this.parameterInfoStruct;
   }

   public TypeVariableInfoStruct typeVariableInfo() {
      if (this.typeVariableInfoStruct == null) {
         this.typeVariableInfoStruct = new TypeVariableInfoStruct(this.names, this.module, this.dependency, this.classInfoProvider);
      }

      return this.typeVariableInfoStruct;
   }

   public ParameterizedTypeInfoStruct parameterizedTypeInfo() {
      if (this.parameterizedTypeInfoStruct == null) {
         this.parameterizedTypeInfoStruct = new ParameterizedTypeInfoStruct(this.names, this.module, this.dependency, this);
      }

      return this.parameterizedTypeInfoStruct;
   }

   public TypeVariableReferenceStruct typeVariableReference() {
      if (this.typeVariableReferenceStruct == null) {
         this.typeVariableReferenceStruct = new TypeVariableReferenceStruct(this.names, this.module);
      }

      return this.typeVariableReferenceStruct;
   }

   public GenericArrayInfoStruct genericArrayInfo() {
      if (this.genericArrayInfoStruct == null) {
         this.genericArrayInfoStruct = new GenericArrayInfoStruct(this.names, this.module);
      }

      return this.genericArrayInfoStruct;
   }

   public WildcardTypeInfoStruct wildcardTypeInfo() {
      if (this.wildcardTypeInfoStruct == null) {
         this.wildcardTypeInfoStruct = new WildcardTypeInfoStruct(this.names, this.module);
      }

      return this.wildcardTypeInfoStruct;
   }

   public WasmStorageType typeForAnnotation(ValueType type) {
      return this.typeForAnnotation(type, false);
   }

   public WasmStorageType typeForAnnotation(ValueType type, boolean array) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
            case BYTE:
               return WasmStorageType.INT8;
            case SHORT:
            case CHARACTER:
               return WasmStorageType.INT16;
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
         if (type instanceof ValueType.Array) {
            WasmStorageType itemType = this.typeForAnnotation(((ValueType.Array)type).getItemType(), true);
            return this.arrayTypeOf(itemType).getReference().asStorage();
         }

         if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object)type).getClassName();
            switch (className) {
               case "java.lang.Class":
                  return this.derivedClassInfo().structure().getReference().asStorage();
               case "java.lang.String":
                  return this.classInfoProvider.getClassInfo("java.lang.String").getType().asStorage();
               default:
                  ClassReader cls = this.classes.get(className);
                  if (cls == null) {
                     return WasmType.STRUCT.asStorage();
                  } else if (cls.hasModifier(ElementModifier.ENUM)) {
                     return WasmStorageType.INT16;
                  } else {
                     return array ? WasmType.STRUCT.asStorage() : this.annotationData(className).structure().getReference().asStorage();
                  }
            }
         } else {
            throw new IllegalArgumentException();
         }
      }
   }

   public WasmArray arrayTypeOf(WasmStorageType type) {
      return this.annotationTypesForArrays.computeIfAbsent(type, item -> {
         WasmArray array = new WasmArray(null, item);
         this.module.types.add(array);
         return array;
      });
   }

   public WasmArray genericTypeArray() {
      return this.arrayTypeOf(WasmType.STRUCT.asStorage());
   }
}
