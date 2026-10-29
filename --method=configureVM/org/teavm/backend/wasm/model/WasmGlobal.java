package org.teavm.backend.wasm.model;

import java.util.Objects;
import org.teavm.backend.wasm.model.instruction.WasmInstructionList;

public class WasmGlobal extends WasmEntity {
   private String name;
   private WasmType type;
   private WasmInstructionList initialValue = new WasmInstructionList();
   private boolean immutable;
   private String exportName;
   private String importName;
   private String importModule;

   public WasmGlobal(String name, WasmType type) {
      this.name = name;
      this.type = Objects.requireNonNull(type);
   }

   public String getName() {
      return this.name;
   }

   public WasmType getType() {
      return this.type;
   }

   public void setType(WasmType type) {
      this.type = Objects.requireNonNull(type);
   }

   public WasmInstructionList getInitialValue() {
      return this.initialValue;
   }

   public boolean isImmutable() {
      return this.immutable;
   }

   public void setImmutable(boolean immutable) {
      this.immutable = immutable;
   }

   public String getExportName() {
      return this.exportName;
   }

   public void setExportName(String exportName) {
      this.exportName = exportName;
   }

   public String getImportName() {
      return this.importName;
   }

   public void setImportName(String importName) {
      this.importName = importName;
      if (this.collection != null) {
         this.collection.invalidateIndexes();
      }
   }

   public String getImportModule() {
      return this.importModule;
   }

   public void setImportModule(String importModule) {
      this.importModule = importModule;
   }

   @Override
   boolean isImported() {
      return this.importName != null;
   }
}
