package org.teavm.backend.wasm.intrinsics.reflection;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.TemporaryVariablePool;
import org.teavm.backend.wasm.generate.ValueCache;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfo;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCTypeMapper;
import org.teavm.backend.wasm.generate.methods.WasmGCVirtualCallGenerator;
import org.teavm.backend.wasm.generate.reflection.AnnotationDataStruct;
import org.teavm.backend.wasm.generate.reflection.AnnotationInfoStruct;
import org.teavm.backend.wasm.generate.reflection.ClassInfoStruct;
import org.teavm.backend.wasm.generate.reflection.ClassReflectionInfoStruct;
import org.teavm.backend.wasm.generate.reflection.DerivedClassInfoStruct;
import org.teavm.backend.wasm.generate.reflection.FieldInfoStruct;
import org.teavm.backend.wasm.generate.reflection.FieldReflectionInfoStruct;
import org.teavm.backend.wasm.generate.reflection.GenericArrayInfoStruct;
import org.teavm.backend.wasm.generate.reflection.MethodInfoStruct;
import org.teavm.backend.wasm.generate.reflection.MethodReflectionInfoStruct;
import org.teavm.backend.wasm.generate.reflection.ParameterInfoStruct;
import org.teavm.backend.wasm.generate.reflection.ParameterizedTypeInfoStruct;
import org.teavm.backend.wasm.generate.reflection.ReflectionTypes;
import org.teavm.backend.wasm.generate.reflection.TypeVariableInfoStruct;
import org.teavm.backend.wasm.generate.reflection.WildcardTypeInfoStruct;
import org.teavm.backend.wasm.generate.strings.WasmGCStringConstant;
import org.teavm.backend.wasm.generate.strings.WasmGCStringProvider;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStorageType;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmInstructionList;
import org.teavm.backend.wasm.model.instruction.WasmSignedType;
import org.teavm.backend.wasm.transformation.CoroutineTransformation;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTableProvider;
import org.teavm.dependency.DependencyInfo;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.GenericTypeParameter;
import org.teavm.model.GenericValueType;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.PrimitiveType;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.reflection.AnnotationGenerationHelper;
import org.teavm.reflection.ReflectionDependencyListener;
import org.teavm.runtime.reflect.ClassReflectionInfo;

public class ReflectionMetadataGenerator {
   private WasmGCNameProvider names;
   private WasmModule module;
   private WasmFunctionTypes functionTypes;
   private DependencyInfo dependencies;
   private ReflectionDependencyListener reflection;
   private ListableClassReaderSource classes;
   private WasmGCClassInfoProvider classInfoProvider;
   private BaseWasmFunctionRepository functions;
   private WasmGCTypeMapper typeMapper;
   private WasmGCStringProvider strings;
   private ClassInitializerInfo classInitInfo;
   private WasmGCVirtualTableProvider virtualTables;
   private Predicate<MethodReference> asyncMethods;
   private boolean innerClassesRequired;
   private Set<String> innerClassesAccessed = new HashSet<>();
   private Map<PrimitiveType, WasmFunction> readerConverters = new EnumMap<>(PrimitiveType.class);
   private WasmFunction referenceReaderConverter;
   private Map<PrimitiveType, WasmFunction> writerConverters = new EnumMap<>(PrimitiveType.class);
   private WasmFunction referenceWriterConverter;
   private WasmFunction initFunction;

   public ReflectionMetadataGenerator(
      WasmGCNameProvider names,
      WasmModule module,
      WasmFunctionTypes functionTypes,
      DependencyInfo dependencies,
      ReflectionDependencyListener reflection,
      ListableClassReaderSource classes,
      WasmGCClassInfoProvider classInfoProvider,
      BaseWasmFunctionRepository functions,
      WasmGCTypeMapper typeMapper,
      WasmGCStringProvider strings,
      ClassInitializerInfo classInitInfo,
      WasmGCVirtualTableProvider virtualTables,
      Predicate<MethodReference> asyncMethods
   ) {
      this.names = names;
      this.module = module;
      this.functionTypes = functionTypes;
      this.dependencies = dependencies;
      this.reflection = reflection;
      this.classes = classes;
      this.functions = functions;
      this.classInfoProvider = classInfoProvider;
      this.typeMapper = typeMapper;
      this.strings = strings;
      this.classInitInfo = classInitInfo;
      this.virtualTables = virtualTables;
      this.asyncMethods = asyncMethods;
      WasmFunction fn = new WasmFunction(functionTypes.of(null));
      fn.setName(names.topLevel("teavm@initReflection"));
      module.functions.add(fn);
      this.initFunction = fn;
   }

   public WasmFunction initFunction() {
      return this.initFunction;
   }

   public void generate() {
      Map<String, List<AnnotationReader>> annotationsByClass = this.collectAnnotations();
      ClassInfoStruct classInfoStruct = this.classInfoProvider.reflectionTypes().classInfo();
      boolean needFieldsMetadata = this.dependencies.getMethod(new MethodReference(ClassReflectionInfo.class, "fieldCount", int.class)) != null;
      boolean needMethodsMetadata = this.dependencies.getMethod(new MethodReference(ClassReflectionInfo.class, "methodCount", int.class)) != null;
      boolean needTypeVars = this.dependencies.getMethod(new MethodReference(ClassReflectionInfo.class, "typeParameterCount", int.class)) != null;
      MethodDependencyInfo classesMethod = this.dependencies.getMethod(new MethodReference(Class.class, "getDeclaredClasses", Class[].class));
      if (classesMethod != null) {
         this.innerClassesRequired = true;

         for (ValueType type : classesMethod.getVariable(0).getClassValueNode().getTypes()) {
            if (type instanceof ValueType.Object) {
               this.innerClassesAccessed.add(((ValueType.Object)type).getClassName());
            }
         }
      }

      for (String className : this.classes.getClassNames()) {
         List<AnnotationReader> annotations = annotationsByClass.get(className);
         ClassReader cls = this.classes.get(className);
         Collection<String> fields = needFieldsMetadata && this.reflection.getClassesWithReflectableFields().contains(className)
            ? this.reflection.getAccessibleFields(className)
            : List.of();
         Collection<MethodDescriptor> methods = needMethodsMetadata && this.reflection.getClassesWithReflectableMethods().contains(className)
            ? this.reflection.getAccessibleMethods(className)
            : List.of();
         List<? extends String> innerClasses = this.extractInnerClasses(cls);
         GenericTypeParameter[] typeParameters = needTypeVars ? cls.getGenericParameters() : new GenericTypeParameter[0];
         WasmInstructionList metadata = this.generateClassMetadata(className, annotations, fields, methods, typeParameters, innerClasses);
         if (metadata != null) {
            WasmInstructionBuilder builder = this.initFunction.getBody().builder();
            builder.getGlobal(this.classInfoProvider.getClassInfo(className).getPointer())
               .transferFrom(metadata.builder())
               .structSet(classInfoStruct.structure(), classInfoStruct.reflectionInfoIndex());
         }
      }
   }

