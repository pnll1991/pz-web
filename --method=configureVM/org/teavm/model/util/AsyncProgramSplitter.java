package org.teavm.model.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.teavm.common.Graph;
import org.teavm.common.GraphSplittingBackend;
import org.teavm.common.GraphUtils;
import org.teavm.common.IntegerArray;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;

public class AsyncProgramSplitter {
   private List<AsyncProgramSplitter.Part> parts = new ArrayList<>();
   private Map<Instruction, Integer> partMap = new HashMap<>();
   private ClassReaderSource classSource;
   private Set<MethodReference> asyncMethods;
   private Program program;
   private static final MethodDescriptor CLINIT_METHOD = new MethodDescriptor("<clinit>", ValueType.VOID);

   public AsyncProgramSplitter(ClassReaderSource classSource, Set<MethodReference> asyncMethods) {
      this.classSource = classSource;
      this.asyncMethods = asyncMethods;
   }

   public void split(Program program) {
      this.program = program;
      this.parts.clear();
      Program initialProgram = this.createStubCopy(program);
      AsyncProgramSplitter.Part initialPart = new AsyncProgramSplitter.Part(program.basicBlockCount());
      initialPart.program = initialProgram;
      this.parts.add(initialPart);
      AsyncProgramSplitter.Step initialStep = new AsyncProgramSplitter.Step();
      initialStep.source = 0;
      initialStep.targetPart = initialPart;
      Queue<AsyncProgramSplitter.Step> queue = new ArrayDeque<>();
      queue.add(initialStep);
      Set<BasicBlock> visitedBlocks = new HashSet<>();

      label124:
      while (!queue.isEmpty()) {
         AsyncProgramSplitter.Step step = queue.remove();
         BasicBlock targetBlock = step.targetPart.program.basicBlockAt(step.source);
         if (visitedBlocks.add(targetBlock)) {
            BasicBlock sourceBlock = program.basicBlockAt(step.source);
            step.targetPart.originalBlocks[step.source] = step.source;
            Instruction last = sourceBlock.getFirstInstruction();

            for (Instruction insn : sourceBlock) {
               if (!(
                  insn instanceof InvokeInstruction invoke
                     ? (
                        invoke.getType() == InvocationType.VIRTUAL
                           ? !this.isAsyncMethod(this.findRealMethod(invoke.getMethod()))
                           : !this.asyncMethods.contains(this.findRealMethod(invoke.getMethod()))
                     )
                     : (
                        insn instanceof InitClassInstruction
                           ? !this.isSplittingClassInitializer(((InitClassInstruction)insn).getClassName())
                           : !(insn instanceof MonitorEnterInstruction)
                     )
               )) {
                  if (sourceBlock.getExceptionVariable() != null) {
                     targetBlock.setExceptionVariable(targetBlock.getProgram().variableAt(sourceBlock.getExceptionVariable().getIndex()));
                  }

                  targetBlock.addAll(ProgramUtils.copyInstructions(last, insn, targetBlock.getProgram()));
                  targetBlock.getTryCatchBlocks().addAll(ProgramUtils.copyTryCatches(sourceBlock, targetBlock.getProgram()));

                  for (TryCatchBlock tryCatch : targetBlock.getTryCatchBlocks()) {
                     if (tryCatch.getHandler() != null) {
                        AsyncProgramSplitter.Step next = new AsyncProgramSplitter.Step();
                        next.source = tryCatch.getHandler().getIndex();
                        next.targetPart = step.targetPart;
                        queue.add(next);
                     }
                  }

                  last = insn;
                  step.targetPart.splitPoints[targetBlock.getIndex()] = insn;
                  if (this.partMap.containsKey(insn)) {
                     step.targetPart.blockSuccessors[targetBlock.getIndex()] = this.partMap.get(insn);
                     continue label124;
                  }

                  Program nextProgram = this.createStubCopy(program);
                  AsyncProgramSplitter.Part part = new AsyncProgramSplitter.Part(program.basicBlockCount() + 1);
                  part.program = nextProgram;
                  int partId = this.parts.size();
                  this.parts.add(part);
                  this.partMap.put(insn, partId);
                  step.targetPart.blockSuccessors[targetBlock.getIndex()] = partId;
                  targetBlock = nextProgram.createBasicBlock();
                  if (targetBlock.getIndex() > 0) {
                     JumpInstruction jumpToNextBlock = new JumpInstruction();
                     jumpToNextBlock.setTarget(targetBlock);
                     nextProgram.basicBlockAt(0).add(jumpToNextBlock);
                     nextProgram.basicBlockAt(0).getTryCatchBlocks().addAll(ProgramUtils.copyTryCatches(sourceBlock, nextProgram));
                  }

                  step.targetPart = part;
                  part.originalBlocks[targetBlock.getIndex()] = step.source;
                  this.partMap.put(program.basicBlockAt(0).getFirstInstruction(), 0);
               }
            }

            if (sourceBlock.getExceptionVariable() != null) {
               targetBlock.setExceptionVariable(targetBlock.getProgram().variableAt(sourceBlock.getExceptionVariable().getIndex()));
            }

            targetBlock.addAll(ProgramUtils.copyInstructions(last, null, targetBlock.getProgram()));
            targetBlock.getTryCatchBlocks().addAll(ProgramUtils.copyTryCatches(sourceBlock, targetBlock.getProgram()));
            targetBlock.setExceptionVariable(sourceBlock.getExceptionVariable());

            for (TryCatchBlock tryCatch : targetBlock.getTryCatchBlocks()) {
               if (tryCatch.getHandler() != null) {
                  AsyncProgramSplitter.Step next = new AsyncProgramSplitter.Step();
                  next.source = tryCatch.getHandler().getIndex();
                  next.targetPart = step.targetPart;
                  queue.add(next);
               }
            }

            TransitionExtractor successorExtractor = new TransitionExtractor();
            sourceBlock.getLastInstruction().acceptVisitor(successorExtractor);

            for (BasicBlock successor : successorExtractor.getTargets()) {
               BasicBlock targetSuccessor = targetBlock.getProgram().basicBlockAt(successor.getIndex());
               if (targetSuccessor.instructionCount() == 0) {
                  AsyncProgramSplitter.Step next = new AsyncProgramSplitter.Step();
                  next.source = successor.getIndex();
                  next.targetPart = step.targetPart;
                  queue.add(next);
               }
            }
         }
      }

      for (AsyncProgramSplitter.Part part : this.parts) {
         Graph graph = ProgramUtils.buildControlFlowGraph(part.program);
         if (GraphUtils.isIrreducible(graph)) {
            IntegerArray blockSuccessors = IntegerArray.of(part.blockSuccessors);
            IntegerArray originalBlocks = IntegerArray.of(part.originalBlocks);
            List<Instruction> splitPoints = new ArrayList<>(Arrays.asList(part.splitPoints));
            AsyncProgramSplitter.AsyncProgramSplittingBackend splittingBackend = new AsyncProgramSplitter.AsyncProgramSplittingBackend(
               new ProgramNodeSplittingBackend(part.program), blockSuccessors, originalBlocks, splitPoints
            );
            int[] weights = new int[graph.size()];

            for (int i = 0; i < part.program.basicBlockCount(); i++) {
               weights[i] = part.program.basicBlockAt(i).instructionCount();
            }

            GraphUtils.splitIrreducibleGraph(graph, weights, splittingBackend);
            part.blockSuccessors = splittingBackend.blockSuccessors.getAll();
            part.originalBlocks = splittingBackend.originalBlocks.getAll();
            part.splitPoints = splittingBackend.splitPoints.toArray(new Instruction[0]);
         }
      }

      this.partMap.clear();
   }

