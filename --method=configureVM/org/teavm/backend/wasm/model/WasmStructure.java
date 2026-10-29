package org.teavm.backend.wasm.model;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class WasmStructure extends WasmCompositeType {
   private Consumer<List<WasmField>> fieldsSupplier;
   private List<WasmField> fieldsStorage = new ArrayList<>();
   private WasmStructure supertype;
   private boolean indexesValid = true;
   private boolean nominal;
   private List<WasmField> fields = new AbstractList<WasmField>() {
      public WasmField get(int index) {
         WasmStructure.this.init();
         return WasmStructure.this.fieldsStorage.get(index);
      }

      @Override
      public int size() {
         WasmStructure.this.init();
         return WasmStructure.this.fieldsStorage.size();
      }

      public void add(int index, WasmField element) {
         WasmStructure.this.init();
         if (element.structure != null) {
            throw new IllegalArgumentException("This field already belongs to structure");
         }

         element.structure = WasmStructure.this;
         WasmStructure.this.indexesValid = false;
         WasmStructure.this.fieldsStorage.add(index, element);
      }

      public WasmField remove(int index) {
         WasmStructure.this.init();
         WasmField result = WasmStructure.this.fieldsStorage.remove(index);
         WasmStructure.this.indexesValid = false;
         result.structure = null;
         return result;
      }

      @Override
      protected void removeRange(int fromIndex, int toIndex) {
         WasmStructure.this.init();
         List<WasmField> sublist = WasmStructure.this.fieldsStorage.subList(fromIndex, toIndex);

         for (WasmField field : sublist) {
            field.structure = null;
         }

         WasmStructure.this.indexesValid = false;
         sublist.clear();
      }

      @Override
      public void clear() {
         WasmStructure.this.fieldsSupplier = null;

         for (WasmField field : WasmStructure.this.fieldsStorage) {
            field.structure = null;
         }

         WasmStructure.this.indexesValid = true;
         WasmStructure.this.fieldsStorage.clear();
      }

      public WasmField set(int index, WasmField element) {
         WasmStructure.this.init();
         if (element.structure != null) {
            throw new IllegalArgumentException("This field already belongs to structure");
         }

         WasmField former = WasmStructure.this.fieldsStorage.set(index, element);
         former.structure = null;
         if (WasmStructure.this.indexesValid) {
            element.index = former.index;
         }

         element.structure = WasmStructure.this;
         return former;
      }
   };

   public WasmStructure(String name) {
      super(name);
   }

   public WasmStructure(String name, Consumer<List<WasmField>> fieldsSupplier) {
      super(name);
      this.fieldsSupplier = fieldsSupplier;
   }

   public List<WasmField> getFields() {
      return this.fields;
   }

   public WasmStructure getSupertype() {
      return this.supertype;
   }

   public void setSupertype(WasmStructure supertype) {
      this.supertype = supertype;
   }

   public boolean isSupertypeOf(WasmStructure subtype) {
      while (subtype != null) {
         if (subtype == this) {
            return true;
         }

         subtype = subtype.getSupertype();
      }

      return false;
   }

   public boolean isNominal() {
      return this.nominal;
   }

   public void setNominal(boolean nominal) {
      this.nominal = nominal;
   }

   void ensureIndexes() {
      if (!this.indexesValid) {
         this.indexesValid = true;
         int i = 0;

         while (i < this.fieldsStorage.size()) {
            this.fieldsStorage.get(i).index = i++;
         }
      }
   }

   public void init() {
      if (this.fieldsSupplier != null) {
         Consumer<List<WasmField>> supplier = this.fieldsSupplier;
         this.fieldsSupplier = null;
         supplier.accept(this.fieldsStorage);

         for (WasmField field : this.fieldsStorage) {
            field.structure = this;
         }

         this.indexesValid = false;
      }
   }

   @Override
   public void acceptVisitor(WasmCompositeTypeVisitor visitor) {
      visitor.visit(this);
   }
}
