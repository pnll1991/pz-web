package org.teavm.backend.wasm.generate.reflection;

import java.util.List;
import java.util.ServiceLoader;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfo;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmNullConstant;
import org.teavm.dependency.DependencyInfo;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ClassMetadataRequirements;
import org.teavm.runtime.reflect.ClassInfo;
import org.teavm.runtime.reflect.ClassReflectionInfo;

public class ClassInfoStruct {
   private final WasmGCNameProvider names;
   private final WasmModule module;
   private final WasmFunctionTypes functionTypes;
   private final WasmGCClassInfoProvider classInfoProvider;
   private final BaseWasmFunctionRepository functions;
   private final ClassMetadataRequirements metadataRequirements;
   private final DependencyInfo dependencies;
   private final ReflectionTypes reflectionTypes;
   private WasmStructure structure;
   private WasmArray array;
   private int vtableIndex;
   private int classObjectIndex;
   private int modifiersIndex;
   private int primitiveKindIndex;
   private int tagIndex;
   private int nameIndex = -1;
   private int simpleNameIndex = -1;
   private int itemTypeIndex;
   private int arrayTypeIndex;
   private int declaringClassIndex = -1;
   private int enclosingClassIndex = -1;
   private int supertypeFunctionIndex = -1;
   private int parentIndex = -1;
   private int interfacesIndex = -1;
   private int cloneFunctionIndex = -1;
   private int newArrayFunctionIndex = -1;
   private int getItemIndex = -1;
   private int putItemIndex = -1;
   private int arrayLengthIndex = -1;
   private int initializerIndex = -1;
   private int enumConstantsIndex = -1;
   private int initEnumConstantsIndex = -1;
   private int servicesIndex = -1;
   private int copyArrayIndex = -1;
   private int nextClassIndex = -1;
   private int createInstanceIndex = -1;
   private int initNewInstanceIndex = -1;
   private int reflectionInfoIndex = -1;
   private WasmFunctionType supertypeFunctionType;
   private WasmFunctionType cloneFunctionType;
   private WasmFunctionType newArrayFunctionType;
   private WasmFunctionType getItemFunctionType;
   private WasmFunctionType putItemFunctionType;
   private WasmFunctionType arrayLengthFunctionType;
   private WasmFunctionType copyArrayFunctionType;
   private WasmArray enumConstantsType;
   private WasmArray interfaceListType;
   private WasmGlobal firstClassGlobal;
   private WasmGlobal currentClassGlobal;
   private WasmFunction classFunction;

   ClassInfoStruct(
      WasmGCNameProvider names,
      WasmModule module,
      WasmFunctionTypes functionTypes,
      WasmGCClassInfoProvider classInfoProvider,
      BaseWasmFunctionRepository functions,
      ClassMetadataRequirements metadataRequirements,
      DependencyInfo dependencies,
      ReflectionTypes reflectionTypes
   ) {
      this.names = names;
      this.module = module;
      this.functionTypes = functionTypes;
      this.classInfoProvider = classInfoProvider;
      this.functions = functions;
      this.metadataRequirements = metadataRequirements;
      this.dependencies = dependencies;
      this.reflectionTypes = reflectionTypes;
      String structName = names.topLevel(names.suggestForClass(ClassInfo.class.getName()));
      this.structure = new WasmStructure(structName, this::initFields);
      module.types.add(this.structure);
   }

