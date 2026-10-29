package org.teavm.backend.wasm.generate.classes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCInitializerContributor;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.WasmGeneratorUtil;
import org.teavm.backend.wasm.generate.reflection.ClassInfoStruct;
import org.teavm.backend.wasm.generate.reflection.ReflectionTypes;
import org.teavm.backend.wasm.generate.strings.WasmGCStringConstant;
import org.teavm.backend.wasm.generate.strings.WasmGCStringPool;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStorageType;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmFunctionReference;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmInstructionList;
import org.teavm.backend.wasm.model.instruction.WasmNullCondition;
import org.teavm.backend.wasm.model.instruction.WasmNullConstant;
import org.teavm.backend.wasm.model.instruction.WasmSignedType;
import org.teavm.backend.wasm.model.instruction.WasmStructNewDefault;
import org.teavm.backend.wasm.runtime.StringInternPool;
import org.teavm.backend.wasm.runtime.WasmGCSupport;
import org.teavm.backend.wasm.transformation.CoroutineTransformation;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTable;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTableEntry;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTableProvider;
import org.teavm.dependency.DependencyInfo;
import org.teavm.hppc.ObjectByteHashMap;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.interop.Structure;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.PrimitiveType;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.model.analysis.ClassMetadataRequirements;
import org.teavm.model.classes.TagRegistry;
import org.teavm.model.util.ReflectionUtil;

public class WasmGCClassGenerator implements WasmGCClassInfoProvider, WasmGCInitializerContributor {
   private static final MethodDescriptor CLINIT_METHOD_DESC = new MethodDescriptor("<clinit>", ValueType.VOID);
   private static final MethodDescriptor CLONE_METHOD_DESC = new MethodDescriptor("clone", ValueType.object("java.lang.Object"));
   private static final MethodDescriptor GET_CLASS_METHOD = new MethodDescriptor("getClass", ValueType.parse(Class.class));
   private static final FieldReference FAKE_CLASS_FIELD = new FieldReference(Object.class.getName(), "class");
   private static final FieldReference FAKE_MONITOR_FIELD = new FieldReference(Object.class.getName(), "monitor");
   private static final FieldReference FAKE_VT_FIELD = new FieldReference(Object.class.getName(), "vt");
   private static final ValueType OBJECT_TYPE = ValueType.parse(Object.class);
   private final WasmModule module;
   private ClassReaderSource classSource;
   private ClassReaderSource originalClassSource;
   private ClassHierarchy hierarchy;
   private WasmFunctionTypes functionTypes;
   private TagRegistry tagRegistry;
   private ClassMetadataRequirements metadataRequirements;
   private WasmGCVirtualTableProvider virtualTables;
   private BaseWasmFunctionRepository functionProvider;
   private Map<ValueType, WasmGCClassInfo> classInfoMap = new LinkedHashMap<>();
   private Queue<Runnable> queue = new ArrayDeque<>();
   private ObjectIntMap<FieldReference> fieldIndexes = new ObjectIntHashMap();
   private Map<FieldReference, WasmGlobal> staticFieldLocations = new HashMap<>();
   private List<Consumer<WasmFunction>> staticFieldInitializers = new ArrayList<>();
   private ClassInitializerInfo classInitializerInfo;
   private ObjectByteHashMap<String> heapStructures = new ObjectByteHashMap();
   private Set<MethodReference> asyncSplitMethods;
   public final WasmGCStringPool strings;
   public final WasmGCStandardClasses standardClasses;
   private final ReflectionTypes reflectionTypes;
   public final WasmGCTypeMapper typeMapper;
   private final WasmGCNameProvider names;
   private WasmInstructionBuilder initializerFunctionStatements = new WasmInstructionList().builder();
   private WasmFunction createPrimitiveClassFunction;
   private WasmFunction getArrayClassFunction;
   private WasmFunction fillArrayClassFunction;
   private WasmFunction fillRegularClassFunction;
   private final WasmGCSupertypeFunctionGenerator supertypeGenerator;
   private final WasmGCNewArrayFunctionGenerator newArrayGenerator;
   private String arrayDataFieldName;
   private Map<WasmStorageType, WasmArray> arrayTypes = new HashMap<>();
   private int throwableNativeOffset = -1;
   private WasmFunction arrayGetObjectFunction;
   private WasmFunction arraySetObjectFunction;
   private WasmFunction arrayLengthObjectFunction;
   private WasmFunction arrayCopyObjectFunction;
   private List<WasmFunction> multiArrayFunctions = new ArrayList<>();
   private List<WasmStructure> nonInitializedStructures = new ArrayList<>();
   private WasmArray objectArrayType;
   private WasmGCSystemFunctionGenerator systemFunctions;
   private boolean compactMode;
   private WasmGCReflectionGenerator reflectionGenerator;

   public WasmGCClassGenerator(
      WasmModule module,
      ClassReaderSource classSource,
      ClassReaderSource originalClassSource,
      ClassHierarchy hierarchy,
      DependencyInfo dependencyInfo,
      WasmFunctionTypes functionTypes,
      TagRegistry tagRegistry,
      ClassMetadataRequirements metadataRequirements,
      WasmGCVirtualTableProvider virtualTables,
      BaseWasmFunctionRepository functionProvider,
      WasmGCNameProvider names,
      ClassInitializerInfo classInitializerInfo,
      List<WasmGCCustomTypeMapperFactory> customTypeMapperFactories
   ) {
      this.module = module;
      this.classSource = classSource;
      this.originalClassSource = originalClassSource;
      this.hierarchy = hierarchy;
      this.functionTypes = functionTypes;
      this.tagRegistry = tagRegistry;
      this.metadataRequirements = metadataRequirements;
      this.virtualTables = virtualTables;
      this.functionProvider = functionProvider;
      this.names = names;
      this.classInitializerInfo = classInitializerInfo;
      this.standardClasses = new WasmGCStandardClasses(this);
      this.systemFunctions = new WasmGCSystemFunctionGenerator(this, module, functionTypes, names, virtualTables, this.standardClasses);
      this.strings = new WasmGCStringPool(this.standardClasses, module, functionProvider, names, functionTypes, dependencyInfo);
      this.supertypeGenerator = new WasmGCSupertypeFunctionGenerator(module, this, names, tagRegistry, functionTypes, this.queue);
      this.newArrayGenerator = new WasmGCNewArrayFunctionGenerator(module, functionTypes, this, names, this.queue);
      this.reflectionTypes = new ReflectionTypes(names, module, dependencyInfo, classSource, functionTypes, this, functionProvider, metadataRequirements);
      this.typeMapper = new WasmGCTypeMapper(classSource, this, functionTypes, this.reflectionTypes);
      WasmGCCustomTypeMapperFactoryContext customTypeMapperFactoryContext = this.customTypeMapperFactoryContext();
      this.typeMapper
         .setCustomTypeMappers(
            customTypeMapperFactories.stream().map(factory -> factory.createTypeMapper(customTypeMapperFactoryContext)).collect(Collectors.toList())
         );
      this.reflectionGenerator = new WasmGCReflectionGenerator(module, functionTypes, this, names);
   }

   public void setCompactMode(boolean compactMode) {
      this.compactMode = compactMode;
   }

   public void setAsyncSplitMethods(Set<MethodReference> asyncSplitMethods) {
      this.asyncSplitMethods = asyncSplitMethods;
   }

   private WasmGCCustomTypeMapperFactoryContext customTypeMapperFactoryContext() {
      return new WasmGCCustomTypeMapperFactoryContext() {
         @Override
         public ClassReaderSource originalClasses() {
            return WasmGCClassGenerator.this.originalClassSource;
         }

         @Override
         public ClassReaderSource classes() {
            return WasmGCClassGenerator.this.classSource;
         }

         @Override
         public WasmModule module() {
            return WasmGCClassGenerator.this.module;
         }

         @Override
         public WasmGCClassInfoProvider classInfoProvider() {
            return WasmGCClassGenerator.this;
         }

         @Override
         public WasmGCNameProvider names() {
            return WasmGCClassGenerator.this.names;
         }

         @Override
         public WasmGCTypeMapper typeMapper() {
            return WasmGCClassGenerator.this.typeMapper;
         }

         @Override
         public WasmFunctionTypes functionTypes() {
            return WasmGCClassGenerator.this.functionTypes;
         }
      };
   }

   public WasmGCSupertypeFunctionProvider getSupertypeProvider() {
      return this.supertypeGenerator;
   }

   public boolean process() {
      if (this.queue.isEmpty()) {
         return false;
      }

      while (!this.queue.isEmpty()) {
         Runnable action = this.queue.remove();
         action.run();
         this.initStructures();
      }

      return true;
   }

   public boolean hasSomethingToGenerate() {
      return !this.queue.isEmpty();
   }

   private void initStructures() {
      if (!this.nonInitializedStructures.isEmpty()) {
         List<WasmStructure> copy = List.copyOf(this.nonInitializedStructures);
         this.nonInitializedStructures.clear();

         for (WasmStructure structure : copy) {
            structure.init();
         }
      }
   }

   @Override
   public void contributeToInitializerDefinitions(WasmFunction function) {
   }

   @Override
   public void contributeToInitializer(WasmFunction function) {
      WasmInstructionBuilder builder = function.getBody().builder();
      builder.transferFrom(this.initializerFunctionStatements);
      ClassInfoStruct classInfoType = this.reflectionTypes.classInfo();

      for (WasmGCClassInfo classInfo : this.classInfoMap.values()) {
         if (classInfo.supertypeFunction != null) {
            builder.getGlobal(classInfo.pointer)
               .funcRef(classInfo.supertypeFunction)
               .structSet(classInfoType.structure(), classInfoType.supertypeFunctionIndex());
         }

         if (classInfo.initArrayFunction != null) {
            builder.getGlobal(classInfo.pointer)
               .funcRef(classInfo.initArrayFunction)
               .structSet(classInfoType.structure(), classInfoType.newArrayFunctionIndex());
         }
      }

      for (Consumer<WasmFunction> consumer : this.staticFieldInitializers) {
         consumer.accept(function);
      }
   }

