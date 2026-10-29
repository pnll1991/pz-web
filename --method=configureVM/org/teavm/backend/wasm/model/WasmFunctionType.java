package org.teavm.backend.wasm.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class WasmFunctionType extends WasmCompositeType {
   private List<? extends WasmType> parameterTypes;
   private List<? extends WasmType> returnTypes;
   private Supplier<List<? extends WasmType>> parameterTypesSupplier;
   private Supplier<List<? extends WasmType>> returnTypeSupplier;
   private Set<WasmFunctionType> supertypes = new LinkedHashSet<>();
   private boolean isFinal = true;
   private WasmBlockType.Function blockType;

   public WasmFunctionType(String name, WasmType returnType, List<? extends WasmType> parameterTypes) {
      super(name);
      this.returnTypes = returnType != null ? List.of(returnType) : Collections.emptyList();
      this.parameterTypes = parameterTypes;
   }

   public WasmFunctionType(String name, List<? extends WasmType> returnTypes, List<? extends WasmType> parameterTypes) {
      super(name);
      this.returnTypes = returnTypes;
      this.parameterTypes = parameterTypes;
   }

   public WasmFunctionType(String name, Supplier<List<? extends WasmType>> returnTypeSupplier, Supplier<List<? extends WasmType>> parameterTypesSupplier) {
      super(name);
      this.returnTypeSupplier = returnTypeSupplier;
      this.parameterTypesSupplier = parameterTypesSupplier;
   }

   public List<? extends WasmType> getParameterTypes() {
      if (this.parameterTypes == null) {
         this.parameterTypes = List.copyOf(this.parameterTypesSupplier.get());
         this.parameterTypesSupplier = null;
      }

      return this.parameterTypes;
   }

   public List<? extends WasmType> getReturnTypes() {
      if (this.returnTypeSupplier != null) {
         this.returnTypes = this.returnTypeSupplier.get();
         this.returnTypeSupplier = null;
      }

      return this.returnTypes;
   }

   public WasmType getSingleReturnType() {
      if (this.returnTypes.isEmpty()) {
         return null;
      } else if (this.returnTypes.size() != 1) {
         throw new IllegalStateException("Function produces more that one value");
      } else {
         return this.returnTypes.get(0);
      }
   }

   public Set<WasmFunctionType> getSupertypes() {
      return this.supertypes;
   }

   public boolean isFinal() {
      return this.isFinal;
   }

   public void setFinal(boolean aFinal) {
      this.isFinal = aFinal;
   }

   public WasmBlockType.Function asBlock() {
      if (this.blockType == null) {
         this.blockType = new WasmBlockType.Function(this);
      }

      return this.blockType;
   }

   @Override
   public void acceptVisitor(WasmCompositeTypeVisitor visitor) {
      visitor.visit(this);
   }
}