   private WasmInstructionList generateClassMetadata(
      String className,
      List<AnnotationReader> annotations,
      Collection<? extends String> reflectableFields,
      Collection<? extends MethodDescriptor> reflectableMethods,
      GenericTypeParameter[] typeParameters,
      List<? extends String> innerClasses
   ) {
      if (annotations == null
         && reflectableFields.isEmpty()
         && reflectableMethods.isEmpty()
         && (typeParameters == null || typeParameters.length == 0)
         && innerClasses.isEmpty()) {
         return null;
      }

      WasmInstructionBuilder builder = new WasmInstructionList().builder();
      ClassReflectionInfoStruct classInfoStruct = this.classInfoProvider.reflectionTypes().classReflectionInfo();
      if (classInfoStruct.annotationsIndex() >= 0) {
         this.generateAnnotations(builder, annotations);
      }

      if (classInfoStruct.fieldsIndex() >= 0) {
         this.generateFields(builder, className, reflectableFields);
      }

      if (classInfoStruct.methodsIndex() >= 0) {
         this.generateMethods(builder, className, reflectableMethods);
      }

      if (classInfoStruct.typeParametersIndex() >= 0) {
         ClassReader cls = this.classes.get(className);
         this.generateTypeParameters(builder, typeParameters, cls, null);
      }

      if (classInfoStruct.innerClassesIndex() >= 0) {
         this.generateInnerClasses(builder, innerClasses);
      }

      builder.structNew(classInfoStruct.structure());
      return builder.list;
   }

   private void generateFields(WasmInstructionBuilder builder, String className, Collection<? extends String> fields) {
      FieldInfoStruct fieldInfoStruct = this.classInfoProvider.reflectionTypes().fieldInfo();
      if (fields.isEmpty()) {
         builder.nullConst(fieldInfoStruct.array().getReference());
      } else {
         ClassReader cls = this.classes.get(className);
         boolean skipPrivates = ReflectionDependencyListener.shouldSkipPrivates(cls);
         int count = 0;

         for (FieldReader field : cls.getFields()) {
            if (fields.contains(field.getName())
               && (!skipPrivates || field.getLevel() != AccessLevel.PRIVATE && field.getLevel() != AccessLevel.PACKAGE_PRIVATE)) {
               if (fieldInfoStruct.nameIndex() >= 0) {
                  WasmGCStringConstant nameStr = this.strings.getStringConstant(field.getName());
                  builder.getGlobal(nameStr.global);
               }

               if (fieldInfoStruct.modifiersIndex() >= 0) {
                  int modifiers = ElementModifier.asModifiersInfo(field.readModifiers(), field.getLevel());
                  builder.i32Const(modifiers);
               }

               if (fieldInfoStruct.typeIndex() >= 0) {
                  this.generateDerivedClass(builder, field.getType());
               }

               if (fieldInfoStruct.readerIndex() >= 0) {
                  if (this.reflection.isRead(field.getReference())) {
                     builder.funcRef(this.generateGetter(field));
                  } else {
                     builder.nullConst(WasmType.FUNC);
                  }
               }

               if (fieldInfoStruct.readerConverterIndex() >= 0) {
                  if (this.reflection.isRead(field.getReference())) {
                     builder.funcRef(this.getReaderConverter(field.getType()));
                  } else {
                     builder.nullConst(fieldInfoStruct.readerConverterType().getReference());
                  }
               }

               if (fieldInfoStruct.writerIndex() >= 0) {
                  if (this.reflection.isWritten(field.getReference())) {
                     builder.funcRef(this.generateSetter(field));
                  } else {
                     builder.nullConst(WasmType.FUNC);
                  }
               }

               if (fieldInfoStruct.writerConverterIndex() >= 0) {
                  if (this.reflection.isWritten(field.getReference()) && this.canGenerateWriterConverter(field.getType())) {
                     builder.funcRef(this.getWriterConverter(field.getType()));
                  } else {
                     builder.nullConst(fieldInfoStruct.writerConverterType().getReference());
                  }
               }

               if (fieldInfoStruct.reflectionIndex() >= 0) {
                  this.generateFieldReflection(builder, field);
               }

               builder.structNew(fieldInfoStruct.structure());
               count++;
            }
         }

         builder.arrayNewFixed(fieldInfoStruct.array(), count);
      }
   }

   private void generateFieldReflection(WasmInstructionBuilder builder, FieldReader field) {
      FieldReflectionInfoStruct fieldReflectionStruct = this.classInfoProvider.reflectionTypes().fieldReflectionInfo();
      List<AnnotationReader> annotations = fieldReflectionStruct.annotationsIndex() >= 0
         ? AnnotationGenerationHelper.collectRuntimeAnnotations(this.classes, field.getAnnotations().all())
         : List.of();
      GenericValueType genericType = fieldReflectionStruct.genericTypeIndex() >= 0 ? field.getGenericType() : null;
      if (annotations.isEmpty() && genericType == null) {
         builder.nullConst(fieldReflectionStruct.structure().getReference());
      } else {
         if (fieldReflectionStruct.annotationsIndex() >= 0) {
            this.generateAnnotations(builder, annotations);
         }

         if (fieldReflectionStruct.genericTypeIndex() >= 0) {
            ClassReader cls = this.classes.get(field.getOwnerName());
            this.generateGenericType(builder, cls, null, genericType);
         }

         builder.structNew(fieldReflectionStruct.structure());
      }
   }