   private boolean isHeapStructure(String className) {
      byte result = this.heapStructures.getOrDefault(className, (byte)-1);
      if (result < 0) {
         if (className.equals(Structure.class.getName())) {
            result = 1;
         } else {
            ClassReader cls = this.classSource.get(className);
            result = (byte)(cls != null && cls.getParent() != null && this.isHeapStructure(cls.getParent()) ? 1 : 0);
         }

         this.heapStructures.put(className, result);
      }

      return result != 0;
   }

   @Override
   public WasmGCClassInfo getClassInfo(ValueType type) {
      WasmGCClassInfo classInfo = this.classInfoMap.get(type);
      if (classInfo == null) {
         classInfo = new WasmGCClassInfo(type);
         WasmGCClassInfo finalClassInfo = classInfo;
         this.queue.add(() -> {
            if (finalClassInfo.initializer != null) {
               finalClassInfo.initializer.accept(this.initializerFunctionStatements);
               finalClassInfo.initializer = null;
            }
         });
         this.classInfoMap.put(type, classInfo);
         WasmGCVirtualTable virtualTable = null;
         if (!(type instanceof ValueType.Primitive)) {
            String name = type instanceof ValueType.Object ? ((ValueType.Object)type).getClassName() : null;
            boolean isInterface = false;
            ClassReader classReader = name != null ? this.classSource.get(name) : null;
            if (classReader != null && classReader.hasModifier(ElementModifier.INTERFACE)) {
               isInterface = true;
               classInfo.structure = this.standardClasses.objectClass().structure;
            } else {
               if (type instanceof ValueType.Array) {
                  ValueType itemType = ((ValueType.Array)type).getItemType();
                  if (!(itemType instanceof ValueType.Primitive) && !itemType.equals(OBJECT_TYPE)) {
                     classInfo.structure = this.getClassInfo(ValueType.arrayOf(OBJECT_TYPE)).structure;
                  }
               }

               if (classInfo.structure == null) {
                  if (name != null && this.isHeapStructure(name)) {
                     classInfo.heapStructure = true;
                  } else {
                     String structName = this.names.topLevel(this.names.suggestForType(type));
                     classInfo.structure = new WasmStructure(structName, fields -> this.fillFields(finalClassInfo, fields, type));
                     classInfo.structure.setNominal(true);
                     this.module.types.add(classInfo.structure);
                     this.nonInitializedStructures.add(classInfo.structure);
                  }
               }
            }

            if (!classInfo.isHeapStructure()) {
               if (name != null) {
                  virtualTable = this.virtualTables.lookup(name);
                  if (isInterface && virtualTable != null && !virtualTable.isFakeInterfaceRepresentative()) {
                     virtualTable = null;
                  }

                  if (classReader != null && classReader.getParent() != null && !isInterface) {
                     classInfo.structure.setSupertype(this.getClassInfo(classReader.getParent()).structure);
                  }
               } else {
                  virtualTable = this.virtualTables.lookup("java.lang.Object");
                  classInfo.structure.setSupertype(this.standardClasses.objectClass().structure);
               }
            }
         }

         String pointerName = this.names.topLevel(this.names.suggestForType(type) + "@class");
         String vtName = this.names.topLevel(this.names.suggestForType(type) + "@vt");
         if (virtualTable != null) {
            if (type instanceof ValueType.Object) {
               if (virtualTable.isUsed()) {
                  this.initRegularVirtualTableStructure(classInfo, ((ValueType.Object)type).getClassName());
               } else {
                  WasmGCVirtualTable usedVt = virtualTable.getFirstUsed();
                  if (usedVt != null) {
                     classInfo.virtualTableStructure = this.getClassInfo(usedVt.getClassName()).virtualTableStructure;
                  } else {
                     classInfo.virtualTableStructure = this.standardClasses.objectClass().virtualTableStructure;
                  }
               }
            } else {
               classInfo.virtualTableStructure = this.standardClasses.objectClass().getVirtualTableStructure();
            }
         } else {
            classInfo.virtualTableStructure = this.standardClasses.objectClass().virtualTableStructure;
         }

         WasmStructure vtStructure = classInfo.virtualTableStructure;
         if (vtStructure != null) {
            ClassInfoStruct classInfoType = this.reflectionTypes.classInfo();
            classInfo.pointer = new WasmGlobal(pointerName, classInfoType.structure().getNonNullReference());
            classInfo.pointer.getInitialValue().add(new WasmStructNewDefault(classInfoType.structure()));
            classInfo.pointer.setImmutable(true);
            this.module.globals.add(classInfo.pointer);
            if (virtualTable != null && virtualTable.isConcrete()) {
               classInfo.virtualTablePointer = new WasmGlobal(vtName, vtStructure.getNonNullReference());
               classInfo.virtualTablePointer.getInitialValue().add(new WasmStructNewDefault(vtStructure));
               if (this.compactMode) {
                  WasmGCClassInfo cls = classInfo;
                  WasmGCVirtualTable vt = virtualTable;
                  this.queue.add(() -> {
                     cls.virtualTablePointer.getInitialValue().clear();
                     this.fillVirtualTableInitializer(cls.virtualTablePointer.getInitialValue().builder(), vt, cls);
                  });
               }

               classInfo.virtualTablePointer.setImmutable(true);
               this.module.globals.add(classInfo.virtualTablePointer);
            }

            if (type instanceof ValueType.Primitive) {
               this.initPrimitiveClass(classInfo, (ValueType.Primitive)type);
            } else if (type instanceof ValueType.Void) {
               this.initVoidClass(classInfo);
            } else if (type instanceof ValueType.Array) {
               this.initArrayClass(classInfo, (ValueType.Array)type);
            } else if (type instanceof ValueType.Object) {
               this.initRegularClass(classInfo, virtualTable, ((ValueType.Object)type).getClassName());
            }

            ClassMetadataRequirements.Info req = this.metadataRequirements.getInfo(type);
            if (req != null && type instanceof ValueType.Primitive && this.metadataRequirements.hasArrayNewInstance()) {
               classInfo.initArrayFunction = this.getArrayConstructor(classInfo.getValueType());
               classInfo.initArrayFunction.setReferenced(true);
            }

            if (!(type instanceof ValueType.Array arr && !(arr.getItemType() instanceof ValueType.Primitive))) {
               WasmFunction supertypeFunction = this.supertypeGenerator.getIsSupertypeFunction(classInfo.getValueType());
               supertypeFunction.setReferenced(true);
               classInfo.supertypeFunction = supertypeFunction;
            }
         }
      }

      return classInfo;
   }

   @Override
   public WasmFunction getArrayConstructor(ValueType type) {
      WasmGCClassInfo arrayInfo = this.getClassInfo(type);
      WasmFunction function = arrayInfo.newArrayFunction;
      if (function == null) {
         function = this.newArrayGenerator.generateNewArrayFunction(type);
         arrayInfo.newArrayFunction = function;
      }

      return function;
   }

   @Override
   public WasmFunction getMultiArrayConstructor(int depth) {
      if (depth >= this.multiArrayFunctions.size()) {
         this.multiArrayFunctions.addAll(Collections.nCopies(depth + 1, null));
      }

      WasmFunction result = this.newArrayGenerator.generateNewMultiArrayFunction(depth);
      this.multiArrayFunctions.set(depth, result);
      return result;
   }

   @Override
   public ReflectionTypes reflectionTypes() {
      return this.reflectionTypes;
   }

   @Override
   public int getThrowableNativeOffset() {
      return this.throwableNativeOffset;
   }

   private void initPrimitiveClass(WasmGCClassInfo classInfo, ValueType.Primitive type) {
      classInfo.initializer = target -> {
         int kind = switch (type.getKind()) {
            case BOOLEAN -> 1;
            case BYTE -> 2;
            case SHORT -> 3;
            case CHARACTER -> 4;
            case INTEGER -> 5;
            case LONG -> 6;
            case FLOAT -> 7;
            case DOUBLE -> 8;
            default -> throw new IllegalArgumentException();
         };
         ClassMetadataRequirements.Info req = this.metadataRequirements.getInfo(type);
         String name = req != null && req.name() ? ReflectionUtil.typeName(type.getKind()) : null;
         this.fillPrimitiveClass(target, classInfo.pointer, name, kind);
      };
   }

   private void initVoidClass(WasmGCClassInfo classInfo) {
      classInfo.initializer = target -> this.fillPrimitiveClass(target, classInfo.pointer, "void", 9);
   }

