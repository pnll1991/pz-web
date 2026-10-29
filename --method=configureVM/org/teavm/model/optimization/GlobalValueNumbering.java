package org.teavm.model.optimization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.teavm.common.DominatorTree;
import org.teavm.common.Graph;
import org.teavm.common.GraphUtils;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.InvokeDynamicInstruction;
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
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.CastIntegerInstruction;
import org.teavm.model.instructions.CastNumberInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InstructionVisitor;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NegateInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.PutElementInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.SwitchInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;
import org.teavm.model.util.ProgramUtils;

public class GlobalValueNumbering implements MethodOptimization {
   private Map<String, GlobalValueNumbering.KnownValue> knownValues = new HashMap<>();
   private boolean eliminate;
   private int[] map;
   private int[] replaceMap;
   private boolean[] deletedVars;
   private ObjectIntMap<Number> constantIndexes = new ObjectIntHashMap();
   private Number[] numericConstants;
   private Number evaluatedConstant;
   private int receiver;
   private Program program;
   private int currentBlockIndex;
   private DominatorTree domTree;
   private boolean namesPreserved;
   private InstructionVisitor constantPreprocessor = new AbstractInstructionVisitor() {
      private void setConstant(int index, Number value) {
         GlobalValueNumbering.this.numericConstants[index] = value;
         int knownIndex = GlobalValueNumbering.this.constantIndexes.getOrDefault(value, -1);
         if (knownIndex < 0) {
            GlobalValueNumbering.this.constantIndexes.put(value, index);
         } else {
            GlobalValueNumbering.this.map[index] = knownIndex;
         }
      }

      @Override
      public void visit(IntegerConstantInstruction insn) {
         this.setConstant(insn.getReceiver().getIndex(), insn.getConstant());
      }

      @Override
      public void visit(LongConstantInstruction insn) {
         this.setConstant(insn.getReceiver().getIndex(), insn.getConstant());
      }

      @Override
      public void visit(FloatConstantInstruction insn) {
         this.setConstant(insn.getReceiver().getIndex(), insn.getConstant());
      }

      @Override
      public void visit(DoubleConstantInstruction insn) {
         this.setConstant(insn.getReceiver().getIndex(), insn.getConstant());
      }
   };
   private InstructionVisitor optimizer = new AbstractInstructionVisitor() {
      private UnaryOperator<Variable> mapper = var -> GlobalValueNumbering.this.program.variableAt(GlobalValueNumbering.this.replaceMap[var.getIndex()]);

      @Override
      public void visit(BinaryInstruction insn) {
         int a = GlobalValueNumbering.this.map[insn.getFirstOperand().getIndex()];
         int p = GlobalValueNumbering.this.replaceMap[insn.getFirstOperand().getIndex()];
         int b = GlobalValueNumbering.this.map[insn.getSecondOperand().getIndex()];
         int q = GlobalValueNumbering.this.replaceMap[insn.getSecondOperand().getIndex()];
         insn.setFirstOperand(GlobalValueNumbering.this.program.variableAt(p));
         insn.setSecondOperand(GlobalValueNumbering.this.program.variableAt(q));
         boolean commutative = false;
         String value;
         switch (insn.getOperation()) {
            case ADD:
               value = "+";
               commutative = true;
               break;
            case SUBTRACT:
               value = "-";
               break;
            case MULTIPLY:
               value = "*";
               commutative = true;
               break;
            case DIVIDE:
               value = "/";
               break;
            case MODULO:
               value = "%";
               break;
            case COMPARE_GREATER:
               value = "$>";
               break;
            case COMPARE_LESS:
               value = "$<";
               break;
            case AND:
               value = "&";
               commutative = true;
               break;
            case OR:
               value = "|";
               commutative = true;
               break;
            case XOR:
               value = "^";
               commutative = true;
               break;
            case SHIFT_LEFT:
               value = "<<";
               break;
            case SHIFT_RIGHT:
               value = ">>";
               break;
            case SHIFT_RIGHT_UNSIGNED:
               value = ">>>";
               break;
            default:
               return;
         }

         GlobalValueNumbering.this.bind(insn.getReceiver().getIndex(), "@" + a + value + "@" + b);
         if (commutative) {
            GlobalValueNumbering.this.bind(insn.getReceiver().getIndex(), "@" + b + value + "@" + a);
         }

         this.evaluateBinary(insn.getOperation(), insn.getOperandType(), a, b);
         if (GlobalValueNumbering.this.evaluatedConstant != null) {
            GlobalValueNumbering.this.numericConstants[insn.getReceiver().getIndex()] = GlobalValueNumbering.this.evaluatedConstant;
         }

         GlobalValueNumbering.this.receiver = insn.getReceiver().getIndex();
      }

      private void evaluateBinary(BinaryOperation operation, NumericOperandType type, int a, int b) {
         Number first = GlobalValueNumbering.this.numericConstants[a];
         Number second = GlobalValueNumbering.this.numericConstants[b];
         if (first != null && second != null) {
            switch (type) {
               case INT:
                  int p = first.intValue();
                  int q = second.intValue();
                  switch (operation) {
                     case ADD:
                        GlobalValueNumbering.this.evaluatedConstant = p + q;
                        return;
                     case SUBTRACT:
                        GlobalValueNumbering.this.evaluatedConstant = p - q;
                        return;
                     case MULTIPLY:
                        GlobalValueNumbering.this.evaluatedConstant = p * q;
                        return;
                     case DIVIDE:
                        if (q != 0) {
                           GlobalValueNumbering.this.evaluatedConstant = p / q;
                        }

                        return;
                     case MODULO:
                        if (q != 0) {
                           GlobalValueNumbering.this.evaluatedConstant = p % q;
                        }

                        return;
                     case COMPARE_GREATER:
                     case COMPARE_LESS:
                        GlobalValueNumbering.this.evaluatedConstant = Integer.compare(p, q);
                        return;
                     case AND:
                        GlobalValueNumbering.this.evaluatedConstant = p & q;
                        return;
                     case OR:
                        GlobalValueNumbering.this.evaluatedConstant = p | q;
                        return;
                     case XOR:
                        GlobalValueNumbering.this.evaluatedConstant = p ^ q;
                        return;
                     case SHIFT_LEFT:
                        GlobalValueNumbering.this.evaluatedConstant = p << q;
                        return;
                     case SHIFT_RIGHT:
                        GlobalValueNumbering.this.evaluatedConstant = p >> q;
                        return;
                     case SHIFT_RIGHT_UNSIGNED:
                        GlobalValueNumbering.this.evaluatedConstant = p >>> q;
                        return;
                     default:
                        return;
                  }
               case LONG:
                  long p = first.longValue();
                  long q = second.longValue();
                  switch (operation) {
                     case ADD:
                        GlobalValueNumbering.this.evaluatedConstant = p + q;
                        return;
                     case SUBTRACT:
                        GlobalValueNumbering.this.evaluatedConstant = p - q;
                        return;
                     case MULTIPLY:
                        GlobalValueNumbering.this.evaluatedConstant = p * q;
                        return;
                     case DIVIDE:
                        if (q != 0L) {
                           GlobalValueNumbering.this.evaluatedConstant = p / q;
                        }

                        return;
                     case MODULO:
                        if (q != 0L) {
                           GlobalValueNumbering.this.evaluatedConstant = p % q;
                        }

                        return;
                     case COMPARE_GREATER:
                     case COMPARE_LESS:
                        GlobalValueNumbering.this.evaluatedConstant = Long.compare(p, q);
                        return;
                     case AND:
                        GlobalValueNumbering.this.evaluatedConstant = p & q;
                        return;
                     case OR:
                        GlobalValueNumbering.this.evaluatedConstant = p | q;
                        return;
                     case XOR:
                        GlobalValueNumbering.this.evaluatedConstant = p ^ q;
                        return;
                     case SHIFT_LEFT:
                        GlobalValueNumbering.this.evaluatedConstant = p << (int)q;
                        return;
                     case SHIFT_RIGHT:
                        GlobalValueNumbering.this.evaluatedConstant = p >> (int)q;
                        return;
                     case SHIFT_RIGHT_UNSIGNED:
                        GlobalValueNumbering.this.evaluatedConstant = p >>> (int)q;
                        return;
                     default:
                        return;
                  }
               case FLOAT:
                  float p = first.floatValue();
                  float q = second.floatValue();
                  switch (operation) {
                     case ADD:
                        GlobalValueNumbering.this.evaluatedConstant = p + q;
                        return;
                     case SUBTRACT:
                        GlobalValueNumbering.this.evaluatedConstant = p - q;
                        return;
                     case MULTIPLY:
                        GlobalValueNumbering.this.evaluatedConstant = p * q;
                        return;
                     case DIVIDE:
                        if (q != 0.0F) {
                           GlobalValueNumbering.this.evaluatedConstant = p / q;
                        }

                        return;
                     case MODULO:
                        if (q != 0.0F) {
                           GlobalValueNumbering.this.evaluatedConstant = p % q;
                        }

                        return;
                     case COMPARE_GREATER:
                        GlobalValueNumbering.this.evaluatedConstant = p > q ? 1 : (p < q ? -1 : (p == q ? 0 : 1));
                        return;
                     case COMPARE_LESS:
                        GlobalValueNumbering.this.evaluatedConstant = p > q ? 1 : (p < q ? -1 : (p == q ? 0 : -1));
                        return;
                     case AND:
                     case OR:
                     case XOR:
                     case SHIFT_LEFT:
                     case SHIFT_RIGHT:
                     case SHIFT_RIGHT_UNSIGNED:
                     default:
                        return;
                  }
               case DOUBLE:
                  double p = first.doubleValue();
                  double q = second.doubleValue();
                  switch (operation) {
                     case ADD:
                        GlobalValueNumbering.this.evaluatedConstant = p + q;
                        break;
                     case SUBTRACT:
                        GlobalValueNumbering.this.evaluatedConstant = p - q;
                        break;
                     case MULTIPLY:
                        GlobalValueNumbering.this.evaluatedConstant = p * q;
                        break;
                     case DIVIDE:
                        if (q != 0.0) {
                           GlobalValueNumbering.this.evaluatedConstant = p / q;
                        }
                        break;
                     case MODULO:
                        if (q != 0.0) {
                           GlobalValueNumbering.this.evaluatedConstant = p % q;
                        }
                        break;
                     case COMPARE_GREATER:
                        GlobalValueNumbering.this.evaluatedConstant = p > q ? 1 : (p < q ? -1 : (p == q ? 0 : 1));
                        break;
                     case COMPARE_LESS:
                        GlobalValueNumbering.this.evaluatedConstant = p > q ? 1 : (p < q ? -1 : (p == q ? 0 : -1));
                     case AND:
                     case OR:
                     case XOR:
                     case SHIFT_LEFT:
                     case SHIFT_RIGHT:
                     case SHIFT_RIGHT_UNSIGNED:
                  }
            }
         }
      }

      @Override
      public void visit(NegateInstruction insn) {
         int a = GlobalValueNumbering.this.map[insn.getOperand().getIndex()];
         int p = GlobalValueNumbering.this.replaceMap[insn.getOperand().getIndex()];
         insn.setOperand(GlobalValueNumbering.this.program.variableAt(p));
         GlobalValueNumbering.this.bind(insn.getReceiver().getIndex(), "-@" + a);
         Number value = GlobalValueNumbering.this.numericConstants[a];
         if (value != null) {
            switch (insn.getOperandType()) {
               case INT:
                  GlobalValueNumbering.this.evaluatedConstant = -value.intValue();
                  break;
               case LONG:
                  GlobalValueNumbering.this.evaluatedConstant = -value.longValue();
                  break;
               case FLOAT:
                  GlobalValueNumbering.this.evaluatedConstant = -value.floatValue();
                  break;
               case DOUBLE:
                  GlobalValueNumbering.this.evaluatedConstant = -value.doubleValue();
            }
         }

         GlobalValueNumbering.this.receiver = insn.getReceiver().getIndex();
      }

      @Override
      public void visit(AssignInstruction insn) {
         if (GlobalValueNumbering.this.namesPreserved
            && insn.getReceiver().getDebugName() != null
            && insn.getAssignee().getDebugName() != null
            && !insn.getAssignee().getDebugName().equals(insn.getReceiver().getDebugName())) {
            insn.setAssignee(GlobalValueNumbering.this.program.variableAt(GlobalValueNumbering.this.replaceMap[insn.getAssignee().getIndex()]));
         } else {
            if (insn.getReceiver().getDebugName() != null) {
               insn.getAssignee().setDebugName(insn.getReceiver().getDebugName());
            }

            if (insn.getReceiver().getLabel() != null) {
               insn.getAssignee().setLabel(insn.getReceiver().getLabel());
            }

            GlobalValueNumbering.this.map[insn.getReceiver().getIndex()] = GlobalValueNumbering.this.map[insn.getAssignee().getIndex()];
            GlobalValueNumbering.this.replaceMap[insn.getReceiver().getIndex()] = GlobalValueNumbering.this.replaceMap[insn.getAssignee().getIndex()];
            GlobalValueNumbering.this.eliminate = true;
         }
      }

      @Override
      public void visit(CastInstruction insn) {
         int a = GlobalValueNumbering.this.map[insn.getValue().getIndex()];
         int p = GlobalValueNumbering.this.replaceMap[insn.getValue().getIndex()];
         insn.setValue(GlobalValueNumbering.this.program.variableAt(p));
         String operator = insn.isWeak() ? ":::" : "::";
         GlobalValueNumbering.this.bind(insn.getReceiver().getIndex(), "@" + a + operator + insn.getTargetType());
      }

      @Override
      public void visit(CastNumberInstruction insn) {
         int a = GlobalValueNumbering.this.map[insn.getValue().getIndex()];
         int p = GlobalValueNumbering.this.replaceMap[insn.getValue().getIndex()];
         insn.setValue(GlobalValueNumbering.this.program.variableAt(p));
         GlobalValueNumbering.this.bind(insn.getReceiver().getIndex(), "@" + a + "::" + insn.getTargetType());
         Number value = GlobalValueNumbering.this.numericConstants[a];
         if (value != null) {
            switch (insn.getTargetType()) {
               case INT:
                  GlobalValueNumbering.this.evaluatedConstant = value.intValue();
                  break;
               case LONG:
                  GlobalValueNumbering.this.evaluatedConstant = value.longValue();
                  break;
               case FLOAT:
                  GlobalValueNumbering.this.evaluatedConstant = value.floatValue();
                  break;
               case DOUBLE:
                  GlobalValueNumbering.this.evaluatedConstant = value.doubleValue();
            }
         }

         GlobalValueNumbering.this.receiver = insn.getReceiver().getIndex();
      }

      @Override
      public void visit(CastIntegerInstruction insn) {
         int a = GlobalValueNumbering.this.map[insn.getValue().getIndex()];
         int p = GlobalValueNumbering.this.replaceMap[insn.getValue().getIndex()];
         insn.setValue(GlobalValueNumbering.this.program.variableAt(p));
         GlobalValueNumbering.this.bind(insn.getReceiver().getIndex(), "@" + a + "::" + insn.getTargetType() + " " + insn.getDirection());
         Number value = GlobalValueNumbering.this.numericConstants[a];
         if (value != null) {
            switch (insn.getDirection()) {
               case TO_INTEGER:
                  GlobalValueNumbering.this.evaluatedConstant = value;
                  break;
               case FROM_INTEGER:
                  switch (insn.getTargetType()) {
                     case BYTE:
                        GlobalValueNumbering.this.evaluatedConstant = value.intValue() << 24 >> 24;
                        break;
                     case SHORT:
                        GlobalValueNumbering.this.evaluatedConstant = value.intValue() << 16 >> 16;
                        break;
                     case CHAR:
                        GlobalValueNumbering.this.evaluatedConstant = value.intValue() & 65535;
                  }
            }
         }

         GlobalValueNumbering.this.receiver = insn.getReceiver().getIndex();
      }

      @Override
      public void visit(BranchingInstruction insn) {
         int a = GlobalValueNumbering.this.replaceMap[insn.getOperand().getIndex()];
         insn.setOperand(GlobalValueNumbering.this.program.variableAt(a));
      }

      @Override
      public void visit(BinaryBranchingInstruction insn) {
         int a = GlobalValueNumbering.this.replaceMap[insn.getFirstOperand().getIndex()];
         int b = GlobalValueNumbering.this.replaceMap[insn.getSecondOperand().getIndex()];
         insn.setFirstOperand(GlobalValueNumbering.this.program.variableAt(a));
         insn.setSecondOperand(GlobalValueNumbering.this.program.variableAt(b));
      }

      @Override
      public void visit(SwitchInstruction insn) {
         int a = GlobalValueNumbering.this.replaceMap[insn.getCondition().getIndex()];
         insn.setCondition(GlobalValueNumbering.this.program.variableAt(a));
      }

      @Override
      public void visit(ExitInstruction insn) {
         if (insn.getValueToReturn() != null) {
            int a = GlobalValueNumbering.this.replaceMap[insn.getValueToReturn().getIndex()];
            insn.setValueToReturn(GlobalValueNumbering.this.program.variableAt(a));
         }
      }

      @Override
      public void visit(RaiseInstruction insn) {
         int a = GlobalValueNumbering.this.replaceMap[insn.getException().getIndex()];
         insn.setException(GlobalValueNumbering.this.program.variableAt(a));
      }

      @Override
      public void visit(ConstructArrayInstruction insn) {
         int a = GlobalValueNumbering.this.replaceMap[insn.getSize().getIndex()];
         insn.setSize(GlobalValueNumbering.this.program.variableAt(a));
      }

      @Override
      public void visit(ConstructMultiArrayInstruction insn) {
         for (int i = 0; i < insn.getDimensions().size(); i++) {
            int a = GlobalValueNumbering.this.replaceMap[insn.getDimensions().get(i).getIndex()];
            insn.getDimensions().set(i, GlobalValueNumbering.this.program.variableAt(a));
         }
      }

      @Override
      public void visit(GetFieldInstruction insn) {
         if (insn.getInstance() != null) {
            int instance = GlobalValueNumbering.this.replaceMap[insn.getInstance().getIndex()];
            insn.setInstance(GlobalValueNumbering.this.program.variableAt(instance));
         }
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         if (insn.getInstance() != null) {
            int instance = GlobalValueNumbering.this.replaceMap[insn.getInstance().getIndex()];
            insn.setInstance(GlobalValueNumbering.this.program.variableAt(instance));
         }

         int val = GlobalValueNumbering.this.replaceMap[insn.getValue().getIndex()];
         insn.setValue(GlobalValueNumbering.this.program.variableAt(val));
      }

      @Override
      public void visit(ArrayLengthInstruction insn) {
         int a = GlobalValueNumbering.this.map[insn.getArray().getIndex()];
         int p = GlobalValueNumbering.this.replaceMap[insn.getArray().getIndex()];
         insn.setArray(GlobalValueNumbering.this.program.variableAt(p));
         GlobalValueNumbering.this.bind(insn.getReceiver().getIndex(), "@" + a + ".length");
      }

      @Override
      public void visit(CloneArrayInstruction insn) {
         int a = GlobalValueNumbering.this.replaceMap[insn.getArray().getIndex()];
         insn.setArray(GlobalValueNumbering.this.program.variableAt(a));
      }

      @Override
      public void visit(UnwrapArrayInstruction insn) {
         int a = GlobalValueNumbering.this.map[insn.getArray().getIndex()];
         int p = GlobalValueNumbering.this.replaceMap[insn.getArray().getIndex()];
         insn.setArray(GlobalValueNumbering.this.program.variableAt(p));
         GlobalValueNumbering.this.bind(insn.getReceiver().getIndex(), "@" + a + ".data");
      }

      @Override
      public void visit(GetElementInstruction insn) {
         int a = GlobalValueNumbering.this.replaceMap[insn.getArray().getIndex()];
         insn.setArray(GlobalValueNumbering.this.program.variableAt(a));
         int index = GlobalValueNumbering.this.replaceMap[insn.getIndex().getIndex()];
         insn.setIndex(GlobalValueNumbering.this.program.variableAt(index));
      }

      @Override
      public void visit(PutElementInstruction insn) {
         int a = GlobalValueNumbering.this.replaceMap[insn.getArray().getIndex()];
         insn.setArray(GlobalValueNumbering.this.program.variableAt(a));
         int index = GlobalValueNumbering.this.replaceMap[insn.getIndex().getIndex()];
         insn.setIndex(GlobalValueNumbering.this.program.variableAt(index));
         int val = GlobalValueNumbering.this.replaceMap[insn.getValue().getIndex()];
         insn.setValue(GlobalValueNumbering.this.program.variableAt(val));
      }

      @Override
      public void visit(InvokeInstruction insn) {
         if (insn.getInstance() != null) {
            int instance = GlobalValueNumbering.this.replaceMap[insn.getInstance().getIndex()];
            insn.setInstance(GlobalValueNumbering.this.program.variableAt(instance));
         }

         insn.replaceArguments(this.mapper);
      }

      @Override
      public void visit(InvokeDynamicInstruction insn) {
         Optional.ofNullable(insn.getInstance()).map(this.mapper).ifPresent(insn::setInstance);
         insn.getArguments().replaceAll(this.mapper);
      }

      @Override
      public void visit(IsInstanceInstruction insn) {
         int val = GlobalValueNumbering.this.map[insn.getValue().getIndex()];
         int p = GlobalValueNumbering.this.replaceMap[insn.getValue().getIndex()];
         insn.setValue(GlobalValueNumbering.this.program.variableAt(p));
         GlobalValueNumbering.this.bind(insn.getReceiver().getIndex(), "@" + val + " :? " + insn.getType());
      }

      @Override
      public void visit(NullCheckInstruction insn) {
         int val = GlobalValueNumbering.this.map[insn.getValue().getIndex()];
         int p = GlobalValueNumbering.this.replaceMap[insn.getValue().getIndex()];
         insn.setValue(GlobalValueNumbering.this.program.variableAt(p));
         GlobalValueNumbering.this.bind(insn.getReceiver().getIndex(), "nullCheck @" + val);
      }

      @Override
      public void visit(MonitorEnterInstruction insn) {
         int val = GlobalValueNumbering.this.replaceMap[insn.getObjectRef().getIndex()];
         insn.setObjectRef(GlobalValueNumbering.this.program.variableAt(val));
      }

      @Override
      public void visit(MonitorExitInstruction insn) {
         int val = GlobalValueNumbering.this.replaceMap[insn.getObjectRef().getIndex()];
         insn.setObjectRef(GlobalValueNumbering.this.program.variableAt(val));
      }

      @Override
      public void visit(BoundCheckInstruction insn) {
         int index = GlobalValueNumbering.this.replaceMap[insn.getIndex().getIndex()];
         insn.setIndex(GlobalValueNumbering.this.program.variableAt(index));
         if (insn.getArray() != null) {
            int array = GlobalValueNumbering.this.replaceMap[insn.getArray().getIndex()];
            insn.setArray(GlobalValueNumbering.this.program.variableAt(array));
         }
      }
   };

