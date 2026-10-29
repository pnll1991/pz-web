package org.teavm.backend.wasm.parser;

public enum Opcode {
   UNREACHABLE,
   NOP,
   RETURN,
   DROP,
   REF_EQ,
   ARRAY_LENGTH,
   IS_NULL,
   ANY_TO_EXTERN,
   EXTERN_TO_ANY,
   THROW_REF;
}
