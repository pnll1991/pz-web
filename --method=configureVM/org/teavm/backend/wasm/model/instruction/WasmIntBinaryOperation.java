package org.teavm.backend.wasm.model.instruction;

public enum WasmIntBinaryOperation {
   ADD,
   SUB,
   MUL,
   DIV_SIGNED,
   DIV_UNSIGNED,
   REM_SIGNED,
   REM_UNSIGNED,
   OR,
   AND,
   XOR,
   SHL,
   SHR_SIGNED,
   SHR_UNSIGNED,
   ROTL,
   ROTR,
   EQ,
   NE,
   LT_SIGNED,
   LT_UNSIGNED,
   LE_SIGNED,
   LE_UNSIGNED,
   GT_SIGNED,
   GT_UNSIGNED,
   GE_SIGNED,
   GE_UNSIGNED;
}
