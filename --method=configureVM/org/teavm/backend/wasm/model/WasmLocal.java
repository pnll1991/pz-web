package org.teavm.backend.wasm.model;

import java.util.Objects;
import org.teavm.backend.wasm.debug.info.VariableType;

public class WasmLocal {
   WasmFunction function;
   int index;
   private String name;
   private WasmType type;
   private VariableType javaType;

   public WasmLocal(WasmType type, String name) {
      Objects.requireNonNull(type);
      this.type = type;
      this.name = name;
   }

   public WasmLocal(WasmType type) {
      this(type, null);
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public WasmType getType() {
      return this.type;
   }

   public void setType(WasmType type) {
      Objects.requireNonNull(type);
      this.type = type;
   }

   public WasmFunction getFunction() {
      return this.function;
   }

   public int getIndex() {
      return this.index;
   }

   public void setJavaType(VariableType javaType) {
      this.javaType = javaType;
   }

   public VariableType getJavaType() {
      return this.javaType;
   }
}
