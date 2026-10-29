package org.teavm.backend.wasm.generate.reflection;

import java.util.List;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStorageType;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.runtime.reflect.DerivedClassInfo;

public class DerivedClassInfoStruct {
   private WasmGCNameProvider names;
   private WasmModule module;
   private ReflectionTypes reflectionTypes;
   private WasmStructure structure;
   private WasmArray array;
   private int classInfoIndex;
   private int arrayDegreeIndex;

   DerivedClassInfoStruct(WasmGCNameProvider names, WasmModule module, ReflectionTypes reflectionTypes) {
      this.names = names;
      this.module = module;
      this.reflectionTypes = reflectionTypes;
      String name = names.topLevel(names.suggestForClass(DerivedClassInfo.class.getName()));
      this.structure = new WasmStructure(name, this::initFields);
      module.types.add(this.structure);
   }

   private void initFields(List<WasmField> fields) {
      this.classInfoIndex = fields.size();
      fields.add(new WasmField(this.reflectionTypes.classInfo().structure(), "classInfo"));
      this.arrayDegreeIndex = fields.size();
      fields.add(new WasmField(WasmStorageType.INT8, "arrayDegree"));
   }

   public WasmStructure structure() {
      return this.structure;
   }

   public WasmArray array() {
      if (this.array == null) {
         String name = this.names.topLevel(this.names.suggestForArray(this.names.suggestForClass(DerivedClassInfo.class.getName())));
         this.array = new WasmArray(name, this.structure.getReference().asStorage());
         this.module.types.add(this.array);
      }

      return this.array;
   }

   public int classInfoIndex() {
      this.init();
      return this.classInfoIndex;
   }

   public int arrayDegreeIndex() {
      this.init();
      return this.arrayDegreeIndex;
   }

   private void init() {
      this.structure.init();
   }
}