   private void initFields(List<WasmField> fields) {
      this.vtableIndex = fields.size();
      WasmGCClassInfo objectTypeInfo = this.classInfoProvider.getClassInfo("java.lang.Object");
      WasmStructure vtableStruct = objectTypeInfo.getVirtualTableStructure();
      fields.add(new WasmField(vtableStruct, "vtable"));
      this.classObjectIndex = fields.size();
      WasmStructure classObjectStruct = this.classInfoProvider.getClassInfo("java.lang.Class").getStructure();
      fields.add(new WasmField(classObjectStruct, "classObject"));
      this.tagIndex = fields.size();
      fields.add(new WasmField(WasmType.INT32, "tag"));
      this.modifiersIndex = fields.size();
      fields.add(new WasmField(WasmType.INT32, "modifiers"));
      this.primitiveKindIndex = fields.size();
      fields.add(new WasmField(WasmType.INT32, "primitiveKind"));
      if (this.metadataRequirements.hasName()) {
         this.nameIndex = fields.size();
         fields.add(new WasmField(this.stringType(), "name"));
      }

      if (this.metadataRequirements.hasSimpleName()) {
         this.simpleNameIndex = fields.size();
         fields.add(new WasmField(this.stringType(), "simpleName"));
      }

      this.itemTypeIndex = fields.size();
      fields.add(new WasmField(this.structure, "itemType"));
      this.arrayTypeIndex = fields.size();
      fields.add(new WasmField(this.structure, "arrayType"));
      if (this.metadataRequirements.hasDeclaringClass()) {
         this.declaringClassIndex = fields.size();
         fields.add(new WasmField(this.structure, "declaringClass"));
      }

      if (this.metadataRequirements.hasEnclosingClass()) {
         this.enclosingClassIndex = fields.size();
         fields.add(new WasmField(this.structure, "enclosingClass"));
      }

      if (this.metadataRequirements.hasIsAssignable()) {
         this.supertypeFunctionType = this.functionTypes.of(WasmType.INT32, this.structure.getReference(), this.structure.getReference());
         this.supertypeFunctionIndex = fields.size();
         fields.add(new WasmField(this.supertypeFunctionType, "isSuperTypeOf"));
      }

      if (this.metadataRequirements.hasSuperclass()) {
         this.parentIndex = fields.size();
         fields.add(new WasmField(this.structure, "parent"));
      }

      if (this.metadataRequirements.hasGetInterfaces()) {
         this.interfacesIndex = fields.size();
         this.interfaceListType = this.array();
         fields.add(new WasmField(this.interfaceListType, "interfaces"));
      }

      this.cloneFunctionType = this.functionTypes.of(objectTypeInfo.getType(), objectTypeInfo.getType());
      this.cloneFunctionIndex = fields.size();
      fields.add(new WasmField(this.cloneFunctionType, "clone"));
      if (this.metadataRequirements.hasArrayNewInstance()) {
         this.newArrayFunctionType = this.functionTypes.of(objectTypeInfo.getType(), this.structure.getReference(), WasmType.INT32);
         this.newArrayFunctionType.setFinal(false);
         this.newArrayFunctionIndex = fields.size();
         fields.add(new WasmField(this.newArrayFunctionType, "newArray"));
      }

      if (this.metadataRequirements.hasArrayGet()) {
         this.getItemFunctionType = this.functionTypes.of(objectTypeInfo.getType(), this.structure.getReference(), objectTypeInfo.getType(), WasmType.INT32);
         this.getItemIndex = fields.size();
         fields.add(new WasmField(this.getItemFunctionType, "getItem"));
      }

      if (this.metadataRequirements.hasArraySet()) {
         this.putItemFunctionType = this.functionTypes
            .of(null, this.structure.getReference(), objectTypeInfo.getType(), WasmType.INT32, objectTypeInfo.getType());
         this.putItemIndex = fields.size();
         fields.add(new WasmField(this.putItemFunctionType, "putItem"));
      }

      if (this.metadataRequirements.hasArrayLength()) {
         this.arrayLengthFunctionType = this.functionTypes.of(WasmType.INT32, this.structure.getReference(), objectTypeInfo.getType());
         this.arrayLengthIndex = fields.size();
         fields.add(new WasmField(this.arrayLengthFunctionType, "arrayLength"));
      }

      if (this.metadataRequirements.hasEnumConstants()) {
         this.enumConstantsType = new WasmArray(this.names.topLevel("TeaVM.EnumConstants)"), objectTypeInfo.getType().asStorage());
         this.module.types.add(this.enumConstantsType);
         this.enumConstantsIndex = fields.size();
         fields.add(new WasmField(this.enumConstantsType, "enumConstants"));
         this.initEnumConstantsIndex = fields.size();
         fields.add(new WasmField(this.functionTypes.of(this.enumConstantsType.getReference()), "initEnumConstants"));
      }

      if (this.metadataRequirements.hasClassInit()) {
         this.initializerIndex = fields.size();
         fields.add(new WasmField(this.functionTypes.of(null), "initializer"));
      }

      MethodDependencyInfo loadServicesMethod = this.dependencies
         .getMethod(new MethodReference(ServiceLoader.class, "loadServices", ClassInfo.class, Object[].class));
      if (loadServicesMethod != null && loadServicesMethod.isUsed()) {
         this.servicesIndex = fields.size();
         WasmFunctionType serviceFunctionType = this.functionTypes.of(this.classInfoProvider.getClassInfo(ValueType.parse(Object[].class)).getType());
         fields.add(new WasmField(serviceFunctionType, "services"));
      }

      MethodDependencyInfo arrayCopyMethod = this.dependencies
         .getMethod(new MethodReference(System.class, "arrayCopyImpl", Object.class, int.class, Object.class, int.class, int.class, void.class));
      if (arrayCopyMethod != null && arrayCopyMethod.isUsed()) {
         WasmGCClassInfo objectClass = this.classInfoProvider.getClassInfo("java.lang.Object");
         this.copyArrayFunctionType = this.functionTypes
            .of(null, this.structure.getReference(), objectClass.getType(), WasmType.INT32, objectClass.getType(), WasmType.INT32, WasmType.INT32);
         this.copyArrayIndex = fields.size();
         fields.add(new WasmField(this.copyArrayFunctionType, "copyArray"));
      }

      MethodDependencyInfo fillNameMapMethod = this.dependencies.getMethod(new MethodReference(Class.class, "fillNameMap", void.class));
      if (fillNameMapMethod != null && fillNameMapMethod.isUsed()) {
         this.nextClassIndex = fields.size();
         fields.add(new WasmField(this.structure, "nextClass"));
      }

      MethodDependencyInfo newInstanceMethod = this.dependencies.getMethod(new MethodReference(ClassInfo.class, "newInstance", Object.class));
      if (newInstanceMethod != null && newInstanceMethod.isUsed()) {
         WasmGCClassInfo objectClass = this.classInfoProvider.getClassInfo("java.lang.Object");
         this.createInstanceIndex = fields.size();
         fields.add(new WasmField(this.functionTypes.of(objectClass.getType()), "createInstance"));
         this.initNewInstanceIndex = fields.size();
         fields.add(new WasmField(this.functionTypes.of(null, objectClass.getType()), "initNewInstance"));
      }

      MethodDependencyInfo reflectionInfoMethod = this.dependencies.getMethod(new MethodReference(ClassInfo.class, "reflection", ClassReflectionInfo.class));
      if (reflectionInfoMethod != null && reflectionInfoMethod.isUsed()) {
         this.reflectionInfoIndex = fields.size();
         fields.add(new WasmField(this.reflectionTypes.classReflectionInfo().structure(), "reflection"));
      }
   }

