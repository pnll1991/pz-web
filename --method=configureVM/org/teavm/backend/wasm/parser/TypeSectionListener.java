package org.teavm.backend.wasm.parser;

public interface TypeSectionListener extends SectionListener {
   default void startRecType(int count) {
   }

   default void endRecType() {
   }

   default void startType(int index, boolean open, int[] supertypes) {
   }

   default void startArrayType() {
   }

   default void endArrayType() {
   }

   default void startStructType(int fieldCount) {
   }

   default void field(WasmHollowStorageType hollowType, boolean mutable) {
   }

   default void endStructType() {
   }

   default void funcType(int paramCount) {
   }

   default void funcTypeResults(int returnCount) {
   }

   default void resultType(WasmHollowType type) {
   }

   default void endFuncType() {
   }

   default void endType() {
   }
}
