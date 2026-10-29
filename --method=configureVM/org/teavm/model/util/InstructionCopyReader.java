package org.teavm.model.util;

import java.util.List;
import java.util.stream.Collectors;
import org.teavm.model.BasicBlock;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.FieldReference;
import org.teavm.model.Instruction;
import org.teavm.model.InvokeDynamicInstruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHandle;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.RuntimeConstant;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BoundCheckInstruction;
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
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InstructionReader;
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
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.PutElementInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.instructions.SwitchInstruction;
import org.teavm.model.instructions.SwitchTableEntry;
import org.teavm.model.instructions.SwitchTableEntryReader;
import org.teavm.model.instructions.UnwrapArrayInstruction;

public class InstructionCopyReader implements InstructionReader {
   private Instruction copy;
   private Program programCopy;
   private TextLocation location;

   public InstructionCopyReader(Program programCopy) {
      this.programCopy = programCopy;
   }

   public Instruction getCopy() {
      return this.copy;
   }

   public void resetLocation() {
      this.location = null;
   }

   @Override
   public void location(TextLocation location) {
      this.location = location;
   }

   private Variable copyVar(VariableReader var) {
      return this.programCopy.variableAt(var.getIndex());
   }

   private BasicBlock copyBlock(BasicBlockReader block) {
      return this.programCopy.basicBlockAt(block.getIndex());
   }

   @Override
   public void nop() {
      this.copy = new EmptyInstruction();
      this.copy.setLocation(this.location);
   }

