package org.teavm.model.instructions;

import java.util.List;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHandle;
import org.teavm.model.MethodReference;
import org.teavm.model.RuntimeConstant;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;

public interface InstructionReader {
   void location(TextLocation var1);

   void nop();

   void classConstant(VariableReader var1, ValueType var2);

   void nullConstant(VariableReader var1);

   void integerConstant(VariableReader var1, int var2);

   void longConstant(VariableReader var1, long var2);

   void floatConstant(VariableReader var1, float var2);

   void doubleConstant(VariableReader var1, double var2);

   void stringConstant(VariableReader var1, String var2);

   void binary(BinaryOperation var1, VariableReader var2, VariableReader var3, VariableReader var4, NumericOperandType var5);

   void negate(VariableReader var1, VariableReader var2, NumericOperandType var3);

   void assign(VariableReader var1, VariableReader var2);

   void cast(VariableReader var1, VariableReader var2, ValueType var3, boolean var4);

   void cast(VariableReader var1, VariableReader var2, NumericOperandType var3, NumericOperandType var4);

   void cast(VariableReader var1, VariableReader var2, IntegerSubtype var3, CastIntegerDirection var4);

   void jumpIf(BranchingCondition var1, VariableReader var2, BasicBlockReader var3, BasicBlockReader var4);

   void jumpIf(BinaryBranchingCondition var1, VariableReader var2, VariableReader var3, BasicBlockReader var4, BasicBlockReader var5);

   void jump(BasicBlockReader var1);

   void choose(VariableReader var1, List<? extends SwitchTableEntryReader> var2, BasicBlockReader var3);

   void exit(VariableReader var1);

   void raise(VariableReader var1);

   void createArray(VariableReader var1, ValueType var2, VariableReader var3);

   void createArray(VariableReader var1, ValueType var2, List<? extends VariableReader> var3);

   void create(VariableReader var1, String var2);

   void getField(VariableReader var1, VariableReader var2, FieldReference var3, ValueType var4);

   void putField(VariableReader var1, FieldReference var2, VariableReader var3, ValueType var4);

   void arrayLength(VariableReader var1, VariableReader var2);

   void cloneArray(VariableReader var1, VariableReader var2);

   void unwrapArray(VariableReader var1, VariableReader var2, ArrayElementType var3);

   void getElement(VariableReader var1, VariableReader var2, VariableReader var3, ArrayElementType var4);

   void putElement(VariableReader var1, VariableReader var2, VariableReader var3, ArrayElementType var4);

   void invoke(VariableReader var1, VariableReader var2, MethodReference var3, List<? extends VariableReader> var4, InvocationType var5);

   void invokeDynamic(
      VariableReader var1, VariableReader var2, MethodDescriptor var3, List<? extends VariableReader> var4, MethodHandle var5, List<RuntimeConstant> var6
   );

   void isInstance(VariableReader var1, VariableReader var2, ValueType var3);

   void initClass(String var1);

   void nullCheck(VariableReader var1, VariableReader var2);

   void monitorEnter(VariableReader var1);

   void monitorExit(VariableReader var1);

   void boundCheck(VariableReader var1, VariableReader var2, VariableReader var3, boolean var4);
}
