package org.teavm.backend.wasm.transformation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmBlock;
import org.teavm.backend.wasm.model.instruction.WasmBranch;
import org.teavm.backend.wasm.model.instruction.WasmBreak;
import org.teavm.backend.wasm.model.instruction.WasmCall;
import org.teavm.backend.wasm.model.instruction.WasmCallReference;
import org.teavm.backend.wasm.model.instruction.WasmCast;
import org.teavm.backend.wasm.model.instruction.WasmCastBranch;
import org.teavm.backend.wasm.model.instruction.WasmConditional;
import org.teavm.backend.wasm.model.instruction.WasmDefaultInstructionVisitor;
import org.teavm.backend.wasm.model.instruction.WasmDrop;
import org.teavm.backend.wasm.model.instruction.WasmFloat32Constant;
import org.teavm.backend.wasm.model.instruction.WasmFloat64Constant;
import org.teavm.backend.wasm.model.instruction.WasmGetLocal;
import org.teavm.backend.wasm.model.instruction.WasmInstruction;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmInstructionList;
import org.teavm.backend.wasm.model.instruction.WasmInstructionUtil;
import org.teavm.backend.wasm.model.instruction.WasmInt32Constant;
import org.teavm.backend.wasm.model.instruction.WasmInt64Constant;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.backend.wasm.model.instruction.WasmIntUnary;
import org.teavm.backend.wasm.model.instruction.WasmIntUnaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmNullBranch;
import org.teavm.backend.wasm.model.instruction.WasmNullConstant;
import org.teavm.backend.wasm.model.instruction.WasmReturn;
import org.teavm.backend.wasm.model.instruction.WasmSetLocal;
import org.teavm.backend.wasm.model.instruction.WasmSwitch;
import org.teavm.backend.wasm.model.instruction.WasmTeeLocal;
import org.teavm.backend.wasm.model.instruction.WasmTry;
import org.teavm.backend.wasm.model.instruction.WasmTypeInference;
import org.teavm.backend.wasm.render.WasmSignature;
import org.teavm.model.TextLocation;

public class CoroutineTransformation {
   private static final String FIBER = "org.teavm.runtime.Fiber";
   private WasmFunctionTypes functionTypes;
   private WasmGCClassInfoProvider classInfoProvider;
   private CoroutineFunctions coroutineFunctions;
   private WasmFunction currentFunction;
   private WasmLocal savedFunctionLocal;
   private WasmLocal fiberLocal;
   private WasmLocal stateLocal;
   private SuspensionPointCollector collector;
   private int currentStateOffset;
   private Set<WasmInstruction> usedBreakTargets = new HashSet<>();

   public CoroutineTransformation(WasmFunctionTypes functionTypes, BaseWasmFunctionRepository functions, WasmGCClassInfoProvider classInfoProvider) {
      this.functionTypes = functionTypes;
      this.classInfoProvider = classInfoProvider;
      this.coroutineFunctions = new CoroutineFunctions(functions);
   }

   public void transform(WasmFunction function) {
      this.currentFunction = function;
      this.collector = new SuspensionPointCollector();
      this.collector.visitMany(function.getBody());
      int originalLocals = function.getLocalVariables().size();
      this.fiberLocal = new WasmLocal(this.classInfoProvider.getClassInfo("org.teavm.runtime.Fiber").getType(), "coroutine$fiber");
      this.stateLocal = new WasmLocal(WasmType.INT32, "coroutine$state");
      function.add(this.fiberLocal);
      function.add(this.stateLocal);
      new CoroutineTransformation.BreakTargetCollector().visitMany(function.getBody());
      WasmBlock mainBlock = new WasmBlock(false);
      mainBlock.getBody().transferFrom(function.getBody());
      this.generatePrologue(originalLocals);
      function.getBody().add(mainBlock);
      this.splitList(mainBlock.getBody(), Collections.emptyList(), function.getType().getReturnTypes(), mainBlock.getBody());
      if (!mainBlock.getBody().getLast().isTerminating()) {
         mainBlock.getBody().add(new WasmReturn());
      }

      this.generateEpilogue(originalLocals);
      this.currentFunction = null;
      this.savedFunctionLocal = null;
      this.collector = null;
      this.currentStateOffset = 0;
      this.stateLocal = null;
      this.fiberLocal = null;
      this.usedBreakTargets.clear();
   }

