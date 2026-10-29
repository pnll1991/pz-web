package org.teavm.model.optimization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.teavm.dependency.DependencyInfo;
import org.teavm.hppc.IntArrayList;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.hppc.cursors.ObjectCursor;
import org.teavm.model.BasicBlock;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.Incoming;
import org.teavm.model.InliningInfo;
import org.teavm.model.Instruction;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.ProgramReader;
import org.teavm.model.TextLocation;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.analysis.ClassInference;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.util.BasicBlockMapper;
import org.teavm.model.util.InstructionVariableMapper;
import org.teavm.model.util.ProgramUtils;
import org.teavm.model.util.TransitionExtractor;

public class Inlining {
   private IntArrayList depthsByBlock;
   private Set<Instruction> instructionsToSkip;
   private ClassHierarchy hierarchy;
   private ListableClassReaderSource classes;
   private DependencyInfo dependencyInfo;
   private InliningStrategy strategy;
   private Inlining.MethodUsageCounter usageCounter;
   private Set<MethodReference> methodsUsedOnce = new HashSet<>();
   private boolean devirtualization;
   private ClassInference classInference;
   private InliningFilterFactory filterFactory;

   public Inlining(
      ClassHierarchy hierarchy,
      DependencyInfo dependencyInfo,
      InliningStrategy strategy,
      ListableClassReaderSource classes,
      Predicate<MethodReference> externalMethods,
      boolean devirtualization,
      InliningFilterFactory filterFactory
   ) {
      this.hierarchy = hierarchy;
      this.classes = classes;
      this.dependencyInfo = dependencyInfo;
      this.strategy = strategy;
      this.devirtualization = devirtualization;
      this.filterFactory = filterFactory;
      this.usageCounter = new Inlining.MethodUsageCounter(externalMethods);

      for (String className : classes.getClassNames()) {
         ClassReader cls = classes.get(className);

         for (MethodReader method : cls.getMethods()) {
            ProgramReader program = method.getProgram();
            if (program != null) {
               this.usageCounter.currentMethod = method.getReference();

               for (BasicBlockReader block : program.getBasicBlocks()) {
                  block.readAllInstructions(this.usageCounter);
               }
            }
         }
      }

      for (ObjectCursor<MethodReference> cursor : this.usageCounter.methodUsageCount.keys()) {
         if (this.usageCounter.methodUsageCount.get((MethodReference)cursor.value) == 1) {
            this.methodsUsedOnce.add((MethodReference)cursor.value);
         }
      }
   }

   public List<MethodReference> getOrder() {
      List<MethodReference> order = new ArrayList<>();
      Set<MethodReference> visited = new HashSet<>();

      for (String className : this.classes.getClassNames()) {
         ClassReader cls = this.classes.get(className);

         for (MethodReader method : cls.getMethods()) {
            if (method.getProgram() != null) {
               this.computeOrder(method.getReference(), order, visited);
            }
         }
      }

      Collections.reverse(order);
      return order;
   }

   private void computeOrder(MethodReference method, List<MethodReference> order, Set<MethodReference> visited) {
      if (visited.add(method)) {
         Set<MethodReference> invokedMethods = this.usageCounter.methodDependencies.get(method);
         if (invokedMethods != null) {
            for (MethodReference invokedMethod : invokedMethods) {
               this.computeOrder(invokedMethod, order, visited);
            }
         }

         order.add(method);
      }
   }

   public boolean hasUsages(MethodReference method) {
      return this.usageCounter.methodUsageCount.getOrDefault(method, -1) != 0;
   }

   public void removeUsages(Program program) {
      for (BasicBlock block : program.getBasicBlocks()) {
         for (Instruction instruction : block) {
            if (instruction instanceof InvokeInstruction invoke && invoke.getType() == InvocationType.SPECIAL) {
               int usageCount = this.usageCounter.methodUsageCount.getOrDefault(invoke.getMethod(), -1);
               if (usageCount > 0) {
                  this.usageCounter.methodUsageCount.put(invoke.getMethod(), usageCount - 1);
               }
            }
         }
      }
   }

   public void apply(Program program, MethodReference method) {
      this.depthsByBlock = new IntArrayList(program.basicBlockCount());

      for (int i = 0; i < program.basicBlockCount(); i++) {
         this.depthsByBlock.add(0);
      }

      this.instructionsToSkip = new HashSet<>();
      if (this.devirtualization) {
         while (this.applyOnce(program, method)) {
            this.devirtualize(program, method, this.dependencyInfo);
         }
      } else {
         this.applyOnce(program, method);
      }

      this.depthsByBlock = null;
      this.instructionsToSkip = null;
      new UnreachableBasicBlockEliminator().optimize(program);
      this.strategy.methodChanged(method);
   }

