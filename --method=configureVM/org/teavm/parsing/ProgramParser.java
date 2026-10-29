package org.teavm.parsing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.asm.AnnotationVisitor;
import org.teavm.asm.Attribute;
import org.teavm.asm.ConstantDynamic;
import org.teavm.asm.Handle;
import org.teavm.asm.Label;
import org.teavm.asm.MethodVisitor;
import org.teavm.asm.Type;
import org.teavm.asm.tree.AbstractInsnNode;
import org.teavm.asm.tree.InsnList;
import org.teavm.asm.tree.LabelNode;
import org.teavm.asm.tree.LineNumberNode;
import org.teavm.asm.tree.LocalVariableNode;
import org.teavm.asm.tree.MethodNode;
import org.teavm.asm.tree.TryCatchBlockNode;
import org.teavm.model.BasicBlock;
import org.teavm.model.DynamicConstant;
import org.teavm.model.FieldReference;
import org.teavm.model.Instruction;
import org.teavm.model.InvokeDynamicInstruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHandle;
import org.teavm.model.Program;
import org.teavm.model.ReferenceCache;
import org.teavm.model.RuntimeConstant;
import org.teavm.model.TextLocation;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.CastIntegerDirection;
import org.teavm.model.instructions.CastIntegerInstruction;
import org.teavm.model.instructions.CastNumberInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.EmptyInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.IntegerSubtype;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NegateInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.PutElementInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.instructions.SwitchInstruction;
import org.teavm.model.instructions.SwitchTableEntry;
import org.teavm.model.instructions.UnwrapArrayInstruction;
import org.teavm.model.util.TransitionExtractor;