   private void generateMethods(WasmInstructionBuilder builder, String className, Collection<? extends MethodDescriptor> methods) {
      MethodInfoStruct methodInfoStruct = this.classInfoProvider.reflectionTypes().methodInfo();
      if (methods.isEmpty()) {
         builder.nullConst(methodInfoStruct.array().getReference());
      } else {
         ClassReader cls = this.classes.get(className);
         if (cls != null && !cls.getMethods().isEmpty()) {
            WasmFunctionType callerType = methodInfoStruct.callerType();
            int count = 0;
            boolean skipPrivates = ReflectionDependencyListener.shouldSkipPrivates(cls);

            for (MethodReader method : cls.getMethods()) {
               if ((!skipPrivates || method.getLevel() != AccessLevel.PRIVATE && method.getLevel() != AccessLevel.PACKAGE_PRIVATE)
                  && !method.getName().equals("<clinit>")
                  && methods.contains(method.getDescriptor())) {
                  if (methodInfoStruct.nameIndex() >= 0) {
                     WasmGCStringConstant nameStr = this.strings.getStringConstant(method.getName());
                     builder.getGlobal(nameStr.global);
                  }

                  if (methodInfoStruct.modifiersIndex() >= 0) {
                     int modifiers = ElementModifier.asModifiersInfo(method.readModifiers(), method.getLevel());
                     builder.i32Const(modifiers);
                  }

                  if (methodInfoStruct.returnTypeIndex() >= 0) {
                     this.generateDerivedClass(builder, method.getResultType());
                  }

                  if (methodInfoStruct.parameterTypesIndex() >= 0) {
                     ValueType[] paramTypes = method.getParameterTypes();
                     DerivedClassInfoStruct derivedClassInfoStruct = this.classInfoProvider.reflectionTypes().derivedClassInfo();
                     if (paramTypes.length == 0) {
                        builder.nullConst(derivedClassInfoStruct.array().getReference());
                     } else {
                        for (ValueType param : paramTypes) {
                           this.generateDerivedClass(builder, param);
                        }

                        builder.arrayNewFixed(derivedClassInfoStruct.array(), paramTypes.length);
                     }
                  }

                  if (methodInfoStruct.checkedExceptionTypesIndex() >= 0) {
                     List<? extends String> thrownTypes = method.getThrownTypes();
                     ClassInfoStruct classInfoStruct = this.classInfoProvider.reflectionTypes().classInfo();
                     if (thrownTypes != null && !thrownTypes.isEmpty()) {
                        for (String thrownType : thrownTypes) {
                           builder.getGlobal(this.classInfoProvider.getClassInfo(thrownType).getPointer());
                        }

                        builder.arrayNewFixed(classInfoStruct.array(), thrownTypes.size());
                     } else {
                        builder.nullConst(classInfoStruct.array().getReference());
                     }
                  }

                  if (methodInfoStruct.callerIndex() >= 0) {
                     if (this.reflection.isCalled(method.getReference())) {
                        WasmFunction caller = this.generateCaller(method);
                        builder.funcRef(caller);
                     } else {
                        builder.nullConst(callerType.getReference());
                     }
                  }

                  if (methodInfoStruct.reflectionIndex() >= 0) {
                     this.generateMethodReflection(builder, method);
                  }

                  builder.structNew(methodInfoStruct.structure());
                  count++;
               }
            }

            builder.arrayNewFixed(methodInfoStruct.array(), count);
         } else {
            builder.nullConst(methodInfoStruct.array().getReference());
         }
      }
   }

   private void generateMethodReflection(WasmInstructionBuilder builder, MethodReader method) {
      ReflectionTypes reflectionTypes = this.classInfoProvider.reflectionTypes();
      MethodReflectionInfoStruct methodReflectionStruct = reflectionTypes.methodReflectionInfo();
      List<AnnotationReader> annotations = methodReflectionStruct.annotationsIndex() >= 0
         ? AnnotationGenerationHelper.collectRuntimeAnnotations(this.classes, method.getAnnotations().all())
         : List.of();
      GenericValueType genericReturnType = methodReflectionStruct.genericReturnTypeIndex() >= 0 ? method.getGenericResultType() : null;
      GenericValueType[] genericParameterTypes = null;
      List<List<AnnotationReader>> paramAnnotations = null;
      if (methodReflectionStruct.parameterInfosIndex() >= 0) {
         ParameterInfoStruct paramInfoStruct = reflectionTypes.parameterInfo();
         if (paramInfoStruct.genericTypeIndex() >= 0) {
            genericParameterTypes = method.getGenericParameterTypes();
         }

         if (paramInfoStruct.annotationsIndex() >= 0) {
            paramAnnotations = this.collectParamAnnotations(method);
         }
      }

      GenericTypeParameter[] typeParameters = methodReflectionStruct.typeParametersIndex() >= 0 ? method.getTypeParameters() : null;
      boolean hasNonTrivialParams = this.computeHasNonTrivialParams(method, genericParameterTypes, paramAnnotations);
      if (!annotations.isEmpty() || genericReturnType != null || hasNonTrivialParams || typeParameters != null && typeParameters.length != 0) {
         ClassReader cls = this.classes.get(method.getOwnerName());
         if (methodReflectionStruct.genericReturnTypeIndex() >= 0) {
            if (genericReturnType != null) {
               this.generateGenericType(builder, cls, method, genericReturnType);
            } else {
               builder.nullConst(WasmType.STRUCT);
            }
         }

         if (methodReflectionStruct.parameterInfosIndex() >= 0) {
            if (hasNonTrivialParams) {
               this.generateParameterInfos(builder, cls, method, genericParameterTypes, paramAnnotations);
            } else {
               builder.nullConst(reflectionTypes.parameterInfo().array().getReference());
            }
         }

         if (methodReflectionStruct.annotationsIndex() >= 0) {
            this.generateAnnotations(builder, annotations);
         }

         if (methodReflectionStruct.typeParametersIndex() >= 0) {
            this.generateTypeParameters(builder, typeParameters, cls, method);
         }

         builder.structNew(methodReflectionStruct.structure());
      } else {
         builder.nullConst(methodReflectionStruct.structure().getReference());
      }
   }

