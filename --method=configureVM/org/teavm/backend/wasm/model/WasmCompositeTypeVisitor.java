package org.teavm.backend.wasm.model;

public interface WasmCompositeTypeVisitor {
   void visit(WasmStructure var1);

   void visit(WasmArray var1);

   void visit(WasmFunctionType var1);
}
