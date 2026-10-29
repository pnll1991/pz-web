package org.teavm.model.transformation;

import java.util.Arrays;
import org.teavm.hppc.IntArrayList;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;
import org.teavm.hppc.cursors.IntCursor;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BoundCheckInstruction;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.PutElementInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;
import org.teavm.model.util.DominatorWalker;
import org.teavm.model.util.DominatorWalkerCallback;
import org.teavm.model.util.PhiUpdater;

public class BoundCheckInsertion {
   public void transformProgram(Program program, MethodReference methodReference) {
      if (program.basicBlockCount() != 0) {
         BoundCheckInsertion.InsertionVisitor visitor = new BoundCheckInsertion.InsertionVisitor(program.variableCount());
         new DominatorWalker(program).walk(visitor);
         if (visitor.changed) {
            new PhiUpdater().updatePhis(program, methodReference.parameterCount() + 1);
         }
      }
   }

   static class BlockBounds {
      IntArrayList addedArrayBounds = new IntArrayList();
      IntArrayList nonNegatives = new IntArrayList();
      int conditionBlock;
      int comparisonValue;
      int comparisonVariable;
      BoundCheckInsertion.ComparisonMode comparisonMode;
   }

   enum ComparisonMode {
      LESS_THAN_ARRAY_LENGTH,
      NON_NEGATIVE;
   }

   static class InsertionVisitor extends AbstractInstructionVisitor implements DominatorWalkerCallback<BoundCheckInsertion.BlockBounds> {
      BoundCheckInsertion.BlockBounds bounds;
      boolean changed;
      private boolean[] isConstant;
      private boolean[] isConstantSizedArray;
      private int[] constantValue;
      private IntSet[] upperArrayLengths;
      private boolean[] nonNegative;
      private int[] map;
      private int[] arrayLengthVars;
      private int[] arrayLengthReverseVars;
      private int[] comparisonLeft;
      private int[] comparisonRight;
      private int conditionBlock;
      private int comparisonValue;
      private int comparisonVariable;
      private BoundCheckInsertion.ComparisonMode comparisonMode;

      InsertionVisitor(int variableCount) {
         this.isConstant = new boolean[variableCount];
         this.isConstantSizedArray = new boolean[variableCount];
         this.constantValue = new int[variableCount];
         this.upperArrayLengths = new IntSet[variableCount];
         this.nonNegative = new boolean[variableCount];
         this.map = new int[variableCount];
         int i = 0;

         while (i < variableCount) {
            this.map[i] = i++;
         }

         this.arrayLengthVars = new int[variableCount];
         Arrays.fill(this.arrayLengthVars, -1);
         this.arrayLengthReverseVars = new int[variableCount];
         this.comparisonLeft = new int[variableCount];
         Arrays.fill(this.comparisonLeft, -1);
         this.comparisonRight = new int[variableCount];
      }

      public BoundCheckInsertion.BlockBounds visit(BasicBlock block) {
         this.bounds = new BoundCheckInsertion.BlockBounds();
         if (this.comparisonMode != null && this.conditionBlock == block.getIndex()) {
            switch (this.comparisonMode) {
               case LESS_THAN_ARRAY_LENGTH:
                  this.addArrayBound(this.comparisonVariable, this.comparisonValue);
                  break;
               case NON_NEGATIVE:
                  this.markAsNonNegative(this.comparisonVariable);
            }
         }

         for (Instruction instruction : block) {
            instruction.acceptVisitor(this);
         }

         this.bounds.comparisonMode = this.comparisonMode;
         this.bounds.comparisonValue = this.comparisonValue;
         this.bounds.comparisonVariable = this.comparisonVariable;
         this.bounds.conditionBlock = this.conditionBlock;
         return this.bounds;
      }

      public void endVisit(BasicBlock block, BoundCheckInsertion.BlockBounds state) {
         IntArrayList addedArrayBounds = state.addedArrayBounds;
         int size = addedArrayBounds.size();

         for (int i = 0; i < size; i += 2) {
            int index = addedArrayBounds.get(i);
            int array = addedArrayBounds.get(i + 1);
            this.upperArrayLengths[index].removeAll(array);
         }

         for (IntCursor cursor : state.nonNegatives) {
            this.nonNegative[cursor.value] = false;
         }

         this.comparisonMode = state.comparisonMode;
         this.comparisonValue = state.comparisonValue;
         this.comparisonVariable = state.comparisonVariable;
         this.conditionBlock = state.conditionBlock;
      }

      @Override
      public void visit(JumpInstruction insn) {
         this.prepareJump();
      }