   private void generatePrologue(int localsCount) {
      WasmInstructionList prologue = new WasmInstructionList();
      WasmInstructionBuilder builder = prologue.builder();
      builder.call(this.coroutineFunctions.currentFiber()).teeLocal(this.fiberLocal).call(this.coroutineFunctions.isResuming());
      WasmConditional restoreCond = builder.conditional(WasmType.INT32);
      restoreCond.getThenBlock().builder().getLocal(this.fiberLocal).call(this.coroutineFunctions.popInt());

      for (int i = localsCount - 1; i >= 0; i--) {
         WasmLocal local = this.currentFunction.getLocalVariables().get(i);
         restoreCond.getThenBlock().add(new WasmGetLocal(this.fiberLocal));
         this.coroutineFunctions.restoreValue(local.getType(), restoreCond.getThenBlock(), null);
         restoreCond.getThenBlock().add(new WasmSetLocal(local));
      }

      restoreCond.getElseBlock().builder().i32Const(0);
      builder.setLocal(this.stateLocal);
      this.currentFunction.getBody().addFirst(prologue);
   }

   private void generateEpilogue(int localsCount) {
      WasmInstructionList list = new WasmInstructionList();

      for (int i = 0; i < localsCount; i++) {
         WasmLocal local = this.currentFunction.getLocalVariables().get(i);
         list.add(new WasmGetLocal(local));
         this.coroutineFunctions.saveValue(local.getType(), list, this.fiberLocal, null);
      }

      list.add(new WasmGetLocal(this.stateLocal));
      list.add(new WasmGetLocal(this.fiberLocal));
      list.add(new WasmCall(this.coroutineFunctions.pushInt()));
      WasmType returnType = this.currentFunction.getType().getSingleReturnType();
      if (returnType != null) {
         if (returnType instanceof WasmType.Number) {
            switch (((WasmType.Number)returnType).number) {
               case INT32:
                  list.add(new WasmInt32Constant(0));
                  break;
               case INT64:
                  list.add(new WasmInt64Constant(0L));
                  break;
               case FLOAT32:
                  list.add(new WasmFloat32Constant(0.0F));
                  break;
               case FLOAT64:
                  list.add(new WasmFloat64Constant(0.0));
            }
         } else {
            list.add(new WasmNullConstant((WasmType.Reference)returnType));
         }
      }

      this.currentFunction.getBody().transferFrom(list);
   }

   private void splitList(WasmInstructionList list, List<? extends WasmType> inputTypes, List<? extends WasmType> outputTypes, WasmInstructionList suspendLabel) {
      CoroutineTransformation.ListSplitter splitter = new CoroutineTransformation.ListSplitter(inputTypes, outputTypes, suspendLabel);
      WasmInstruction first = list.getFirst();
      splitter.addPrologue(list);
      splitter.process(first);
   }

   private List<WasmType> mapToNullableTypes(List<? extends WasmType> types) {
      ArrayList<WasmType> result = new ArrayList<>();

      for (WasmType type : types) {
         if (type instanceof WasmType.Reference) {
            type = ((WasmType.Reference)type).asNullable();
         }

         result.add(type);
      }

      return result;
   }

   private void moveAllPreviousTo(WasmInstruction instruction, WasmInstructionList list) {
      instruction = instruction.getPrevious();

      while (instruction != null) {
         WasmInstruction previous = instruction.getPrevious();
         instruction.delete();
         list.addFirst(instruction);
         instruction = previous;
      }
   }