   private boolean applyOnce(Program program, MethodReference method) {
      InliningStep step = this.strategy.start(method, program);
      if (step == null) {
         return false;
      }

      List<Inlining.PlanEntry> plan = this.buildPlan(program, -1, step, method, null);
      if (plan.isEmpty()) {
         return false;
      }

      this.execPlan(program, plan, 0);
      return true;
   }

   private void execPlan(Program program, List<Inlining.PlanEntry> plan, int offset) {
      for (Inlining.PlanEntry entry : plan) {
         this.execPlanEntry(program, entry, offset);
      }
   }

   private void execPlanEntry(Program program, Inlining.PlanEntry planEntry, int offset) {
      int usageCount = this.usageCounter.methodUsageCount.getOrDefault(planEntry.method, -1);
      if (usageCount > 0) {
         this.usageCounter.methodUsageCount.put(planEntry.method, usageCount - 1);
      }

      BasicBlock block = program.basicBlockAt(planEntry.targetBlock + offset);
      InvokeInstruction invoke = (InvokeInstruction)planEntry.targetInstruction;
      BasicBlock splitBlock = program.createBasicBlock();
      BasicBlock firstInlineBlock = program.createBasicBlock();
      Program inlineProgram = planEntry.program;

      for (int i = 1; i < inlineProgram.basicBlockCount(); i++) {
         program.createBasicBlock();
      }

      while (this.depthsByBlock.size() < program.basicBlockCount()) {
         this.depthsByBlock.add(planEntry.depth + 1);
      }

      int variableOffset = program.variableCount();

      for (int i = 0; i < inlineProgram.variableCount(); i++) {
         program.createVariable();
      }

      while (planEntry.targetInstruction.getNext() != null) {
         Instruction insn = planEntry.targetInstruction.getNext();
         insn.delete();
         splitBlock.add(insn);
      }

      splitBlock.getTryCatchBlocks().addAll(ProgramUtils.copyTryCatches(block, program));
      invoke.delete();
      JumpInstruction jumpToInlinedProgram = new JumpInstruction();
      jumpToInlinedProgram.setTarget(firstInlineBlock);
      block.add(jumpToInlinedProgram);
      InliningInfoMerger inliningInfoMerger = new InliningInfoMerger(planEntry.locationInfo);

      for (int i = 0; i < inlineProgram.basicBlockCount(); i++) {
         BasicBlock blockToInline = inlineProgram.basicBlockAt(i);
         BasicBlock inlineBlock = program.basicBlockAt(firstInlineBlock.getIndex() + i);

         while (blockToInline.getFirstInstruction() != null) {
            Instruction insn = blockToInline.getFirstInstruction();
            insn.delete();
            inlineBlock.add(insn);
            if (insn instanceof InvokeInstruction invokeInsn && invokeInsn.getType() == InvocationType.SPECIAL) {
               usageCount = this.usageCounter.methodUsageCount.getOrDefault(invokeInsn.getMethod(), -1);
               if (usageCount >= 0) {
                  this.usageCounter.methodUsageCount.put(invokeInsn.getMethod(), usageCount + 1);
               }
            }

            TextLocation location = insn.getLocation();
            if (location == null) {
               location = TextLocation.EMPTY;
            }

            location = new TextLocation(location.getFileName(), location.getLine(), inliningInfoMerger.merge(location.getInlining()));
            insn.setLocation(location);
         }

         List<Phi> phis = new ArrayList<>(blockToInline.getPhis());
         blockToInline.getPhis().clear();
         inlineBlock.getPhis().addAll(phis);
         List<TryCatchBlock> tryCatches = new ArrayList<>(blockToInline.getTryCatchBlocks());
         blockToInline.getTryCatchBlocks().clear();
         inlineBlock.getTryCatchBlocks().addAll(tryCatches);
         inlineBlock.setExceptionVariable(blockToInline.getExceptionVariable());
      }

      BasicBlockMapper blockMapper = new BasicBlockMapper(b -> program.basicBlockAt(b.getIndex() + firstInlineBlock.getIndex()));
      InstructionVariableMapper variableMapper = new InstructionVariableMapper(
         var -> {
            if (var.getIndex() == 0) {
               return invoke.getInstance();
            } else {
               return var.getIndex() <= invoke.getArguments().size()
                  ? invoke.getArguments().get(var.getIndex() - 1)
                  : program.variableAt(var.getIndex() + variableOffset);
            }
         }
      );
      List<Incoming> resultVariables = new ArrayList<>();

      for (int i = 0; i < inlineProgram.basicBlockCount(); i++) {
         BasicBlock mappedBlock = program.basicBlockAt(firstInlineBlock.getIndex() + i);
         blockMapper.transform(mappedBlock);
         variableMapper.apply(mappedBlock);
         mappedBlock.getTryCatchBlocks().addAll(ProgramUtils.copyTryCatches(block, program));
         if (mappedBlock.getLastInstruction() instanceof ExitInstruction exit) {
            JumpInstruction exitReplacement = new JumpInstruction();
            exitReplacement.setTarget(splitBlock);
            exitReplacement.setLocation(exit.getLocation());
            exit.replace(exitReplacement);
            if (exit.getValueToReturn() != null) {
               Incoming resultIncoming = new Incoming();
               resultIncoming.setSource(mappedBlock);
               resultIncoming.setValue(exit.getValueToReturn());
               resultVariables.add(resultIncoming);
            }
         }
      }

      if (!resultVariables.isEmpty() && invoke.getReceiver() != null) {
         if (resultVariables.size() == 1) {
            AssignInstruction resultAssignment = new AssignInstruction();
            resultAssignment.setReceiver(invoke.getReceiver());
            resultAssignment.setAssignee(resultVariables.get(0).getValue());
            splitBlock.addFirst(resultAssignment);
         } else {
            Phi resultPhi = new Phi();
            resultPhi.setReceiver(invoke.getReceiver());
            resultPhi.getIncomings().addAll(resultVariables);
            splitBlock.getPhis().add(resultPhi);
         }
      }

      TransitionExtractor transitionExtractor = new TransitionExtractor();
      Instruction splitLastInsn = splitBlock.getLastInstruction();
      if (splitLastInsn != null) {
         splitLastInsn.acceptVisitor(transitionExtractor);
         if (transitionExtractor.getTargets() != null) {
            for (Incoming incoming : Arrays.stream(transitionExtractor.getTargets())
               .flatMap(bb -> bb.getPhis().stream())
               .flatMap(phi -> phi.getIncomings().stream())
               .filter(incomingx -> incomingx.getSource() == block)
               .collect(Collectors.toList())) {
               incoming.setSource(splitBlock);
            }
         }
      }

      this.execPlan(program, planEntry.innerPlan, firstInlineBlock.getIndex());
   }