   private void initRegularClass(WasmGCClassInfo classInfo, WasmGCVirtualTable virtualTable, String name) {
      ClassReader cls = this.classSource.get(name);
      if (!classInfo.isHeapStructure() && this.classInitializerInfo.isDynamicInitializer(name) && cls != null && cls.getMethod(CLINIT_METHOD_DESC) != null) {
         WasmFunctionType clinitType = this.functionTypes.of(null);
         String wasmName = this.names.topLevel(this.names.suggestForClass(name) + "@initializer");
         classInfo.initializerPointer = new WasmGlobal(wasmName, clinitType.getReference());
         classInfo.initializerPointer.getInitialValue().add(new WasmNullConstant(clinitType.getReference()));
         this.module.globals.add(classInfo.initializerPointer);
      }

      classInfo.initializer = target -> {
         int tag;
         if (!classInfo.isHeapStructure()) {
            List<TagRegistry.Range> ranges = this.tagRegistry.getRanges(name);
            tag = ranges.stream().mapToInt(range -> range.lower).min().orElse(0);
         } else {
            tag = 0;
         }

         int flags = !classInfo.isHeapStructure() && cls != null ? ElementModifier.encodeModifiers(cls) : 0;
         target.getGlobal(classInfo.pointer).i32Const(tag).i32Const(flags).call(this.getFillRegularClassFunction());
         ClassMetadataRequirements.Info metadataReq = this.metadataRequirements.getInfo(name);
         ClassInfoStruct classInfoCls = this.reflectionTypes.classInfo();
         if (metadataReq.name()) {
            WasmGlobal namePtr = this.strings.getStringConstant(name).global;
            target.getGlobal(classInfo.pointer).getGlobal(namePtr).structSet(classInfoCls.structure(), classInfoCls.nameIndex());
         }

         if (cls != null) {
            if (metadataReq.simpleName() && cls.getSimpleName() != null) {
               WasmGlobal namePtr = this.strings.getStringConstant(cls.getSimpleName()).global;
               target.getGlobal(classInfo.pointer).getGlobal(namePtr).structSet(classInfoCls.structure(), classInfoCls.simpleNameIndex());
            }

            if (cls.getParent() != null && metadataReq.superclass() && !cls.hasModifier(ElementModifier.INTERFACE)) {
               WasmGCClassInfo parent = this.getClassInfo(cls.getParent());
               target.getGlobal(classInfo.pointer).getGlobal(parent.pointer).structSet(classInfoCls.structure(), classInfoCls.parentIndex());
            }

            if (cls.getOwnerName() != null && metadataReq.enclosingClass()) {
               WasmGCClassInfo owner = this.getClassInfo(cls.getOwnerName());
               target.getGlobal(classInfo.pointer).getGlobal(owner.pointer).structSet(classInfoCls.structure(), classInfoCls.enclosingClassIndex());
            }

            if (cls.getDeclaringClassName() != null && metadataReq.declaringClass()) {
               WasmGCClassInfo owner = this.getClassInfo(cls.getDeclaringClassName());
               target.getGlobal(classInfo.pointer).getGlobal(owner.pointer).structSet(classInfoCls.structure(), classInfoCls.declaringClassIndex());
            }

            if (metadataReq.cloneMethod()) {
               WasmFunction cloneFunction;
               if (this.hierarchy.isSuperType("java.lang.Cloneable", name, false)) {
                  cloneFunction = this.generateCloneFunction(classInfo, name);
               } else {
                  cloneFunction = this.functionProvider.forStaticMethod(new MethodReference(WasmGCSupport.class, "defaultClone", Object.class, Object.class));
               }

               cloneFunction.setReferenced(true);
               target.getGlobal(classInfo.pointer).funcRef(cloneFunction).structSet(classInfoCls.structure(), classInfoCls.cloneFunctionIndex());
            }

            if (metadataReq.enumConstants() && cls.hasModifier(ElementModifier.ENUM)) {
               target.getGlobal(classInfo.pointer)
                  .funcRef(this.createEnumConstantsFunction(cls))
                  .structSet(classInfoCls.structure(), classInfoCls.initEnumConstantsIndex());
            }

            if (metadataReq.interfaces() && !cls.getInterfaces().isEmpty()) {
               target.getGlobal(classInfo.pointer);
               this.createInterfacesArray(target, cls);
               target.structSet(classInfoCls.structure(), classInfoCls.interfacesIndex());
            }

            if (!cls.hasModifier(ElementModifier.INTERFACE) && !cls.hasModifier(ElementModifier.ABSTRACT) && !classInfo.isHeapStructure()) {
               if (classInfoCls.createInstanceIndex() >= 0) {
                  WasmFunction fn = this.createNewInstanceFunction(cls.getName(), classInfo);
                  target.getGlobal(classInfo.pointer).funcRef(fn).structSet(classInfoCls.structure(), classInfoCls.createInstanceIndex());
               }

               if (classInfoCls.initNewInstanceIndex() >= 0) {
                  MethodReader defaultConstructor = cls.getMethod(new MethodDescriptor("<init>", void.class));
                  if (defaultConstructor != null && defaultConstructor.getLevel() == AccessLevel.PUBLIC && defaultConstructor.getProgram() != null) {
                     WasmFunction fn = this.createInitNewInstanceFunction(defaultConstructor);
                     target.getGlobal(classInfo.pointer).funcRef(fn).structSet(classInfoCls.structure(), classInfoCls.initNewInstanceIndex());
                  }
               }
            }
         }

         if (!classInfo.isHeapStructure() && classInfo.virtualTablePointer != null) {
            if (this.compactMode) {
               this.assignVTToClass(classInfo, target);
            } else {
               this.fillVirtualTableMethods(target, virtualTable, classInfo);
            }
         }

         if (!classInfo.isHeapStructure() && classInfo.initializerPointer != null) {
            WasmFunction initFunction = this.functionProvider.forStaticMethod(new MethodReference(name, CLINIT_METHOD_DESC));
            initFunction.setReferenced(true);
            classInfo.initializerPointer.getInitialValue().clear();
            classInfo.initializerPointer.getInitialValue().add(new WasmFunctionReference(initFunction));
            if (this.metadataRequirements.hasClassInit() && metadataReq != null && metadataReq.classInit()) {
               target.getGlobal(classInfo.pointer).funcRef(initFunction).structSet(classInfoCls.structure(), classInfoCls.initializerIndex());
            }
         }
      };
   }

   private void createInterfacesArray(WasmInstructionBuilder builder, ClassReader cls) {
      ClassInfoStruct classInfoStruct = this.reflectionTypes.classInfo();

      for (String itf : cls.getInterfaces()) {
         builder.getGlobal(this.getClassInfo(itf).getPointer());
      }

      builder.arrayNewFixed(classInfoStruct.interfacesType(), cls.getInterfaces().size());
   }

   private void assignClassToVT(WasmGCVirtualTable virtualTable, WasmGCClassInfo classInfo, WasmInstructionBuilder target) {
      if (virtualTable != null) {
         while (virtualTable.getParent() != null) {
            virtualTable = virtualTable.getParent();
         }

         if (!virtualTable.getClassName().equals("java.lang.Object")) {
            return;
         }
      }

      WasmStructure vtStruct = this.standardClasses.objectClass().getVirtualTableStructure();
      target.getGlobal(classInfo.virtualTablePointer).getGlobal(classInfo.pointer).structSet(vtStruct, 0);
      this.assignVTToClass(classInfo, target);
   }

   private void assignVTToClass(WasmGCClassInfo classInfo, WasmInstructionBuilder target) {
      target.getGlobal(classInfo.pointer)
         .getGlobal(classInfo.virtualTablePointer)
         .structSet(this.reflectionTypes.classInfo().structure(), this.reflectionTypes.classInfo().vtableIndex());
   }

   private WasmFunction generateCloneFunction(WasmGCClassInfo classInfo, String className) {
      WasmFunction function = new WasmFunction(
         this.functionTypes.of(this.standardClasses.objectClass().getType(), this.standardClasses.objectClass().getType())
      );
      function.setName(this.names.topLevel(className + "@clone"));
      this.module.functions.add(function);
      WasmLocal objLocal = new WasmLocal(this.standardClasses.objectClass().getType(), "obj");
      WasmLocal castObjLocal = new WasmLocal(classInfo.getType(), "castObj");
      function.add(objLocal);
      function.add(castObjLocal);
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(objLocal).cast(classInfo.getStructure().getReference()).setLocal(castObjLocal);

      for (int i = 0; i < classInfo.structure.getFields().size(); i++) {
         if (i == 1) {
            body.nullConst(WasmType.EQ);
         } else {
            WasmStorageType fieldType = classInfo.structure.getFields().get(i).getType();
            WasmSignedType signedType = null;
            if (fieldType instanceof WasmStorageType.Packed) {
               signedType = WasmSignedType.UNSIGNED;
            }

            body.getLocal(castObjLocal).structGet(classInfo.structure, i, signedType);
         }
      }

      body.structNew(classInfo.structure);
      return function;
   }

   private void fillVirtualTableMethods(WasmInstructionBuilder target, WasmGCVirtualTable virtualTable, WasmGCClassInfo classInfo) {
      WasmGCVirtualTable usedVt = virtualTable.getFirstUsed();
      if (usedVt != null) {
         WasmGlobal global = classInfo.virtualTablePointer;
         WasmStructure structure = classInfo.virtualTableStructure;
         boolean isObject = this.isObject(usedVt);
         if (isObject) {
            target.getGlobal(global).getGlobal(classInfo.pointer).call(this.systemFunctions.getFillObjectVirtualTableFunction());
         }

         for (int i = 0; i < usedVt.getEntries().size(); i++) {
            WasmGCVirtualTableEntry entry = virtualTable.getEntries().get(i);
            MethodReference implementor = virtualTable.implementor(entry);
            if (implementor != null && (!isObject || !implementor.getClassName().equals("java.lang.Object"))) {
               this.fillVirtualTableEntry(target, global, structure, virtualTable, entry);
            }
         }

         if (!isObject) {
            this.assignClassToVT(virtualTable, classInfo, target);
         }
      }
   }

   private void fillVirtualTableInitializer(WasmInstructionBuilder builder, WasmGCVirtualTable virtualTable, WasmGCClassInfo classInfo) {
      WasmGCVirtualTable usedVt = virtualTable.getFirstUsed();
      if (usedVt == null) {
         builder.structNewDefault(classInfo.structure);
      }

      ArrayList<Consumer<WasmInstructionBuilder>> entries = new ArrayList<>(Collections.nCopies(classInfo.virtualTableStructure.getFields().size(), null));

      for (int i = 0; i < usedVt.getEntries().size(); i++) {
         WasmGCVirtualTableEntry entry = virtualTable.getEntries().get(i);
         MethodReference implementor = virtualTable.implementor(entry);
         if (implementor != null) {
            WasmFunction function = this.functionProvider.forInstanceMethod(implementor);
            function.setReferenced(true);
            entries.set(entry.getIndex() + 1, b -> b.funcRef(function));
         }
      }

      entries.set(0, b -> b.getGlobal(classInfo.pointer));

      for (int i = 1; i < classInfo.virtualTableStructure.getFields().size(); i++) {
         if (entries.get(i) == null) {
            WasmType functionType = classInfo.virtualTableStructure.getFields().get(i).getUnpackedType();
            entries.set(i, b -> b.nullConst((WasmType.Reference)functionType));
         }
      }

      for (Consumer<WasmInstructionBuilder> entry : entries) {
         entry.accept(builder);
      }

      builder.structNew(classInfo.virtualTableStructure);
   }

