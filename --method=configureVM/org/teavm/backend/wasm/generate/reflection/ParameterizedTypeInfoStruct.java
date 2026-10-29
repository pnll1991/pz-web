package org.teavm.backend.wasm.generate.reflection;

import java.util.List;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.dependency.DependencyInfo;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.ClassInfo;
import org.teavm.runtime.reflect.GenericTypeInfo;
import org.teavm.runtime.reflect.ParameterizedTypeInfo;

public class ParameterizedTypeInfoStruct {
   private final DependencyInfo dependencies;
   private final ReflectionTypes reflectionTypes;
   private WasmStructure structure;
   private int rawTypeIndex = -1;
   private int actualTypeArgumentsIndex = -1;
   private int ownerTypeIndex = -1;

   ParameterizedTypeInfoStruct(WasmGCNameProvider names, WasmModule module, DependencyInfo dependencies, ReflectionTypes reflectionTypes) {
      this.dependencies = dependencies;
      this.reflectionTypes = reflectionTypes;
      String name = names.topLevel(names.suggestForClass(ParameterizedTypeInfo.class.getName()));
      this.structure = new WasmStructure(name, this::initFields);
      module.types.add(this.structure);
   }

   private void initFields(List<WasmField> fields) {
      if (this.dependencies.getMethod(new MethodReference(ParameterizedTypeInfo.class, "rawType", ClassInfo.class)) != null) {
         this.rawTypeIndex = fields.size();
         fields.add(new WasmField(this.reflectionTypes.classInfo().structure(), "rawType"));
      }

      if (this.dependencies.getMethod(new MethodReference(ParameterizedTypeInfo.class, "actualTypeArgumentCount", int.class)) != null) {
         this.actualTypeArgumentsIndex = fields.size();
         fields.add(new WasmField(this.reflectionTypes.genericTypeArray(), "actualTypeArguments"));
      }

      if (this.dependencies.getMethod(new MethodReference(ParameterizedTypeInfo.class, "ownerType", GenericTypeInfo.class)) != null) {
         this.ownerTypeIndex = fields.size();
         fields.add(new WasmField(WasmType.STRUCT, "ownerType"));
      }
   }

   public WasmStructure structure() {
      return this.structure;
   }

   public int rawTypeIndex() {
      this.init();
      return this.rawTypeIndex;
   }

   public int actualTypeArgumentsIndex() {
      this.init();
      return this.actualTypeArgumentsIndex;
   }

   public int ownerTypeIndex() {
      this.init();
      return this.ownerTypeIndex;
   }

   private void init() {
      this.structure.init();
   }
}