public class ProgramParser {
   private ReferenceCache referenceCache;
   private static final byte ROOT = 0;
   private static final byte SINGLE = 1;
   private static final byte DOUBLE_FIRST_HALF = 2;
   private static final byte DOUBLE_SECOND_HALF = 3;
   private String fileName;
   private ProgramParser.StackFrame[] stackBefore;
   private ProgramParser.StackFrame[] stackAfter;
   private ProgramParser.StackFrame stack;
   private int index;
   private int[] nextIndexes;
   private Map<Label, Integer> labelIndexes;
   private Map<Label, Integer> lineNumbers;
   private List<List<Instruction>> targetInstructions;
   private List<Instruction> builder = new ArrayList<>();
   private List<BasicBlock> basicBlocks = new ArrayList<>();
   private int minLocal;
   private Program program;
   private Map<Integer, List<LocalVariableNode>> localVariableMap = new HashMap<>();
   private Map<Instruction, Map<Integer, String>> variableDebugNames = new HashMap<>();
   private MethodVisitor methodVisitor = new MethodVisitor(589824) {
      public void visitVarInsn(int opcode, int local) {
         switch (opcode) {
            case 21:
            case 23:
            case 25:
               ProgramParser.this.emitAssignInsn(ProgramParser.this.minLocal + ProgramParser.this.mapLocal(local), ProgramParser.this.pushSingle());
               break;
            case 22:
            case 24:
               ProgramParser.this.emitAssignInsn(ProgramParser.this.minLocal + ProgramParser.this.mapLocal(local), ProgramParser.this.pushDouble());
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
            case 31:
            case 32:
            case 33:
            case 34:
            case 35:
            case 36:
            case 37:
            case 38:
            case 39:
            case 40:
            case 41:
            case 42:
            case 43:
            case 44:
            case 45:
            case 46:
            case 47:
            case 48:
            case 49:
            case 50:
            case 51:
            case 52:
            case 53:
            default:
               break;
            case 54:
            case 56:
            case 58:
               ProgramParser.this.emitAssignInsn(ProgramParser.this.popSingle(), ProgramParser.this.minLocal + ProgramParser.this.mapLocal(local));
               break;
            case 55:
            case 57:
               ProgramParser.this.emitAssignInsn(ProgramParser.this.popDouble(), ProgramParser.this.minLocal + ProgramParser.this.mapLocal(local));
         }
      }

      private ValueType parseType(String type) {
         return type.startsWith("[")
            ? ProgramParser.this.referenceCache.parseValueTypeCached(type)
            : ProgramParser.this.referenceCache.getCached(ValueType.object(type.replace('/', '.')));
      }

      public void visitTypeInsn(int opcode, String type) {
         switch (opcode) {
            case 187: {
               String cls = type.replace('/', '.');
               ConstructInstruction insn = new ConstructInstruction();
               insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
               insn.setType(ProgramParser.this.referenceCache.getCached(cls));
               ProgramParser.this.addInstruction(insn);
            }
            case 188:
            case 190:
            case 191:
            default:
               break;
            case 189: {
               ValueType valueType = this.parseType(type);
               ConstructArrayInstruction insn = new ConstructArrayInstruction();
               insn.setSize(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
               insn.setItemType(valueType);
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 192: {
               CastInstruction insn = new CastInstruction();
               insn.setValue(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
               insn.setTargetType(this.parseType(type));
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 193: {
               IsInstanceInstruction insn = new IsInstanceInstruction();
               insn.setValue(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
               insn.setType(this.parseType(type));
               ProgramParser.this.addInstruction(insn);
            }
         }
      }

      public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
      }

      public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
         SwitchTableEntry[] table = new SwitchTableEntry[labels.length];
         ProgramParser.this.nextIndexes = new int[labels.length + 1];

         for (int i = 0; i < labels.length; i++) {
            Label label = labels[i];
            int target = ProgramParser.this.labelIndexes.get(label);
            SwitchTableEntry entry = new SwitchTableEntry();
            entry.setCondition(i + min);
            entry.setTarget(ProgramParser.this.getBasicBlock(target));
            table[i] = entry;
            ProgramParser.this.nextIndexes[i] = target;
         }

         SwitchInstruction insn = new SwitchInstruction();
         insn.setCondition(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
         insn.getEntries().addAll(Arrays.asList(table));
         ProgramParser.this.addInstruction(insn);
         int defaultIndex = ProgramParser.this.labelIndexes.get(dflt);
         insn.setDefaultTarget(ProgramParser.this.getBasicBlock(defaultIndex));
         ProgramParser.this.nextIndexes[labels.length] = defaultIndex;
      }

      public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
         return null;
      }

      public void visitMultiANewArrayInsn(String desc, int dims) {
         ValueType arrayType = this.parseType(desc);
         Variable[] dimensions = new Variable[dims];

         for (int i = dims - 1; i >= 0; i--) {
            dimensions[i] = ProgramParser.this.getVariable(ProgramParser.this.popSingle());
         }

         ConstructMultiArrayInstruction insn = new ConstructMultiArrayInstruction();
         insn.setItemType(arrayType);
         insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
         insn.getDimensions().addAll(Arrays.asList(dimensions));
         ProgramParser.this.addInstruction(insn);
      }

      public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
         InvokeDynamicInstruction insn = new InvokeDynamicInstruction();
         insn.setBootstrapMethod(ProgramParser.this.parseHandle(bsm));
         switch (insn.getBootstrapMethod().getKind()) {
            default:
               insn.setInstance(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
            case GET_STATIC_FIELD:
            case PUT_STATIC_FIELD:
            case INVOKE_STATIC:
            case INVOKE_CONSTRUCTOR:
               Type[] types = Type.getArgumentTypes(desc);
               Variable[] args = new Variable[types.length];
               int j = args.length;

               for (int i = types.length - 1; i >= 0; i--) {
                  args[--j] = types[i].getSize() == 2
                     ? ProgramParser.this.getVariable(ProgramParser.this.popDouble())
                     : ProgramParser.this.getVariable(ProgramParser.this.popSingle());
               }

               insn.getArguments().addAll(Arrays.asList(args));
               Type returnType = Type.getReturnType(desc);
               if (returnType.getSize() > 0) {
                  insn.setReceiver(
                     ProgramParser.this.getVariable(returnType.getSize() == 2 ? ProgramParser.this.pushDouble() : ProgramParser.this.pushSingle())
                  );
               }

               insn.setMethod(ProgramParser.this.referenceCache.getCached(new MethodDescriptor(name, MethodDescriptor.parseSignature(desc))));

               for (Object bsmArg : bsmArgs) {
                  insn.getBootstrapArguments().add(this.convertConstant(bsmArg));
               }

               ProgramParser.this.addInstruction(insn);
         }
      }

      private RuntimeConstant convertConstant(Object value) {
         if (value instanceof Integer) {
            return new RuntimeConstant((Integer)value);
         } else if (value instanceof Long) {
            return new RuntimeConstant((Long)value);
         } else if (value instanceof Float) {
            return new RuntimeConstant((Float)value);
         } else if (value instanceof Double) {
            return new RuntimeConstant((Double)value);
         } else if (value instanceof String) {
            return new RuntimeConstant((String)value);
         } else if (value instanceof Type type) {
            return type.getSort() == 11
               ? new RuntimeConstant(ProgramParser.this.parseSignature(type.getDescriptor()))
               : new RuntimeConstant(ProgramParser.this.referenceCache.parseValueTypeCached(type.getDescriptor()));
         } else if (value instanceof Handle) {
            return new RuntimeConstant(ProgramParser.this.parseHandle((Handle)value));
         } else if (!(value instanceof ConstantDynamic cd)) {
            throw new IllegalArgumentException("Unknown runtime constant: " + value);
         } else {
            RuntimeConstant[] args = new RuntimeConstant[cd.getBootstrapMethodArgumentCount()];

            for (int i = 0; i < args.length; i++) {
               args[i] = this.convertConstant(cd.getBootstrapMethodArgument(i));
            }

            DynamicConstant cst = new DynamicConstant(
               cd.getName(), ValueType.parse(cd.getDescriptor()), ProgramParser.this.parseHandle(cd.getBootstrapMethod()), List.of(args)
            );
            return new RuntimeConstant(cst);
         }
      }

      public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
         switch (opcode) {
            case 182:
            case 183:
            case 184:
            case 185:
               String ownerCls;
               if (owner.startsWith("[")) {
                  if (name.equals("clone") && desc.startsWith("()")) {
                     CloneArrayInstruction insn = new CloneArrayInstruction();
                     insn.setArray(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
                     insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
                     ProgramParser.this.addInstruction(insn);
                     return;
                  }

                  ownerCls = "java.lang.Object";
               } else {
                  ownerCls = owner.replace('/', '.');
               }

               Type[] types = Type.getArgumentTypes(desc);
               Variable[] args = new Variable[types.length];
               int j = args.length;

               for (int i = types.length - 1; i >= 0; i--) {
                  args[--j] = types[i].getSize() == 2
                     ? ProgramParser.this.getVariable(ProgramParser.this.popDouble())
                     : ProgramParser.this.getVariable(ProgramParser.this.popSingle());
               }

               MethodDescriptor method = ProgramParser.this.referenceCache.getCached(new MethodDescriptor(name, MethodDescriptor.parseSignature(desc)));
               int instance = -1;
               if (opcode != 184) {
                  instance = ProgramParser.this.popSingle();
               }

               Type returnType = Type.getReturnType(desc);
               int result = -1;
               if (returnType.getSize() > 0) {
                  result = returnType.getSize() == 2 ? ProgramParser.this.pushDouble() : ProgramParser.this.pushSingle();
               }

               if (instance == -1) {
                  InvokeInstruction insn = new InvokeInstruction();
                  insn.setType(InvocationType.SPECIAL);
                  insn.setMethod(ProgramParser.this.referenceCache.getCached(ownerCls, method));
                  if (result >= 0) {
                     insn.setReceiver(ProgramParser.this.getVariable(result));
                  }

                  insn.setArguments(args);
                  ProgramParser.this.addInstruction(insn);
               } else {
                  InvokeInstruction insn = new InvokeInstruction();
                  if (opcode == 183) {
                     insn.setType(InvocationType.SPECIAL);
                  } else {
                     insn.setType(InvocationType.VIRTUAL);
                  }

                  insn.setMethod(ProgramParser.this.referenceCache.getCached(ownerCls, method));
                  if (result >= 0) {
                     insn.setReceiver(ProgramParser.this.getVariable(result));
                  }

                  insn.setInstance(ProgramParser.this.getVariable(instance));
                  insn.setArguments(args);
                  ProgramParser.this.addInstruction(insn);
               }
         }
      }

      public void visitMaxs(int maxStack, int maxLocals) {
      }

      public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
         SwitchTableEntry[] table = new SwitchTableEntry[labels.length];
         ProgramParser.this.nextIndexes = new int[labels.length + 1];

         for (int i = 0; i < labels.length; i++) {
            Label label = labels[i];
            int target = ProgramParser.this.labelIndexes.get(label);
            SwitchTableEntry entry = new SwitchTableEntry();
            entry.setCondition(keys[i]);
            entry.setTarget(ProgramParser.this.getBasicBlock(target));
            table[i] = entry;
            ProgramParser.this.nextIndexes[i] = target;
         }

         SwitchInstruction insn = new SwitchInstruction();
         insn.setCondition(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
         insn.getEntries().addAll(Arrays.asList(table));
         ProgramParser.this.addInstruction(insn);
         int defaultTarget = ProgramParser.this.labelIndexes.get(dflt);
         insn.setDefaultTarget(ProgramParser.this.getBasicBlock(defaultTarget));
         ProgramParser.this.nextIndexes[labels.length] = ProgramParser.this.labelIndexes.get(dflt);
      }

      public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
      }

      public void visitLineNumber(int line, Label start) {
      }

      public void visitLdcInsn(Object cst) {
         if (cst instanceof Integer) {
            this.pushConstant((Integer)cst);
         } else if (cst instanceof Float) {
            this.pushConstant((Float)cst);
         } else if (cst instanceof Long) {
            this.pushConstant((Long)cst);
         } else if (cst instanceof Double) {
            this.pushConstant((Double)cst);
         } else if (cst instanceof String) {
            StringConstantInstruction insn = new StringConstantInstruction();
            insn.setConstant(ProgramParser.this.referenceCache.getCached((String)cst));
            insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
            ProgramParser.this.addInstruction(insn);
         } else {
            if (!(cst instanceof Type)) {
               throw new IllegalArgumentException();
            }

            ClassConstantInstruction insn = new ClassConstantInstruction();
            insn.setConstant(ProgramParser.this.referenceCache.getCached(ValueType.parse(((Type)cst).getDescriptor())));
            insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
            ProgramParser.this.addInstruction(insn);
         }
      }

      public void visitLabel(Label label) {
      }

      private void emitBranching(BranchingCondition condition, int value, int target) {
         BranchingInstruction insn = new BranchingInstruction(condition);
         insn.setOperand(ProgramParser.this.getVariable(value));
         insn.setConsequent(ProgramParser.this.getBasicBlock(target));
         insn.setAlternative(ProgramParser.this.getBasicBlock(ProgramParser.this.index + 1));
         ProgramParser.this.addInstruction(insn);
      }

      private void emitBranching(BinaryBranchingCondition condition, int first, int second, int target) {
         BinaryBranchingInstruction insn = new BinaryBranchingInstruction(condition);
         insn.setFirstOperand(ProgramParser.this.getVariable(first));
         insn.setSecondOperand(ProgramParser.this.getVariable(second));
         insn.setConsequent(ProgramParser.this.getBasicBlock(target));
         insn.setAlternative(ProgramParser.this.getBasicBlock(ProgramParser.this.index + 1));
         ProgramParser.this.addInstruction(insn);
      }

      private void emitBinary(BinaryOperation operation, NumericOperandType operandType, int first, int second, int receiver) {
         BinaryInstruction insn = new BinaryInstruction(operation, operandType);
         insn.setFirstOperand(ProgramParser.this.getVariable(first));
         insn.setSecondOperand(ProgramParser.this.getVariable(second));
         insn.setReceiver(ProgramParser.this.getVariable(receiver));
         ProgramParser.this.addInstruction(insn);
      }

      private void emitNeg(NumericOperandType operandType, int operand, int receiver) {
         NegateInstruction insn = new NegateInstruction(operandType);
         insn.setOperand(ProgramParser.this.getVariable(operand));
         insn.setReceiver(ProgramParser.this.getVariable(receiver));
         ProgramParser.this.addInstruction(insn);
      }

      private void emitNumberCast(NumericOperandType source, NumericOperandType target, int value, int result) {
         CastNumberInstruction insn = new CastNumberInstruction(source, target);
         insn.setReceiver(ProgramParser.this.getVariable(result));
         insn.setValue(ProgramParser.this.getVariable(value));
         ProgramParser.this.addInstruction(insn);
      }

      public void visitJumpInsn(int opcode, Label label) {
         int target = ProgramParser.this.labelIndexes.get(label);
         switch (opcode) {
            case 153:
               this.emitBranching(BranchingCondition.EQUAL, ProgramParser.this.popSingle(), target);
               break;
            case 154:
               this.emitBranching(BranchingCondition.NOT_EQUAL, ProgramParser.this.popSingle(), target);
               break;
            case 155:
               this.emitBranching(BranchingCondition.LESS, ProgramParser.this.popSingle(), target);
               break;
            case 156:
               this.emitBranching(BranchingCondition.GREATER_OR_EQUAL, ProgramParser.this.popSingle(), target);
               break;
            case 157:
               this.emitBranching(BranchingCondition.GREATER, ProgramParser.this.popSingle(), target);
               break;
            case 158:
               this.emitBranching(BranchingCondition.LESS_OR_EQUAL, ProgramParser.this.popSingle(), target);
               break;
            case 159: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               this.emitBinary(BinaryOperation.COMPARE_GREATER, NumericOperandType.INT, a, b, a);
               this.emitBranching(BranchingCondition.EQUAL, a, target);
               break;
            }
            case 160: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               this.emitBinary(BinaryOperation.COMPARE_GREATER, NumericOperandType.INT, a, b, a);
               this.emitBranching(BranchingCondition.NOT_EQUAL, a, target);
               break;
            }
            case 161: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               this.emitBinary(BinaryOperation.COMPARE_GREATER, NumericOperandType.INT, a, b, a);
               this.emitBranching(BranchingCondition.LESS, a, target);
               break;
            }
            case 162: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               this.emitBinary(BinaryOperation.COMPARE_GREATER, NumericOperandType.INT, a, b, a);
               this.emitBranching(BranchingCondition.GREATER_OR_EQUAL, a, target);
               break;
            }
            case 163: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               this.emitBinary(BinaryOperation.COMPARE_GREATER, NumericOperandType.INT, a, b, a);
               this.emitBranching(BranchingCondition.GREATER, a, target);
               break;
            }
            case 164: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               this.emitBinary(BinaryOperation.COMPARE_GREATER, NumericOperandType.INT, a, b, a);
               this.emitBranching(BranchingCondition.LESS_OR_EQUAL, a, target);
               break;
            }
            case 165: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               this.emitBranching(BinaryBranchingCondition.REFERENCE_EQUAL, a, b, target);
               break;
            }
            case 166: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               this.emitBranching(BinaryBranchingCondition.REFERENCE_NOT_EQUAL, a, b, target);
               break;
            }
            case 167:
               JumpInstruction insn = new JumpInstruction();
               insn.setTarget(ProgramParser.this.getBasicBlock(target));
               ProgramParser.this.addInstruction(insn);
               ProgramParser.this.nextIndexes = new int[]{ProgramParser.this.labelIndexes.get(label)};
               return;
            case 168:
            case 169:
            case 170:
            case 171:
            case 172:
            case 173:
            case 174:
            case 175:
            case 176:
            case 177:
            case 178:
            case 179:
            case 180:
            case 181:
            case 182:
            case 183:
            case 184:
            case 185:
            case 186:
            case 187:
            case 188:
            case 189:
            case 190:
            case 191:
            case 192:
            case 193:
            case 194:
            case 195:
            case 196:
            case 197:
            default:
               throw new RuntimeException("Unknown opcode");
            case 198:
               this.emitBranching(BranchingCondition.NULL, ProgramParser.this.popSingle(), target);
               break;
            case 199:
               this.emitBranching(BranchingCondition.NOT_NULL, ProgramParser.this.popSingle(), target);
         }

         ProgramParser.this.nextIndexes = new int[]{ProgramParser.this.labelIndexes.get(label), ProgramParser.this.index + 1};
      }

      public void visitIntInsn(int opcode, int operand) {
         switch (opcode) {
            case 16:
            case 17: {
               IntegerConstantInstruction insn = new IntegerConstantInstruction();
               insn.setConstant(operand);
               insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 188: {
               ValueType itemType = switch (operand) {
                  case 4 -> ValueType.BOOLEAN;
                  case 5 -> ValueType.CHARACTER;
                  case 6 -> ValueType.FLOAT;
                  case 7 -> ValueType.DOUBLE;
                  case 8 -> ValueType.BYTE;
                  case 9 -> ValueType.SHORT;
                  case 10 -> ValueType.INTEGER;
                  case 11 -> ValueType.LONG;
                  default -> throw new RuntimeException("Illegal opcode");
               };
               ConstructArrayInstruction insn = new ConstructArrayInstruction();
               insn.setSize(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
               insn.setItemType(itemType);
               ProgramParser.this.addInstruction(insn);
            }
         }
      }

      private void pushConstant(int value) {
         IntegerConstantInstruction insn = new IntegerConstantInstruction();
         insn.setConstant(value);
         insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
         ProgramParser.this.addInstruction(insn);
      }

      private void pushConstant(long value) {
         LongConstantInstruction insn = new LongConstantInstruction();
         insn.setConstant(value);
         insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushDouble()));
         ProgramParser.this.addInstruction(insn);
      }

      private void pushConstant(double value) {
         DoubleConstantInstruction insn = new DoubleConstantInstruction();
         insn.setConstant(value);
         insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushDouble()));
         ProgramParser.this.addInstruction(insn);
      }

      private void pushConstant(float value) {
         FloatConstantInstruction insn = new FloatConstantInstruction();
         insn.setConstant(value);
         insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
         ProgramParser.this.addInstruction(insn);
      }

      private void loadArrayElement(int sz, ArrayElementType type) {
         int arrIndex = ProgramParser.this.popSingle();
         int array = ProgramParser.this.popSingle();
         int var = sz == 1 ? ProgramParser.this.pushSingle() : ProgramParser.this.pushDouble();
         UnwrapArrayInstruction unwrapInsn = new UnwrapArrayInstruction(type);
         unwrapInsn.setArray(ProgramParser.this.getVariable(array));
         unwrapInsn.setReceiver(unwrapInsn.getArray());
         ProgramParser.this.addInstruction(unwrapInsn);
         GetElementInstruction insn = new GetElementInstruction(type);
         insn.setArray(ProgramParser.this.getVariable(array));
         insn.setIndex(ProgramParser.this.getVariable(arrIndex));
         insn.setReceiver(ProgramParser.this.getVariable(var));
         ProgramParser.this.addInstruction(insn);
      }

      private void storeArrayElement(int sz, ArrayElementType type) {
         int value = sz == 1 ? ProgramParser.this.popSingle() : ProgramParser.this.popDouble();
         int arrIndex = ProgramParser.this.popSingle();
         int array = ProgramParser.this.popSingle();
         UnwrapArrayInstruction unwrapInsn = new UnwrapArrayInstruction(type);
         unwrapInsn.setArray(ProgramParser.this.getVariable(array));
         unwrapInsn.setReceiver(unwrapInsn.getArray());
         ProgramParser.this.addInstruction(unwrapInsn);
         PutElementInstruction insn = new PutElementInstruction(type);
         insn.setArray(ProgramParser.this.getVariable(array));
         insn.setIndex(ProgramParser.this.getVariable(arrIndex));
         insn.setValue(ProgramParser.this.getVariable(value));
         ProgramParser.this.addInstruction(insn);
      }

      public void visitInsn(int opcode) {
         switch (opcode) {
            case 1: {
               NullConstantInstruction insn = new NullConstantInstruction();
               insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 2:
               this.pushConstant(-1);
               break;
            case 3:
               this.pushConstant(0);
               break;
            case 4:
               this.pushConstant(1);
               break;
            case 5:
               this.pushConstant(2);
               break;
            case 6:
               this.pushConstant(3);
               break;
            case 7:
               this.pushConstant(4);
               break;
            case 8:
               this.pushConstant(5);
               break;
            case 9:
               this.pushConstant(0L);
               break;
            case 10:
               this.pushConstant(1L);
               break;
            case 11:
               this.pushConstant(0.0F);
               break;
            case 12:
               this.pushConstant(1.0F);
               break;
            case 13:
               this.pushConstant(2.0F);
               break;
            case 14:
               this.pushConstant(0.0);
               break;
            case 15:
               this.pushConstant(1.0);
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
            case 31:
            case 32:
            case 33:
            case 34:
            case 35:
            case 36:
            case 37:
            case 38:
            case 39:
            case 40:
            case 41:
            case 42:
            case 43:
            case 44:
            case 45:
            case 54:
            case 55:
            case 56:
            case 57:
            case 58:
            case 59:
            case 60:
            case 61:
            case 62:
            case 63:
            case 64:
            case 65:
            case 66:
            case 67:
            case 68:
            case 69:
            case 70:
            case 71:
            case 72:
            case 73:
            case 74:
            case 75:
            case 76:
            case 77:
            case 78:
            case 132:
            case 153:
            case 154:
            case 155:
            case 156:
            case 157:
            case 158:
            case 159:
            case 160:
            case 161:
            case 162:
            case 163:
            case 164:
            case 165:
            case 166:
            case 167:
            case 168:
            case 169:
            case 170:
            case 171:
            case 178:
            case 179:
            case 180:
            case 181:
            case 182:
            case 183:
            case 184:
            case 185:
            case 186:
            case 187:
            case 188:
            case 189:
            case 192:
            case 193:
            default:
               break;
            case 46:
               this.loadArrayElement(1, ArrayElementType.INT);
               break;
            case 47:
               this.loadArrayElement(2, ArrayElementType.LONG);
               break;
            case 48:
               this.loadArrayElement(1, ArrayElementType.FLOAT);
               break;
            case 49:
               this.loadArrayElement(2, ArrayElementType.DOUBLE);
               break;
            case 50:
               this.loadArrayElement(1, ArrayElementType.OBJECT);
               break;
            case 51: {
               this.loadArrayElement(1, ArrayElementType.BYTE);
               CastIntegerInstruction insn = new CastIntegerInstruction(IntegerSubtype.BYTE, CastIntegerDirection.TO_INTEGER);
               insn.setValue(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 52: {
               this.loadArrayElement(1, ArrayElementType.CHAR);
               CastIntegerInstruction insn = new CastIntegerInstruction(IntegerSubtype.CHAR, CastIntegerDirection.TO_INTEGER);
               insn.setValue(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 53: {
               this.loadArrayElement(1, ArrayElementType.SHORT);
               CastIntegerInstruction insn = new CastIntegerInstruction(IntegerSubtype.SHORT, CastIntegerDirection.TO_INTEGER);
               insn.setValue(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 79:
               this.storeArrayElement(1, ArrayElementType.INT);
               break;
            case 80:
               this.storeArrayElement(2, ArrayElementType.LONG);
               break;
            case 81:
               this.storeArrayElement(1, ArrayElementType.FLOAT);
               break;
            case 82:
               this.storeArrayElement(2, ArrayElementType.DOUBLE);
               break;
            case 83:
               this.storeArrayElement(1, ArrayElementType.OBJECT);
               break;
            case 84:
               this.storeArrayElement(1, ArrayElementType.BYTE);
               break;
            case 85:
               this.storeArrayElement(1, ArrayElementType.CHAR);
               break;
            case 86:
               this.storeArrayElement(1, ArrayElementType.SHORT);
               break;
            case 87:
               ProgramParser.this.popSingle();
               break;
            case 88:
               if (ProgramParser.this.stack.type == 1) {
                  ProgramParser.this.popSingle();
                  ProgramParser.this.popSingle();
               } else {
                  ProgramParser.this.popDouble();
               }
               break;
            case 89:
               ProgramParser.this.popSingle();
               int orig = ProgramParser.this.pushSingle();
               int copy = ProgramParser.this.pushSingle();
               ProgramParser.this.emitAssignInsn(orig, copy);
               break;
            case 90: {
               ProgramParser.this.popSingle();
               ProgramParser.this.popSingle();
               int ins = ProgramParser.this.pushSingle();
               int b = ProgramParser.this.pushSingle();
               int a = ProgramParser.this.pushSingle();
               ProgramParser.this.emitAssignInsn(a - 1, a);
               ProgramParser.this.emitAssignInsn(b - 1, b);
               ProgramParser.this.emitAssignInsn(a, ins);
               break;
            }
            case 91:
               ProgramParser.this.popSingle();
               if (ProgramParser.this.stack.type == 1) {
                  ProgramParser.this.popSingle();
                  ProgramParser.this.popSingle();
                  int insx = ProgramParser.this.pushSingle();
                  int c = ProgramParser.this.pushSingle();
                  int bx = ProgramParser.this.pushSingle();
                  int ax = ProgramParser.this.pushSingle();
                  ProgramParser.this.emitAssignInsn(ax - 1, ax);
                  ProgramParser.this.emitAssignInsn(bx - 1, bx);
                  ProgramParser.this.emitAssignInsn(c - 1, c);
                  ProgramParser.this.emitAssignInsn(ax, insx);
               } else {
                  ProgramParser.this.popDouble();
                  int insx = ProgramParser.this.pushSingle();
                  int bx = ProgramParser.this.pushDouble();
                  int ax = ProgramParser.this.pushSingle();
                  ProgramParser.this.emitAssignInsn(ax - 1, ax);
                  ProgramParser.this.emitAssignInsn(bx - 1, bx);
                  ProgramParser.this.emitAssignInsn(ax, insx);
               }
               break;
            case 92:
               if (ProgramParser.this.stack.type == 1) {
                  ProgramParser.this.popSingle();
                  ProgramParser.this.popSingle();
                  int origA = ProgramParser.this.pushSingle();
                  int origB = ProgramParser.this.pushSingle();
                  int copyA = ProgramParser.this.pushSingle();
                  int copyB = ProgramParser.this.pushSingle();
                  ProgramParser.this.emitAssignInsn(origA, copyA);
                  ProgramParser.this.emitAssignInsn(origB, copyB);
               } else {
                  ProgramParser.this.popDouble();
                  int origx = ProgramParser.this.pushDouble();
                  int copyx = ProgramParser.this.pushDouble();
                  ProgramParser.this.emitAssignInsn(origx, copyx);
               }
               break;
            case 93:
               if (ProgramParser.this.stack.type == 1) {
                  ProgramParser.this.popSingle();
                  ProgramParser.this.popSingle();
                  ProgramParser.this.popSingle();
                  int ins1 = ProgramParser.this.pushSingle();
                  int ins2 = ProgramParser.this.pushSingle();
                  int bx = ProgramParser.this.pushSingle();
                  int a1 = ProgramParser.this.pushSingle();
                  int a2 = ProgramParser.this.pushSingle();
                  ProgramParser.this.emitAssignInsn(a2 - 2, a2);
                  ProgramParser.this.emitAssignInsn(a1 - 2, a1);
                  ProgramParser.this.emitAssignInsn(bx - 2, bx);
                  ProgramParser.this.emitAssignInsn(a1, ins1);
                  ProgramParser.this.emitAssignInsn(a2, ins2);
               } else {
                  ProgramParser.this.popDouble();
                  ProgramParser.this.popSingle();
                  int insx = ProgramParser.this.pushDouble();
                  int bx = ProgramParser.this.pushSingle();
                  int ax = ProgramParser.this.pushDouble();
                  ProgramParser.this.emitAssignInsn(ax - 2, ax);
                  ProgramParser.this.emitAssignInsn(bx - 2, bx);
                  ProgramParser.this.emitAssignInsn(ax, insx);
               }
               break;
            case 94:
               if (ProgramParser.this.stack.type == 1) {
                  ProgramParser.this.popSingle();
                  ProgramParser.this.popSingle();
                  if (ProgramParser.this.stack.type == 1) {
                     ProgramParser.this.popSingle();
                     ProgramParser.this.popSingle();
                     int ins1 = ProgramParser.this.pushSingle();
                     int ins2 = ProgramParser.this.pushSingle();
                     int c = ProgramParser.this.pushSingle();
                     int bx = ProgramParser.this.pushSingle();
                     int a1 = ProgramParser.this.pushSingle();
                     int a2 = ProgramParser.this.pushSingle();
                     ProgramParser.this.emitAssignInsn(a2 - 2, a2);
                     ProgramParser.this.emitAssignInsn(a1 - 2, a1);
                     ProgramParser.this.emitAssignInsn(bx - 2, bx);
                     ProgramParser.this.emitAssignInsn(c - 2, c);
                     ProgramParser.this.emitAssignInsn(a1, ins1);
                     ProgramParser.this.emitAssignInsn(a2, ins2);
                  } else {
                     ProgramParser.this.popDouble();
                     int ins1 = ProgramParser.this.pushSingle();
                     int ins2 = ProgramParser.this.pushSingle();
                     int bx = ProgramParser.this.pushDouble();
                     int a1 = ProgramParser.this.pushSingle();
                     int a2 = ProgramParser.this.pushSingle();
                     ProgramParser.this.emitAssignInsn(a2 - 2, a2);
                     ProgramParser.this.emitAssignInsn(a1 - 2, a1);
                     ProgramParser.this.emitAssignInsn(bx - 2, bx);
                     ProgramParser.this.emitAssignInsn(a1, ins1);
                     ProgramParser.this.emitAssignInsn(a2, ins2);
                  }
               } else {
                  ProgramParser.this.popDouble();
                  if (ProgramParser.this.stack.type == 1) {
                     ProgramParser.this.popSingle();
                     ProgramParser.this.popSingle();
                     int insx = ProgramParser.this.pushDouble();
                     int c = ProgramParser.this.pushSingle();
                     int bx = ProgramParser.this.pushSingle();
                     int ax = ProgramParser.this.pushDouble();
                     ProgramParser.this.emitAssignInsn(ax - 2, ax);
                     ProgramParser.this.emitAssignInsn(bx - 2, bx);
                     ProgramParser.this.emitAssignInsn(c - 2, c);
                     ProgramParser.this.emitAssignInsn(ax, insx);
                  } else {
                     ProgramParser.this.popDouble();
                     int insx = ProgramParser.this.pushDouble();
                     int bx = ProgramParser.this.pushDouble();
                     int ax = ProgramParser.this.pushDouble();
                     ProgramParser.this.emitAssignInsn(ax - 2, ax);
                     ProgramParser.this.emitAssignInsn(bx - 2, bx);
                     ProgramParser.this.emitAssignInsn(ax, insx);
                  }
               }
               break;
            case 95: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               ProgramParser.this.pushSingle();
               ProgramParser.this.pushSingle();
               int tmp = b + 1;
               ProgramParser.this.emitAssignInsn(a, tmp);
               ProgramParser.this.emitAssignInsn(b, a);
               ProgramParser.this.emitAssignInsn(tmp, b);
               break;
            }
            case 96: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.ADD, NumericOperandType.INT, a, b, r);
               break;
            }
            case 97: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.ADD, NumericOperandType.LONG, a, b, r);
               break;
            }
            case 98: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.ADD, NumericOperandType.FLOAT, a, b, r);
               break;
            }
            case 99: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.ADD, NumericOperandType.DOUBLE, a, b, r);
               break;
            }
            case 100: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.SUBTRACT, NumericOperandType.INT, a, b, r);
               break;
            }
            case 101: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.SUBTRACT, NumericOperandType.LONG, a, b, r);
               break;
            }
            case 102: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.SUBTRACT, NumericOperandType.FLOAT, a, b, r);
               break;
            }
            case 103: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.SUBTRACT, NumericOperandType.DOUBLE, a, b, r);
               break;
            }
            case 104: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.MULTIPLY, NumericOperandType.INT, a, b, r);
               break;
            }
            case 105: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.MULTIPLY, NumericOperandType.LONG, a, b, r);
               break;
            }
            case 106: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.MULTIPLY, NumericOperandType.FLOAT, a, b, r);
               break;
            }
            case 107: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.MULTIPLY, NumericOperandType.DOUBLE, a, b, r);
               break;
            }
            case 108: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.DIVIDE, NumericOperandType.INT, a, b, r);
               break;
            }
            case 109: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.DIVIDE, NumericOperandType.LONG, a, b, r);
               break;
            }
            case 110: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.DIVIDE, NumericOperandType.FLOAT, a, b, r);
               break;
            }
            case 111: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.DIVIDE, NumericOperandType.DOUBLE, a, b, r);
               break;
            }
            case 112: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.MODULO, NumericOperandType.INT, a, b, r);
               break;
            }
            case 113: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.MODULO, NumericOperandType.LONG, a, b, r);
               break;
            }
            case 114: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.MODULO, NumericOperandType.FLOAT, a, b, r);
               break;
            }
            case 115: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.MODULO, NumericOperandType.DOUBLE, a, b, r);
               break;
            }
            case 116: {
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitNeg(NumericOperandType.INT, a, r);
               break;
            }
            case 117: {
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitNeg(NumericOperandType.LONG, a, r);
               break;
            }
            case 118: {
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitNeg(NumericOperandType.FLOAT, a, r);
               break;
            }
            case 119: {
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitNeg(NumericOperandType.DOUBLE, a, r);
               break;
            }
            case 120: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.SHIFT_LEFT, NumericOperandType.INT, a, b, r);
               break;
            }
            case 121: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.SHIFT_LEFT, NumericOperandType.LONG, a, b, r);
               break;
            }
            case 122: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.SHIFT_RIGHT, NumericOperandType.INT, a, b, r);
               break;
            }
            case 123: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.SHIFT_RIGHT, NumericOperandType.LONG, a, b, r);
               break;
            }
            case 124: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.SHIFT_RIGHT_UNSIGNED, NumericOperandType.INT, a, b, r);
               break;
            }
            case 125: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.SHIFT_RIGHT_UNSIGNED, NumericOperandType.LONG, a, b, r);
               break;
            }
            case 126: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.AND, NumericOperandType.INT, a, b, r);
               break;
            }
            case 127: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.AND, NumericOperandType.LONG, a, b, r);
               break;
            }
            case 128: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.OR, NumericOperandType.INT, a, b, r);
               break;
            }
            case 129: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.OR, NumericOperandType.LONG, a, b, r);
               break;
            }
            case 130: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.XOR, NumericOperandType.INT, a, b, r);
               break;
            }
            case 131: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitBinary(BinaryOperation.XOR, NumericOperandType.LONG, a, b, r);
               break;
            }
            case 133: {
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushDouble();
               this.emitNumberCast(NumericOperandType.INT, NumericOperandType.LONG, a, r);
               break;
            }
            case 134: {
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitNumberCast(NumericOperandType.INT, NumericOperandType.FLOAT, a, r);
               break;
            }
            case 135: {
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushDouble();
               this.emitNumberCast(NumericOperandType.INT, NumericOperandType.DOUBLE, a, r);
               break;
            }
            case 136: {
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushSingle();
               this.emitNumberCast(NumericOperandType.LONG, NumericOperandType.INT, a, r);
               break;
            }
            case 137: {
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushSingle();
               this.emitNumberCast(NumericOperandType.LONG, NumericOperandType.FLOAT, a, r);
               break;
            }
            case 138: {
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitNumberCast(NumericOperandType.LONG, NumericOperandType.DOUBLE, a, r);
               break;
            }
            case 139: {
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitNumberCast(NumericOperandType.FLOAT, NumericOperandType.INT, a, r);
               break;
            }
            case 140: {
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushDouble();
               this.emitNumberCast(NumericOperandType.FLOAT, NumericOperandType.LONG, a, r);
               break;
            }
            case 141: {
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushDouble();
               this.emitNumberCast(NumericOperandType.FLOAT, NumericOperandType.DOUBLE, a, r);
               break;
            }
            case 142: {
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushSingle();
               this.emitNumberCast(NumericOperandType.DOUBLE, NumericOperandType.INT, a, r);
               break;
            }
            case 143: {
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushDouble();
               this.emitNumberCast(NumericOperandType.DOUBLE, NumericOperandType.LONG, a, r);
               break;
            }
            case 144: {
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushSingle();
               this.emitNumberCast(NumericOperandType.DOUBLE, NumericOperandType.FLOAT, a, r);
               break;
            }
            case 145: {
               CastIntegerInstruction insn = new CastIntegerInstruction(IntegerSubtype.BYTE, CastIntegerDirection.FROM_INTEGER);
               insn.setValue(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 146: {
               CastIntegerInstruction insn = new CastIntegerInstruction(IntegerSubtype.CHAR, CastIntegerDirection.FROM_INTEGER);
               insn.setValue(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 147: {
               CastIntegerInstruction insn = new CastIntegerInstruction(IntegerSubtype.SHORT, CastIntegerDirection.FROM_INTEGER);
               insn.setValue(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               insn.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 148: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.COMPARE_LESS, NumericOperandType.LONG, a, b, r);
               break;
            }
            case 149: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.COMPARE_LESS, NumericOperandType.FLOAT, a, b, r);
               break;
            }
            case 150: {
               int b = ProgramParser.this.popSingle();
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.COMPARE_GREATER, NumericOperandType.FLOAT, a, b, r);
               break;
            }
            case 151: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.COMPARE_LESS, NumericOperandType.DOUBLE, a, b, r);
               break;
            }
            case 152: {
               int b = ProgramParser.this.popDouble();
               int a = ProgramParser.this.popDouble();
               int r = ProgramParser.this.pushSingle();
               this.emitBinary(BinaryOperation.COMPARE_GREATER, NumericOperandType.DOUBLE, a, b, r);
               break;
            }
            case 172:
            case 174:
            case 176: {
               ExitInstruction insn = new ExitInstruction();
               insn.setValueToReturn(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               ProgramParser.this.addInstruction(insn);
               ProgramParser.this.nextIndexes = new int[0];
               return;
            }
            case 173:
            case 175: {
               ExitInstruction insn = new ExitInstruction();
               insn.setValueToReturn(ProgramParser.this.getVariable(ProgramParser.this.popDouble()));
               ProgramParser.this.addInstruction(insn);
               ProgramParser.this.nextIndexes = new int[0];
               return;
            }
            case 177: {
               ExitInstruction insn = new ExitInstruction();
               ProgramParser.this.addInstruction(insn);
               ProgramParser.this.nextIndexes = new int[0];
               return;
            }
            case 190: {
               int a = ProgramParser.this.popSingle();
               int r = ProgramParser.this.pushSingle();
               UnwrapArrayInstruction unwrapInsn = new UnwrapArrayInstruction(ArrayElementType.OBJECT);
               unwrapInsn.setArray(ProgramParser.this.getVariable(a));
               unwrapInsn.setReceiver(ProgramParser.this.getVariable(r));
               ProgramParser.this.addInstruction(unwrapInsn);
               ArrayLengthInstruction insn = new ArrayLengthInstruction();
               insn.setArray(ProgramParser.this.getVariable(a));
               insn.setReceiver(ProgramParser.this.getVariable(r));
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 191: {
               RaiseInstruction insn = new RaiseInstruction();
               insn.setException(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               ProgramParser.this.addInstruction(insn);
               ProgramParser.this.nextIndexes = new int[0];
               return;
            }
            case 194: {
               MonitorEnterInstruction insn = new MonitorEnterInstruction();
               insn.setObjectRef(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 195: {
               MonitorExitInstruction insn = new MonitorExitInstruction();
               insn.setObjectRef(ProgramParser.this.getVariable(ProgramParser.this.popSingle()));
               ProgramParser.this.addInstruction(insn);
            }
         }
      }

      public void visitIincInsn(int var, int increment) {
         var = ProgramParser.this.minLocal + ProgramParser.this.mapLocal(var);
         int tmp = ProgramParser.this.pushSingle();
         ProgramParser.this.popSingle();
         IntegerConstantInstruction intInsn = new IntegerConstantInstruction();
         intInsn.setConstant(increment);
         intInsn.setReceiver(ProgramParser.this.getVariable(tmp));
         ProgramParser.this.addInstruction(intInsn);
         this.emitBinary(BinaryOperation.ADD, NumericOperandType.INT, var, tmp, var);
      }

      public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
      }

      public void visitFieldInsn(int opcode, String owner, String name, String desc) {
         String ownerCls = owner.replace('/', '.');
         switch (opcode) {
            case 178:
               ValueType primitiveClassLiteral = ProgramParser.getPrimitiveTypeField(owner + "." + name);
               if (primitiveClassLiteral != null) {
                  ClassConstantInstruction insnx = new ClassConstantInstruction();
                  insnx.setConstant(primitiveClassLiteral);
                  insnx.setReceiver(ProgramParser.this.getVariable(ProgramParser.this.pushSingle()));
                  ProgramParser.this.addInstruction(insnx);
               } else {
                  ValueType type = ProgramParser.this.referenceCache.parseValueTypeCached(desc);
                  int valuex = !desc.equals("D") && !desc.equals("J") ? ProgramParser.this.pushSingle() : ProgramParser.this.pushDouble();
                  GetFieldInstruction insnx = new GetFieldInstruction();
                  insnx.setField(ProgramParser.this.referenceCache.getCached(new FieldReference(ownerCls, name)));
                  insnx.setFieldType(type);
                  insnx.setReceiver(ProgramParser.this.getVariable(valuex));
                  ProgramParser.this.addInstruction(insnx);
               }
               break;
            case 179: {
               int value = !desc.equals("D") && !desc.equals("J") ? ProgramParser.this.popSingle() : ProgramParser.this.popDouble();
               PutFieldInstruction insn = new PutFieldInstruction();
               insn.setField(ProgramParser.this.referenceCache.getCached(new FieldReference(ownerCls, name)));
               insn.setValue(ProgramParser.this.getVariable(value));
               insn.setFieldType(ProgramParser.this.referenceCache.parseValueTypeCached(desc));
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 180: {
               int instance = ProgramParser.this.popSingle();
               ValueType type = ProgramParser.this.referenceCache.parseValueTypeCached(desc);
               int value = !desc.equals("D") && !desc.equals("J") ? ProgramParser.this.pushSingle() : ProgramParser.this.pushDouble();
               GetFieldInstruction insn = new GetFieldInstruction();
               insn.setInstance(ProgramParser.this.getVariable(instance));
               insn.setField(ProgramParser.this.referenceCache.getCached(new FieldReference(ownerCls, name)));
               insn.setFieldType(type);
               insn.setReceiver(ProgramParser.this.getVariable(value));
               ProgramParser.this.addInstruction(insn);
               break;
            }
            case 181: {
               int value = !desc.equals("D") && !desc.equals("J") ? ProgramParser.this.popSingle() : ProgramParser.this.popDouble();
               int instance = ProgramParser.this.popSingle();
               PutFieldInstruction insn = new PutFieldInstruction();
               insn.setInstance(ProgramParser.this.getVariable(instance));
               insn.setField(ProgramParser.this.referenceCache.getCached(new FieldReference(ownerCls, name)));
               insn.setValue(ProgramParser.this.getVariable(value));
               insn.setFieldType(ProgramParser.this.referenceCache.parseValueTypeCached(desc));
               ProgramParser.this.addInstruction(insn);
            }
         }
      }

      public void visitEnd() {
      }

      public void visitCode() {
      }

      public void visitAttribute(Attribute attr) {
      }

      public AnnotationVisitor visitAnnotationDefault() {
         return null;
      }

      public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
         return null;
      }
   };

   public ProgramParser(ReferenceCache methodReferenceCache) {
      this.referenceCache = methodReferenceCache;
   }

   public String getFileName() {
      return this.fileName;
   }

   public void setFileName(String fileName) {
      this.fileName = fileName;
   }

   public Program parse(MethodNode method) {
      this.program = new Program();
      InsnList instructions = method.instructions;
      if (instructions.size() == 0) {
         return this.program;
      }

      this.prepare(method);
      this.program.createBasicBlock();
      this.getBasicBlock(0);
      JumpInstruction insn = new JumpInstruction();
      insn.setTarget(this.program.basicBlockAt(1));
      this.program.basicBlockAt(0).add(insn);
      this.doAnalyze(method);
      this.assemble(method);

      for (int i = 0; i < this.program.basicBlockCount(); i++) {
         BasicBlock block = this.program.basicBlockAt(i);

         for (int j = 0; j < block.getTryCatchBlocks().size(); j++) {
            TryCatchBlock tryCatch = block.getTryCatchBlocks().get(j);
            if (tryCatch.getHandler() == block) {
               block.getTryCatchBlocks().remove(j--);
            }
         }
      }

      int signatureVars = this.countSignatureVariables(method.desc);

      while (this.program.variableCount() <= signatureVars) {
         this.program.createVariable();
      }

      return this.program;
   }

   private int countSignatureVariables(String desc) {
      int count = 1;

      for (Type paramType : Type.getArgumentTypes(desc)) {
         count += paramType.getSize();
      }

      return count;
   }

   private int pushSingle() {
      this.stack = new ProgramParser.StackFrame(this.stack, (byte)1);
      return this.stack.depth;
   }

   private int pushDouble() {
      this.stack = new ProgramParser.StackFrame(this.stack, (byte)2);
      this.stack = new ProgramParser.StackFrame(this.stack, (byte)3);
      return this.stack.next.depth;
   }

   private int popSingle() {
      if (this.stack != null && this.stack.type == 1) {
         int depth = this.stack.depth;
         this.stack = this.stack.next;
         return depth;
      } else {
         throw new AssertionError("Illegal stack state at " + this.index);
      }
   }

   private int popDouble() {
      if (this.stack != null && this.stack.type == 3) {
         this.stack = this.stack.next;
         if (this.stack != null && this.stack.type == 2) {
            int depth = this.stack.depth;
            this.stack = this.stack.next;
            return depth;
         } else {
            throw new AssertionError("***Illegal stack state at " + this.index);
         }
      } else {
         throw new AssertionError("***Illegal stack state at " + this.index);
      }
   }

   public Map<Integer, String> getDebugNames(Instruction insn) {
      Map<Integer, String> map = this.variableDebugNames.get(insn);
      return map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap();
   }

   private void prepare(MethodNode method) {
      InsnList instructions = method.instructions;
      this.minLocal = 0;
      if ((method.access & 8) != 0) {
         this.minLocal = 1;
      }

      this.labelIndexes = new HashMap<>();
      this.lineNumbers = new HashMap<>();

      for (int i = 0; i < instructions.size(); i++) {
         AbstractInsnNode node = instructions.get(i);
         if (node instanceof LabelNode) {
            this.labelIndexes.put(((LabelNode)node).getLabel(), i);
         }

         if (node instanceof LineNumberNode lineNumberNode) {
            this.lineNumbers.put(lineNumberNode.start.getLabel(), lineNumberNode.line);
         }
      }

      for (LocalVariableNode localVar : method.localVariables) {
         int location = this.labelIndexes.get(localVar.start.getLabel());
         this.localVariableMap.computeIfAbsent(location, k -> new ArrayList<>()).add(localVar);
      }

      this.targetInstructions = new ArrayList<>(instructions.size());
      this.targetInstructions.addAll(Collections.nCopies(instructions.size(), null));
      this.basicBlocks.addAll(Collections.nCopies(instructions.size(), null));
      this.stackBefore = new ProgramParser.StackFrame[instructions.size()];
      this.stackAfter = new ProgramParser.StackFrame[instructions.size()];
   }

   private void doAnalyze(MethodNode method) {
      InsnList instructions = method.instructions;
      Deque<ProgramParser.Step> workStack = new ArrayDeque<>();

      for (Object obj : method.tryCatchBlocks) {
         TryCatchBlockNode tryCatchNode = (TryCatchBlockNode)obj;
         if (tryCatchNode.start != tryCatchNode.handler) {
            workStack.push(new ProgramParser.Step(-2, this.labelIndexes.get(tryCatchNode.handler.getLabel())));
         }
      }

      workStack.push(new ProgramParser.Step(-1, 0));

      while (!workStack.isEmpty()) {
         ProgramParser.Step step = workStack.pop();
         this.index = step.target;
         if (this.stackBefore[this.index] == null) {
            switch (step.source) {
               case -2:
                  this.stack = new ProgramParser.StackFrame(this.minLocal + method.maxLocals - 1);
                  this.pushSingle();
                  break;
               case -1:
                  this.stack = new ProgramParser.StackFrame(this.minLocal + method.maxLocals - 1);
                  break;
               default:
                  this.stack = this.stackAfter[step.source];
            }

            this.stackBefore[this.index] = this.stack;
            this.nextIndexes = new int[]{this.index + 1};
            instructions.get(this.index).accept(this.methodVisitor);
            this.stackAfter[this.index] = this.stack;
            this.flushInstructions();
            if (this.nextIndexes.length > 1) {
               this.emitNextBasicBlock();
            }

            for (int next : this.nextIndexes) {
               workStack.push(new ProgramParser.Step(this.index, next));
            }
         }
      }

      for (Object obj : method.tryCatchBlocks) {
         TryCatchBlockNode tryCatchNode = (TryCatchBlockNode)obj;
         if (tryCatchNode.start != tryCatchNode.handler) {
            int start = this.labelIndexes.get(tryCatchNode.start.getLabel());
            int end = this.labelIndexes.get(tryCatchNode.end.getLabel());
            this.getBasicBlock(start);
            this.getBasicBlock(end);

            for (int i = start; i < end; i++) {
               BasicBlock block = this.basicBlocks.get(i);
               if (block != null) {
                  TryCatchBlock tryCatch = new TryCatchBlock();
                  if (tryCatchNode.type != null) {
                     tryCatch.setExceptionType(this.referenceCache.getCached(tryCatchNode.type.replace('/', '.')));
                  }

                  tryCatch.setHandler(this.getBasicBlock(this.labelIndexes.get(tryCatchNode.handler.getLabel())));
                  tryCatch.getHandler().setExceptionVariable(this.program.variableAt(this.minLocal + method.maxLocals));
                  block.getTryCatchBlocks().add(tryCatch);
               }
            }
         }
      }
   }

   private void assemble(MethodNode methodNode) {
      BasicBlock basicBlock = null;
      Map<Integer, String> accumulatedDebugNames = new HashMap<>();
      Integer lastLineNumber = null;
      TextLocation lastLocation = TextLocation.EMPTY;

      for (int i = 0; i < this.basicBlocks.size(); i++) {
         BasicBlock newBasicBlock = this.basicBlocks.get(i);
         if (newBasicBlock != null) {
            if (basicBlock != null && !this.hasProperLastInstruction(basicBlock)) {
               JumpInstruction insn = new JumpInstruction();
               insn.setTarget(newBasicBlock);
               basicBlock.add(insn);
            }

            basicBlock = newBasicBlock;
            if (basicBlock.instructionCount() > 0) {
               Map<Integer, String> debugNames = new HashMap<>(accumulatedDebugNames);
               this.variableDebugNames.put(basicBlock.getFirstInstruction(), debugNames);
            }
         }

         List<Instruction> builtInstructions = this.targetInstructions.get(i);
         List<LocalVariableNode> localVarNodes = this.localVariableMap.get(i);
         if (localVarNodes != null) {
            if (builtInstructions == null || builtInstructions.isEmpty()) {
               builtInstructions = Arrays.asList(new EmptyInstruction());
            }

            Map<Integer, String> debugNames = new HashMap<>();
            this.variableDebugNames.put(builtInstructions.get(0), debugNames);

            for (LocalVariableNode localVar : localVarNodes) {
               debugNames.put(localVar.index + this.minLocal, this.referenceCache.getCached(localVar.name));
            }

            accumulatedDebugNames.putAll(debugNames);
         }

         AbstractInsnNode insnNode = methodNode.instructions.get(i);
         if (insnNode instanceof LabelNode) {
            Label label = ((LabelNode)insnNode).getLabel();
            Integer lineNumber = this.lineNumbers.get(label);
            if (lineNumber != null && !lineNumber.equals(lastLineNumber)) {
               lastLineNumber = lineNumber;
               lastLocation = new TextLocation(this.fileName, lastLineNumber);
            }
         }

         if (builtInstructions != null) {
            for (Instruction insn : builtInstructions) {
               insn.setLocation(lastLocation);
            }

            basicBlock.addAll(builtInstructions);
         }
      }
   }

   private boolean hasProperLastInstruction(BasicBlock basicBlock) {
      Instruction lastInsn = basicBlock.getLastInstruction();
      if (lastInsn == null) {
         return false;
      }

      TransitionExtractor extractor = new TransitionExtractor();
      lastInsn.acceptVisitor(extractor);
      return extractor.getTargets() != null;
   }

   private void flushInstructions() {
      this.targetInstructions.set(this.index, this.builder);
      this.builder = new ArrayList<>();
   }

   private BasicBlock getBasicBlock(int index) {
      BasicBlock block = this.basicBlocks.get(index);
      if (block == null) {
         block = this.program.createBasicBlock();
         this.basicBlocks.set(index, block);
      }

      return block;
   }

   private void emitNextBasicBlock() {
      if (this.index + 1 < this.basicBlocks.size()) {
         this.getBasicBlock(this.index + 1);
      }
   }

   private Variable getVariable(int index) {
      while (index >= this.program.variableCount()) {
         this.program.createVariable();
      }

      return this.program.variableAt(index);
   }

   private void emitAssignInsn(int source, int target) {
      AssignInstruction insn = new AssignInstruction();
      insn.setAssignee(this.getVariable(source));
      insn.setReceiver(this.getVariable(target));
      this.addInstruction(insn);
   }

   private void addInstruction(Instruction insn) {
      this.builder.add(insn);
   }

   private int mapLocal(int local) {
      return local;
   }

   private MethodHandle parseHandle(Handle handle) {
      String owner = this.referenceCache.getCached(handle.getOwner().replace('/', '.'));
      String name = this.referenceCache.getCached(handle.getName());
      switch (handle.getTag()) {
         case 1:
            return MethodHandle.fieldGetter(owner, name, this.referenceCache.getCached(ValueType.parse(handle.getDesc())));
         case 2:
            return MethodHandle.staticFieldGetter(owner, name, this.referenceCache.getCached(ValueType.parse(handle.getDesc())));
         case 3:
            return MethodHandle.fieldSetter(owner, name, this.referenceCache.getCached(ValueType.parse(handle.getDesc())));
         case 4:
            return MethodHandle.staticFieldSetter(owner, name, this.referenceCache.getCached(ValueType.parse(handle.getDesc())));
         case 5:
            return MethodHandle.virtualCaller(owner, name, this.parseSignature(handle.getDesc()));
         case 6:
            return MethodHandle.staticCaller(owner, name, this.parseSignature(handle.getDesc()));
         case 7:
            return MethodHandle.specialCaller(owner, name, this.parseSignature(handle.getDesc()));
         case 8:
            return MethodHandle.constructorCaller(owner, name, this.parseSignature(handle.getDesc()));
         case 9:
            return MethodHandle.interfaceCaller(owner, name, this.parseSignature(handle.getDesc()));
         default:
            throw new IllegalArgumentException("Unknown handle tag: " + handle.getTag());
      }
   }

   private ValueType[] parseSignature(String desc) {
      ValueType[] signature = MethodDescriptor.parseSignature(desc);

      for (int i = 0; i < signature.length; i++) {
         signature[i] = this.referenceCache.getCached(signature[i]);
      }

      return signature;
   }

   private static ValueType getPrimitiveTypeField(String fieldName) {
      switch (fieldName) {
         case "java/lang/Boolean.TYPE":
            return ValueType.BOOLEAN;
         case "java/lang/Byte.TYPE":
            return ValueType.BYTE;
         case "java/lang/Short.TYPE":
            return ValueType.SHORT;
         case "java/lang/Character.TYPE":
            return ValueType.CHARACTER;
         case "java/lang/Integer.TYPE":
            return ValueType.INTEGER;
         case "java/lang/Long.TYPE":
            return ValueType.LONG;
         case "java/lang/Float.TYPE":
            return ValueType.FLOAT;
         case "java/lang/Double.TYPE":
            return ValueType.DOUBLE;
         case "java/lang/Void.TYPE":
            return ValueType.VOID;
         default:
            return null;
      }
   }

   private static class StackFrame {
      final ProgramParser.StackFrame next;
      final byte type;
      final int depth;

      StackFrame(int depth) {
         this.next = null;
         this.type = 0;
         this.depth = depth;
      }

      StackFrame(ProgramParser.StackFrame next, byte type) {
         this.next = next;
         this.type = type;
         this.depth = next != null ? next.depth + 1 : 0;
      }
   }

   private static class Step {
      public final int source;
      public final int target;

      public Step(int source, int target) {
         this.source = source;
         this.target = target;
      }
   }
}
