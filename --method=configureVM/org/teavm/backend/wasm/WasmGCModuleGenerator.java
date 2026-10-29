package org.teavm.backend.wasm;

import org.teavm.backend.wasm.generate.WasmGCDeclarationsGenerator;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmInstructionList;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.backend.wasm.runtime.StringInternPool;
import org.teavm.interop.Address;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.runtime.heap.Heap;

public class WasmGCModuleGenerator {
   private WasmGCDeclarationsGenerator declarationsGenerator;
   private WasmFunction initializer;

   public WasmGCModuleGenerator(WasmGCDeclarationsGenerator declarationsGenerator) {
      this.declarationsGenerator = declarationsGenerator;
   }

   public void generate() {
      this.declarationsGenerator.generate();
      this.createInitializer();
   }

   public void initBuffersHeap(WasmGlobal offset, int minSize, WasmGlobal heapLimit) {
      WasmFunction target = this.declarationsGenerator
         .functions()
         .forStaticMethod(new MethodReference(Heap.class, "init", Address.class, int.class, int.class, void.class));
      WasmInstructionBuilder builder = new WasmInstructionList().builder();
      builder.getGlobal(offset).i32Const(minSize).getGlobal(heapLimit).getGlobal(offset).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SUB).call(target);
      this.initializer.getBody().transferFrom(builder.list);
   }

   public WasmFunction generateReportGarbageCollectedStringFunction() {
      ValueType.Object entryType = ValueType.object(StringInternPool.class.getName() + "$Entry");
      return this.declarationsGenerator.functions().forStaticMethod(new MethodReference(StringInternPool.class.getName(), "remove", entryType, ValueType.VOID));
   }

   private void createInitializer() {
      WasmFunctionType functionType = this.declarationsGenerator.functionTypes.of(null);
      this.initializer = new WasmFunction(functionType);
      this.initializer.setName("teavm@initializer");
      this.declarationsGenerator.module.functions.add(this.initializer);
      this.declarationsGenerator.module.setStartFunction(this.initializer);
      this.declarationsGenerator.contributeToInitializer(this.initializer);
   }
}