   private List<Inlining.PlanEntry> buildPlan(Program program, int depth, InliningStep step, MethodReference method, InliningInfo inliningInfo) {
      List<Inlining.PlanEntry> plan = new ArrayList<>();
      int originalDepth = depth;
      InliningFilter filter = this.filterFactory.createFilter(method);
      Inlining.ContextImpl context = new Inlining.ContextImpl();

      for (BasicBlock block : program.getBasicBlocks()) {
         if (block.getTryCatchBlocks().isEmpty()) {
            if (originalDepth < 0) {
               depth = this.depthsByBlock.get(block.getIndex());
            }

            for (Instruction insn : block) {
               if (!this.instructionsToSkip.contains(insn)
                  && insn instanceof InvokeInstruction invoke
                  && invoke.getType() != InvocationType.VIRTUAL
                  && invoke.getMethod().getClassName().equals("org.teavm.runtime.Fiber") == method.getClassName().equals("org.teavm.runtime.Fiber")
                  && filter.apply(invoke.getMethod())) {
                  MethodReader invokedMethod = this.getMethod(invoke.getMethod());
                  if (invokedMethod != null
                     && invokedMethod.getProgram() != null
                     && invokedMethod.getProgram().basicBlockCount() != 0
                     && !invokedMethod.hasModifier(ElementModifier.SYNCHRONIZED)) {
                     context.depth = depth;
                     InliningStep innerStep = step.tryInline(invokedMethod.getReference(), invokedMethod.getProgram(), context);
                     if (innerStep == null) {
                        this.instructionsToSkip.add(insn);
                     } else {
                        Program invokedProgram = ProgramUtils.copy(invokedMethod.getProgram());
                        TextLocation location = insn.getLocation();
                        InliningInfo innerInliningInfo = new InliningInfo(
                           invoke.getMethod(), location != null ? location.getFileName() : null, location != null ? location.getLine() : -1, inliningInfo
                        );
                        Inlining.PlanEntry entry = new Inlining.PlanEntry();
                        entry.targetBlock = block.getIndex();
                        entry.targetInstruction = insn;
                        entry.program = invokedProgram;
                        entry.innerPlan.addAll(this.buildPlan(invokedProgram, depth + 1, innerStep, invokedMethod.getReference(), innerInliningInfo));
                        entry.depth = depth;
                        entry.method = invokedMethod.getReference();
                        entry.locationInfo = innerInliningInfo;
                        plan.add(entry);
                     }
                  } else {
                     this.instructionsToSkip.add(insn);
                  }
               }
            }
         }
      }

      Collections.reverse(plan);
      return plan;
   }