   private boolean isObject(WasmGCVirtualTable vt) {
      while (vt != null) {
         if (vt.getClassName().equals("java.lang.Object")) {
            return true;
         }

         vt = vt.getParent();
      }

      return false;
   }

   private void fillArrayVirtualTableMethods(WasmInstructionBuilder target, WasmGlobal global, WasmGlobal classGlobal) {
      target.getGlobal(global).getGlobal(classGlobal).call(this.systemFunctions.getFillObjectVirtualTableFunction());
   }

   private WasmFunction getArrayLengthFunction(WasmStructure objectStructure) {
      WasmType.CompositeReference arrayTypeRef = (WasmType.CompositeReference)objectStructure.getFields().get(2).getUnpackedType();
      WasmArray arrayType = (WasmArray)arrayTypeRef.composite;
      WasmType elementType = arrayType.getElementType().asUnpackedType();
      if (elementType instanceof WasmType.Reference) {
         if (this.arrayLengthObjectFunction == null) {
            this.arrayLengthObjectFunction = this.createArrayLengthFunction(objectStructure);
         }

         return this.arrayLengthObjectFunction;
      } else {
         return this.createArrayLengthFunction(objectStructure);
      }
   }

   private WasmFunction createArrayLengthFunction(WasmStructure objectStructure) {
      ClassInfoStruct classInfoStruct = this.reflectionTypes.classInfo();
      WasmFunction function = new WasmFunction(
         this.functionTypes.of(WasmType.INT32, classInfoStruct.structure().getReference(), this.standardClasses.objectClass().getType())
      );
      function.setReferenced(true);
      function.setName(this.names.topLevel("Array<*>::length"));
      this.module.functions.add(function);
      WasmLocal classLocal = new WasmLocal(classInfoStruct.structure().getReference(), "this");
      WasmLocal objectLocal = new WasmLocal(this.standardClasses.objectClass().getType(), "object");
      function.add(classLocal);
      function.add(objectLocal);
      function.getBody().builder().getLocal(objectLocal).cast(objectStructure.getNonNullReference()).structGet(objectStructure, 2).arrayLength();
      return function;
   }

   private WasmFunction getArrayGetFunction(ValueType itemType) {
      return itemType instanceof ValueType.Primitive
         ? this.generateArrayGetPrimitiveFunction(((ValueType.Primitive)itemType).getKind())
         : this.getArrayGetObjectFunction();
   }

   private WasmFunction getArrayGetObjectFunction() {
      if (this.arrayGetObjectFunction == null) {
         ClassInfoStruct classInfoStruct = this.reflectionTypes.classInfo();
         this.arrayGetObjectFunction = new WasmFunction(classInfoStruct.getItemFunctionType());
         this.arrayGetObjectFunction.setName(this.names.topLevel(this.names.suggestForArray(this.names.suggestForClass("java.lang.Object") + "::get")));
         this.module.functions.add(this.arrayGetObjectFunction);
         this.arrayGetObjectFunction.setReferenced(true);
         WasmStructure arrayStruct = this.getClassInfo(ValueType.arrayOf(OBJECT_TYPE)).structure;
         WasmType.CompositeReference arrayDataTypeRef = (WasmType.CompositeReference)arrayStruct.getFields().get(2).getUnpackedType();
         WasmArray arrayDataType = (WasmArray)arrayDataTypeRef.composite;
         WasmLocal selfLocal = new WasmLocal(classInfoStruct.structure().getReference(), "this");
         WasmLocal objectLocal = new WasmLocal(this.standardClasses.objectClass().getType(), "object");
         WasmLocal indexLocal = new WasmLocal(WasmType.INT32, "index");
         this.arrayGetObjectFunction.add(selfLocal);
         this.arrayGetObjectFunction.add(objectLocal);
         this.arrayGetObjectFunction.add(indexLocal);
         this.arrayGetObjectFunction
            .getBody()
            .builder()
            .getLocal(objectLocal)
            .cast(arrayStruct.getNonNullReference())
            .structGet(arrayStruct, 2)
            .getLocal(indexLocal)
            .arrayGet(arrayDataType);
      }

      return this.arrayGetObjectFunction;
   }

   private WasmFunction generateArrayGetPrimitiveFunction(PrimitiveType type) {
      ClassInfoStruct classInfoStruct = this.reflectionTypes.classInfo();
      WasmFunction function = new WasmFunction(classInfoStruct.getItemFunctionType());
      function.setName(this.names.topLevel(this.names.suggestForArray(this.names.suggestForType(ValueType.primitive(type))) + "::get"));
      this.module.functions.add(function);
      function.setReferenced(true);
      WasmStructure arrayStruct = this.getClassInfo(ValueType.arrayOf(ValueType.primitive(type))).structure;
      WasmType.CompositeReference arrayDataTypeRef = (WasmType.CompositeReference)arrayStruct.getFields().get(2).getUnpackedType();
      WasmArray arrayDataType = (WasmArray)arrayDataTypeRef.composite;
      WasmLocal classLocal = new WasmLocal(classInfoStruct.structure().getReference(), "this");
      WasmLocal objectLocal = new WasmLocal(this.standardClasses.objectClass().getType(), "object");
      WasmLocal indexLocal = new WasmLocal(WasmType.INT32, "index");
      function.add(classLocal);
      function.add(objectLocal);
      function.add(indexLocal);
      WasmSignedType signedType = null;
      Class<?> primitiveType;
      Class<?> wrapperType;
      switch (type) {
         case BOOLEAN:
            primitiveType = boolean.class;
            wrapperType = Boolean.class;
            signedType = WasmSignedType.UNSIGNED;
            break;
         case BYTE:
            primitiveType = byte.class;
            wrapperType = Byte.class;
            signedType = WasmSignedType.SIGNED;
            break;
         case SHORT:
            primitiveType = short.class;
            wrapperType = Short.class;
            signedType = WasmSignedType.SIGNED;
            break;
         case CHARACTER:
            primitiveType = char.class;
            wrapperType = Character.class;
            signedType = WasmSignedType.UNSIGNED;
            break;
         case INTEGER:
            primitiveType = int.class;
            wrapperType = Integer.class;
            break;
         case LONG:
            primitiveType = long.class;
            wrapperType = Long.class;
            break;
         case FLOAT:
            primitiveType = float.class;
            wrapperType = Float.class;
            break;
         case DOUBLE:
            primitiveType = double.class;
            wrapperType = Double.class;
            break;
         default:
            throw new IllegalArgumentException();
      }

      MethodReference method = new MethodReference(wrapperType, "valueOf", primitiveType, wrapperType);
      WasmFunction wrapFunction = this.functionProvider.forStaticMethod(method);
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(objectLocal).cast(arrayStruct.getNonNullReference()).structGet(arrayStruct, 2).getLocal(indexLocal);
      if (signedType != null) {
         body.arrayGet(arrayDataType, signedType);
      } else {
         body.arrayGet(arrayDataType);
      }

      body.call(wrapFunction);
      return function;
   }

   private WasmFunction getArraySetFunction(ValueType itemType) {
      return itemType instanceof ValueType.Primitive
         ? this.generateArraySetPrimitiveFunction(((ValueType.Primitive)itemType).getKind())
         : this.getArraySetObjectFunction();
   }

   private WasmFunction getArrayCopyFunction(ValueType itemType) {
      return itemType instanceof ValueType.Primitive ? this.createArrayCopyFunction(itemType) : this.getArrayCopyObjectFunction();
   }

   private WasmFunction getArraySetObjectFunction() {
      if (this.arraySetObjectFunction == null) {
         ClassInfoStruct classInfoStruct = this.reflectionTypes.classInfo();
         this.arraySetObjectFunction = new WasmFunction(classInfoStruct.putItemFunctionType());
         this.arraySetObjectFunction.setName(this.names.topLevel(this.names.suggestForArray(this.names.suggestForClass("java.lang.Object")) + "::set"));
         this.module.functions.add(this.arraySetObjectFunction);
         this.arraySetObjectFunction.setReferenced(true);
         WasmStructure arrayStruct = this.getClassInfo(ValueType.arrayOf(OBJECT_TYPE)).structure;
         WasmType.CompositeReference arrayDataTypeRef = (WasmType.CompositeReference)arrayStruct.getFields().get(2).getUnpackedType();
         WasmArray arrayDataType = (WasmArray)arrayDataTypeRef.composite;
         WasmLocal selfLocal = new WasmLocal(classInfoStruct.structure().getReference(), "this");
         WasmLocal objectLocal = new WasmLocal(this.standardClasses.objectClass().getType(), "object");
         WasmLocal indexLocal = new WasmLocal(WasmType.INT32, "index");
         WasmLocal valueLocal = new WasmLocal(this.standardClasses.objectClass().getType(), "value");
         this.arraySetObjectFunction.add(selfLocal);
         this.arraySetObjectFunction.add(objectLocal);
         this.arraySetObjectFunction.add(indexLocal);
         this.arraySetObjectFunction.add(valueLocal);
         this.arraySetObjectFunction
            .getBody()
            .builder()
            .getLocal(objectLocal)
            .cast(arrayStruct.getNonNullReference())
            .structGet(arrayStruct, 2)
            .getLocal(indexLocal)
            .getLocal(valueLocal)
            .arraySet(arrayDataType);
      }

      return this.arraySetObjectFunction;
   }

