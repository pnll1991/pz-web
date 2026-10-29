package org.teavm.backend.wasm.model.instruction;

public interface WasmInstructionVisitor {
   void visit(WasmUnreachable var1);

   void visit(WasmBlock var1);

   void visit(WasmConditional var1);

   void visit(WasmBranch var1);

   void visit(WasmNullBranch var1);

   void visit(WasmCastBranch var1);

   void visit(WasmBreak var1);

   void visit(WasmSwitch var1);

   void visit(WasmReturn var1);

   void visit(WasmInt32Constant var1);

   void visit(WasmInt64Constant var1);

   void visit(WasmFloat32Constant var1);

   void visit(WasmFloat64Constant var1);

   void visit(WasmNullConstant var1);

   void visit(WasmIsNull var1);

   void visit(WasmGetLocal var1);

   void visit(WasmSetLocal var1);

   void visit(WasmTeeLocal var1);

   void visit(WasmGetGlobal var1);

   void visit(WasmSetGlobal var1);

   void visit(WasmIntBinary var1);

   void visit(WasmFloatBinary var1);

   void visit(WasmIntUnary var1);

   void visit(WasmFloatUnary var1);

   void visit(WasmConversion var1);

   void visit(WasmCall var1);

   void visit(WasmIndirectCall var1);

   void visit(WasmCallReference var1);

   void visit(WasmDrop var1);

   void visit(WasmLoadInt32 var1);

   void visit(WasmLoadInt64 var1);

   void visit(WasmLoadFloat32 var1);

   void visit(WasmLoadFloat64 var1);

   void visit(WasmStoreInt32 var1);

   void visit(WasmStoreInt64 var1);

   void visit(WasmStoreFloat32 var1);

   void visit(WasmStoreFloat64 var1);

   void visit(WasmMemoryGrow var1);

   void visit(WasmFill var1);

   void visit(WasmCopy var1);

   void visit(WasmTry var1);

   void visit(WasmThrow var1);

   void visit(WasmReferencesEqual var1);

   void visit(WasmCast var1);

   void visit(WasmTest var1);

   void visit(WasmExternConversion var1);

   void visit(WasmStructNew var1);

   void visit(WasmStructNewDefault var1);

   void visit(WasmStructGet var1);

   void visit(WasmStructSet var1);

   void visit(WasmArrayNewDefault var1);

   void visit(WasmArrayNewFixed var1);

   void visit(WasmArrayGet var1);

   void visit(WasmArraySet var1);

   void visit(WasmArrayLength var1);

   void visit(WasmArrayCopy var1);

   void visit(WasmFunctionReference var1);

   void visit(WasmInt31Reference var1);

   void visit(WasmInt31Get var1);
}