      @Override
      public void visit(BranchingInstruction insn) {
         this.prepareJump();
         int operand = this.index(insn.getOperand());
         int left = this.comparisonLeft[operand];
         int right = this.comparisonRight[operand];
         if (left >= 0) {
            switch (insn.getCondition()) {
               case LESS:
                  if (this.arrayLengthVars[right] >= 0) {
                     this.comparisonMode = BoundCheckInsertion.ComparisonMode.LESS_THAN_ARRAY_LENGTH;
                     this.comparisonValue = this.arrayLengthVars[right];
                     this.comparisonVariable = left;
                     this.conditionBlock = insn.getConsequent().getIndex();
                  } else if (this.isConstant[left] && this.constantValue[left] >= -1) {
                     this.comparisonMode = BoundCheckInsertion.ComparisonMode.NON_NEGATIVE;
                     this.comparisonVariable = right;
                     this.conditionBlock = insn.getConsequent().getIndex();
                  } else if (this.isConstant[right] && this.constantValue[right] >= 0) {
                     this.comparisonMode = BoundCheckInsertion.ComparisonMode.NON_NEGATIVE;
                     this.comparisonVariable = left;
                     this.conditionBlock = insn.getAlternative().getIndex();
                  }
                  break;
               case GREATER_OR_EQUAL:
                  if (this.arrayLengthVars[right] >= 0) {
                     this.comparisonMode = BoundCheckInsertion.ComparisonMode.LESS_THAN_ARRAY_LENGTH;
                     this.comparisonValue = this.arrayLengthVars[right];
                     this.comparisonVariable = left;
                     this.conditionBlock = insn.getAlternative().getIndex();
                  } else if (this.isConstant[left] && this.constantValue[left] >= -1) {
                     this.comparisonMode = BoundCheckInsertion.ComparisonMode.NON_NEGATIVE;
                     this.comparisonVariable = right;
                     this.conditionBlock = insn.getAlternative().getIndex();
                  } else if (this.isConstant[right] && this.constantValue[right] >= 0) {
                     this.comparisonMode = BoundCheckInsertion.ComparisonMode.NON_NEGATIVE;
                     this.comparisonVariable = left;
                     this.conditionBlock = insn.getConsequent().getIndex();
                  }
                  break;
               case GREATER:
                  if (this.arrayLengthVars[left] >= 0) {
                     this.comparisonMode = BoundCheckInsertion.ComparisonMode.LESS_THAN_ARRAY_LENGTH;
                     this.comparisonValue = this.arrayLengthVars[left];
                     this.comparisonVariable = right;
                     this.conditionBlock = insn.getConsequent().getIndex();
                  } else if (this.isConstant[left] && this.constantValue[left] >= 0) {
                     this.comparisonMode = BoundCheckInsertion.ComparisonMode.NON_NEGATIVE;
                     this.comparisonVariable = right;
                     this.conditionBlock = insn.getAlternative().getIndex();
                  } else if (this.isConstant[right] && this.constantValue[right] >= -1) {
                     this.comparisonMode = BoundCheckInsertion.ComparisonMode.NON_NEGATIVE;
                     this.comparisonVariable = left;
                     this.conditionBlock = insn.getConsequent().getIndex();
                  }
                  break;
               case LESS_OR_EQUAL:
                  if (this.arrayLengthVars[left] >= 0) {
                     this.comparisonMode = BoundCheckInsertion.ComparisonMode.LESS_THAN_ARRAY_LENGTH;
                     this.comparisonValue = this.arrayLengthVars[left];
                     this.comparisonVariable = right;
                     this.conditionBlock = insn.getAlternative().getIndex();
                  } else if (this.isConstant[left] && this.constantValue[left] >= 0) {
                     this.comparisonMode = BoundCheckInsertion.ComparisonMode.NON_NEGATIVE;
                     this.comparisonVariable = right;
                     this.conditionBlock = insn.getConsequent().getIndex();
                  } else if (this.isConstant[right] && this.constantValue[right] >= -1) {
                     this.comparisonMode = BoundCheckInsertion.ComparisonMode.NON_NEGATIVE;
                     this.comparisonVariable = left;
                     this.conditionBlock = insn.getAlternative().getIndex();
                  }
            }
         }
      }

      @Override
      public void visit(BinaryBranchingInstruction insn) {
         this.prepareJump();
      }

      private void prepareJump() {
         this.conditionBlock = -1;
         this.comparisonMode = null;
         this.comparisonValue = 0;
      }

      @Override
      public void visit(IntegerConstantInstruction insn) {
         int receiver = this.index(insn.getReceiver());
         this.isConstant[receiver] = true;
         this.constantValue[receiver] = insn.getConstant();
      }