   private boolean isSplittingClassInitializer(String className) {
      ClassReader cls = this.classSource.get(className);
      if (cls == null) {
         return false;
      }

      MethodReader method = cls.getMethod(CLINIT_METHOD);
      return method != null && this.asyncMethods.contains(method.getReference());
   }

   private MethodReference findRealMethod(MethodReference method) {
      String clsName = method.getClassName();

      while (clsName != null) {
         ClassReader cls = this.classSource.get(clsName);
         if (cls == null) {
            break;
         }

         MethodReader methodReader = cls.getMethod(method.getDescriptor());
         if (methodReader != null) {
            return new MethodReference(clsName, method.getDescriptor());
         }

         clsName = cls.getParent();
         if (clsName != null && clsName.equals(cls.getName())) {
            break;
         }
      }

      return method;
   }

   private boolean isAsyncMethod(MethodReference method) {
      if (this.asyncMethods.isEmpty()) {
         return false;
      }

      if (this.asyncMethods.contains(method)) {
         return true;
      }

      ClassReader cls = this.classSource.get(method.getClassName());
      if (cls == null) {
         return false;
      }

      if (cls.getParent() != null && this.isAsyncMethod(new MethodReference(cls.getParent(), method.getDescriptor()))) {
         return true;
      }

      for (String itf : cls.getInterfaces()) {
         if (this.isAsyncMethod(new MethodReference(itf, method.getDescriptor()))) {
            return true;
         }
      }

      return false;
   }

