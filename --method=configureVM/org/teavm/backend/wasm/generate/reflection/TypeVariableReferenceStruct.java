package org.teavm.backend.wasm.generate.reflection;

import java.util.List;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.runtime.reflect.TypeVariableReference;

public class TypeVariableReferenceStruct {
   private WasmStructure structure;
   private int levelIndex;
   private int indexIndex;

   TypeVariableReferenceStruct(WasmGCNameProvider names, WasmModule module) {
      String name = names.topLevel(names.suggestForClass(TypeVariableReference.class.getName()));
      this.structure = new WasmStructure(name, this::initFields);
      module.types.add(this.structure);
   }

   private void initFields(List<WasmField> fields) {
      this.levelIndex = fields.size();
      fields.add(new WasmField(WasmType.INT32, "level"));
      this.indexIndex = fields.size();
      fields.add(new WasmField(WasmType.INT32, "index"));
   }

   public WasmStructure structure() {
      return this.structure;
   }

   public int levelIndex() {
      this.init();
      return this.levelIndex;
   }

   public int indexIndex() {
      this.init();
      return this.indexIndex;
   }

   private void init() {
      this.structure.init();
   }
}
