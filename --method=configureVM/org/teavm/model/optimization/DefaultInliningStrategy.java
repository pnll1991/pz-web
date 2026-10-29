package org.teavm.model.optimization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ProgramReader;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.SwitchTableEntryReader;

public class DefaultInliningStrategy implements InliningStrategy {
   private final int complexityThreshold;
   private final int depthThreshold;
   private final int totalComplexityThreshold;
   private final boolean onceUsedOnly;
   private int getComplexityDepth;
   private Map<MethodReference, DefaultInliningStrategy.Complexity> complexityCache = new HashMap<>();

   public DefaultInliningStrategy(int complexityThreshold, int depthThreshold, int totalComplexityThreshold, boolean onceUsedOnly) {
      this.complexityThreshold = complexityThreshold;
      this.depthThreshold = depthThreshold;
      this.totalComplexityThreshold = totalComplexityThreshold;
      this.onceUsedOnly = onceUsedOnly;
   }

   @Override
   public InliningStep start(MethodReference method, ProgramReader program) {
      DefaultInliningStrategy.Complexity complexity = this.getComplexity(program, null);
      if (complexity.score > this.totalComplexityThreshold) {
         return null;
      }

      DefaultInliningStrategy.ComplexityHolder complexityHolder = new DefaultInliningStrategy.ComplexityHolder();
      complexityHolder.complexity = complexity.score;
      return new DefaultInliningStrategy.InliningStepImpl(complexityHolder);
   }

   @Override
   public void methodChanged(MethodReference method) {
      this.complexityCache.remove(method);
   }

   private DefaultInliningStrategy.Complexity getComplexity(MethodReference methodRef, InliningContext context) {
      DefaultInliningStrategy.Complexity result = this.complexityCache.get(methodRef);
      if (result == null) {
         result = this.getComplexity(context.getProgram(methodRef), context);
         this.complexityCache.put(methodRef, result);
      }

      return result;
   }

   private DefaultInliningStrategy.Complexity getComplexity(ProgramReader program, InliningContext context) {
      int complexity = 0;
      DefaultInliningStrategy.ComplexityCounter counter = new DefaultInliningStrategy.ComplexityCounter(context);

      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlockReader block = program.basicBlockAt(i);
         counter.complexity = 0;
         block.readAllInstructions(counter);
         complexity += block.instructionCount() + counter.complexity;
      }

      DefaultInliningStrategy.Complexity result = new DefaultInliningStrategy.Complexity();
      result.score = complexity;
      result.callsToUsedOnceMethods = counter.callsToUsedOnceMethods;
      return result;
   }

   static class Complexity {
      int score;
      boolean callsToUsedOnceMethods;
   }

   class ComplexityCounter extends AbstractInstructionReader {
      InliningContext context;
      int complexity;
      boolean callsToUsedOnceMethods;

      ComplexityCounter(InliningContext context) {
         this.context = context;
      }

      @Override
      public void nop() {
         this.complexity--;
      }

      @Override
      public void assign(VariableReader receiver, VariableReader assignee) {
         this.complexity--;
      }

      @Override
      public void invoke(
         VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments, InvocationType type
      ) {
         if (type == InvocationType.SPECIAL && this.context != null && this.context.isUsedOnce(method) && !this.isTrivialCall(method)) {
            this.callsToUsedOnceMethods = true;
         }
      }

      private boolean isTrivialCall(MethodReference methodRef) {
         if (this.context.getProgram(methodRef) != null && DefaultInliningStrategy.this.getComplexityDepth <= 10) {
            DefaultInliningStrategy.this.getComplexityDepth++;
            DefaultInliningStrategy.Complexity complexity = DefaultInliningStrategy.this.getComplexity(methodRef, this.context);
            DefaultInliningStrategy.this.getComplexityDepth--;
            return complexity.score <= 1 && !complexity.callsToUsedOnceMethods;
         } else {
            return false;
         }
      }

      @Override
      public void choose(VariableReader condition, List<? extends SwitchTableEntryReader> table, BasicBlockReader defaultTarget) {
         this.complexity += 2;
      }

      @Override
      public void jumpIf(BranchingCondition cond, VariableReader operand, BasicBlockReader consequent, BasicBlockReader alternative) {
         this.complexity++;
      }

      @Override
      public void jumpIf(BinaryBranchingCondition cond, VariableReader first, VariableReader second, BasicBlockReader consequent, BasicBlockReader alternative) {
         this.complexity++;
      }

      @Override
      public void jump(BasicBlockReader target) {
         this.complexity--;
      }

      @Override
      public void exit(VariableReader valueToReturn) {
         this.complexity--;
      }

      @Override
      public void raise(VariableReader exception) {
         this.complexity--;
      }
   }

   static class ComplexityHolder {
      int complexity;
   }

   class InliningStepImpl implements InliningStep {
      DefaultInliningStrategy.ComplexityHolder complexityHolder;

      InliningStepImpl(DefaultInliningStrategy.ComplexityHolder complexityHolder) {
         this.complexityHolder = complexityHolder;
      }

      @Override
      public InliningStep tryInline(MethodReference method, ProgramReader program, InliningContext context) {
         if (context.getDepth() > DefaultInliningStrategy.this.depthThreshold) {
            return null;
         }

         DefaultInliningStrategy.Complexity complexity = DefaultInliningStrategy.this.getComplexity(method, context);
         if (!DefaultInliningStrategy.this.onceUsedOnly || context.isUsedOnce(method) || !complexity.callsToUsedOnceMethods && complexity.score <= 1) {
            if (complexity.score <= DefaultInliningStrategy.this.complexityThreshold
               && this.complexityHolder.complexity + complexity.score <= DefaultInliningStrategy.this.totalComplexityThreshold) {
               this.complexityHolder.complexity = this.complexityHolder.complexity + complexity.score;
               return DefaultInliningStrategy.this.new InliningStepImpl(this.complexityHolder);
            } else {
               return null;
            }
         } else {
            return null;
         }
      }
   }
}
