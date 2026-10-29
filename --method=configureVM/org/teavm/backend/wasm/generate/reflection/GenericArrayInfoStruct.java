package org.teavm.backend.wasm.generate.reflection;

import java.util.List;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.runtime.reflect.GenericArrayInfo;

public class GenericArrayInfoStruct {
   private WasmStructure structure;
   private int itemTypeIndex;

   GenericArrayInfoStruct(WasmGCNameProvider names, WasmModule module) {
      String name = names.topLevel(names.suggestForClass(GenericArrayInfo.class.getName()));
      this.structure = new WasmStructure(name, this::initFields);
      module.types.add(this.structure);
   }

   private void initFields(List<WasmField> fields) {
      this.itemTypeIndex = fields.size();
      fields.add(new WasmField(WasmType.STRUCT, "itemType"));
   }

   public WasmStructure structure() {
      return this.structure;
   }

   public int itemTypeIndex() {
      this.init();
      return this.itemTypeIndex;
   }

   private void init() {
      this.structure.init();
   }
}