   private Program createStubCopy(Program program) {
      Program copy = new Program();

      for (int i = 0; i < program.basicBlockCount(); i++) {
         copy.createBasicBlock();
      }

      for (int i = 0; i < program.variableCount(); i++) {
         Variable var = program.variableAt(i);
         copy.createVariable();
         Variable varCopy = copy.variableAt(i);
         varCopy.setRegister(var.getRegister());
         varCopy.setDebugName(var.getDebugName());
         varCopy.setLabel(var.getLabel());
      }

      return copy;
   }

   public int size() {
      return this.parts.size();
   }

   public Program getOriginalProgram() {
      return this.program;
   }

   public Program getProgram(int index) {
      return this.parts.get(index).program;
   }

   public int[] getBlockSuccessors(int index) {
      int[] result = this.parts.get(index).blockSuccessors;
      return Arrays.copyOf(result, result.length);
   }

   public Instruction[] getSplitPoints(int index) {
      return (Instruction[])this.parts.get(index).splitPoints.clone();
   }

   public int[] getOriginalBlocks(int index) {
      return (int[])this.parts.get(index).originalBlocks.clone();
   }

   private static class AsyncProgramSplittingBackend implements GraphSplittingBackend {
      private GraphSplittingBackend inner;
      private IntegerArray blockSuccessors;
      private IntegerArray originalBlocks;
      private List<Instruction> splitPoints;

      AsyncProgramSplittingBackend(GraphSplittingBackend inner, IntegerArray blockSuccessors, IntegerArray originalBlocks, List<Instruction> splitPoints) {
         this.inner = inner;
         this.blockSuccessors = blockSuccessors;
         this.originalBlocks = originalBlocks;
         this.splitPoints = splitPoints;
      }

      @Override
      public int[] split(int[] remaining, int[] toCopy) {
         int[] copies = this.inner.split(remaining, toCopy);

         for (int i = 0; i < copies.length; i++) {
            int copy = copies[i];
            int node = toCopy[i];
            if (this.blockSuccessors.size() <= copy) {
               this.blockSuccessors.add(-1);
               this.splitPoints.add(null);
               this.originalBlocks.add(-1);
            }

            this.blockSuccessors.set(copy, this.blockSuccessors.get(node));
            this.originalBlocks.set(copy, this.originalBlocks.get(node));
            this.splitPoints.set(copy, this.splitPoints.get(node));
         }

         return copies;
      }
   }

   static class Part {
      Program program;
      int[] blockSuccessors;
      Instruction[] splitPoints;
      int[] originalBlocks;

      Part(int blockCount) {
         this.blockSuccessors = new int[blockCount];
         Arrays.fill(this.blockSuccessors, -1);
         this.splitPoints = new Instruction[blockCount];
         this.originalBlocks = new int[blockCount];
         Arrays.fill(this.originalBlocks, -1);
      }
   }

   private static class Step {
      AsyncProgramSplitter.Part targetPart;
      int source;
   }
}