   private void handleSplitInstruction(WasmInstruction instruction, int stateIndex) {
      if (instruction instanceof WasmCallReference callRef) {
         instruction.insertPrevious(new WasmTeeLocal(this.savedFunctionLocal()));
         instruction.insertPrevious(new WasmCast(callRef.getType().getReference()));
      } else if (instruction instanceof WasmBlock block) {
         if (block.isLoop()) {
            this.splitLoop(block, stateIndex);
         } else {
            List<? extends WasmType> inputTypes = block.getType() != null ? block.getType().getInputTypes() : Collections.emptyList();
            List<? extends WasmType> outputTypes = block.getType() != null ? block.getType().getOutputTypes() : Collections.emptyList();
            this.splitList(block.getBody(), inputTypes, outputTypes, block.getBody());
         }
      } else if (instruction instanceof WasmConditional conditional) {
         this.splitConditional(conditional);
      } else if (instruction instanceof WasmTry tryInsn) {
         List<? extends WasmType> outTypes = tryInsn.getType() != null ? List.of(tryInsn.getType()) : Collections.emptyList();
         this.splitList(tryInsn.getBody(), Collections.emptyList(), outTypes, tryInsn.getBody());
      }
   }

   private void splitLoop(WasmBlock block, int stateIndex) {
      WasmBlock breakLabel = new WasmBlock(false);
      WasmBlock newLoop = new WasmBlock(true);
      breakLabel.getBody().add(newLoop);
      block.insertPrevious(breakLabel);
      block.delete();
      block.setLoop(false);
      newLoop.getBody().add(block);
      newLoop.getBody().add(new WasmInt32Constant(stateIndex));
      newLoop.getBody().add(new WasmSetLocal(this.stateLocal));
      newLoop.getBody().add(new WasmBreak(newLoop.getBody()));
      this.splitList(newLoop.getBody(), Collections.emptyList(), Collections.emptyList(), breakLabel.getBody());
   }

   private void splitConditional(WasmConditional conditional) {
      ArrayList<WasmType> inputTypes = new ArrayList<>();
      ArrayList<WasmType> outputTypes = new ArrayList<>();
      if (conditional.getType() != null) {
         inputTypes.addAll(conditional.getType().getInputTypes());
         outputTypes.addAll(conditional.getType().getOutputTypes());
      }

      inputTypes.add(WasmType.INT32);
      WasmBlock wrapper = new WasmBlock(false);
      wrapper.setType(this.functionTypes.get(new WasmSignature(outputTypes, inputTypes)).asBlock());
      conditional.insertPrevious(wrapper);
      if (conditional.getElseBlock().isEmpty()) {
         wrapper.getBody().add(new WasmIntUnary(WasmIntType.INT32, WasmIntUnaryOperation.EQZ));
         wrapper.getBody().add(new WasmBranch(conditional.getThenBlock()));
         wrapper.getBody().transferFrom(conditional.getThenBlock());
         this.splitList(wrapper.getBody(), inputTypes, outputTypes, wrapper.getBody());
         BreakTargetReplacement replacement = new BreakTargetReplacement(target -> target == conditional.getThenBlock() ? wrapper.getBody() : null);
         replacement.visit(wrapper);
      } else {
         WasmBlock thenWrapper = new WasmBlock(false);
         thenWrapper.setType(this.functionTypes.get(new WasmSignature(Collections.emptyList(), inputTypes)).asBlock());
         thenWrapper.getBody().add(new WasmIntUnary(WasmIntType.INT32, WasmIntUnaryOperation.EQZ));
         thenWrapper.getBody().add(new WasmBranch(conditional.getThenBlock()));
         thenWrapper.getBody().transferFrom(conditional.getThenBlock());
         CoroutineTransformation.ListSplitter splitter = new CoroutineTransformation.ListSplitter(inputTypes, outputTypes, wrapper.getBody());
         WasmInstruction first = thenWrapper.getBody().getFirst();
         splitter.addPrologue(thenWrapper.getBody());
         splitter.process(first);
         if (!thenWrapper.getBody().getLast().isTerminating()) {
            thenWrapper.getBody().add(new WasmBreak(wrapper.getBody()));
         }

         wrapper.getBody().add(thenWrapper);
         wrapper.getBody().transferFrom(conditional.getElseBlock());
         splitter.typeInference.typeStack.clear();
         if (conditional.getType() != null) {
            splitter.typeInference.typeStack.addAll(conditional.getType().getInputTypes());
         }

         splitter.process(wrapper.getBody().getFirst().getNext());
         BreakTargetReplacement replacement = new BreakTargetReplacement(target -> {
            if (target == conditional.getThenBlock()) {
               return thenWrapper.getBody();
            } else {
               return target == conditional.getElseBlock() ? wrapper.getBody() : null;
            }
         });
         replacement.visit(wrapper);
      }

      conditional.delete();
   }

