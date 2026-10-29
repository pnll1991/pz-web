package org.teavm.backend.wasm.generate.reflection;

import java.util.List;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfo;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.dependency.DependencyInfo;
import org.teavm.model.MethodReference;
import org.teavm.runtime.StringInfo;
import org.teavm.runtime.reflect.TypeVariableInfo;

public class TypeVariableInfoStruct {
   private final WasmGCNameProvider names;
   private final WasmModule module;
   private final DependencyInfo dependencies;
   private final WasmGCClassInfoProvider classInfoProvider;
   private WasmStructure structure;
   private WasmArray array;
   private int nameIndex = -1;
   private int boundsIndex = -1;

   TypeVariableInfoStruct(WasmGCNameProvider names, WasmModule module, DependencyInfo dependencies, WasmGCClassInfoProvider classInfoProvider) {
      this.names = names;
      this.module = module;
      this.dependencies = dependencies;
      this.classInfoProvider = classInfoProvider;
      String name = names.topLevel(names.suggestForClass(TypeVariableInfo.class.getName()));
      this.structure = new WasmStructure(name, this::initFields);
      module.types.add(this.structure);
   }

   private void initFields(List<WasmField> fields) {
      if (this.dependencies.getMethod(new MethodReference(TypeVariableInfo.class, "name", StringInfo.class)) != null) {
         this.nameIndex = fields.size();
         WasmGCClassInfo stringClass = this.classInfoProvider.getClassInfo("java.lang.String");
         fields.add(new WasmField(stringClass.getType(), "name"));
      }

      if (this.dependencies.getMethod(new MethodReference(TypeVariableInfo.class, "boundCount", int.class)) != null) {
         this.boundsIndex = fields.size();
         fields.add(new WasmField(this.classInfoProvider.reflectionTypes().genericTypeArray(), "bounds"));
      }
   }

   public WasmStructure structure() {
      return this.structure;
   }

   public WasmArray array() {
      if (this.array == null) {
         String name = this.names.topLevel(this.names.suggestForArray(this.names.suggestForClass(TypeVariableInfo.class.getName())));
         this.array = new WasmArray(name, this.structure.getReference().asStorage());
         this.module.types.add(this.array);
      }

      return this.array;
   }

   public int nameIndex() {
      this.init();
      return this.nameIndex;
   }

   public int boundsIndex() {
      this.init();
      return this.boundsIndex;
   }

   private void init() {
      this.structure.init();
   }
}
