package org.teavm.backend.wasm.generate.classes;

import org.teavm.backend.wasm.generate.reflection.ReflectionTypes;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmStorageType;
import org.teavm.model.FieldReference;
import org.teavm.model.ValueType;

public interface WasmGCClassInfoProvider {
   int CLASS_FIELD_OFFSET = 0;
   int VT_FIELD_OFFSET = 0;
   int VIRTUAL_METHOD_OFFSET = 1;
   int MONITOR_FIELD_OFFSET = 1;
   int CUSTOM_FIELD_OFFSETS = 2;
   int ARRAY_DATA_FIELD_OFFSET = 2;
   int WEAK_REFERENCE_OFFSET = 2;
   int STRING_POOL_ENTRY_OFFSET = 5;

   WasmGCClassInfo getClassInfo(ValueType var1);

   WasmGCReflectionProvider reflection();

   int getFieldIndex(FieldReference var1);

   int getHeapFieldOffset(FieldReference var1);

   int getHeapSize(String var1);

   int getHeapAlignment(String var1);

   WasmGlobal getStaticFieldLocation(FieldReference var1);

   WasmFunction getArrayConstructor(ValueType var1);

   WasmFunction getMultiArrayConstructor(int var1);

   WasmFunction getGetArrayClassFunction();

   ReflectionTypes reflectionTypes();

   int getThrowableNativeOffset();

   WasmArray getObjectArrayType();

   WasmArray getPrimitiveArrayType(WasmStorageType var1);

   default WasmGCClassInfo getClassInfo(String name) {
      return this.getClassInfo(ValueType.object(name));
   }
}
