package org.teavm.backend.wasm.model;

import java.util.Objects;
import java.util.function.Supplier;

public class WasmArray extends WasmCompositeType {
   private WasmStorageType elementType;
   private boolean immutable;
   private Supplier<WasmStorageType> elementTypeSupplier;

   public WasmArray(String name, WasmStorageType elementType) {
      super(name);
      this.elementType = Objects.requireNonNull(elementType);
   }

   public WasmArray(String name, Supplier<WasmStorageType> elementTypeSupplier) {
      super(name);
      this.elementTypeSupplier = elementTypeSupplier;
   }

   public WasmStorageType getElementType() {
      this.init();
      return this.elementType;
   }

   public void init() {
      if (this.elementType == null) {
         this.elementType = this.elementTypeSupplier.get();
         this.elementTypeSupplier = null;
      }
   }

   public boolean isImmutable() {
      return this.immutable;
   }

   public void setImmutable(boolean immutable) {
      this.immutable = immutable;
   }

   @Override
   public void acceptVisitor(WasmCompositeTypeVisitor visitor) {
      visitor.visit(this);
   }
}
