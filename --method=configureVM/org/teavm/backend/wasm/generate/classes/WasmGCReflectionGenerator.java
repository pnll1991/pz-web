package org.teavm.backend.wasm.generate.classes;

import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStorageType;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.model.ValueType;

class WasmGCReflectionGenerator implements WasmGCReflectionProvider {
   private WasmModule module;
   private WasmFunctionTypes functionTypes;
   private WasmGCClassInfoProvider classInfoProvider;
   private WasmGCNameProvider names;
   private WasmStructure reflectionFieldType;
   private WasmArray reflectionFieldArrayType;
   private WasmStructure reflectionMethodType;
   private WasmArray reflectionMethodArrayType;
   private WasmArray classArrayType;

   WasmGCReflectionGenerator(WasmModule module, WasmFunctionTypes functionTypes, WasmGCClassInfoProvider classInfoProvider, WasmGCNameProvider names) {
      this.module = module;
      this.functionTypes = functionTypes;
      this.classInfoProvider = classInfoProvider;
      this.names = names;
   }

   @Override
   public WasmStructure getReflectionFieldType() {
      if (this.reflectionFieldType == null) {
         this.reflectionFieldType = new WasmStructure(this.names.topLevel("@teavm.Field"), fields -> {
            WasmGCClassInfo stringClass = this.classInfoProvider.getClassInfo("java.lang.String");
            WasmGCClassInfo classClass = this.classInfoProvider.getClassInfo("java.lang.Class");
            WasmGCClassInfo objectClass = this.classInfoProvider.getClassInfo("java.lang.Object");
            WasmType.CompositeReference getterType = this.functionTypes.of(objectClass.getType(), objectClass.getType()).getReference();
            WasmType.CompositeReference setterType = this.functionTypes.of(null, objectClass.getType(), objectClass.getType()).getReference();
            fields.add(new WasmField(stringClass.getType().asStorage(), this.names.structureField("@name")));
            fields.add(new WasmField(WasmType.INT32.asStorage(), this.names.structureField("@flags")));
            fields.add(new WasmField(WasmType.INT32.asStorage(), this.names.structureField("@accessLevel")));
            fields.add(new WasmField(this.classInfoProvider.getObjectArrayType().getReference().asStorage(), this.names.structureField("@annotations")));
            fields.add(new WasmField(classClass.getType().asStorage(), this.names.structureField("@type")));
            fields.add(new WasmField(objectClass.getType().asStorage(), this.names.structureField("@genericType")));
            fields.add(new WasmField(getterType.asStorage(), this.names.structureField("@reader")));
            fields.add(new WasmField(setterType.asStorage(), this.names.structureField("@writer")));
         });
         this.module.types.add(this.reflectionFieldType);
      }

      return this.reflectionFieldType;
   }

   @Override
   public WasmArray getReflectionFieldArrayType() {
      if (this.reflectionFieldArrayType == null) {
         this.reflectionFieldArrayType = new WasmArray("Array<@teavm.Field>", this.getReflectionFieldType().getReference().asStorage());
         this.module.types.add(this.reflectionFieldArrayType);
      }

      return this.reflectionFieldArrayType;
   }

   @Override
   public WasmStructure getReflectionMethodType() {
      if (this.reflectionMethodType == null) {
         this.reflectionMethodType = new WasmStructure(
            this.names.topLevel("@teavm.Method"),
            fields -> {
               WasmGCClassInfo stringClass = this.classInfoProvider.getClassInfo("java.lang.String");
               WasmGCClassInfo classClass = this.classInfoProvider.getClassInfo("java.lang.Class");
               WasmGCClassInfo objectClass = this.classInfoProvider.getClassInfo("java.lang.Object");
               WasmGCClassInfo objectArrayClass = this.classInfoProvider.getClassInfo(ValueType.arrayOf(ValueType.object("java.lang.Object")));
               WasmType.CompositeReference callerType = this.functionTypes
                  .of(objectClass.getType(), objectClass.getType(), objectArrayClass.getType())
                  .getReference();
               WasmStorageType.Regular objectArrayType = this.classInfoProvider.getObjectArrayType().getReference().asStorage();
               fields.add(new WasmField(stringClass.getType().asStorage(), this.names.structureField("@name")));
               fields.add(new WasmField(WasmType.INT32.asStorage(), this.names.structureField("@flags")));
               fields.add(new WasmField(WasmType.INT32.asStorage(), this.names.structureField("@accessLevel")));
               fields.add(new WasmField(objectArrayType, this.names.structureField("@annotations")));
               fields.add(new WasmField(classClass.getType().asStorage(), this.names.structureField("@returnType")));
               fields.add(new WasmField(objectClass.getType().asStorage(), this.names.structureField("@genericReturnType")));
               fields.add(new WasmField(this.getClassArrayType().getReference().asStorage(), this.names.structureField("@parameterTypes")));
               fields.add(new WasmField(objectArrayType, this.names.structureField("@genericParameterTypes")));
               fields.add(new WasmField(callerType.asStorage(), this.names.structureField("@caller")));
               fields.add(new WasmField(objectArrayType, this.names.structureField("@typeParameters")));
            }
         );
         this.module.types.add(this.reflectionMethodType);
      }

      return this.reflectionMethodType;
   }

   @Override
   public WasmArray getReflectionMethodArrayType() {
      if (this.reflectionMethodArrayType == null) {
         this.reflectionMethodArrayType = new WasmArray("Array<@teavm.Method>", this.getReflectionMethodType().getReference().asStorage());
         this.module.types.add(this.reflectionMethodArrayType);
      }

      return this.reflectionMethodArrayType;
   }

   @Override
   public WasmArray getClassArrayType() {
      if (this.classArrayType == null) {
         this.classArrayType = new WasmArray("Array<java.lang.Class>", () -> this.classInfoProvider.getClassInfo("java.lang.Class").getType().asStorage());
         this.module.types.add(this.classArrayType);
      }

      return this.classArrayType;
   }
}
