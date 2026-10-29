package org.teavm.backend.wasm.generate.classes;

import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmStructure;

public interface WasmGCReflectionProvider {
   int FIELD_NAME = 0;
   int FIELD_MODIFIERS = 1;
   int FIELD_ACCESS = 2;
   int FIELD_ANNOTATIONS = 3;
   int FIELD_TYPE = 4;
   int FIELD_GENERIC_TYPE = 5;
   int FIELD_READER = 6;
   int FIELD_WRITER = 7;
   int FIELD_RETURN_TYPE = 4;
   int FIELD_GENERIC_RETURN_TYPE = 5;
   int FIELD_PARAMETER_TYPES = 6;
   int FIELD_GENERIC_PARAMETER_TYPES = 7;
   int FIELD_CALLER = 8;
   int FIELD_TYPE_PARAMETERS = 9;

   WasmStructure getReflectionFieldType();

   WasmArray getReflectionFieldArrayType();

   WasmStructure getReflectionMethodType();

   WasmArray getReflectionMethodArrayType();

   WasmArray getClassArrayType();
}
