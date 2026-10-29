package org.teavm.model.optimization;

import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.analysis.NullnessInformation;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.util.TransitionExtractor;

public class ConstantConditionElimination implements MethodOptimization {
   private int[] constants;
   private boolean[] constantDefined;
   private NullnessInformation nullness;

   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      return this.optimize(context.getMethod().getDescriptor(), program);
   }

   public boolean optimize(MethodDescriptor descriptor, Program program) {
      this.constants = new int[program.variableCount()];
      this.constantDefined = new boolean[program.variableCount()];
      this.nullness = NullnessInformation.build(program, descriptor);

      for (int i = 0; i < program.basicBlockCount(); i++) {
         for (Instruction insn : program.basicBlockAt(i)) {
            if (insn instanceof IntegerConstantInstruction constInsn) {
               int receiver = constInsn.getReceiver().getIndex();
               this.constants[receiver] = constInsn.getConstant();
               this.constantDefined[receiver] = true;
            }
         }
      }

      boolean changed = false;
      TransitionExtractor transitionExtractor = new TransitionExtractor();

      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlock block = program.basicBlockAt(i);
         Instruction insn = block.getLastInstruction();
         BasicBlock target = this.constantTarget(insn);
         if (target != null) {
            block.getLastInstruction().acceptVisitor(transitionExtractor);

            for (BasicBlock successor : transitionExtractor.getTargets()) {
               if (successor != target) {
                  for (Phi phi : successor.getPhis()) {
                     for (int j = 0; j < phi.getIncomings().size(); j++) {
                        if (phi.getIncomings().get(j).getSource() == block) {
                           phi.getIncomings().remove(j--);
                        }
                     }
                  }
               }
            }

            JumpInstruction jump = new JumpInstruction();
            jump.setTarget(target);
            jump.setLocation(insn.getLocation());
            block.getLastInstruction().replace(jump);
            changed = true;
         }
      }

      this.nullness.dispose();
      this.nullness = null;
      this.constantDefined = null;
      this.constants = null;
      if (changed) {
         new UnreachableBasicBlockEliminator().optimize(program);
      }

      return changed;
   }

   private BasicBlock constantTarget(Instruction instruction) {
      if (instruction instanceof BranchingInstruction branching) {
         switch (branching.getCondition()) {
            case NULL:
               if (this.nullness.isNull(branching.getOperand())) {
                  return branching.getConsequent();
               }

               if (this.nullness.isNotNull(branching.getOperand())) {
                  return branching.getAlternative();
               }
               break;
            case NOT_NULL:
               if (this.nullness.isNull(branching.getOperand())) {
                  return branching.getAlternative();
               }

               if (this.nullness.isNotNull(branching.getOperand())) {
                  return branching.getConsequent();
               }
               break;
            default:
               int operand = branching.getOperand().getIndex();
               if (this.constantDefined[operand]) {
                  return this.checkCondition(branching.getCondition(), this.constants[operand]) ? branching.getConsequent() : branching.getAlternative();
               }
         }
      } else if (instruction instanceof BinaryBranchingInstruction branching) {
         int first = branching.getFirstOperand().getIndex();
         int second = branching.getSecondOperand().getIndex();
         switch (branching.getCondition()) {
            case EQUAL:
            case NOT_EQUAL:
               if (this.constantDefined[first] && this.constantDefined[second]) {
                  boolean result = this.constants[first] == this.constants[second];
                  if (branching.getCondition() == BinaryBranchingCondition.NOT_EQUAL) {
                     result = !result;
                  }

                  return result ? branching.getConsequent() : branching.getAlternative();
               }
         }
      }

      return null;
   }

   private boolean checkCondition(BranchingCondition condition, int constant) {
      switch (condition) {
         case NULL:
         case NOT_NULL:
         default:
            return false;
         case EQUAL:
            return constant == 0;
         case NOT_EQUAL:
            return constant != 0;
         case GREATER:
            return constant > 0;
         case GREATER_OR_EQUAL:
            return constant >= 0;
         case LESS:
            return constant < 0;
         case LESS_OR_EQUAL:
            return constant <= 0;
      }
   }
}