   private WasmType stringType() {
      return this.classInfoProvider.getClassInfo("java.lang.String").getType();
   }

   public WasmStructure structure() {
      return this.structure;
   }

   public WasmArray array() {
      if (this.array == null) {
         String name = this.names.suggestForType(ValueType.arrayOf(ValueType.object(ClassInfo.class.getName())));
         this.array = new WasmArray(name, this.structure.getReference().asStorage());
         this.module.types.add(this.array);
      }

      return this.array;
   }

   public int vtableIndex() {
      this.init();
      return this.vtableIndex;
   }

   public int classObjectIndex() {
      this.init();
      return this.classObjectIndex;
   }

   public int tagIndex() {
      this.init();
      return this.tagIndex;
   }

   public int modifiersIndex() {
      this.init();
      return this.modifiersIndex;
   }

   public int primitiveKindIndex() {
      this.init();
      return this.primitiveKindIndex;
   }

   public int nameIndex() {
      this.init();
      return this.nameIndex;
   }

   public int simpleNameIndex() {
      this.init();
      return this.simpleNameIndex;
   }

   public int itemTypeIndex() {
      this.init();
      return this.itemTypeIndex;
   }

   public int arrayTypeIndex() {
      this.init();
      return this.arrayTypeIndex;
   }

   public int declaringClassIndex() {
      this.init();
      return this.declaringClassIndex;
   }

   public int enclosingClassIndex() {
      this.init();
      return this.enclosingClassIndex;
   }

   public int supertypeFunctionIndex() {
      this.init();
      return this.supertypeFunctionIndex;
   }

   public int parentIndex() {
      this.init();
      return this.parentIndex;
   }

   public int interfacesIndex() {
      this.init();
      return this.interfacesIndex;
   }

   public int cloneFunctionIndex() {
      this.init();
      return this.cloneFunctionIndex;
   }

   public int newArrayFunctionIndex() {
      this.init();
      return this.newArrayFunctionIndex;
   }

   public int getItemIndex() {
      this.init();
      return this.getItemIndex;
   }

