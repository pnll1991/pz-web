package org.teavm.backend.wasm.parser;

public interface AddressListener {
   AddressListener EMPTY = new AddressListener() {};

   default void address(int address) {
   }
}