   private WasmLocal savedFunctionLocal() {
      if (this.savedFunctionLocal == null) {
         this.savedFunctionLocal = new WasmLocal(WasmType.FUNC, "coroutine$savedFunction");
         this.currentFunction.add(this.savedFunctionLocal);
      }

      return this.savedFunctionLocal;
   }

   private void pushDefault(WasmInstructionList list, WasmType type, TextLocation location) {
      if (type instanceof WasmType.Number) {
         switch (((WasmType.Number)type).number) {
            case INT32:
               list.add(new WasmInt32Constant(0), location);
               break;
            case INT64:
               list.add(new WasmInt64Constant(0L), location);
               break;
            case FLOAT32:
               list.add(new WasmFloat32Constant(0.0F), location);
               break;
            case FLOAT64:
               list.add(new WasmFloat64Constant(0.0), location);
         }
      } else {
         WasmType.Reference ref = (WasmType.Reference)type;
         list.add(new WasmNullConstant(ref), location);
      }
   }

   private void emitIsSuspending(WasmInstructionList list, TextLocation location) {
      list.add(new WasmGetLocal(this.fiberLocal), location);
      list.add(new WasmCall(this.coroutineFunctions.isSuspending()), location);
   }

   private class BreakTargetCollector extends WasmDefaultInstructionVisitor {
      @Override
      public void visit(WasmBreak instruction) {
         CoroutineTransformation.this.usedBreakTargets.add(instruction.getTarget().getBreakTarget());
         super.visit(instruction);
      }

      @Override
      public void visit(WasmBranch instruction) {
         CoroutineTransformation.this.usedBreakTargets.add(instruction.getTarget().getBreakTarget());
         super.visit(instruction);
      }

      @Override
      public void visit(WasmCastBranch instruction) {
         CoroutineTransformation.this.usedBreakTargets.add(instruction.getTarget().getBreakTarget());
         super.visit(instruction);
      }

      @Override
      public void visit(WasmNullBranch instruction) {
         CoroutineTransformation.this.usedBreakTargets.add(instruction.getTarget().getBreakTarget());
         super.visit(instruction);
      }

      @Override
      public void visit(WasmSwitch instruction) {
         CoroutineTransformation.this.usedBreakTargets.add(instruction.getDefaultTarget().getBreakTarget());

         for (WasmInstructionList target : instruction.getTargets()) {
            CoroutineTransformation.this.usedBreakTargets.add(target.getBreakTarget());
         }

         super.visit(instruction);
      }
   }

   private class ListSplitter {
      private List<? extends WasmType> inputTypes;
      private List<? extends WasmType> outputTypes;
      private WasmInstructionList suspendLabel;
      private WasmTypeInference typeInference;
      private int minDepth;
      private List<WasmType> stackSnapshot = new ArrayList<>();
      private WasmSwitch switchInsn;

