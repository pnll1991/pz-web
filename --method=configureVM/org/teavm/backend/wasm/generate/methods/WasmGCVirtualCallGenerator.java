package org.teavm.backend.wasm.generate.methods;

import java.util.function.Consumer;
import org.teavm.backend.wasm.generate.CachedValue;
import org.teavm.backend.wasm.generate.ValueCache;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfo;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTable;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTableEntry;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTableProvider;
import org.teavm.model.MethodReference;

public class WasmGCVirtualCallGenerator {
   private WasmGCVirtualTableProvider virtualTables;
   private WasmGCClassInfoProvider classInfoProvider;

   public WasmGCVirtualCallGenerator(WasmGCVirtualTableProvider virtualTables, WasmGCClassInfoProvider classInfoProvider) {
      this.virtualTables = virtualTables;
      this.classInfoProvider = classInfoProvider;
   }

   public void generate(
      WasmInstructionBuilder builder,
      MethodReference method,
      boolean suspending,
      ValueCache valueCache,
      WasmType.CompositeReference instanceType,
      Consumer<WasmInstructionBuilder> argsBuilder
   ) {
      WasmGCVirtualTable vtable = this.virtualTables.lookup(method.getClassName());
      if (vtable == null) {
         builder.unreachable();
      } else {
         WasmGCVirtualTableEntry entry = vtable.entry(method.getDescriptor());
         WasmGCVirtualTable nonInterfaceAncestor = vtable.closestNonInterfaceAncestor();
         if (entry != null && nonInterfaceAncestor != null) {
            CachedValue instance = valueCache.create(instanceType, builder);
            WasmGCClassInfo objectClass = this.classInfoProvider.getClassInfo("java.lang.Object");
            int index = 1 + entry.getIndex();
            WasmGCClassInfo expectedInstanceClassInfo = this.classInfoProvider.getClassInfo(vtable.getClassName());
            WasmStructure expectedInstanceClassStruct = this.classInfoProvider.getClassInfo(nonInterfaceAncestor.getClassName()).getStructure();
            WasmStructure vtableStruct = expectedInstanceClassInfo.getVirtualTableStructure();
            WasmStructure instanceStruct = (WasmStructure)instanceType.composite;
            if (!expectedInstanceClassStruct.isSupertypeOf(instanceStruct)) {
               builder.cast(expectedInstanceClassStruct.getNonNullReference());
            }

            argsBuilder.accept(builder);
            builder.append(instance).structGet(objectClass.getStructure(), 0).cast(vtableStruct.getNonNullReference()).structGet(vtableStruct, index);
            WasmType.CompositeReference functionTypeRef = (WasmType.CompositeReference)vtableStruct.getFields().get(index).getUnpackedType();
            builder.callReference((WasmFunctionType)functionTypeRef.composite, suspending);
            instance.release();
         } else {
            builder.unreachable();
         }
      }
   }
}
