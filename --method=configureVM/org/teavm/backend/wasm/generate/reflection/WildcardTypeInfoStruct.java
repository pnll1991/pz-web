package org.teavm.backend.wasm.generate.reflection;

import java.util.List;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.runtime.reflect.WildcardTypeInfo;

public class WildcardTypeInfoStruct {
   private WasmStructure structure;
   private int kindIndex;
   private int boundIndex;

   WildcardTypeInfoStruct(WasmGCNameProvider names, WasmModule module) {
      String name = names.topLevel(names.suggestForClass(WildcardTypeInfo.class.getName()));
      this.structure = new WasmStructure(name, this::initFields);
      module.types.add(this.structure);
   }

   private void initFields(List<WasmField> fields) {
      this.kindIndex = fields.size();
      fields.add(new WasmField(WasmType.INT32, "kind"));
      this.boundIndex = fields.size();
      fields.add(new WasmField(WasmType.STRUCT, "bound"));
   }

   public WasmStructure structure() {
      return this.structure;
   }

   public int kindIndex() {
      this.init();
      return this.kindIndex;
   }

   public int boundIndex() {
      this.init();
      return this.boundIndex;
   }

   private void init() {
      this.structure.init();
   }
}
