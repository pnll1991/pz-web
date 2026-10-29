package org.teavm.backend.wasm.generate.reflection;

import java.util.List;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.dependency.DependencyInfo;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.runtime.StringInfo;
import org.teavm.runtime.reflect.DerivedClassInfo;
import org.teavm.runtime.reflect.MethodInfo;
import org.teavm.runtime.reflect.MethodReflectionInfo;

public class MethodInfoStruct {
   private final WasmGCNameProvider names;
   private final WasmModule module;
   private final WasmFunctionTypes functionTypes;
   private final DependencyInfo dependencies;
   private final WasmGCClassInfoProvider classInfoProvider;
   private WasmStructure structure;
   private int nameIndex = -1;
   private int modifiersIndex = -1;
   private int returnTypeIndex = -1;
   private int parameterTypesIndex = -1;
   private int checkedExceptionTypesIndex = -1;
   private int callerIndex = -1;
   private int reflectionIndex = -1;
   private WasmFunctionType callerType;
   private WasmArray array;

   MethodInfoStruct(
      WasmGCNameProvider names, WasmModule module, WasmFunctionTypes functionTypes, DependencyInfo dependencies, WasmGCClassInfoProvider classInfoProvider
   ) {
      this.names = names;
      this.module = module;
      this.functionTypes = functionTypes;
      this.dependencies = dependencies;
      this.classInfoProvider = classInfoProvider;
      String name = names.topLevel(names.suggestForClass(MethodInfo.class.getName()));
      this.structure = new WasmStructure(name, this::initFields);
      module.types.add(this.structure);
   }

   private void initFields(List<WasmField> fields) {
      if (this.dependencies.getMethod(new MethodReference(MethodInfo.class, "name", StringInfo.class)) != null) {
         this.nameIndex = fields.size();
         WasmType.CompositeReference strType = this.classInfoProvider.getClassInfo("java.lang.String").getType();
         fields.add(new WasmField(strType, "name"));
      }

      if (this.dependencies.getMethod(new MethodReference(MethodInfo.class, "modifiers", int.class)) != null) {
         this.modifiersIndex = fields.size();
         fields.add(new WasmField(WasmType.INT32, "modifiers"));
      }

      if (this.dependencies.getMethod(new MethodReference(MethodInfo.class, "returnType", DerivedClassInfo.class)) != null) {
         this.returnTypeIndex = fields.size();
         WasmType.CompositeReference infoType = this.classInfoProvider.reflectionTypes().derivedClassInfo().structure().getReference();
         fields.add(new WasmField(infoType, "returnType"));
      }

      if (this.dependencies.getMethod(new MethodReference(MethodInfo.class, "parameterCount", int.class)) != null) {
         this.parameterTypesIndex = fields.size();
         WasmType.CompositeReference infoType = this.classInfoProvider.reflectionTypes().derivedClassInfo().array().getReference();
         fields.add(new WasmField(infoType, "parameterTypes"));
      }

      if (this.dependencies.getMethod(new MethodReference(MethodInfo.class, "checkedExceptionCount", int.class)) != null) {
         this.checkedExceptionTypesIndex = fields.size();
         WasmType.CompositeReference infoType = this.classInfoProvider.reflectionTypes().classInfo().array().getReference();
         fields.add(new WasmField(infoType, "checkedExceptionTypes"));
      }

      if (this.dependencies.getMethod(new MethodReference(MethodInfo.class, "call", Object.class, Object[].class, Object.class)) != null) {
         WasmType.CompositeReference objType = this.classInfoProvider.getClassInfo("java.lang.Object").getType();
         WasmType.CompositeReference objArrayType = this.classInfoProvider.getClassInfo(ValueType.parse(Object[].class)).getType();
         this.callerType = this.functionTypes.of(objType, objType, objArrayType);
         this.callerIndex = fields.size();
         fields.add(new WasmField(this.callerType, "caller"));
      }

      if (this.dependencies.getMethod(new MethodReference(MethodInfo.class, "reflection", MethodReflectionInfo.class)) != null) {
         this.reflectionIndex = fields.size();
         WasmStructure reflectionStruct = this.classInfoProvider.reflectionTypes().methodReflectionInfo().structure();
         fields.add(new WasmField(reflectionStruct, "reflection"));
      }
   }

   public WasmStructure structure() {
      return this.structure;
   }

   public WasmArray array() {
      if (this.array == null) {
         String name = this.names.topLevel(this.names.suggestForArray(this.names.suggestForClass(MethodInfo.class.getName())));
         this.array = new WasmArray(name, this.structure.getReference().asStorage());
         this.module.types.add(this.array);
      }

      return this.array;
   }

   public int nameIndex() {
      this.init();
      return this.nameIndex;
   }

   public int modifiersIndex() {
      this.init();
      return this.modifiersIndex;
   }

   public int returnTypeIndex() {
      this.init();
      return this.returnTypeIndex;
   }

   public int parameterTypesIndex() {
      this.init();
      return this.parameterTypesIndex;
   }

   public int checkedExceptionTypesIndex() {
      this.init();
      return this.checkedExceptionTypesIndex;
   }

   public int callerIndex() {
      this.init();
      return this.callerIndex;
   }

   public int reflectionIndex() {
      this.init();
      return this.reflectionIndex;
   }

   public WasmFunctionType callerType() {
      this.init();
      return this.callerType;
   }

   private void init() {
      this.structure.init();
   }
}
