package org.teavm.backend.wasm.model;

public abstract class WasmCompositeType extends WasmEntity {
   private String name;
   private WasmType.CompositeReference reference;
   private WasmType.CompositeReference nonNullReference;
   int recursiveTypeCount = -1;

   WasmCompositeType(String name) {
      this.name = name;
   }

   public String getName() {
      return this.name;
   }

   public WasmType.CompositeReference getReference() {
      if (this.reference == null) {
         this.reference = new WasmType.CompositeReference(this, true);
      }

      return this.reference;
   }

   public WasmType.CompositeReference getNonNullReference() {
      if (this.nonNullReference == null) {
         this.nonNullReference = new WasmType.CompositeReference(this, false);
      }

      return this.nonNullReference;
   }

   public int getRecursiveTypeCount() {
      return this.recursiveTypeCount;
   }

   public abstract void acceptVisitor(WasmCompositeTypeVisitor var1);
}
