package org.teavm.backend.wasm.model;

public class WasmTag extends WasmEntity {
   private WasmFunctionType type;
   private String exportName;

   public WasmTag(WasmFunctionType type) {
      this.type = type;
   }

   public WasmFunctionType getType() {
      return this.type;
   }

   public String getExportName() {
      return this.exportName;
   }

   public void setExportName(String exportName) {
      this.exportName = exportName;
   }

   public int getIndex() {
      return this.index;
   }
}