   private WasmFunction generateArraySetPrimitiveFunction(PrimitiveType type) {
      ClassInfoStruct classInfoStruct = this.reflectionTypes.classInfo();
      WasmFunction function = new WasmFunction(classInfoStruct.putItemFunctionType());
      function.setName(this.names.topLevel(this.names.suggestForArray(this.names.suggestForType(ValueType.primitive(type))) + "::set"));
      this.module.functions.add(function);
      function.setReferenced(true);
      WasmStructure arrayStruct = this.getClassInfo(ValueType.arrayOf(ValueType.primitive(type))).structure;
      WasmType.CompositeReference arrayDataTypeRef = (WasmType.CompositeReference)arrayStruct.getFields().get(2).getUnpackedType();
      WasmArray arrayDataType = (WasmArray)arrayDataTypeRef.composite;
      WasmLocal classLocal = new WasmLocal(classInfoStruct.structure().getReference(), "this");
      WasmLocal objectLocal = new WasmLocal(this.standardClasses.objectClass().getType(), "object");
      WasmLocal indexLocal = new WasmLocal(WasmType.INT32, "index");
      WasmLocal valueLocal = new WasmLocal(this.standardClasses.objectClass().getType(), "value");
      function.add(classLocal);
      function.add(objectLocal);
      function.add(indexLocal);
      function.add(valueLocal);
      Class<?> primitiveType;
      Class<?> wrapperType;
      switch (type) {
         case BOOLEAN:
            primitiveType = boolean.class;
            wrapperType = Boolean.class;
            break;
         case BYTE:
            primitiveType = byte.class;
            wrapperType = Byte.class;
            break;
         case SHORT:
            primitiveType = short.class;
            wrapperType = Short.class;
            break;
         case CHARACTER:
            primitiveType = char.class;
            wrapperType = Character.class;
            break;
         case INTEGER:
            primitiveType = int.class;
            wrapperType = Integer.class;
            break;
         case LONG:
            primitiveType = long.class;
            wrapperType = Long.class;
            break;
         case FLOAT:
            primitiveType = float.class;
            wrapperType = Float.class;
            break;
         case DOUBLE:
            primitiveType = double.class;
            wrapperType = Double.class;
            break;
         default:
            throw new IllegalArgumentException();
      }

      MethodReference method = new MethodReference(wrapperType, primitiveType.getName() + "Value", primitiveType);
      WasmFunction unwrapFunction = this.functionProvider.forInstanceMethod(method);
      function.getBody()
         .builder()
         .getLocal(objectLocal)
         .cast(arrayStruct.getNonNullReference())
         .structGet(arrayStruct, 2)
         .getLocal(indexLocal)
         .getLocal(valueLocal)
         .cast(this.getClassInfo(ValueType.parse(wrapperType)).getType())
         .call(unwrapFunction)
         .arraySet(arrayDataType);
      return function;
   }

   private WasmFunction getArrayCopyObjectFunction() {
      if (this.arrayCopyObjectFunction == null) {
         this.arrayCopyObjectFunction = this.createArrayCopyFunction(OBJECT_TYPE);
      }

      return this.arrayCopyObjectFunction;
   }

   private WasmFunction createArrayCopyFunction(ValueType type) {
      ClassInfoStruct classInfoStruct = this.reflectionTypes.classInfo();
      WasmFunction function = new WasmFunction(classInfoStruct.copyArrayFunctionType());
      function.setName(this.names.topLevel(this.names.suggestForArray(this.names.suggestForType(type)) + "::copy"));
      this.module.functions.add(function);
      function.setReferenced(true);
      WasmStructure arrayStruct = this.getClassInfo(ValueType.arrayOf(type)).structure;
      WasmType.CompositeReference arrayDataTypeRef = (WasmType.CompositeReference)arrayStruct.getFields().get(2).getUnpackedType();
      WasmArray arrayDataType = (WasmArray)arrayDataTypeRef.composite;
      WasmLocal thisLocal = new WasmLocal(classInfoStruct.structure().getReference(), "this");
      WasmLocal sourceLocal = new WasmLocal(this.standardClasses.objectClass().getType(), "source");
      WasmLocal sourceIndexLocal = new WasmLocal(WasmType.INT32, "sourceIndex");
      WasmLocal targetLocal = new WasmLocal(this.standardClasses.objectClass().getType(), "target");
      WasmLocal targetIndexLocal = new WasmLocal(WasmType.INT32, "targetIndex");
      WasmLocal countLocal = new WasmLocal(WasmType.INT32, "count");
      function.add(thisLocal);
      function.add(sourceLocal);
      function.add(sourceIndexLocal);
      function.add(targetLocal);
      function.add(targetIndexLocal);
      function.add(countLocal);
      function.getBody()
         .builder()
         .getLocal(targetLocal)
         .cast(arrayStruct.getNonNullReference())
         .structGet(arrayStruct, 2)
         .getLocal(targetIndexLocal)
         .getLocal(sourceLocal)
         .cast(arrayStruct.getNonNullReference())
         .structGet(arrayStruct, 2)
         .getLocal(sourceIndexLocal)
         .getLocal(countLocal)
         .arrayCopy(arrayDataType, arrayDataType);
      return function;
   }

   private void fillVirtualTableEntry(
      WasmInstructionBuilder target, WasmGlobal global, WasmStructure structure, WasmGCVirtualTable virtualTable, WasmGCVirtualTableEntry entry
   ) {
      this.fillVirtualTableEntry(target, b -> b.getGlobal(global), structure, virtualTable, entry);
   }

   void fillVirtualTableEntry(
      WasmInstructionBuilder target,
      Consumer<WasmInstructionBuilder> instance,
      WasmStructure structure,
      WasmGCVirtualTable virtualTable,
      WasmGCVirtualTableEntry entry
   ) {
      MethodReference implementor = virtualTable.implementor(entry);
      if (entry.getOrigin() != virtualTable) {
         structure = this.getClassInfo(entry.getOrigin().getClassName()).virtualTableStructure;
      }

      if (implementor != null && !entry.getMethod().equals(GET_CLASS_METHOD)) {
         int fieldIndex = 1 + entry.getIndex();
         WasmType.CompositeReference expectedType = (WasmType.CompositeReference)structure.getFields().get(fieldIndex).getUnpackedType();
         WasmFunctionType expectedFunctionType = (WasmFunctionType)expectedType.composite;
         WasmFunction function = this.functionProvider.forInstanceMethod(implementor);
         if (!entry.getOrigin().getClassName().equals(implementor.getClassName()) || expectedFunctionType != function.getType()) {
            WasmFunction wrapperFunction = new WasmFunction(expectedFunctionType);
            wrapperFunction.setName(this.names.topLevel(this.names.suggestForMethod(implementor) + "@caller"));
            this.module.functions.add(wrapperFunction);
            WasmLocal instanceParam = new WasmLocal(this.getClassInfo(virtualTable.getClassName()).getType());
            wrapperFunction.add(instanceParam);
            WasmType.CompositeReference castTarget = this.getClassInfo(implementor.getClassName()).getStructure().getReference();
            WasmLocal[] params = new WasmLocal[entry.getMethod().parameterCount()];

            for (int i = 0; i < entry.getMethod().parameterCount(); i++) {
               params[i] = new WasmLocal(this.typeMapper.mapType(entry.getMethod().parameterType(i)));
               wrapperFunction.add(params[i]);
            }

            WasmInstructionBuilder wrapBody = wrapperFunction.getBody().builder();
            wrapBody.getLocal(instanceParam).cast(castTarget);

            for (WasmLocal param : params) {
               wrapBody.getLocal(param);
            }

            wrapBody.call(function);
            function = wrapperFunction;
         }

         function.setReferenced(true);
         instance.accept(target);
         target.funcRef(function).structSet(structure, fieldIndex);
      }
   }

   private WasmFunction generateArrayCloneMethod(WasmStructure objectStructure, ValueType itemType) {
      WasmType.CompositeReference arrayTypeRef = (WasmType.CompositeReference)objectStructure.getFields().get(2).getUnpackedType();
      WasmArray arrayType = (WasmArray)arrayTypeRef.composite;
      WasmFunctionType type = this.typeMapper.getFunctionType(this.standardClasses.objectClass().getType(), CLONE_METHOD_DESC);
      WasmFunction function = new WasmFunction(type);
      function.setName(this.names.topLevel(this.names.suggestForArray(this.names.suggestForType(itemType)) + "::clone"));
      this.module.functions.add(function);
      WasmLocal instanceLocal = new WasmLocal(this.standardClasses.objectClass().getType(), "instance");
      WasmLocal originalLocal = new WasmLocal(objectStructure.getReference(), "original");
      WasmLocal originalDataLocal = new WasmLocal(arrayType.getNonNullReference(), "originalData");
      WasmLocal dataCopyLocal = new WasmLocal(arrayType.getNonNullReference(), "resultData");
      function.add(instanceLocal);
      function.add(originalLocal);
      function.add(originalDataLocal);
      function.add(dataCopyLocal);
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(instanceLocal).cast(objectStructure.getNonNullReference()).setLocal(originalLocal);
      body.getLocal(originalLocal).structGet(objectStructure, 2).setLocal(originalDataLocal);
      body.getLocal(originalDataLocal).arrayLength().arrayNewDefault(arrayType).setLocal(dataCopyLocal);
      body.getLocal(dataCopyLocal)
         .i32Const(0)
         .getLocal(originalDataLocal)
         .i32Const(0)
         .getLocal(originalDataLocal)
         .arrayLength()
         .arrayCopy(arrayType, arrayType);
      body.getLocal(originalLocal).structGet(objectStructure, 0).nullConst(WasmType.EQ).getLocal(dataCopyLocal).structNew(objectStructure);
      return function;
   }