   public GlobalValueNumbering(boolean namesPreserved) {
      this.namesPreserved = namesPreserved;
   }

   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      return this.optimize(program);
   }

   public boolean optimize(Program program) {
      boolean affected = false;
      this.program = program;
      this.knownValues.clear();
      Graph cfg = ProgramUtils.buildControlFlowGraph2(program);
      this.domTree = GraphUtils.buildDominatorTree(cfg);
      Graph dom = GraphUtils.buildDominatorGraph(this.domTree, cfg.size());
      this.map = new int[program.variableCount()];
      this.replaceMap = new int[program.variableCount()];
      this.numericConstants = new Number[program.variableCount()];

      for (int i = 0; i < this.map.length; this.replaceMap[i] = i++) {
         this.map[i] = i;
      }

      this.deletedVars = new boolean[program.variableCount()];
      this.preprocessConstants();
      List<List<Incoming>> outgoings = ProgramUtils.getPhiOutputs(program);
      int[] stack = new int[cfg.size() * 2];
      int top = 0;

      for (int i = 0; i < cfg.size(); i++) {
         if (cfg.incomingEdgesCount(i) == 0) {
            stack[top++] = i;
         }
      }

      while (top > 0) {
         int v = stack[--top];
         if (v % 2 == 0) {
            int blockIndex = v / 2;
            this.currentBlockIndex = blockIndex;
            BasicBlock block = program.basicBlockAt(blockIndex);
            if (block.getExceptionVariable() != null) {
               int var = this.map[block.getExceptionVariable().getIndex()];
               block.setExceptionVariable(program.variableAt(var));
            }

            for (Instruction currentInsn : block) {
               this.evaluatedConstant = null;
               currentInsn.acceptVisitor(this.optimizer);
               if (this.eliminate) {
                  affected = true;
                  currentInsn.delete();
                  this.eliminate = false;
               } else if (this.evaluatedConstant != null) {
                  if (this.evaluatedConstant instanceof Integer) {
                     IntegerConstantInstruction newInsn = new IntegerConstantInstruction();
                     newInsn.setConstant((Integer)this.evaluatedConstant);
                     newInsn.setReceiver(program.variableAt(this.receiver));
                     newInsn.setLocation(currentInsn.getLocation());
                     currentInsn.replace(newInsn);
                  } else if (this.evaluatedConstant instanceof Long) {
                     LongConstantInstruction newInsn = new LongConstantInstruction();
                     newInsn.setConstant((Long)this.evaluatedConstant);
                     newInsn.setReceiver(program.variableAt(this.receiver));
                     newInsn.setLocation(currentInsn.getLocation());
                     currentInsn.replace(newInsn);
                  } else if (this.evaluatedConstant instanceof Float) {
                     FloatConstantInstruction newInsn = new FloatConstantInstruction();
                     newInsn.setConstant((Float)this.evaluatedConstant);
                     newInsn.setReceiver(program.variableAt(this.receiver));
                     newInsn.setLocation(currentInsn.getLocation());
                     currentInsn.replace(newInsn);
                  } else if (this.evaluatedConstant instanceof Double) {
                     DoubleConstantInstruction newInsn = new DoubleConstantInstruction();
                     newInsn.setConstant((Double)this.evaluatedConstant);
                     newInsn.setReceiver(program.variableAt(this.receiver));
                     newInsn.setLocation(currentInsn.getLocation());
                     currentInsn.replace(newInsn);
                  }
               }
            }

            for (Incoming incoming : outgoings.get(blockIndex)) {
               int value = this.replaceMap[incoming.getValue().getIndex()];
               incoming.setValue(program.variableAt(value));
            }
         }

         for (int succ : dom.outgoingEdges(v)) {
            stack[top++] = succ;
         }
      }

      for (int i = 0; i < this.map.length; i++) {
         if (this.deletedVars[i]) {
            program.deleteVariable(i);
         }
      }

      program.pack();
      this.program = null;
      return affected;
   }

   private void preprocessConstants() {
      for (BasicBlock block : this.program.getBasicBlocks()) {
         for (Instruction instruction : block) {
            instruction.acceptVisitor(this.constantPreprocessor);
         }
      }
   }

   private void bind(int var, String value) {
      this.bind(var, value, false);
   }

   private void bind(int var, String value, boolean noReplace) {
      String name = this.program.variableAt(this.map[var]).getDebugName();
      if (name == null || this.namesPreserved) {
         name = "";
      }

      GlobalValueNumbering.KnownValue known = this.knownValues.get(name + ":" + value);
      if (known == null) {
         known = this.knownValues.get(":" + value);
      }

      boolean namesCompatible = !this.namesPreserved;
      if (!namesCompatible && known != null) {
         String knownName = this.program.variableAt(known.value).getDebugName();
         if (knownName == null) {
            knownName = "";
         }

         namesCompatible = knownName.isEmpty() || name.isEmpty() || knownName.equals(name);
      }

      if (known != null && this.dominates(known.location, this.currentBlockIndex) && known.value != var && namesCompatible) {
         this.map[var] = known.value;
         if (!noReplace) {
            this.replaceMap[var] = known.value;
            this.deletedVars[var] = true;
            this.eliminate = true;
         }

         if (!name.isEmpty()) {
            this.program.variableAt(known.value).setDebugName(name);
            this.knownValues.put(name + ":" + value, known);
         }
      } else {
         known = new GlobalValueNumbering.KnownValue();
         known.location = this.currentBlockIndex;
         known.value = var;
         this.knownValues.put(name + ":" + value, known);
         if (!name.isEmpty()) {
            this.knownValues.put(":" + value, known);
         }
      }
   }

   private boolean dominates(int a, int b) {
      return a == b || this.domTree.dominates(a * 2 + 1, b * 2);
   }

   private static class KnownValue {
      int value;
      int location;
   }
}
