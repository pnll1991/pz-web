package org.teavm.backend.wasm.generate.classes;

import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.reflection.ClassInfoStruct;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTable;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTableEntry;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTableProvider;

class WasmGCSystemFunctionGenerator {
   private WasmGCClassGenerator classGenerator;
   private WasmModule module;
   private WasmFunctionTypes functionTypes;
   private WasmGCNameProvider names;
   private WasmGCVirtualTableProvider virtualTables;
   private WasmGCStandardClasses standardClasses;
   private WasmFunction objectVirtualTableFunction;

   WasmGCSystemFunctionGenerator(
      WasmGCClassGenerator classGenerator,
      WasmModule module,
      WasmFunctionTypes functionTypes,
      WasmGCNameProvider names,
      WasmGCVirtualTableProvider virtualTables,
      WasmGCStandardClasses standardClasses
   ) {
      this.classGenerator = classGenerator;
      this.module = module;
      this.functionTypes = functionTypes;
      this.names = names;
      this.virtualTables = virtualTables;
      this.standardClasses = standardClasses;
   }

   WasmFunction getFillObjectVirtualTableFunction() {
      if (this.objectVirtualTableFunction == null) {
         this.objectVirtualTableFunction = this.createFillObjectVirtualTableFunction();
      }

      return this.objectVirtualTableFunction;
   }

   private WasmFunction createFillObjectVirtualTableFunction() {
      WasmGCClassInfo objectCls = this.standardClasses.objectClass();
      WasmStructure struct = objectCls.getVirtualTableStructure();
      ClassInfoStruct classInfoType = this.classGenerator.reflectionTypes().classInfo();
      WasmFunction function = new WasmFunction(this.functionTypes.of(null, struct.getNonNullReference(), classInfoType.structure().getNonNullReference()));
      function.setName(this.names.topLevel("teavm@objectVirtualTable"));
      WasmLocal vtParam = new WasmLocal(struct.getNonNullReference(), "vt");
      WasmLocal clsParam = new WasmLocal(classInfoType.structure().getNonNullReference());
      function.add(vtParam);
      function.add(clsParam);
      WasmGCVirtualTable virtualTable = this.virtualTables.lookup("java.lang.Object");
      WasmInstructionBuilder body = function.getBody().builder();

      for (int i = 0; i < virtualTable.getEntries().size(); i++) {
         WasmGCVirtualTableEntry entry = virtualTable.getEntries().get(i);
         this.classGenerator.fillVirtualTableEntry(body, b -> b.getLocal(vtParam), struct, virtualTable, entry);
      }

      body.getLocal(vtParam)
         .getLocal(clsParam)
         .structSet(struct, 0)
         .getLocal(clsParam)
         .getLocal(vtParam)
         .structSet(classInfoType.structure(), classInfoType.vtableIndex());
      this.module.functions.add(function);
      return function;
   }
}
