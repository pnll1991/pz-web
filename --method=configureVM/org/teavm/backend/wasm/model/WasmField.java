package org.teavm.backend.wasm.model;

import java.util.Objects;

public class WasmField {
   WasmStructure structure;
   int index;
   private String name;
   private WasmStorageType type;
   private boolean immutable;

   public WasmField(WasmStorageType type, String name) {
      this(type);
      this.name = name;
   }

   public WasmField(WasmType type, String name) {
      this(type.asStorage(), name);
   }

   public WasmField(WasmCompositeType type, String name) {
      this(type.getReference(), name);
   }

   public WasmField(WasmStorageType type) {
      this.type = Objects.requireNonNull(type);
   }

   public WasmField(WasmType type) {
      this(type.asStorage());
   }

   public WasmField(WasmCompositeType type) {
      this(type.getReference());
   }

   public WasmStructure getStructure() {
      return this.structure;
   }

   public WasmStorageType getType() {
      return this.type;
   }

   public WasmType getUnpackedType() {
      return this.type.asUnpackedType();
   }

   public void setType(WasmStorageType type) {
      this.type = Objects.requireNonNull(type);
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public boolean isImmutable() {
      return this.immutable;
   }

   public void setImmutable(boolean immutable) {
      this.immutable = immutable;
   }

   public int getIndex() {
      if (this.structure == null) {
         return -1;
      }

      this.structure.ensureIndexes();
      return this.index;
   }
}