      ListSplitter(List<? extends WasmType> inputTypes, List<? extends WasmType> outputTypes, WasmInstructionList suspendLabel) {
         this.inputTypes = inputTypes;
         this.outputTypes = outputTypes;
         this.suspendLabel = suspendLabel;
         this.typeInference = new WasmTypeInference(true);
         this.typeInference.typeStack.addAll(inputTypes);
         this.minDepth = inputTypes.size();
      }

      void process(WasmInstruction insn) {
         while (insn != null) {
            WasmInstruction next = insn.getNext();
            if (CoroutineTransformation.this.collector.isSuspending(insn)) {
               if (insn instanceof WasmBlock block && !block.isLoop() && block.getType() == null) {
                  this.processOptimizedBlock(block);
                  insn = next;
                  continue;
               }

               if (insn instanceof WasmConditional conditional && this.isOptimizableConditional(conditional)) {
                  this.processOptimizedConditional(conditional);
                  insn = next;
                  continue;
               }

               WasmBlock block = new WasmBlock(false);
               WasmBlock jumpToInsn = block;
               if (!this.inputTypes.isEmpty()) {
                  WasmSignature signature = new WasmSignature(Collections.emptyList(), CoroutineTransformation.this.mapToNullableTypes(this.inputTypes));
                  block.setType(CoroutineTransformation.this.functionTypes.get(signature).asBlock());
               }

               CoroutineTransformation.this.moveAllPreviousTo(insn, block.getBody());
               int stateIndex = ++CoroutineTransformation.this.currentStateOffset;
               block.getBody().add(new WasmInt32Constant(stateIndex), insn.getLocation());
               block.getBody().add(new WasmSetLocal(CoroutineTransformation.this.stateLocal), insn.getLocation());
               this.minDepth = Math.min(this.minDepth, this.typeInference.typeStack.size());
               this.stackSnapshot.clear();
               this.stackSnapshot.addAll(this.typeInference.typeStack);
               WasmType functionType = null;
               if (insn instanceof WasmCallReference) {
                  functionType = ((WasmCallReference)insn).getType().getReference();
               }

               this.updateTypes(insn);
               if (this.minDepth != this.inputTypes.size() || this.inputTypes.size() != this.stackSnapshot.size()) {
                  block = this.createRestoreInstructions(block, insn.getLocation(), functionType != null);
               }

               insn.insertPrevious(block);
               insn.insertNext(this.createSaveInstructions(functionType, insn.getLocation()));
               this.switchInsn.getTargets().add(jumpToInsn.getBody());
               CoroutineTransformation.this.handleSplitInstruction(insn, stateIndex);

               for (int i = stateIndex; i < CoroutineTransformation.this.currentStateOffset; i++) {
                  this.switchInsn.getTargets().add(jumpToInsn.getBody());
               }
            } else {
               this.updateTypes(insn);
            }

            insn = next;
         }
      }

      private void processOptimizedBlock(WasmBlock block) {
         WasmInstruction start = block.getBody().getFirst();
         CoroutineTransformation.this.moveAllPreviousTo(block, block.getBody());
         this.process(start);
         if (!this.typeInference.typeStack.isEmpty() || this.inputTypes.isEmpty()) {
            block.setType(
               CoroutineTransformation.this.functionTypes
                  .get(new WasmSignature(List.copyOf(this.typeInference.typeStack), List.copyOf(this.inputTypes)))
                  .asBlock()
            );
         }
      }

      private boolean isOptimizableConditional(WasmConditional conditional) {
         return conditional.getType() == null
            || conditional.getType().getInputTypes().isEmpty() && !CoroutineTransformation.this.usedBreakTargets.contains(conditional);
      }

