package org.teavm.backend.wasm.generate.classes;

import java.util.function.Consumer;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.model.ValueType;

public class WasmGCClassInfo {
   private ValueType valueType;
   boolean heapStructure;
   WasmStructure structure;
   WasmStructure virtualTableStructure;
   WasmGlobal pointer;
   WasmGlobal virtualTablePointer;
   WasmGlobal initializerPointer;
   Consumer<WasmInstructionBuilder> initializer;
   WasmFunction newArrayFunction;
   WasmFunction initArrayFunction;
   WasmFunction supertypeFunction;
   ObjectIntMap<String> heapFieldOffsets;
   int heapSize;
   int heapAlignment;

   WasmGCClassInfo(ValueType valueType) {
      this.valueType = valueType;
   }

   public ValueType getValueType() {
      return this.valueType;
   }

   public boolean isHeapStructure() {
      return this.heapStructure;
   }

   public WasmStructure getStructure() {
      return this.structure;
   }

   public WasmArray getArray() {
      WasmField field = this.structure.getFields().get(2);
      WasmType.CompositeReference type = (WasmType.CompositeReference)field.getUnpackedType();
      return (WasmArray)type.composite;
   }

   public WasmStructure getVirtualTableStructure() {
      return this.virtualTableStructure;
   }

   public WasmType.CompositeReference getType() {
      return this.getStructure().getReference();
   }

   public WasmGlobal getPointer() {
      return this.pointer;
   }

   public WasmGlobal getVirtualTablePointer() {
      return this.virtualTablePointer;
   }

   public WasmGlobal getInitializerPointer() {
      return this.initializerPointer;
   }
}