      @Override
      public void visit(BinaryInstruction insn) {
         int first = this.index(insn.getFirstOperand());
         int second = this.index(insn.getSecondOperand());
         int receiver = this.index(insn.getReceiver());
         if (this.isConstant[first] && this.isConstant[second]) {
            int a = this.constantValue[first];
            int b = this.constantValue[second];
            int r;
            switch (insn.getOperation()) {
               case ADD:
                  r = a + b;
                  break;
               case SUBTRACT:
                  r = a - b;
                  break;
               case COMPARE_LESS:
               case COMPARE_GREATER:
                  r = Integer.compare(a, b);
                  break;
               case DIVIDE:
                  r = a / b;
                  break;
               case MODULO:
                  r = a % b;
                  break;
               case MULTIPLY:
                  r = a * b;
                  break;
               case AND:
                  r = a & b;
                  break;
               case OR:
                  r = a | b;
                  break;
               case XOR:
                  r = a ^ b;
                  break;
               case SHIFT_LEFT:
                  r = a << b;
                  break;
               case SHIFT_RIGHT:
                  r = a >> b;
                  break;
               case SHIFT_RIGHT_UNSIGNED:
                  r = a >>> b;
                  break;
               default:
                  return;
            }

            this.isConstant[receiver] = true;
            this.constantValue[receiver] = r;
         } else if ((insn.getOperation() == BinaryOperation.COMPARE_LESS || insn.getOperation() == BinaryOperation.COMPARE_GREATER)
            && insn.getOperandType() == NumericOperandType.INT) {
            this.comparisonLeft[receiver] = first;
            this.comparisonRight[receiver] = second;
         }
      }

      @Override
      public void visit(ConstructArrayInstruction insn) {
         int size = this.index(insn.getSize());
         int receiver = this.index(insn.getReceiver());
         if (this.isConstant[size]) {
            this.isConstantSizedArray[receiver] = true;
            this.constantValue[receiver] = this.constantValue[size];
         }

         this.arrayLengthVars[size] = receiver;
         this.arrayLengthReverseVars[receiver] = size;
      }

      @Override
      public void visit(ArrayLengthInstruction insn) {
         int array = this.index(insn.getArray());
         int receiver = this.index(insn.getReceiver());
         if (this.arrayLengthVars[receiver] >= 0) {
            this.map[receiver] = this.arrayLengthReverseVars[receiver];
         } else {
            if (this.isConstantSizedArray[array]) {
               this.isConstant[receiver] = true;
               this.constantValue[receiver] = this.constantValue[array];
            }

            this.arrayLengthVars[receiver] = array;
            this.arrayLengthReverseVars[array] = receiver;
         }
      }

      @Override
      public void visit(AssignInstruction insn) {
         this.assign(insn.getAssignee(), insn.getReceiver());
      }

      @Override
      public void visit(UnwrapArrayInstruction insn) {
         this.assign(insn.getArray(), insn.getReceiver());
      }

      @Override
      public void visit(NullCheckInstruction insn) {
         this.isConstantSizedArray[this.index(insn.getReceiver())] = this.isConstantSizedArray[this.index(insn.getValue())];
      }

      private void assign(Variable from, Variable to) {
         this.map[to.getIndex()] = this.map[from.getIndex()];
      }

      @Override
      public void visit(GetElementInstruction insn) {
         this.tryInsertBoundCheck(insn.getIndex(), insn.getArray(), insn);
      }

      @Override
      public void visit(PutElementInstruction insn) {
         this.tryInsertBoundCheck(insn.getIndex(), insn.getArray(), insn);
      }

      private void tryInsertBoundCheck(Variable indexVar, Variable arrayVar, Instruction instruction) {
         boolean lower = true;
         boolean upper = true;
         int index = this.index(indexVar);
         int array = this.index(arrayVar);
         if (this.isConstant[index] && this.isConstantSizedArray[array]) {
            upper = false;
         }

         if (upper) {
            IntSet bounds = this.upperArrayLengths[index];
            if (bounds != null && bounds.contains(array)) {
               upper = false;
            }
         }

         if (this.isConstant[index] && this.constantValue[index] >= 0 || this.nonNegative[index]) {
            lower = false;
         }

         if (upper) {
            this.addArrayBound(index, array);
         }

         this.markAsNonNegative(index);
         if (lower || upper) {
            BoundCheckInstruction boundCheck = new BoundCheckInstruction();
            if (lower) {
               boundCheck.setLower(true);
            }

            if (upper) {
               boundCheck.setArray(arrayVar);
            }

            boundCheck.setIndex(indexVar);
            boundCheck.setReceiver(indexVar);
            boundCheck.setLocation(instruction.getLocation());
            instruction.insertPrevious(boundCheck);
            this.changed = true;
         }
      }

      private void addArrayBound(int index, int array) {
         IntSet upperSet = this.upperArrayLengths[index];
         if (upperSet == null) {
            upperSet = new IntHashSet();
            this.upperArrayLengths[index] = upperSet;
         }

         if (upperSet.add(array)) {
            this.bounds.addedArrayBounds.add(index, array);
         }
      }

      private void markAsNonNegative(int index) {
         if (!this.nonNegative[index]) {
            this.nonNegative[index] = true;
            this.bounds.nonNegatives.add(index);
         }
      }

      private int index(Variable var) {
         return this.map[var.getIndex()];
      }
   }
}