   private void initRegularVirtualTableStructure(WasmGCClassInfo classInfo, String className) {
      WasmGCVirtualTable virtualTable = this.virtualTables.lookup(className);
      String wasmName = this.names.topLevel("VT<" + this.names.suggestForClass(className) + ">");
      WasmStructure structure = new WasmStructure(wasmName, fields -> {
         this.addVirtualTableBaseFields(fields);
         this.addVirtualTableFields(fields, virtualTable);
      });
      classInfo.virtualTableStructure = structure;
      this.nonInitializedStructures.add(structure);
      WasmGCVirtualTable usedParent = virtualTable.getUsedParent();
      WasmStructure supertype = usedParent != null ? this.getClassInfo(usedParent.getClassName()).getVirtualTableStructure() : null;
      structure.setSupertype(supertype);
      this.module.types.add(structure);
   }

   private void addVirtualTableBaseFields(List<WasmField> fields) {
      WasmField classField = new WasmField(this.reflectionTypes.classInfo().structure().getReference().asStorage());
      classField.setName(this.names.forMemberField(FAKE_CLASS_FIELD));
      fields.add(classField);
   }

   private void addSystemFields(List<WasmField> fields) {
      WasmField vtField = new WasmField(this.standardClasses.objectClass().getVirtualTableStructure().getReference());
      vtField.setName(this.names.forMemberField(FAKE_VT_FIELD));
      fields.add(vtField);
      WasmField monitorField = new WasmField(WasmType.EQ);
      monitorField.setName(this.names.forMemberField(FAKE_MONITOR_FIELD));
      fields.add(monitorField);
   }

   private void addVirtualTableFields(List<WasmField> fields, WasmGCVirtualTable virtualTable) {
      for (WasmGCVirtualTableEntry entry : virtualTable.getEntries()) {
         WasmType.Reference receiverType = this.compactMode ? WasmType.ANY : this.getClassInfo(entry.getOrigin().getClassName()).getType();
         WasmFunctionType functionType = this.typeMapper.getFunctionType(receiverType, entry.getMethod());
         WasmField field = new WasmField(functionType.getReference().asStorage());
         field.setName(this.names.forVirtualMethod(entry.getMethod()));
         fields.add(field);
      }
   }

   private void initArrayClass(WasmGCClassInfo classInfo, ValueType.Array type) {
      if (type.getItemType() instanceof ValueType.Primitive) {
         classInfo.initializer = target -> {
            WasmGCClassInfo itemTypeInfo = this.getClassInfo(type.getItemType());
            target.getGlobal(classInfo.pointer).getGlobal(itemTypeInfo.pointer).call(this.getFillArrayClassFunction());
            if (!this.compactMode) {
               this.fillArrayVirtualTableMethods(target, classInfo.virtualTablePointer, classInfo.pointer);
            } else {
               this.assignVTToClass(classInfo, target);
            }

            ClassMetadataRequirements.Info metadataReq = this.metadataRequirements.getInfo(type);
            if (metadataReq.cloneMethod()) {
               WasmFunction cloneFunction = this.generateArrayCloneMethod(classInfo.structure, type.getItemType());
               cloneFunction.setReferenced(true);
               ClassInfoStruct classInfoType = this.reflectionTypes.classInfo();
               target.getGlobal(classInfo.pointer).funcRef(cloneFunction).structSet(classInfoType.structure(), classInfoType.cloneFunctionIndex());
            }

            if (metadataReq.name() && type.getItemType() instanceof ValueType.Primitive) {
               WasmGCStringConstant name = this.strings.getStringConstant(type.toString());
               ClassInfoStruct classInfoType = this.reflectionTypes.classInfo();
               target.getGlobal(classInfo.pointer).getGlobal(name.global).structSet(classInfoType.structure(), classInfoType.nameIndex());
            }

            ValueType itemType = type.getItemType();
            if (metadataReq.arrayLength()) {
               WasmFunction lengthFunction = this.getArrayLengthFunction(classInfo.structure);
               ClassInfoStruct classInfoType = this.reflectionTypes.classInfo();
               target.getGlobal(classInfo.pointer).funcRef(lengthFunction).structSet(classInfoType.structure(), classInfoType.arrayLengthIndex());
            }

            if (metadataReq.arrayGet()) {
               WasmFunction getFunction = this.getArrayGetFunction(itemType);
               ClassInfoStruct classInfoType = this.reflectionTypes.classInfo();
               target.getGlobal(classInfo.pointer).funcRef(getFunction).structSet(classInfoType.structure(), classInfoType.getItemIndex());
            }

            if (metadataReq.arraySet()) {
               WasmFunction setFunction = this.getArraySetFunction(itemType);
               ClassInfoStruct classInfoType = this.reflectionTypes.classInfo();
               target.getGlobal(classInfo.pointer).funcRef(setFunction).structSet(classInfoType.structure(), classInfoType.putItemIndex());
            }

            if (metadataReq.arrayCopy()) {
               WasmFunction copyFunction = this.getArrayCopyFunction(itemType);
               ClassInfoStruct classInfoType = this.reflectionTypes.classInfo();
               target.getGlobal(classInfo.pointer).funcRef(copyFunction).structSet(classInfoType.structure(), classInfoType.copyArrayIndex());
            }
         };
      }
   }

   private void fillPrimitiveClass(WasmInstructionBuilder builder, WasmGlobal global, String name, int kind) {
      builder.getGlobal(global);
      if (this.metadataRequirements.hasName()) {
         if (name != null) {
            builder.getGlobal(this.strings.getStringConstant(name).global);
         } else {
            builder.nullConst(this.standardClasses.stringClass().getType());
         }
      }

      builder.i32Const(kind).call(this.getCreatePrimitiveClassFunction());
   }

   @Override
   public int getFieldIndex(FieldReference fieldRef) {
      WasmGCClassInfo classInfo = this.getClassInfo(fieldRef.getClassName());
      if (classInfo.isHeapStructure()) {
         throw new IllegalArgumentException("Given field belongs to heap structure");
      }

      classInfo.structure.init();
      return this.fieldIndexes.getOrDefault(fieldRef, -1);
   }

   @Override
   public int getHeapFieldOffset(FieldReference fieldRef) {
      WasmGCClassInfo classInfo = this.getClassInfo(fieldRef.getClassName());
      if (!classInfo.isHeapStructure()) {
         throw new IllegalArgumentException("Given field does not belong to heap structure");
      }

      if (classInfo.heapFieldOffsets == null) {
         this.fillHeapFieldOffsets(classInfo);
      }

      return classInfo.heapFieldOffsets.get(fieldRef.getFieldName());
   }

   @Override
   public int getHeapSize(String className) {
      WasmGCClassInfo classInfo = this.getClassInfo(className);
      if (!classInfo.isHeapStructure()) {
         throw new IllegalArgumentException("Given field does not belong to heap structure");
      }

      if (classInfo.heapFieldOffsets == null) {
         this.fillHeapFieldOffsets(classInfo);
      }

      return classInfo.heapSize;
   }

   @Override
   public int getHeapAlignment(String className) {
      WasmGCClassInfo classInfo = this.getClassInfo(className);
      if (!classInfo.isHeapStructure()) {
         throw new IllegalArgumentException("Given field does not belong to heap structure");
      }

      if (classInfo.heapFieldOffsets == null) {
         this.fillHeapFieldOffsets(classInfo);
      }

      return classInfo.heapAlignment;
   }

   @Override
   public WasmGCReflectionProvider reflection() {
      return this.reflectionGenerator;
   }

   private void fillHeapFieldOffsets(WasmGCClassInfo classInfo) {
      ObjectIntHashMap<String> offsets = new ObjectIntHashMap();
      classInfo.heapFieldOffsets = offsets;
      String className = ((ValueType.Object)classInfo.getValueType()).getClassName();
      int offset = 0;
      int alignment = 1;
      if (!className.equals(Structure.class.getName())) {
         ClassReader cls = this.classSource.get(className);
         WasmGCClassInfo parentInfo = this.getClassInfo(cls.getParent());
         if (parentInfo.heapFieldOffsets == null) {
            this.fillHeapFieldOffsets(parentInfo);
         }

         offset = parentInfo.heapSize;

         for (FieldReader field : cls.getFields()) {
            if (!field.hasModifier(ElementModifier.STATIC)) {
               int size = WasmGeneratorUtil.getNativeTypeSize(field.getType());
               if (offset == 0) {
                  alignment = size;
               }

               offset = WasmGeneratorUtil.align(offset, size);
               offsets.put(field.getName(), offset);
               offset += size;
            }
         }
      }

      classInfo.heapSize = offset;
      classInfo.heapAlignment = alignment;
   }

   @Override
   public WasmGlobal getStaticFieldLocation(FieldReference fieldRef) {
      return this.staticFieldLocations.computeIfAbsent(fieldRef, this::generateStaticFieldLocation);
   }

   private WasmGlobal generateStaticFieldLocation(FieldReference fieldRef) {
      ValueType javaType = null;
      Object initValue = null;
      ClassReader cls = this.classSource.get(fieldRef.getClassName());
      if (cls != null) {
         FieldReader field = cls.getField(fieldRef.getFieldName());
         if (field != null) {
            javaType = field.getType();
            initValue = field.getInitialValue();
         }
      }

      if (javaType == null) {
         javaType = ValueType.object("java.lang.Object");
      }

      WasmType type = this.typeMapper.mapType(javaType);
      String wasmName = this.names.topLevel(this.names.suggestForStaticField(fieldRef));
      WasmGlobal global = new WasmGlobal(wasmName, type);
      WasmInstructionBuilder initBuilder = global.getInitialValue().builder();
      if (initValue != null) {
         this.staticInitialValue(initBuilder, initValue);
      } else {
         this.defaultValueOfType(initBuilder, type);
      }

      this.dynamicInitialValue(global, initValue);
      this.module.globals.add(global);
      return global;
   }