   private boolean computeHasNonTrivialParams(MethodReader method, GenericValueType[] genericParameterTypes, List<List<AnnotationReader>> paramAnnotations) {
      if (genericParameterTypes != null) {
         for (int i = 0; i < genericParameterTypes.length; i++) {
            GenericValueType paramType = genericParameterTypes[i];
            if (!paramType.canBeRepresentedAsRaw() || !paramType.asValueType().equals(method.parameterType(i))) {
               return true;
            }
         }
      }

      if (paramAnnotations != null) {
         for (List<AnnotationReader> annots : paramAnnotations) {
            if (!annots.isEmpty()) {
               return true;
            }
         }
      }

      return false;
   }

   private List<List<AnnotationReader>> collectParamAnnotations(MethodReader method) {
      AnnotationContainerReader[] paramAnnots = method.getParameterAnnotations();
      if (paramAnnots == null) {
         return null;
      }

      ArrayList<List<AnnotationReader>> result = new ArrayList<>();
      boolean hasAny = false;

      for (AnnotationContainerReader container : paramAnnots) {
         List<AnnotationReader> annots = AnnotationGenerationHelper.collectRuntimeAnnotations(this.classes, container.all());
         result.add(annots);
         if (!annots.isEmpty()) {
            hasAny = true;
         }
      }

      return hasAny ? result : null;
   }

   private void generateParameterInfos(
      WasmInstructionBuilder builder,
      ClassReader cls,
      MethodReader method,
      GenericValueType[] genericParameterTypes,
      List<List<AnnotationReader>> paramAnnotations
   ) {
      ReflectionTypes reflectionTypes = this.classInfoProvider.reflectionTypes();
      ParameterInfoStruct paramInfoStruct = reflectionTypes.parameterInfo();
      int count = method.parameterCount();

      for (int i = 0; i < count; i++) {
         if (paramInfoStruct.annotationsIndex() >= 0) {
            List<AnnotationReader> annots = paramAnnotations != null && i < paramAnnotations.size() ? paramAnnotations.get(i) : List.of();
            this.generateAnnotations(builder, annots);
         }

         if (paramInfoStruct.genericTypeIndex() >= 0) {
            boolean hasNonTrivialType = genericParameterTypes != null && i < genericParameterTypes.length;
            if (hasNonTrivialType) {
               GenericValueType paramType = genericParameterTypes[i];
               hasNonTrivialType = !paramType.canBeRepresentedAsRaw() || !paramType.asValueType().equals(method.parameterType(i));
               if (hasNonTrivialType) {
                  this.generateGenericType(builder, cls, method, paramType);
               } else {
                  builder.nullConst(WasmType.STRUCT);
               }
            } else {
               builder.nullConst(WasmType.STRUCT);
            }
         }

         builder.structNew(paramInfoStruct.structure());
      }

      builder.arrayNewFixed(paramInfoStruct.array(), count);
   }

   private void generateAnnotations(WasmInstructionBuilder builder, List<AnnotationReader> annotations) {
      AnnotationInfoStruct annotationInfoStruct = this.classInfoProvider.reflectionTypes().annotationInfo();
      if (annotations != null && !annotations.isEmpty()) {
         for (AnnotationReader annotation : annotations) {
            this.generateAnnotation(builder, annotation);
            AnnotationDataStruct dataStruct = this.classInfoProvider.reflectionTypes().annotationData(annotation.getType());
            dataStruct.constructor().setReferenced(true);
            builder.funcRef(dataStruct.constructor());
            builder.structNew(annotationInfoStruct.structure());
         }

         builder.arrayNewFixed(annotationInfoStruct.array(), annotations.size());
      } else {
         builder.nullConst(annotationInfoStruct.array().getReference());
      }
   }

   private void generateAnnotation(WasmInstructionBuilder builder, AnnotationReader annotation) {
      AnnotationDataStruct struct = this.classInfoProvider.reflectionTypes().annotationData(annotation.getType());

      for (AnnotationDataStruct.Field field : struct.fields()) {
         AnnotationValue value = annotation.getValue(field.name);
         if (value == null) {
            value = field.defaultValue;
         }

         this.generateAnnotationValue(builder, value, field.type);
      }

      builder.structNew(struct.structure());
   }

   private void generateAnnotationValue(WasmInstructionBuilder builder, AnnotationValue value, ValueType type) {
      switch (value.getType()) {
         case 0:
            builder.i32Const(value.getBoolean() ? 1 : 0);
            return;
         case 1:
            builder.i32Const(value.getByte());
            return;
         case 2:
            builder.i32Const(value.getShort());
            return;
         case 3:
            builder.i32Const(value.getInt());
            return;
         case 4:
            builder.i64Const(value.getLong());
            return;
         case 5:
            builder.f32Const(value.getFloat());
            return;
         case 6:
            builder.f64Const(value.getDouble());
            return;
         case 7:
            builder.getGlobal(this.strings.getStringConstant(value.getString()).global);
            return;
         case 8:
            this.generateDerivedClass(builder, value.getJavaClass());
            return;
         case 9:
            ValueType itemType = ((ValueType.Array)type).getItemType();
            WasmStorageType wasmItemType = this.classInfoProvider.reflectionTypes().typeForAnnotation(itemType, true);

            for (AnnotationValue item : value.getList()) {
               this.generateAnnotationValue(builder, item, itemType);
            }

            builder.arrayNewFixed(this.classInfoProvider.reflectionTypes().arrayTypeOf(wasmItemType), value.getList().size());
            return;
         case 10:
            ClassReader enumClass = this.classes.get(value.getEnumValue().getClassName());
            if (enumClass == null) {
               builder.i32Const(-1);
               return;
            }

            int index = 0;

            for (FieldReader field : enumClass.getFields()) {
               if (field.hasModifier(ElementModifier.ENUM) || field.hasModifier(ElementModifier.STATIC)) {
                  if (field.getName().equals(value.getEnumValue().getFieldName())) {
                     break;
                  }

                  index++;
               }
            }

            builder.i32Const(index);
            return;
         case 11:
            this.generateAnnotation(builder, value.getAnnotation());
            return;
         case 12:
            builder.i32Const(value.getChar());
            return;
         default:
            throw new IllegalStateException();
      }
   }