      private void processOptimizedConditional(WasmConditional conditional) {
         ArrayList<WasmType> outputTypes = new ArrayList<>(this.typeInference.typeStack);
         outputTypes.remove(outputTypes.size() - 1);
         List<WasmType> typesAfterConditionCheck = List.copyOf(outputTypes);
         if (conditional.getType() != null) {
            outputTypes.addAll(conditional.getType().getOutputTypes());
         }

         WasmBlock wrapper = new WasmBlock(false);
         if (!outputTypes.isEmpty() || this.inputTypes.isEmpty()) {
            wrapper.setType(CoroutineTransformation.this.functionTypes.get(new WasmSignature(outputTypes, List.copyOf(this.inputTypes))).asBlock());
         }

         this.typeInference.typeStack.remove(this.typeInference.typeStack.size() - 1);
         this.minDepth = Math.min(this.minDepth, this.typeInference.typeStack.size());
         if (conditional.getElseBlock().isEmpty()) {
            CoroutineTransformation.this.moveAllPreviousTo(conditional, wrapper.getBody());
            WasmInstructionUtil.negate(wrapper.getBody().getLast());
            WasmBranch branch = new WasmBranch(wrapper.getBody());
            wrapper.getBody().add(branch);
            wrapper.getBody().transferFrom(conditional.getThenBlock());
            this.process(branch.getNext());
            BreakTargetReplacement replacement = new BreakTargetReplacement(target -> target == conditional.getThenBlock() ? wrapper.getBody() : null);
            replacement.visit(wrapper);
         } else {
            WasmBlock thenWrapper = new WasmBlock(false);
            if (!this.inputTypes.isEmpty() || !typesAfterConditionCheck.isEmpty()) {
               thenWrapper.setType(
                  CoroutineTransformation.this.functionTypes.get(new WasmSignature(typesAfterConditionCheck, List.copyOf(this.inputTypes))).asBlock()
               );
            }

            CoroutineTransformation.this.moveAllPreviousTo(conditional, thenWrapper.getBody());
            WasmInstructionUtil.negate(thenWrapper.getBody().getLast());
            WasmBranch branch = new WasmBranch(thenWrapper.getBody());
            thenWrapper.getBody().add(branch);
            thenWrapper.getBody().transferFrom(conditional.getThenBlock());
            List<WasmType> backupTypes = List.copyOf(this.typeInference.typeStack);
            int backupMinDepth = this.minDepth;
            this.process(branch.getNext());
            if (!thenWrapper.getBody().getLast().isTerminating()) {
               thenWrapper.getBody().add(new WasmBreak(wrapper.getBody()));
            }

            wrapper.getBody().add(thenWrapper);
            wrapper.getBody().transferFrom(conditional.getElseBlock());
            this.typeInference.typeStack.clear();
            this.typeInference.typeStack.addAll(backupTypes);
            this.minDepth = backupMinDepth;
            this.process(thenWrapper.getNext());
            BreakTargetReplacement replacement = new BreakTargetReplacement(target -> {
               if (target == conditional.getThenBlock()) {
                  return thenWrapper.getBody();
               } else {
                  return target == conditional.getElseBlock() ? wrapper.getBody() : null;
               }
            });
            replacement.visit(wrapper);
         }

         this.typeInference.typeStack.clear();
         this.typeInference.typeStack.addAll(outputTypes);
         conditional.insertPrevious(wrapper);
         conditional.delete();
      }

      private void updateTypes(WasmInstruction insn) {
         insn.acceptVisitor(this.typeInference);
         this.minDepth = Math.min(this.minDepth, this.typeInference.getDepthBeforeLastInstructionOut());
      }

      void addPrologue(WasmInstructionList target) {
         WasmBlock jumpToFirstLabel = new WasmBlock(false);
         WasmBlock jumpToUnreachable = new WasmBlock(false);
         jumpToFirstLabel.getBody().builder().add(jumpToUnreachable).unreachable();
         WasmInstructionBuilder jumpToUnreachableBuilder = jumpToUnreachable.getBody().builder();
         jumpToUnreachableBuilder.getLocal(CoroutineTransformation.this.stateLocal);
         if (CoroutineTransformation.this.currentStateOffset > 0) {
            jumpToUnreachableBuilder.i32Const(CoroutineTransformation.this.currentStateOffset).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SUB);
         }