   private void staticInitialValue(WasmInstructionBuilder builder, Object value) {
      if (value instanceof Boolean) {
         builder.i32Const((Boolean)value ? 1 : 0);
      } else if (value instanceof Byte) {
         builder.i32Const((Byte)value);
      } else if (value instanceof Short) {
         builder.i32Const((Short)value);
      } else if (value instanceof Character) {
         builder.i32Const((Character)value);
      } else if (value instanceof Integer) {
         builder.i32Const((Integer)value);
      } else if (value instanceof Long) {
         builder.i64Const((Long)value);
      } else if (value instanceof Float) {
         builder.f32Const((Float)value);
      } else if (value instanceof Double) {
         builder.f64Const((Double)value);
      } else {
         builder.nullConst(this.standardClasses.stringClass().getType());
      }
   }

   private void defaultValueOfType(WasmInstructionBuilder builder, WasmType type) {
      if (type == WasmType.INT32) {
         builder.i32Const(0);
      } else if (type == WasmType.INT64) {
         builder.i64Const(0L);
      } else if (type == WasmType.FLOAT32) {
         builder.f32Const(0.0F);
      } else if (type == WasmType.FLOAT64) {
         builder.f64Const(0.0);
      } else {
         if (!(type instanceof WasmType.Reference)) {
            throw new IllegalArgumentException();
         }

         builder.nullConst((WasmType.Reference)type);
      }
   }

   private void dynamicInitialValue(WasmGlobal global, Object value) {
      if (value instanceof String) {
         WasmGlobal constant = this.strings.getStringConstant((String)value).global;
         this.staticFieldInitializers.add(function -> function.getBody().builder().getGlobal(constant).setGlobal(global));
      } else if (value instanceof ValueType) {
         WasmGlobal constant = this.getClassInfo((ValueType)value).pointer;
         this.staticFieldInitializers.add(function -> function.getBody().builder().getGlobal(constant).setGlobal(global));
      }
   }

   private void fillFields(WasmGCClassInfo classInfo, List<WasmField> fields, ValueType type) {
      this.addSystemFields(fields);
      if (type instanceof ValueType.Object) {
         this.fillClassFields(fields, ((ValueType.Object)type).getClassName());
      } else if (type instanceof ValueType.Array) {
         this.fillArrayFields(classInfo, ((ValueType.Array)type).getItemType());
      }
   }

   private void fillClassFields(List<WasmField> fields, String className) {
      ClassReader classReader = this.classSource.get(className);
      if (classReader != null && !classReader.hasModifier(ElementModifier.INTERFACE)) {
         this.fillSimpleClassFields(fields, className);
      } else {
         this.fillSimpleClassFields(fields, "java.lang.Object");
      }
   }

   private void fillSimpleClassFields(List<WasmField> fields, String className) {
      ClassReader classReader = this.classSource.get(className);
      if (classReader.getParent() != null) {
         this.fillClassFields(fields, classReader.getParent());
      }

      if (className.equals("java.lang.ref.WeakReference")) {
         WasmField field = new WasmField(WasmType.EXTERN.asStorage(), "nativeRef");
         fields.add(field);
      } else {
         for (FieldReader field : classReader.getFields()) {
            if ((!className.equals("java.lang.Object") || !field.getName().equals("monitor"))
               && (!className.equals("java.lang.Class") || !field.getName().equals("platformClass"))
               && !field.hasModifier(ElementModifier.STATIC)) {
               this.fieldIndexes.putIfAbsent(field.getReference(), fields.size());
               WasmField wasmField = new WasmField(this.typeMapper.mapStorageType(field.getType()), this.names.forMemberField(field.getReference()));
               fields.add(wasmField);
            }
         }
      }

      if (className.equals(StringInternPool.class.getName() + "$Entry")) {
         WasmField field = new WasmField(WasmType.EXTERN.asStorage(), "nativeRef");
         fields.add(field);
      }

      if (className.equals("java.lang.Throwable")) {
         this.throwableNativeOffset = fields.size();
         WasmField field = new WasmField(WasmType.EXTERN.asStorage(), "nativeRef");
         fields.add(field);
      }
   }

   private void fillArrayFields(WasmGCClassInfo classInfo, ValueType elementType) {
      WasmArray wasmArray;
      if (elementType instanceof ValueType.Primitive) {
         wasmArray = this.getPrimitiveArrayType(switch (((ValueType.Primitive)elementType).getKind()) {
            case BOOLEAN, BYTE -> WasmStorageType.INT8;
            case SHORT, CHARACTER -> WasmStorageType.INT16;
            case INTEGER -> WasmType.INT32.asStorage();
            case LONG -> WasmType.INT64.asStorage();
            case FLOAT -> WasmType.FLOAT32.asStorage();
            case DOUBLE -> WasmType.FLOAT64.asStorage();
            default -> throw new IllegalArgumentException();
         });
      } else {
         wasmArray = this.getObjectArrayType();
      }

      classInfo.structure.getFields().add(new WasmField(wasmArray.getNonNullReference().asStorage(), this.arrayDataFieldName()));
   }

   @Override
   public WasmArray getPrimitiveArrayType(WasmStorageType type) {
      WasmArray array = this.arrayTypes.get(type);
      if (array == null) {
         array = new WasmArray(null, type);
         this.module.types.add(array);
         this.arrayTypes.put(type, array);
      }

      return array;
   }

   @Override
   public WasmArray getObjectArrayType() {
      WasmArray wasmArray = this.objectArrayType;
      if (wasmArray == null) {
         WasmStorageType.Regular wasmElementType = this.standardClasses.objectClass().getType().asStorage();
         String wasmArrayName = this.names.topLevel(this.names.suggestForType(ValueType.arrayOf(ValueType.object("java.lang.Object"))) + "$Data");
         wasmArray = new WasmArray(wasmArrayName, wasmElementType);
         this.module.types.add(wasmArray);
         this.objectArrayType = wasmArray;
      }

      return wasmArray;
   }

   private String arrayDataFieldName() {
      if (this.arrayDataFieldName == null) {
         this.arrayDataFieldName = this.names.structureField("@data");
      }

      return this.arrayDataFieldName;
   }

   private WasmFunction getCreatePrimitiveClassFunction() {
      if (this.createPrimitiveClassFunction == null) {
         this.createPrimitiveClassFunction = this.createCreatePrimitiveClassFunction();
      }

      return this.createPrimitiveClassFunction;
   }

   private WasmFunction createCreatePrimitiveClassFunction() {
      ArrayList<WasmType> params = new ArrayList<>();
      ClassInfoStruct classInfoType = this.reflectionTypes.classInfo();
      params.add(classInfoType.structure().getReference());
      if (this.metadataRequirements.hasName()) {
         params.add(this.standardClasses.stringClass().getType());
      }

      params.add(WasmType.INT32);
      WasmFunctionType functionType = this.functionTypes.of(null, params.toArray(new WasmType[0]));
      WasmFunction function = new WasmFunction(functionType);
      function.setName(this.names.topLevel("teavm@fillPrimitiveClass"));
      this.module.functions.add(function);
      WasmLocal targetVar = new WasmLocal(classInfoType.structure().getReference(), "target");
      function.add(targetVar);
      WasmLocal nameVar;
      if (this.metadataRequirements.hasName()) {
         nameVar = new WasmLocal(this.standardClasses.stringClass().getType(), "name");
         function.add(nameVar);
      } else {
         nameVar = null;
      }

      WasmLocal kindVar = new WasmLocal(WasmType.INT32, "kind");
      function.add(kindVar);
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(targetVar).i32Const(17).structSet(classInfoType.structure(), classInfoType.modifiersIndex());
      body.getLocal(targetVar).getLocal(kindVar).structSet(classInfoType.structure(), classInfoType.primitiveKindIndex());
      if (nameVar != null) {
         body.getLocal(targetVar).getLocal(nameVar).structSet(classInfoType.structure(), classInfoType.nameIndex());
      }

      body.getLocal(targetVar).i32Const(Integer.MAX_VALUE).structSet(classInfoType.structure(), classInfoType.tagIndex());
      return function;
   }

   private WasmFunction getFillRegularClassFunction() {
      if (this.fillRegularClassFunction == null) {
         this.fillRegularClassFunction = this.createFillRegularClassFunction();
      }

      return this.fillRegularClassFunction;
   }

   private WasmFunction createFillRegularClassFunction() {
      ClassInfoStruct classInfoType = this.reflectionTypes.classInfo();
      WasmFunctionType functionType = this.functionTypes.of(null, classInfoType.structure().getReference(), WasmType.INT32, WasmType.INT32);
      WasmFunction function = new WasmFunction(functionType);
      this.module.functions.add(function);
      function.setName(this.names.topLevel("teavm@fillRegularClass"));
      WasmLocal targetVar = new WasmLocal(classInfoType.structure().getReference(), "target");
      WasmLocal idVar = new WasmLocal(WasmType.INT32, "id");
      WasmLocal flagsVar = new WasmLocal(WasmType.INT32, "flags");
      function.add(targetVar);
      function.add(idVar);
      function.add(flagsVar);
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(targetVar)
         .getGlobal(this.standardClasses.objectClass().virtualTablePointer)
         .structSet(classInfoType.structure(), classInfoType.vtableIndex());
      body.getLocal(targetVar).getLocal(idVar).structSet(classInfoType.structure(), classInfoType.tagIndex());
      body.getLocal(targetVar).getLocal(flagsVar).structSet(classInfoType.structure(), classInfoType.modifiersIndex());
      if (this.metadataRequirements.hasArrayNewInstance()) {
         WasmFunction newArrayFunction = this.newArrayGenerator.getNewObjectArrayFunction();
         newArrayFunction.setReferenced(true);
         body.getLocal(targetVar).funcRef(newArrayFunction).structSet(classInfoType.structure(), classInfoType.newArrayFunctionIndex());
      }

      if (classInfoType.nextClassIndex() >= 0) {
         body.getLocal(targetVar).getGlobal(classInfoType.firstClassGlobal()).structSet(classInfoType.structure(), classInfoType.nextClassIndex());
         body.getLocal(targetVar).setGlobal(classInfoType.firstClassGlobal());
      }

      return function;
   }

