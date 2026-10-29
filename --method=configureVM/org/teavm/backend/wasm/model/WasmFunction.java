package org.teavm.backend.wasm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.teavm.backend.wasm.model.instruction.WasmInstructionList;
import org.teavm.model.MethodReference;

public class WasmFunction extends WasmEntity {
   private String name;
   private String exportName;
   private String importName;
   private String importModule;
   private boolean referenced;
   private WasmFunctionType type;
   private List<WasmLocal> localVariables = new ArrayList<>();
   private List<WasmLocal> readonlyLocalVariables = Collections.unmodifiableList(this.localVariables);
   private WasmInstructionList body = new WasmInstructionList();
   private MethodReference javaMethod;

   public WasmFunction(WasmFunctionType type) {
      this.type = Objects.requireNonNull(type);
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
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

   public boolean isReferenced() {
      return this.referenced;
   }

   public void setReferenced(boolean referenced) {
      this.referenced = referenced;
   }

   @Override
   boolean isImported() {
      return this.importName != null;
   }

   public WasmFunctionType getType() {
      return this.type;
   }

   public void setType(WasmFunctionType type) {
      this.type = Objects.requireNonNull(type);
   }

   public List<WasmLocal> getLocalVariables() {
      return this.readonlyLocalVariables;
   }

   public WasmInstructionList getBody() {
      return this.body;
   }

   public void add(WasmLocal local) {
      if (local.function != null) {
         throw new IllegalArgumentException("This local is already registered in another function");
      }

      local.function = this;
      local.index = this.localVariables.size();
      this.localVariables.add(local);
   }

   public MethodReference getJavaMethod() {
      return this.javaMethod;
   }

   public void setJavaMethod(MethodReference javaMethod) {
      this.javaMethod = javaMethod;
   }
}
