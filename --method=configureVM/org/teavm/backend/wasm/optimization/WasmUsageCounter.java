package org.teavm.backend.wasm.optimization;

import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmBlockType;
import org.teavm.backend.wasm.model.WasmCompositeType;
import org.teavm.backend.wasm.model.WasmCompositeTypeVisitor;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmArrayCopy;
import org.teavm.backend.wasm.model.instruction.WasmArrayGet;
import org.teavm.backend.wasm.model.instruction.WasmArrayNewDefault;
import org.teavm.backend.wasm.model.instruction.WasmArrayNewFixed;
import org.teavm.backend.wasm.model.instruction.WasmArraySet;
import org.teavm.backend.wasm.model.instruction.WasmBlock;
import org.teavm.backend.wasm.model.instruction.WasmCall;
import org.teavm.backend.wasm.model.instruction.WasmCallReference;
import org.teavm.backend.wasm.model.instruction.WasmCast;
import org.teavm.backend.wasm.model.instruction.WasmCastBranch;
import org.teavm.backend.wasm.model.instruction.WasmDefaultInstructionVisitor;
import org.teavm.backend.wasm.model.instruction.WasmFunctionReference;
import org.teavm.backend.wasm.model.instruction.WasmGetGlobal;
import org.teavm.backend.wasm.model.instruction.WasmIndirectCall;
import org.teavm.backend.wasm.model.instruction.WasmInstruction;
import org.teavm.backend.wasm.model.instruction.WasmSetGlobal;
import org.teavm.backend.wasm.model.instruction.WasmStructGet;
import org.teavm.backend.wasm.model.instruction.WasmStructNew;
import org.teavm.backend.wasm.model.instruction.WasmStructNewDefault;
import org.teavm.backend.wasm.model.instruction.WasmStructSet;
import org.teavm.backend.wasm.model.instruction.WasmTest;
import org.teavm.backend.wasm.model.instruction.WasmTry;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;

public class WasmUsageCounter extends WasmDefaultInstructionVisitor implements WasmCompositeTypeVisitor {
   private ObjectIntMap<WasmFunction> usagesByFunction = new ObjectIntHashMap();
   private ObjectIntMap<WasmGlobal> usagesByGlobals = new ObjectIntHashMap();
   private ObjectIntMap<WasmCompositeType> usagesByTypes = new ObjectIntHashMap();

   public void applyToModule(WasmModule module) {
      for (WasmCompositeType type : module.types) {
         type.acceptVisitor(this);
      }

      for (WasmFunction function : module.functions) {
         for (WasmInstruction part : function.getBody()) {
            part.acceptVisitor(this);
         }

         this.addUsage(function.getType());
      }

      for (WasmGlobal global : module.globals) {
         this.visitMany(global.getInitialValue());
         this.addUsage(global.getType());
      }
   }

   public int usages(WasmFunction function) {
      return this.usagesByFunction.get(function);
   }

   public int usages(WasmGlobal global) {
      return this.usagesByGlobals.get(global);
   }

   public int usages(WasmCompositeType type) {
      return this.usagesByTypes.get(type);
   }

   @Override
   public void visit(WasmStructure type) {
      for (WasmField field : type.getFields()) {
         this.addUsage(field.getUnpackedType());
      }
   }

   @Override
   public void visit(WasmArray type) {
      this.addUsage(type.getElementType().asUnpackedType());
   }

   @Override
   public void visit(WasmFunctionType type) {
      for (WasmType ret : type.getReturnTypes()) {
         this.addUsage(ret);
      }

      for (WasmType param : type.getParameterTypes()) {
         this.addUsage(param);
      }
   }

   @Override
   public void visit(WasmCall instruction) {
      this.addUsage(instruction.getFunction());
   }

   @Override
   public void visit(WasmFunctionReference instruction) {
      this.addUsage(instruction.getFunction());
   }

   @Override
   public void visit(WasmGetGlobal instruction) {
      this.addUsage(instruction.getGlobal());
   }

   @Override
   public void visit(WasmSetGlobal instruction) {
      this.addUsage(instruction.getGlobal());
   }

   @Override
   public void visit(WasmBlock instruction) {
      super.visit(instruction);
      this.addUsage(instruction.getType());
   }

   @Override
   public void visit(WasmTry instruction) {
      super.visit(instruction);
      this.addUsage(instruction.getType());
   }

   @Override
   public void visit(WasmCastBranch instruction) {
      this.addUsage(instruction.getSourceType());
      this.addUsage(instruction.getTargetType());
   }

   @Override
   public void visit(WasmCallReference instruction) {
      this.addUsage(instruction.getType());
   }

   @Override
   public void visit(WasmIndirectCall instruction) {
      this.addUsage(instruction.getType());
   }

   @Override
   public void visit(WasmCast instruction) {
      this.addUsage(instruction.getTargetType());
   }

   @Override
   public void visit(WasmTest instruction) {
      this.addUsage(instruction.getTestType());
   }

   @Override
   public void visit(WasmStructNew instruction) {
      this.addUsage(instruction.getType());
   }

   @Override
   public void visit(WasmStructNewDefault instruction) {
      this.addUsage(instruction.getType());
   }

   @Override
   public void visit(WasmStructGet instruction) {
      this.addUsage(instruction.getType());
   }

   @Override
   public void visit(WasmStructSet instruction) {
      this.addUsage(instruction.getType());
   }

   @Override
   public void visit(WasmArrayNewDefault instruction) {
      this.addUsage(instruction.getType());
   }

   @Override
   public void visit(WasmArrayNewFixed instruction) {
      this.addUsage(instruction.getType());
   }

   @Override
   public void visit(WasmArrayGet instruction) {
      this.addUsage(instruction.getType());
   }

   @Override
   public void visit(WasmArraySet instruction) {
      this.addUsage(instruction.getType());
   }

   @Override
   public void visit(WasmArrayCopy instruction) {
      this.addUsage(instruction.getSourceArrayType());
      this.addUsage(instruction.getTargetArrayType());
   }

   private void addUsage(WasmBlockType type) {
      if (type != null) {
         if (type instanceof WasmBlockType.Function) {
            this.addUsage(((WasmBlockType.Function)type).ref);
         } else {
            this.addUsage(((WasmBlockType.Value)type).type);
         }
      }
   }

   private void addUsage(WasmType type) {
      if (type instanceof WasmType.CompositeReference) {
         this.addUsage(((WasmType.CompositeReference)type).composite);
      }
   }

   private void addUsage(WasmFunction function) {
      int index = this.usagesByFunction.indexOf(function);
      if (index < 0) {
         this.usagesByFunction.put(function, 1);
      } else {
         this.usagesByFunction.indexReplace(index, this.usagesByFunction.indexGet(index) + 1);
      }
   }

   private void addUsage(WasmGlobal function) {
      int index = this.usagesByGlobals.indexOf(function);
      if (index < 0) {
         this.usagesByGlobals.put(function, 1);
      } else {
         this.usagesByGlobals.indexReplace(index, this.usagesByGlobals.indexGet(index) + 1);
      }
   }

   private void addUsage(WasmCompositeType type) {
      int index = this.usagesByTypes.indexOf(type);
      if (index < 0) {
         this.usagesByTypes.put(type, 1);
      } else {
         this.usagesByTypes.indexReplace(index, this.usagesByTypes.indexGet(index) + 1);
      }
   }
}