   @Override
   public WasmFunction getGetArrayClassFunction() {
      if (this.getArrayClassFunction == null) {
         this.getArrayClassFunction = this.createGetArrayClassFunction();
      }

      return this.getArrayClassFunction;
   }

   private WasmFunction createGetArrayClassFunction() {
      ClassInfoStruct classInfoType = this.reflectionTypes.classInfo();
      WasmFunctionType functionType = this.functionTypes.of(classInfoType.structure().getNonNullReference(), classInfoType.structure().getReference());
      WasmFunction function = new WasmFunction(functionType);
      WasmGCClassInfo arrayType = this.getClassInfo(ValueType.arrayOf(ValueType.object("java.lang.Object")));
      this.module.functions.add(function);
      function.setName(this.names.topLevel("teavm@getArrayClass"));
      this.queue.add(() -> {
         WasmLocal itemTypeVar = new WasmLocal(classInfoType.structure().getReference(), "itemType");
         WasmLocal resultVar = new WasmLocal(classInfoType.structure().getNonNullReference(), "result");
         WasmLocal vtVar = new WasmLocal(this.standardClasses.objectClass().getVirtualTableStructure().getNonNullReference(), "vt");
         function.add(itemTypeVar);
         function.add(resultVar);
         function.add(vtVar);
         WasmInstructionBuilder body = function.getBody().builder();
         WasmInstructionBuilder blockBody = body.block();
         blockBody.getLocal(itemTypeVar).structGet(classInfoType.structure(), classInfoType.arrayTypeIndex()).nullBranch(WasmNullCondition.NULL, blockBody);
         blockBody.return_();
         body.structNewDefault(classInfoType.structure()).setLocal(resultVar);
         body.getLocal(itemTypeVar).getLocal(resultVar).structSet(classInfoType.structure(), classInfoType.arrayTypeIndex());
         body.getLocal(resultVar).getLocal(itemTypeVar).call(this.getFillArrayClassFunction());
         WasmFunction supertypeFunction = this.supertypeGenerator.getIsArraySupertypeFunction();
         supertypeFunction.setReferenced(true);
         body.getLocal(resultVar).funcRef(supertypeFunction).structSet(classInfoType.structure(), classInfoType.supertypeFunctionIndex());
         WasmFunction cloneFunction = this.generateArrayCloneMethod(arrayType.structure, ValueType.object("java.lang.Object"));
         cloneFunction.setReferenced(true);
         body.getLocal(resultVar).funcRef(cloneFunction).structSet(classInfoType.structure(), classInfoType.cloneFunctionIndex());
         body.structNewDefault(this.standardClasses.objectClass().getVirtualTableStructure()).setLocal(vtVar);
         body.getLocal(vtVar).getLocal(resultVar).call(this.systemFunctions.getFillObjectVirtualTableFunction());
         body.getLocal(resultVar);
      });
      return function;
   }

   private WasmFunction getFillArrayClassFunction() {
      if (this.fillArrayClassFunction == null) {
         this.fillArrayClassFunction = this.createFillArrayClassFunction();
      }

      return this.fillArrayClassFunction;
   }

   private WasmFunction createFillArrayClassFunction() {
      ClassInfoStruct classInfoType = this.reflectionTypes.classInfo();
      WasmFunctionType functionType = this.functionTypes.of(null, classInfoType.structure().getReference(), classInfoType.structure().getReference());
      WasmFunction function = new WasmFunction(functionType);
      this.module.functions.add(function);
      function.setName(this.names.topLevel("teavm@fillArrayClass"));
      WasmLocal targetVar = new WasmLocal(classInfoType.structure().getReference(), "target");
      WasmLocal itemVar = new WasmLocal(classInfoType.structure().getReference(), "item");
      function.add(targetVar);
      function.add(itemVar);
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(targetVar)
         .getGlobal(this.standardClasses.objectClass().getVirtualTablePointer())
         .structSet(classInfoType.structure(), classInfoType.vtableIndex());
      body.getLocal(targetVar).i32Const(17).structSet(classInfoType.structure(), classInfoType.modifiersIndex());
      body.getLocal(targetVar).getLocal(itemVar).structSet(classInfoType.structure(), classInfoType.itemTypeIndex());
      body.getLocal(itemVar).getLocal(targetVar).structSet(classInfoType.structure(), classInfoType.arrayTypeIndex());
      body.getLocal(targetVar).i32Const(0).structSet(classInfoType.structure(), classInfoType.tagIndex());
      if (classInfoType.parentIndex() >= 0) {
         body.getLocal(targetVar).getGlobal(this.standardClasses.objectClass().pointer).structSet(classInfoType.structure(), classInfoType.parentIndex());
      }

      if (classInfoType.arrayLengthIndex() >= 0) {
         WasmGCClassInfo arrayClass = this.getClassInfo(ValueType.arrayOf(ValueType.object("java.lang.Object")));
         WasmFunction lengthFunction = this.getArrayLengthFunction(arrayClass.structure);
         body.getLocal(targetVar).funcRef(lengthFunction).structSet(classInfoType.structure(), classInfoType.arrayLengthIndex());
      }

      if (classInfoType.getItemIndex() >= 0) {
         body.getLocal(targetVar).funcRef(this.getArrayGetObjectFunction()).structSet(classInfoType.structure(), classInfoType.getItemIndex());
      }

      if (classInfoType.putItemIndex() >= 0) {
         body.getLocal(targetVar).funcRef(this.getArraySetObjectFunction()).structSet(classInfoType.structure(), classInfoType.putItemIndex());
      }

      if (classInfoType.newArrayFunctionIndex() >= 0) {
         WasmFunction newArrayFunction = this.newArrayGenerator.getNewObjectArrayFunction();
         newArrayFunction.setReferenced(true);
         body.getLocal(targetVar).funcRef(newArrayFunction).structSet(classInfoType.structure(), classInfoType.newArrayFunctionIndex());
      }

      if (classInfoType.copyArrayIndex() >= 0) {
         WasmFunction copyFunction = this.getArrayCopyObjectFunction();
         body.getLocal(targetVar).funcRef(copyFunction).structSet(classInfoType.structure(), classInfoType.copyArrayIndex());
      }

      return function;
   }

   private WasmFunction createEnumConstantsFunction(ClassReader cls) {
      ClassInfoStruct classInfoStruct = this.reflectionTypes.classInfo();
      WasmFunction function = new WasmFunction(this.functionTypes.of(classInfoStruct.enumConstantsType().getReference()));
      function.setName(this.names.topLevel(cls.getName() + "@constants"));
      this.module.functions.add(function);
      function.setReferenced(true);
      List<? extends FieldReader> fields = cls.getFields()
         .stream()
         .filter(fieldx -> fieldx.hasModifier(ElementModifier.ENUM))
         .filter(fieldx -> fieldx.hasModifier(ElementModifier.STATIC))
         .collect(Collectors.toList());
      WasmInstructionBuilder body = function.getBody().builder();

      for (FieldReader field : fields) {
         body.getGlobal(this.getStaticFieldLocation(field.getReference()));
      }

      body.arrayNewFixed(classInfoStruct.enumConstantsType(), fields.size());
      return function;
   }

   private WasmFunction createNewInstanceFunction(String className, WasmGCClassInfo classInfo) {
      WasmGCClassInfo objCls = this.standardClasses.objectClass();
      WasmFunction function = new WasmFunction(this.functionTypes.of(objCls.getType()));
      function.setName(this.names.topLevel(className + "@newInstance"));
      WasmLocal resultLocal = new WasmLocal(classInfo.getType(), "result");
      function.add(resultLocal);
      WasmInstructionBuilder body = function.getBody().builder();
      body.structNewDefault(classInfo.getStructure()).setLocal(resultLocal);
      body.getLocal(resultLocal).getGlobal(classInfo.getVirtualTablePointer()).structSet(objCls.getStructure(), 0);
      body.getLocal(resultLocal);
      function.setReferenced(true);
      this.module.functions.add(function);
      return function;
   }

   private WasmFunction createInitNewInstanceFunction(MethodReader method) {
      String className = method.getOwnerName();
      WasmGCClassInfo objectClass = this.getClassInfo("java.lang.Object");
      WasmFunctionType instantiatorType = this.functionTypes.of(null, objectClass.getType());
      WasmFunction instantiator = new WasmFunction(instantiatorType);
      instantiator.setName(this.names.topLevel(className + "@initInstance"));
      instantiator.setReferenced(true);
      this.module.functions.add(instantiator);
      WasmGCClassInfo classInfo = this.getClassInfo(className);
      WasmLocal param = new WasmLocal(objectClass.getType(), "param");
      WasmLocal localVar = new WasmLocal(classInfo.getType(), "instance");
      instantiator.add(param);
      instantiator.add(localVar);
      WasmInstructionBuilder body = instantiator.getBody().builder();
      body.getLocal(param).cast(classInfo.getType()).setLocal(localVar);
      boolean suspend = this.asyncSplitMethods.contains(method.getReference());
      body.getLocal(localVar).call(this.functionProvider.forInstanceMethod(method.getReference()), suspend);
      if (suspend) {
         CoroutineTransformation transformation = new CoroutineTransformation(this.functionTypes, this.functionProvider, this);
         transformation.transform(instantiator);
      }

      return instantiator;
   }
}