         this.switchInsn = new WasmSwitch(jumpToUnreachable.getBody());
         this.switchInsn.getTargets().add(jumpToFirstLabel.getBody());
         jumpToUnreachableBuilder.add(this.switchInsn);
         target.addFirst(jumpToFirstLabel);
      }

      private WasmBlock createRestoreInstructions(WasmBlock block, TextLocation location, boolean isCallRef) {
         int depthWithoutArgs = this.typeInference.getDepthBeforeLastInstructionOut();
         WasmBlock restoreBlock = new WasmBlock(false);
         WasmSignature signature = new WasmSignature(
            CoroutineTransformation.this.mapToNullableTypes(this.stackSnapshot), CoroutineTransformation.this.mapToNullableTypes(this.inputTypes)
         );
         restoreBlock.setType(CoroutineTransformation.this.functionTypes.get(signature).asBlock());
         restoreBlock.setLocation(location);
         restoreBlock.getBody().add(block);
         block.getBody().add(new WasmBreak(restoreBlock.getBody()), location);

         for (WasmType type : this.stackSnapshot.subList(0, depthWithoutArgs)) {
            restoreBlock.getBody().add(new WasmGetLocal(CoroutineTransformation.this.fiberLocal), location);
            CoroutineTransformation.this.coroutineFunctions.restoreValue(type, restoreBlock.getBody(), location);
         }

         int dummyArgs = this.stackSnapshot.size();
         if (isCallRef) {
            dummyArgs--;
         }

         for (int i = depthWithoutArgs; i < dummyArgs; i++) {
            WasmType type = this.stackSnapshot.get(i);
            CoroutineTransformation.this.pushDefault(restoreBlock.getBody(), type, location);
         }

         if (isCallRef) {
            WasmType type = this.stackSnapshot.get(dummyArgs);
            restoreBlock.getBody().add(new WasmGetLocal(CoroutineTransformation.this.fiberLocal), location);
            CoroutineTransformation.this.coroutineFunctions.restoreValue(type, restoreBlock.getBody(), location);
         }

         return restoreBlock;
      }

      private WasmInstructionList createSaveInstructions(WasmType callRefType, TextLocation location) {
         int depthWithoutArgs = this.typeInference.getDepthBeforeLastInstructionOut();
         WasmInstructionList result = new WasmInstructionList();
         CoroutineTransformation.this.emitIsSuspending(result, location);
         WasmConditional check = new WasmConditional();
         result.add(check, location);
         if (callRefType != null) {
            check.getThenBlock().add(new WasmGetLocal(CoroutineTransformation.this.savedFunctionLocal()));
            CoroutineTransformation.this.coroutineFunctions.saveValue(callRefType, check.getThenBlock(), CoroutineTransformation.this.fiberLocal, location);
         }

         List<WasmType> condTypes = CoroutineTransformation.this.mapToNullableTypes(
            this.typeInference.typeStack.subList(this.minDepth, this.typeInference.typeStack.size())
         );
         if (!condTypes.isEmpty()) {
            check.setType(CoroutineTransformation.this.functionTypes.get(new WasmSignature(condTypes, condTypes)).asBlock());
         }

         for (int i = this.typeInference.typeStack.size() - 1; i >= depthWithoutArgs; i--) {
            check.getThenBlock().add(new WasmDrop(), location);
         }

         for (int i = depthWithoutArgs - 1; i >= this.minDepth; i--) {
            WasmType type = this.stackSnapshot.get(i);
            CoroutineTransformation.this.coroutineFunctions.saveValue(type, check.getThenBlock(), CoroutineTransformation.this.fiberLocal, location);
         }

         for (int i = 0; i < this.outputTypes.size(); i++) {
            CoroutineTransformation.this.pushDefault(check.getThenBlock(), this.outputTypes.get(i), location);
         }

         check.getThenBlock().add(new WasmBreak(this.suspendLabel), location);
         return result;
      }
   }
}