   private MethodReader getMethod(MethodReference methodRef) {
      ClassReader cls = this.classes.get(methodRef.getClassName());
      return cls != null ? cls.getMethod(methodRef.getDescriptor()) : null;
   }

   private void devirtualize(Program program, MethodReference method, DependencyInfo dependencyInfo) {
      if (this.classInference == null) {
         this.classInference = new ClassInference(dependencyInfo, this.hierarchy, this.classes.getClassNames(), 30);
      }

      this.classInference.infer(program, method);

      for (BasicBlock block : program.getBasicBlocks()) {
         for (Instruction instruction : block) {
            if (instruction instanceof InvokeInstruction invoke && invoke.getType() == InvocationType.VIRTUAL) {
               Set<MethodReference> implementations = new HashSet<>();
               if (this.classInference.isOverflow(invoke.getInstance().getIndex())) {
                  List<? extends MethodReference> knownImplementations = this.classInference.getMethodImplementations(invoke.getMethod().getDescriptor());
                  if (knownImplementations != null) {
                     implementations.addAll(knownImplementations);
                  }
               } else {
                  for (ValueType type : this.classInference.typesOf(invoke.getInstance().getIndex())) {
                     if (type instanceof ValueType.Object) {
                        String className = ((ValueType.Object)type).getClassName();
                        MethodReference rawMethod = new MethodReference(className, invoke.getMethod().getDescriptor());
                        MethodReader resolvedMethod = dependencyInfo.getClassSource().resolveImplementation(rawMethod);
                        if (resolvedMethod != null) {
                           implementations.add(resolvedMethod.getReference());
                        }
                     }
                  }
               }

               if (implementations.size() == 1) {
                  MethodReference implementation = implementations.iterator().next();
                  MethodReader implementationMethod = this.getMethod(implementation);
                  if (implementationMethod != null && !implementationMethod.hasModifier(ElementModifier.ABSTRACT)) {
                     invoke.setType(InvocationType.SPECIAL);
                     invoke.setMethod(implementation);
                     if (!implementation.getName().equals(invoke.getMethod().getClassName())) {
                        CastInstruction cast = new CastInstruction();
                        cast.setWeak(true);
                        cast.setValue(invoke.getInstance());
                        cast.setReceiver(program.createVariable());
                        cast.setLocation(invoke.getLocation());
                        cast.setTargetType(ValueType.object(implementation.getClassName()));
                        invoke.insertPrevious(cast);
                        invoke.setInstance(cast.getReceiver());
                     }
                  }
               }
            }
         }
      }
   }

   class ContextImpl implements InliningContext {
      int depth;

      @Override
      public boolean isUsedOnce(MethodReference method) {
         return Inlining.this.methodsUsedOnce.contains(method);
      }

      @Override
      public ProgramReader getProgram(MethodReference method) {
         ClassReader cls = Inlining.this.classes.get(method.getClassName());
         if (cls == null) {
            return null;
         }

         MethodReader methodReader = cls.getMethod(method.getDescriptor());
         return methodReader != null ? methodReader.getProgram() : null;
      }

      @Override
      public int getDepth() {
         return this.depth;
      }
   }

   static class MethodUsageCounter extends AbstractInstructionReader {
      ObjectIntMap<MethodReference> methodUsageCount = new ObjectIntHashMap();
      Map<MethodReference, Set<MethodReference>> methodDependencies = new LinkedHashMap<>();
      Predicate<MethodReference> externalMethods;
      MethodReference currentMethod;

      MethodUsageCounter(Predicate<MethodReference> externalMethods) {
         this.externalMethods = externalMethods;
      }

      @Override
      public void invoke(
         VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments, InvocationType type
      ) {
         if (type == InvocationType.SPECIAL && !this.externalMethods.test(method)) {
            this.methodUsageCount.put(method, this.methodUsageCount.get(method) + 1);
            this.methodDependencies.computeIfAbsent(this.currentMethod, k -> new LinkedHashSet<>()).add(method);
         }
      }
   }

   static class PlanEntry {
      int targetBlock;
      Instruction targetInstruction;
      MethodReference method;
      Program program;
      int depth;
      final List<Inlining.PlanEntry> innerPlan = new ArrayList<>();
      InliningInfo locationInfo;
   }
}