   public int putItemIndex() {
      this.init();
      return this.putItemIndex;
   }

   public int arrayLengthIndex() {
      this.init();
      return this.arrayLengthIndex;
   }

   public int initializerIndex() {
      this.init();
      return this.initializerIndex;
   }

   public int enumConstantsIndex() {
      this.init();
      return this.enumConstantsIndex;
   }

   public int initEnumConstantsIndex() {
      this.init();
      return this.initEnumConstantsIndex;
   }

   public int servicesIndex() {
      this.init();
      return this.servicesIndex;
   }

   public int copyArrayIndex() {
      this.init();
      return this.copyArrayIndex;
   }

   public int nextClassIndex() {
      this.init();
      return this.nextClassIndex;
   }

   public int createInstanceIndex() {
      this.init();
      return this.createInstanceIndex;
   }

   public int initNewInstanceIndex() {
      this.init();
      return this.initNewInstanceIndex;
   }

   public int reflectionInfoIndex() {
      this.init();
      return this.reflectionInfoIndex;
   }

   public WasmFunctionType supertypeFunctionType() {
      this.init();
      return this.supertypeFunctionType;
   }

   public WasmFunctionType cloneFunctionType() {
      this.init();
      return this.cloneFunctionType;
   }

   public WasmFunctionType newArrayFunctionType() {
      this.init();
      return this.newArrayFunctionType;
   }

   public WasmFunctionType getItemFunctionType() {
      this.init();
      return this.getItemFunctionType;
   }

   public WasmFunctionType putItemFunctionType() {
      this.init();
      return this.putItemFunctionType;
   }

   public WasmFunctionType arrayLengthFunctionType() {
      this.init();
      return this.arrayLengthFunctionType;
   }

   public WasmFunctionType copyArrayFunctionType() {
      this.init();
      return this.copyArrayFunctionType;
   }

   public WasmArray enumConstantsType() {
      this.init();
      return this.enumConstantsType;
   }

   public WasmArray interfacesType() {
      this.init();
      return this.interfaceListType;
   }

   public WasmGlobal firstClassGlobal() {
      if (this.firstClassGlobal == null) {
         this.firstClassGlobal = new WasmGlobal(this.names.topLevel("teavm@firstClass"), this.structure.getReference());
         this.firstClassGlobal.getInitialValue().add(new WasmNullConstant(this.structure.getReference()));
         this.module.globals.add(this.firstClassGlobal);
      }

      return this.firstClassGlobal;
   }

   public WasmGlobal currentClassGlobal() {
      if (this.currentClassGlobal == null) {
         this.currentClassGlobal = new WasmGlobal(this.names.topLevel("teavm@currentClass"), this.structure.getReference());
         this.currentClassGlobal.getInitialValue().add(new WasmNullConstant(this.structure.getReference()));
         this.module.globals.add(this.currentClassGlobal);
      }

      return this.currentClassGlobal;
   }

   private void init() {
      this.structure.init();
   }

   public WasmFunction classObjectFunction() {
      if (this.classFunction == null) {
         WasmType.CompositeReference classType = this.classInfoProvider.getClassInfo("java.lang.Class").getType();
         WasmFunctionType functionType = this.functionTypes.of(classType, this.structure.getReference());
         this.classFunction = new WasmFunction(functionType);
         this.module.functions.add(this.classFunction);
         this.classFunction.setName(this.names.topLevel("teavm@classObject"));
         WasmLocal classInfoLocal = new WasmLocal(this.structure.getReference(), "classInfo");
         WasmLocal resultLocal = new WasmLocal(classType, "result");
         this.classFunction.add(classInfoLocal);
         this.classFunction.add(resultLocal);
         this.structure.init();
         WasmInstructionBuilder body = this.classFunction.getBody().builder();
         body.getLocal(classInfoLocal).structGet(this.structure, this.classObjectIndex).setLocal(resultLocal);
         WasmInstructionBuilder block = body.block();
         block.getLocal(resultLocal).isNull().negate().branch(block);
         WasmFunction constructor = this.functions.forStaticMethod(new MethodReference(Class.class, "createClass", ClassInfo.class, Class.class));
         block.getLocal(classInfoLocal).call(constructor).setLocal(resultLocal);
         block.getLocal(classInfoLocal).getLocal(resultLocal).structSet(this.structure, this.classObjectIndex);
         body.getLocal(resultLocal);
      }

      return this.classFunction;
   }
}