   private Map<String, List<AnnotationReader>> collectAnnotations() {
      MethodDependencyInfo methodDep = this.dependencies.getMethod(new MethodReference(Class.class, "getDeclaredAnnotations", Annotation[].class));
      if (methodDep == null) {
         return Collections.emptyMap();
      }

      LinkedHashMap<String, List<AnnotationReader>> result = new LinkedHashMap<>();

      for (ValueType type : methodDep.getVariable(0).getClassValueNode().getTypes()) {
         if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object)type).getClassName();
            ClassReader cls = this.classes.get(className);
            if (cls != null) {
               List<AnnotationReader> annotations = AnnotationGenerationHelper.collectRuntimeAnnotations(this.classes, cls.getAnnotations().all());
               if (!annotations.isEmpty()) {
                  result.put(className, annotations);
               }
            }
         }
      }

      return result;
   }

   private void generateDerivedClass(WasmInstructionBuilder builder, ValueType type) {
      int degree;
      for (degree = 0; type instanceof ValueType.Array; degree++) {
         type = ((ValueType.Array)type).getItemType();
      }

      builder.getGlobal(this.classInfoProvider.getClassInfo(type).getPointer())
         .i32Const(degree)
         .structNew(this.classInfoProvider.reflectionTypes().derivedClassInfo().structure());
   }

   private WasmFunction generateGetter(FieldReader field) {
      WasmGCClassInfo objectClass = this.classInfoProvider.getClassInfo("java.lang.Object");
      FieldInfoStruct fieldInfoStruct = this.classInfoProvider.reflectionTypes().fieldInfo();
      WasmType rawType = this.rawWasmType(field.getType());
      WasmFunctionType getterType = fieldInfoStruct.rawReaderFunctionType(rawType);
      WasmFunction function = new WasmFunction(getterType);
      function.setName(this.names.topLevel(this.names.suggestForStaticField(field.getReference()) + "@getter"));
      this.module.functions.add(function);
      function.setReferenced(true);
      WasmLocal thisVar = new WasmLocal(objectClass.getType(), "this");
      function.add(thisVar);
      WasmInstructionBuilder body = function.getBody().builder();
      WasmGCClassInfo classInfo = this.classInfoProvider.getClassInfo(field.getOwnerName());
      if (field.hasModifier(ElementModifier.STATIC)) {
         this.initClass(classInfo, field.getOwnerName(), function);
         body.getGlobal(this.classInfoProvider.getStaticFieldLocation(field.getReference()));
      } else {
         body.getLocal(thisVar).cast(classInfo.getType());
         WasmSignedType signedType = null;
         if (field.getType() instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive)field.getType()).getKind()) {
               case BYTE:
               case SHORT:
                  signedType = WasmSignedType.SIGNED;
                  break;
               case BOOLEAN:
               case CHARACTER:
                  signedType = WasmSignedType.UNSIGNED;
            }
         }

         body.structGet(classInfo.getStructure(), this.classInfoProvider.getFieldIndex(field.getReference()), signedType);
      }

      return function;
   }

   private WasmFunction generateSetter(FieldReader field) {
      WasmGCClassInfo objectClass = this.classInfoProvider.getClassInfo("java.lang.Object");
      FieldInfoStruct fieldInfoStruct = this.classInfoProvider.reflectionTypes().fieldInfo();
      WasmType rawType = this.rawWasmType(field.getType());
      WasmFunctionType setterType = fieldInfoStruct.rawWriterFunctionType(rawType);
      WasmFunction function = new WasmFunction(setterType);
      function.setName(this.names.topLevel(this.names.suggestForStaticField(field.getReference()) + "@setter"));
      this.module.functions.add(function);
      function.setReferenced(true);
      WasmLocal thisVar = new WasmLocal(objectClass.getType(), "this");
      function.add(thisVar);
      WasmLocal valueVar = new WasmLocal(rawType, "value");
      function.add(valueVar);
      WasmInstructionBuilder body = function.getBody().builder();
      WasmInstructionBuilder value = new WasmInstructionList().builder();
      value.getLocal(valueVar);
      if (!(field.getType() instanceof ValueType.Primitive)) {
         this.unboxIfNecessary(value, field.getType());
      }

      WasmGCClassInfo classInfo = this.classInfoProvider.getClassInfo(field.getOwnerName());
      if (field.hasModifier(ElementModifier.STATIC)) {
         this.initClass(classInfo, field.getOwnerName(), function);
         WasmGlobal global = this.classInfoProvider.getStaticFieldLocation(field.getReference());
         body.transferFrom(value).setGlobal(global);
      } else {
         body.getLocal(thisVar)
            .cast(classInfo.getType())
            .transferFrom(value)
            .structSet(classInfo.getStructure(), this.classInfoProvider.getFieldIndex(field.getReference()));
      }

      return function;
   }

   private WasmType rawWasmType(ValueType type) {
      if (type instanceof ValueType.Primitive) {
         return switch (((ValueType.Primitive)type).getKind()) {
            case BYTE, SHORT, BOOLEAN, CHARACTER, INTEGER -> WasmType.INT32;
            case LONG -> WasmType.INT64;
            case FLOAT -> WasmType.FLOAT32;
            case DOUBLE -> WasmType.FLOAT64;
         };
      } else {
         return this.classInfoProvider.getClassInfo("java.lang.Object").getType();
      }
   }

   private WasmFunction getReaderConverter(ValueType fieldType) {
      if (fieldType instanceof ValueType.Primitive) {
         PrimitiveType kind = ((ValueType.Primitive)fieldType).getKind();
         WasmFunction existing = this.readerConverters.get(kind);
         if (existing == null) {
            existing = this.generateReaderConverter(fieldType);
            this.readerConverters.put(kind, existing);
         }

         return existing;
      } else {
         if (this.referenceReaderConverter == null) {
            this.referenceReaderConverter = this.generateReaderConverter(fieldType);
         }

         return this.referenceReaderConverter;
      }
   }

   private WasmFunction generateReaderConverter(ValueType fieldType) {
      WasmGCClassInfo objectClass = this.classInfoProvider.getClassInfo("java.lang.Object");
      FieldInfoStruct fieldInfoStruct = this.classInfoProvider.reflectionTypes().fieldInfo();
      WasmFunctionType converterType = fieldInfoStruct.readerConverterType();
      WasmType rawType = this.rawWasmType(fieldType);
      WasmFunctionType typedReaderType = fieldInfoStruct.rawReaderFunctionType(rawType);
      String name = fieldType instanceof ValueType.Primitive
         ? "teavm@readerConverter_" + ((ValueType.Primitive)fieldType).getKind().name()
         : "teavm@readerConverter_reference";
      WasmFunction function = new WasmFunction(converterType);
      function.setName(this.names.topLevel(name));
      this.module.functions.add(function);
      function.setReferenced(true);
      WasmLocal rawReaderVar = new WasmLocal(WasmType.FUNC, "rawReader");
      function.add(rawReaderVar);
      WasmLocal objVar = new WasmLocal(objectClass.getType(), "obj");
      function.add(objVar);
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(objVar).getLocal(rawReaderVar).cast(typedReaderType.getReference()).callReference(typedReaderType);
      this.boxIfNecessary(body, fieldType);
      return function;
   }

   private WasmFunction getWriterConverter(ValueType fieldType) {
      if (fieldType instanceof ValueType.Primitive) {
         PrimitiveType kind = ((ValueType.Primitive)fieldType).getKind();
         WasmFunction existing = this.writerConverters.get(kind);
         if (existing == null) {
            existing = this.generateWriterConverter(fieldType);
            this.writerConverters.put(kind, existing);
         }

         return existing;
      } else {
         if (this.referenceWriterConverter == null) {
            this.referenceWriterConverter = this.generateWriterConverter(fieldType);
         }

         return this.referenceWriterConverter;
      }
   }

   private boolean canGenerateWriterConverter(ValueType fieldType) {
      return !(fieldType instanceof ValueType.Primitive) ? true : this.dependencies.getMethod(this.unboxMethodFor((ValueType.Primitive)fieldType)) != null;
   }

   private MethodReference unboxMethodFor(ValueType.Primitive type) {
      return switch (type.getKind()) {
         case BYTE -> new MethodReference(Byte.class, "byteValue", byte.class);
         case SHORT -> new MethodReference(Short.class, "shortValue", short.class);
         case BOOLEAN -> new MethodReference(Boolean.class, "booleanValue", boolean.class);
         case CHARACTER -> new MethodReference(Character.class, "charValue", char.class);
         case INTEGER -> new MethodReference(Integer.class, "intValue", int.class);
         case LONG -> new MethodReference(Long.class, "longValue", long.class);
         case FLOAT -> new MethodReference(Float.class, "floatValue", float.class);
         case DOUBLE -> new MethodReference(Double.class, "doubleValue", double.class);
      };
   }

   private WasmFunction generateWriterConverter(ValueType fieldType) {
      WasmGCClassInfo objectClass = this.classInfoProvider.getClassInfo("java.lang.Object");
      FieldInfoStruct fieldInfoStruct = this.classInfoProvider.reflectionTypes().fieldInfo();
      WasmFunctionType converterType = fieldInfoStruct.writerConverterType();
      WasmType rawType = this.rawWasmType(fieldType);
      WasmFunctionType typedWriterType = fieldInfoStruct.rawWriterFunctionType(rawType);
      String name = fieldType instanceof ValueType.Primitive
         ? "teavm@writerConverter_" + ((ValueType.Primitive)fieldType).getKind().name()
         : "teavm@writerConverter_reference";
      WasmFunction function = new WasmFunction(converterType);
      function.setName(this.names.topLevel(name));
      this.module.functions.add(function);
      function.setReferenced(true);
      WasmLocal rawWriterVar = new WasmLocal(WasmType.FUNC, "rawWriter");
      function.add(rawWriterVar);
      WasmLocal objVar = new WasmLocal(objectClass.getType(), "obj");
      function.add(objVar);
      WasmLocal boxedValueVar = new WasmLocal(objectClass.getType(), "boxedValue");
      function.add(boxedValueVar);
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(objVar).getLocal(boxedValueVar);
      if (fieldType instanceof ValueType.Primitive) {
         this.unboxIfNecessary(body, fieldType);
      }

      body.getLocal(rawWriterVar).cast(typedWriterType.getReference()).callReference(typedWriterType);
      return function;
   }

   private WasmFunction generateCaller(MethodReader method) {
      WasmGCClassInfo objectClass = this.classInfoProvider.getClassInfo("java.lang.Object");
      WasmGCClassInfo objectArrayClass = this.classInfoProvider.getClassInfo(ValueType.parse(Object[].class));
      MethodInfoStruct methodInfoStruct = this.classInfoProvider.reflectionTypes().methodInfo();
      WasmFunctionType callerType = methodInfoStruct.callerType();
      WasmFunction function = new WasmFunction(callerType);
      function.setName(this.names.topLevel(this.names.suggestForMethod(method.getReference()) + "@caller"));
      this.module.functions.add(function);
      function.setReferenced(true);
      boolean async = this.asyncMethods.test(method.getReference());
      WasmField dataField = objectArrayClass.getStructure().getFields().get(2);
      WasmLocal thisVar = new WasmLocal(objectClass.getType(), "this");
      function.add(thisVar);
      WasmLocal argsVar = new WasmLocal(objectArrayClass.getType(), "args");
      function.add(argsVar);
      WasmLocal argsDataVar = new WasmLocal(((WasmType.Reference)dataField.getUnpackedType()).asNullable(), "argsData");
      WasmInstructionBuilder body = function.getBody().builder();
      WasmInstructionBuilder args = new WasmInstructionList().builder();
      WasmGCClassInfo classInfo = this.classInfoProvider.getClassInfo(method.getOwnerName());
      WasmFunction callee = null;
      boolean virtual = false;
      if (method.hasModifier(ElementModifier.STATIC)) {
         this.initClass(classInfo, method.getOwnerName(), function);
         callee = this.functions.forStaticMethod(method.getReference());
      } else {
         virtual = !method.hasModifier(ElementModifier.FINAL) && method.getLevel() != AccessLevel.PRIVATE && !method.getName().equals("<init>");
         body.getLocal(thisVar).cast(classInfo.getType());
         if (!virtual) {
            callee = this.functions.forInstanceMethod(method.getReference());
         }
      }

      if (method.parameterCount() > 0) {
         args.getLocal(argsVar).structGet(objectArrayClass.getStructure(), dataField.getIndex());
         if (method.parameterCount() > 1) {
            function.add(argsDataVar);
            args.teeLocal(argsDataVar);
         }
      }

      WasmType.CompositeReference dataType = (WasmType.CompositeReference)dataField.getUnpackedType();
      WasmArray dataArray = (WasmArray)dataType.composite;

      for (int i = 0; i < method.parameterCount(); i++) {
         if (i > 0) {
            args.getLocal(argsDataVar);
         }

         args.i32Const(i).arrayGet(dataArray);
         this.unboxIfNecessary(args, method.parameterType(i));
      }

      if (virtual) {
         WasmGCVirtualCallGenerator callGen = new WasmGCVirtualCallGenerator(this.virtualTables, this.classInfoProvider);
         TemporaryVariablePool tempVars = new TemporaryVariablePool(function);
         ValueCache valueCache = new ValueCache(tempVars);
         callGen.generate(body, method.getReference(), async, valueCache, classInfo.getStructure().getReference(), b -> b.transferFrom(args));
      } else {
         body.transferFrom(args).call(callee, async);
      }

      if (method.getResultType() == ValueType.VOID) {
         body.nullConst(objectClass.getType());
      } else {
         this.boxIfNecessary(body, method.getResultType());
      }

      if (async) {
         CoroutineTransformation transformation = new CoroutineTransformation(this.functionTypes, this.functions, this.classInfoProvider);
         transformation.transform(function);
      }

      return function;
   }

   private void initClass(WasmGCClassInfo classInfo, String className, WasmFunction function) {
      if (this.classInitInfo.isDynamicInitializer(className) && classInfo.getInitializerPointer() != null) {
         WasmFunctionType initType = this.functionTypes.of(null);
         function.getBody().builder().getGlobal(classInfo.getInitializerPointer()).callReference(initType);
      }
   }

   private void boxIfNecessary(WasmInstructionBuilder builder, ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BYTE:
               this.box(builder, Byte.class, type);
               break;
            case SHORT:
               this.box(builder, Short.class, type);
               break;
            case BOOLEAN:
               this.box(builder, Boolean.class, type);
               break;
            case CHARACTER:
               this.box(builder, Character.class, type);
               break;
            case INTEGER:
               this.box(builder, Integer.class, type);
               break;
            case LONG:
               this.box(builder, Long.class, type);
               break;
            case FLOAT:
               this.box(builder, Float.class, type);
               break;
            case DOUBLE:
               this.box(builder, Double.class, type);
         }
      }
   }

   private void box(WasmInstructionBuilder builder, Class<?> wrapperType, ValueType sourceType) {
      MethodReference method = new MethodReference(wrapperType.getName(), "valueOf", sourceType, ValueType.object(wrapperType.getName()));
      builder.call(this.functions.forStaticMethod(method));
   }

   private void unboxIfNecessary(WasmInstructionBuilder builder, ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BYTE:
               this.unbox(builder, byte.class, Byte.class);
               return;
            case SHORT:
               this.unbox(builder, short.class, Short.class);
               return;
            case BOOLEAN:
               this.unbox(builder, boolean.class, Boolean.class);
               return;
            case CHARACTER:
               this.unbox(builder, char.class, Character.class);
               return;
            case INTEGER:
               this.unbox(builder, int.class, Integer.class);
               return;
            case LONG:
               this.unbox(builder, long.class, Long.class);
               return;
            case FLOAT:
               this.unbox(builder, float.class, Float.class);
               return;
            case DOUBLE:
               this.unbox(builder, double.class, Double.class);
               return;
         }
      } else if (type instanceof ValueType.Object && ((ValueType.Object)type).getClassName().equals("java.lang.Object")) {
         return;
      }

      WasmType targetType = this.typeMapper.mapType(type);
      if (targetType != this.typeMapper.mapType(ValueType.object("java.lang.Object"))) {
         builder.cast((WasmType.Reference)targetType);
      }
   }

   private void unbox(WasmInstructionBuilder builder, Class<?> primitiveType, Class<?> wrapperType) {
      MethodReference method = new MethodReference(wrapperType.getName(), primitiveType.getName() + "Value", ValueType.parse(primitiveType));
      WasmFunction function = this.functions.forInstanceMethod(method);
      builder.cast(this.classInfoProvider.getClassInfo(wrapperType.getName()).getType()).call(function);
   }

   private void generateTypeParameters(WasmInstructionBuilder builder, GenericTypeParameter[] params, ClassReader cls, MethodReader method) {
      TypeVariableInfoStruct struct = this.classInfoProvider.reflectionTypes().typeVariableInfo();
      if (params != null && params.length != 0) {
         for (GenericTypeParameter param : params) {
            if (struct.nameIndex() >= 0) {
               builder.getGlobal(this.strings.getStringConstant(param.getName()).global);
            }

            if (struct.boundsIndex() >= 0) {
               List<GenericValueType.Reference> bounds = param.extractAllBounds();
               if (bounds.isEmpty()) {
                  builder.nullConst(this.classInfoProvider.reflectionTypes().genericTypeArray().getReference());
               } else {
                  this.generateTypeParameterBounds(builder, cls, method, bounds);
               }
            }

            builder.structNew(struct.structure());
         }

         builder.arrayNewFixed(struct.array(), params.length);
      } else {
         builder.nullConst(struct.array().getReference());
      }
   }

   private void generateTypeParameterBounds(WasmInstructionBuilder builder, ClassReader cls, MethodReader method, List<GenericValueType.Reference> bounds) {
      for (GenericValueType.Reference bound : bounds) {
         this.generateGenericType(builder, cls, method, bound);
      }

      builder.arrayNewFixed(this.classInfoProvider.reflectionTypes().genericTypeArray(), bounds.size());
   }

   private void generateGenericType(WasmInstructionBuilder builder, ClassReader contextClass, MethodReader contextMethod, GenericValueType type) {
      if (type.canBeRepresentedAsRaw()) {
         this.generateDerivedClass(builder, type.asValueType());
      } else if (type instanceof GenericValueType.Object objectType) {
         GenericValueType.Argument[] args = objectType.getArguments();
         WasmGCClassInfo clsInfo = this.classInfoProvider.getClassInfo(objectType.getFullClassName());
         ParameterizedTypeInfoStruct resultStruct = this.classInfoProvider.reflectionTypes().parameterizedTypeInfo();
         if (resultStruct.rawTypeIndex() >= 0) {
            builder.getGlobal(clsInfo.getPointer());
         }

         if (resultStruct.actualTypeArgumentsIndex() >= 0) {
            WasmArray arrayType = this.classInfoProvider.reflectionTypes().genericTypeArray();

            for (GenericValueType.Argument arg : args) {
               this.generateGenericType(builder, contextClass, contextMethod, arg);
            }

            builder.arrayNewFixed(arrayType, args.length);
         }

         if (resultStruct.ownerTypeIndex() >= 0) {
            GenericValueType.Object ownerType = objectType.getParent();
            if (ownerType != null) {
               this.generateGenericType(builder, contextClass, contextMethod, ownerType);
            } else {
               builder.nullConst(WasmType.STRUCT);
            }
         }

         builder.structNew(resultStruct.structure());
      } else {
         if (type instanceof GenericValueType.Variable typeVar) {
            int level = 0;
            if (contextMethod != null) {
               GenericTypeParameter[] genericParameters = contextMethod.getTypeParameters();

               for (int i = 0; i < genericParameters.length; i++) {
                  GenericTypeParameter param = genericParameters[i];
                  if (param.getName().equals(typeVar.getName())) {
                     this.typeVariableRef(builder, 0, i);
                     return;
                  }
               }

               level++;
            }

            while (contextClass != null) {
               GenericTypeParameter[] genericParameters = contextClass.getGenericParameters();
               if (genericParameters != null) {
                  for (int i = 0; i < genericParameters.length; i++) {
                     GenericTypeParameter param = genericParameters[i];
                     if (param.getName().equals(typeVar.getName())) {
                        this.typeVariableRef(builder, level, i);
                        return;
                     }
                  }
               }

               level++;
               if (contextClass.getOwnerName() == null) {
                  break;
               }

               contextClass = this.classes.get(contextClass.getOwnerName());
            }

            throw new IllegalArgumentException("Unknown type variable: " + typeVar.getName());
         }

         if (type instanceof GenericValueType.Array arrayType) {
            GenericArrayInfoStruct struct = this.classInfoProvider.reflectionTypes().genericArrayInfo();
            this.generateGenericType(builder, contextClass, contextMethod, arrayType.getItemType());
            builder.structNew(struct.structure());
         } else if (type instanceof GenericValueType.Primitive primitiveType) {
            this.generateDerivedClass(builder, ValueType.primitive(primitiveType.getKind()));
         } else {
            if (!(type instanceof GenericValueType.Void)) {
               throw new IllegalArgumentException("Unsupported generic type: " + type);
            }

            this.generateDerivedClass(builder, ValueType.VOID);
         }
      }
   }

   private void typeVariableRef(WasmInstructionBuilder builder, int level, int index) {
      builder.i32Const(level).i32Const(index).structNew(this.classInfoProvider.reflectionTypes().typeVariableReference().structure());
   }

   private void generateGenericType(WasmInstructionBuilder builder, ClassReader contextClass, MethodReader contextMethod, GenericValueType.Argument arg) {
      switch (arg.getKind()) {
         case INVARIANT:
            this.generateGenericType(builder, contextClass, contextMethod, arg.getValue());
            return;
         case ANY: {
            WildcardTypeInfoStruct struct = this.classInfoProvider.reflectionTypes().wildcardTypeInfo();
            builder.i32Const(2).nullConst(WasmType.STRUCT).structNew(struct.structure());
            return;
         }
         case COVARIANT: {
            WildcardTypeInfoStruct struct = this.classInfoProvider.reflectionTypes().wildcardTypeInfo();
            builder.i32Const(0);
            this.generateGenericType(builder, contextClass, contextMethod, arg.getValue());
            builder.structNew(struct.structure());
            return;
         }
         case CONTRAVARIANT: {
            WildcardTypeInfoStruct struct = this.classInfoProvider.reflectionTypes().wildcardTypeInfo();
            builder.i32Const(1);
            this.generateGenericType(builder, contextClass, contextMethod, arg.getValue());
            builder.structNew(struct.structure());
            return;
         }
         default:
            throw new IllegalArgumentException("Unsupported generic type: " + arg.getKind());
      }
   }

   private void generateInnerClasses(WasmInstructionBuilder builder, List<? extends String> innerClasses) {
      if (innerClasses.isEmpty()) {
         builder.nullConst(this.classInfoProvider.reflectionTypes().classInfo().array().getReference());
      } else {
         for (String innerClass : innerClasses) {
            builder.getGlobal(this.classInfoProvider.getClassInfo(innerClass).getPointer());
         }

         builder.arrayNewFixed(this.classInfoProvider.reflectionTypes().classInfo().array(), innerClasses.size());
      }
   }

   private List<? extends String> extractInnerClasses(ClassReader cls) {
      if (this.innerClassesRequired && this.innerClassesAccessed.contains(cls.getName())) {
         ArrayList<String> filtered = new ArrayList<>();

         for (String innerCls : cls.getInnerClasses()) {
            if (this.dependencies.getClass(innerCls) != null) {
               filtered.add(innerCls);
            }
         }

         return filtered;
      } else {
         return Collections.emptyList();
      }
   }
}
