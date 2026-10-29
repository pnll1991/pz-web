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
import org.teavm.runtime.reflect.AnnotationInfo;

public class AnnotationInfoStruct {
   private final WasmFunctionTypes functionTypes;
   private final WasmGCClassInfoProvider classInfoProvider;
   private WasmStructure structure;
   private WasmArray array;
   private int dataIndex;
   private int constructorIndex;
   private WasmFunctionType constructorType;

   AnnotationInfoStruct(WasmGCNameProvider names, WasmModule module, WasmFunctionTypes functionTypes, WasmGCClassInfoProvider classInfoProvider) {
      this.functionTypes = functionTypes;
      this.classInfoProvider = classInfoProvider;
      String structName = names.topLevel(names.suggestForClass(AnnotationInfo.class.getName()));
      this.structure = new WasmStructure(structName, this::initFields);
      module.types.add(this.structure);
      String arrayName = names.topLevel(names.suggestForArray(names.suggestForClass(AnnotationInfo.class.getName())));
      this.array = new WasmArray(arrayName, this.structure.getReference().asStorage());
      module.types.add(this.array);
   }

   public WasmStructure structure() {
      return this.structure;
   }

   public WasmArray array() {
      return this.array;
   }

   public int dataIndex() {
      this.init();
      return this.dataIndex;
   }

   public int constructorIndex() {
      this.init();
      return this.constructorIndex;
   }

   public WasmFunctionType constructorType() {
      this.init();
      return this.constructorType;
   }

   private void init() {
      this.structure.init();
   }

   private void initFields(List<WasmField> fields) {
      this.dataIndex = fields.size();
      fields.add(new WasmField(WasmType.STRUCT, "data"));
      this.constructorType = this.functionTypes.of(this.classInfoProvider.getClassInfo("java.lang.Object").getType(), WasmType.STRUCT);
      this.constructorIndex = fields.size();
      fields.add(new WasmField(this.constructorType, "constructor"));
   }
}