   @Override
   public void classConstant(VariableReader receiver, ValueType cst) {
      ClassConstantInstruction insnCopy = new ClassConstantInstruction();
      insnCopy.setConstant(cst);
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void nullConstant(VariableReader receiver) {
      NullConstantInstruction insnCopy = new NullConstantInstruction();
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void integerConstant(VariableReader receiver, int cst) {
      IntegerConstantInstruction insnCopy = new IntegerConstantInstruction();
      insnCopy.setConstant(cst);
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void longConstant(VariableReader receiver, long cst) {
      LongConstantInstruction insnCopy = new LongConstantInstruction();
      insnCopy.setConstant(cst);
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void floatConstant(VariableReader receiver, float cst) {
      FloatConstantInstruction insnCopy = new FloatConstantInstruction();
      insnCopy.setConstant(cst);
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void doubleConstant(VariableReader receiver, double cst) {
      DoubleConstantInstruction insnCopy = new DoubleConstantInstruction();
      insnCopy.setConstant(cst);
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void stringConstant(VariableReader receiver, String cst) {
      StringConstantInstruction insnCopy = new StringConstantInstruction();
      insnCopy.setConstant(cst);
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void binary(BinaryOperation op, VariableReader receiver, VariableReader first, VariableReader second, NumericOperandType type) {
      BinaryInstruction insnCopy = new BinaryInstruction(op, type);
      insnCopy.setFirstOperand(this.copyVar(first));
      insnCopy.setSecondOperand(this.copyVar(second));
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void negate(VariableReader receiver, VariableReader operand, NumericOperandType type) {
      NegateInstruction insnCopy = new NegateInstruction(type);
      insnCopy.setOperand(this.copyVar(operand));
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void assign(VariableReader receiver, VariableReader assignee) {
      AssignInstruction insnCopy = new AssignInstruction();
      insnCopy.setAssignee(this.copyVar(assignee));
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void cast(VariableReader receiver, VariableReader value, ValueType targetType, boolean weak) {
      CastInstruction insnCopy = new CastInstruction();
      insnCopy.setValue(this.copyVar(value));
      insnCopy.setReceiver(this.copyVar(receiver));
      insnCopy.setTargetType(targetType);
      insnCopy.setWeak(weak);
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void cast(VariableReader receiver, VariableReader value, NumericOperandType sourceType, NumericOperandType targetType) {
      CastNumberInstruction insnCopy = new CastNumberInstruction(sourceType, targetType);
      insnCopy.setValue(this.copyVar(value));
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void cast(VariableReader receiver, VariableReader value, IntegerSubtype type, CastIntegerDirection dir) {
      CastIntegerInstruction insnCopy = new CastIntegerInstruction(type, dir);
      insnCopy.setValue(this.copyVar(value));
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void jumpIf(BranchingCondition cond, VariableReader operand, BasicBlockReader consequent, BasicBlockReader alternative) {
      BranchingInstruction insnCopy = new BranchingInstruction(cond);
      insnCopy.setOperand(this.copyVar(operand));
      insnCopy.setConsequent(this.copyBlock(consequent));
      insnCopy.setAlternative(this.copyBlock(alternative));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void jumpIf(BinaryBranchingCondition cond, VariableReader first, VariableReader second, BasicBlockReader consequent, BasicBlockReader alternative) {
      BinaryBranchingInstruction insnCopy = new BinaryBranchingInstruction(cond);
      insnCopy.setFirstOperand(this.copyVar(first));
      insnCopy.setSecondOperand(this.copyVar(second));
      insnCopy.setConsequent(this.copyBlock(consequent));
      insnCopy.setAlternative(this.copyBlock(alternative));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void jump(BasicBlockReader target) {
      JumpInstruction insnCopy = new JumpInstruction();
      insnCopy.setTarget(this.copyBlock(target));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void choose(VariableReader condition, List<? extends SwitchTableEntryReader> table, BasicBlockReader defaultTarget) {
      SwitchInstruction insnCopy = new SwitchInstruction();
      insnCopy.setCondition(this.copyVar(condition));
      insnCopy.setDefaultTarget(this.copyBlock(defaultTarget));

      for (SwitchTableEntryReader entry : table) {
         SwitchTableEntry entryCopy = new SwitchTableEntry();
         entryCopy.setCondition(entry.getCondition());
         entryCopy.setTarget(this.copyBlock(entry.getTarget()));
         insnCopy.getEntries().add(entryCopy);
      }

      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void exit(VariableReader valueToReturn) {
      ExitInstruction insnCopy = new ExitInstruction();
      insnCopy.setValueToReturn(valueToReturn != null ? this.copyVar(valueToReturn) : null);
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void raise(VariableReader exception) {
      RaiseInstruction insnCopy = new RaiseInstruction();
      insnCopy.setException(this.copyVar(exception));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
      ConstructArrayInstruction insnCopy = new ConstructArrayInstruction();
      insnCopy.setItemType(itemType);
      insnCopy.setSize(this.copyVar(size));
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void createArray(VariableReader receiver, ValueType itemType, List<? extends VariableReader> dimensions) {
      ConstructMultiArrayInstruction insnCopy = new ConstructMultiArrayInstruction();
      insnCopy.setItemType(itemType);
      insnCopy.setReceiver(this.copyVar(receiver));

      for (VariableReader dim : dimensions) {
         insnCopy.getDimensions().add(this.copyVar(dim));
      }

      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void create(VariableReader receiver, String type) {
      ConstructInstruction insnCopy = new ConstructInstruction();
      insnCopy.setType(type);
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void getField(VariableReader receiver, VariableReader instance, FieldReference field, ValueType fieldType) {
      GetFieldInstruction insnCopy = new GetFieldInstruction();
      insnCopy.setField(field);
      insnCopy.setFieldType(fieldType);
      insnCopy.setInstance(instance != null ? this.copyVar(instance) : null);
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void putField(VariableReader instance, FieldReference field, VariableReader value, ValueType fieldType) {
      PutFieldInstruction insnCopy = new PutFieldInstruction();
      insnCopy.setField(field);
      insnCopy.setInstance(instance != null ? this.copyVar(instance) : null);
      insnCopy.setValue(this.copyVar(value));
      insnCopy.setFieldType(fieldType);
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void arrayLength(VariableReader receiver, VariableReader array) {
      ArrayLengthInstruction insnCopy = new ArrayLengthInstruction();
      insnCopy.setArray(this.copyVar(array));
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void cloneArray(VariableReader receiver, VariableReader array) {
      CloneArrayInstruction insnCopy = new CloneArrayInstruction();
      insnCopy.setArray(this.copyVar(array));
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void unwrapArray(VariableReader receiver, VariableReader array, ArrayElementType elementType) {
      UnwrapArrayInstruction insnCopy = new UnwrapArrayInstruction(elementType);
      insnCopy.setArray(this.copyVar(array));
      insnCopy.setReceiver(this.copyVar(receiver));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void getElement(VariableReader receiver, VariableReader array, VariableReader index, ArrayElementType type) {
      GetElementInstruction insnCopy = new GetElementInstruction(type);
      insnCopy.setArray(this.copyVar(array));
      insnCopy.setReceiver(this.copyVar(receiver));
      insnCopy.setIndex(this.copyVar(index));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void putElement(VariableReader array, VariableReader index, VariableReader value, ArrayElementType type) {
      PutElementInstruction insnCopy = new PutElementInstruction(type);
      insnCopy.setArray(this.copyVar(array));
      insnCopy.setValue(this.copyVar(value));
      insnCopy.setIndex(this.copyVar(index));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void invoke(VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments, InvocationType type) {
      InvokeInstruction insnCopy = new InvokeInstruction();
      insnCopy.setMethod(method);
      insnCopy.setType(type);
      insnCopy.setInstance(instance != null ? this.copyVar(instance) : null);
      insnCopy.setReceiver(receiver != null ? this.copyVar(receiver) : null);
      Variable[] argsCopy = new Variable[arguments.size()];

      for (int i = 0; i < argsCopy.length; i++) {
         argsCopy[i] = this.copyVar(arguments.get(i));
      }

      insnCopy.setArguments(argsCopy);
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void invokeDynamic(
      VariableReader receiver,
      VariableReader instance,
      MethodDescriptor method,
      List<? extends VariableReader> arguments,
      MethodHandle bootstrapMethod,
      List<RuntimeConstant> bootstrapArguments
   ) {
      InvokeDynamicInstruction insnCopy = new InvokeDynamicInstruction();
      insnCopy.setMethod(method);
      insnCopy.setBootstrapMethod(bootstrapMethod);
      insnCopy.getBootstrapArguments().addAll(bootstrapArguments);
      if (instance != null) {
         insnCopy.setInstance(this.copyVar(instance));
      }

      insnCopy.getArguments().addAll(arguments.stream().map(this::copyVar).collect(Collectors.toList()));
      insnCopy.setReceiver(receiver != null ? this.copyVar(receiver) : null);
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void isInstance(VariableReader receiver, VariableReader value, ValueType type) {
      IsInstanceInstruction insnCopy = new IsInstanceInstruction();
      insnCopy.setValue(this.copyVar(value));
      insnCopy.setReceiver(this.copyVar(receiver));
      insnCopy.setType(type);
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void initClass(String className) {
      InitClassInstruction insnCopy = new InitClassInstruction();
      insnCopy.setClassName(className);
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void nullCheck(VariableReader receiver, VariableReader value) {
      NullCheckInstruction insnCopy = new NullCheckInstruction();
      insnCopy.setReceiver(this.copyVar(receiver));
      insnCopy.setValue(this.copyVar(value));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void monitorEnter(VariableReader objectRef) {
      MonitorEnterInstruction insnCopy = new MonitorEnterInstruction();
      insnCopy.setObjectRef(this.copyVar(objectRef));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void monitorExit(VariableReader objectRef) {
      MonitorExitInstruction insnCopy = new MonitorExitInstruction();
      insnCopy.setObjectRef(this.copyVar(objectRef));
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }

   @Override
   public void boundCheck(VariableReader receiver, VariableReader index, VariableReader array, boolean lower) {
      BoundCheckInstruction insnCopy = new BoundCheckInstruction();
      insnCopy.setReceiver(this.copyVar(receiver));
      insnCopy.setIndex(this.copyVar(index));
      if (array != null) {
         insnCopy.setArray(this.copyVar(array));
      }

      insnCopy.setLower(lower);
      this.copy = insnCopy;
      this.copy.setLocation(this.location);
   }
}
