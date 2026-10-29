package org.teavm.model.emit;

import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReader;
import org.teavm.model.FieldReference;
import org.teavm.model.Incoming;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.PrimitiveType;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.ArrayLengthInstruction;
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
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.IntegerSubtype;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.NegateInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.PutElementInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;

public class ValueEmitter {
   ProgramEmitter pe;
   BasicBlock block;
   Variable variable;
   ValueType type;

   ValueEmitter(ProgramEmitter programEmitter, BasicBlock block, Variable variable, ValueType type) {
      this.pe = programEmitter;
      this.block = block;
      this.variable = variable;
      this.type = type;
   }

   public ProgramEmitter getProgramEmitter() {
      return this.pe;
   }

   public BasicBlock getBlock() {
      return this.block;
   }

   public Variable getVariable() {
      return this.variable;
   }

   public ValueType getType() {
      return this.type;
   }

   public ValueEmitter getField(String name, ValueType type) {
      if (!(this.type instanceof ValueType.Object)) {
         throw new EmitException("Can't get field of non-object type: " + type);
      }

      String className = ((ValueType.Object)this.type).getClassName();
      Variable var = this.pe.getProgram().createVariable();
      GetFieldInstruction insn = new GetFieldInstruction();
      insn.setField(new FieldReference(className, name));
      insn.setFieldType(type);
      insn.setReceiver(var);
      insn.setInstance(this.variable);
      this.pe.addInstruction(insn);
      return this.pe.var(var, type);
   }

   public ValueEmitter getField(String name, Class<?> type) {
      return this.getField(name, ValueType.parse(type));
   }

   public ProgramEmitter setField(String name, ValueEmitter value) {
      if (!(this.type instanceof ValueType.Object)) {
         throw new EmitException("Can't get field of non-object type: " + this.type);
      }

      String className = ((ValueType.Object)this.type).getClassName();
      PutFieldInstruction insn = new PutFieldInstruction();
      insn.setField(new FieldReference(className, name));
      insn.setFieldType(value.type);
      insn.setInstance(this.variable);
      insn.setValue(value.getVariable());
      this.pe.addInstruction(insn);
      return this.pe;
   }

   public ValueEmitter neg() {
      if (!(this.type instanceof ValueType.Primitive)) {
         throw new EmitException("Can't negate non-primitive: " + this.type);
      }

      ValueEmitter value = this;
      PrimitiveType type = ((ValueType.Primitive)this.type).getKind();
      IntegerSubtype subtype = this.convertToIntegerSubtype(type);
      if (subtype != null) {
         value = value.castToInteger(subtype);
         type = PrimitiveType.INTEGER;
      }

      ValueEmitter result = this.pe.newVar(ValueType.primitive(type));
      NegateInstruction insn = new NegateInstruction(this.convertToNumeric(type));
      insn.setOperand(value.variable);
      insn.setReceiver(result.variable);
      this.pe.addInstruction(insn);
      return result;
   }

   private ValueEmitter.Pair commonNumeric(ValueEmitter other) {
      if (!(this.type instanceof ValueType.Primitive)) {
         throw new EmitException("First argument is not a primitive: " + this.type);
      }

      if (!(other.type instanceof ValueType.Primitive)) {
         throw new EmitException("First argument is not a primitive: " + other.type);
      }

      PrimitiveType firstType = ((ValueType.Primitive)this.type).getKind();
      PrimitiveType secondType = ((ValueType.Primitive)other.type).getKind();
      if (firstType == PrimitiveType.BOOLEAN) {
         throw new EmitException("First argument is not numeric: " + this.type);
      }

      if (secondType == PrimitiveType.BOOLEAN) {
         throw new EmitException("Second argument is not numeric: " + other.type);
      }

      ValueEmitter a = this;
      ValueEmitter b = other;
      IntegerSubtype firstSubtype = this.convertToIntegerSubtype(firstType);
      if (firstSubtype != null) {
         a = this.castFromInteger(firstSubtype);
         firstType = PrimitiveType.INTEGER;
      }

      IntegerSubtype secondSubtype = this.convertToIntegerSubtype(secondType);
      if (secondSubtype != null) {
         b = this.castFromInteger(secondSubtype);
         secondType = PrimitiveType.INTEGER;
      }

      NumericOperandType firstNumeric = this.convertToNumeric(firstType);
      NumericOperandType secondNumeric = this.convertToNumeric(secondType);
      int commonIndex = Math.max(firstNumeric.ordinal(), secondNumeric.ordinal());
      NumericOperandType common = NumericOperandType.values()[commonIndex];
      ValueType commonType = ValueType.primitive(this.convertNumeric(common));
      if (firstNumeric != common) {
         CastNumberInstruction insn = new CastNumberInstruction(firstNumeric, common);
         insn.setValue(a.getVariable());
         a = this.pe.newVar(commonType);
         insn.setReceiver(a.getVariable());
         this.pe.addInstruction(insn);
      }

      if (secondNumeric != common) {
         CastNumberInstruction insn = new CastNumberInstruction(secondNumeric, common);
         insn.setValue(b.getVariable());
         b = this.pe.newVar(commonType);
         insn.setReceiver(b.getVariable());
         this.pe.addInstruction(insn);
      }

      return new ValueEmitter.Pair(a, b);
   }

   private ValueEmitter binary(BinaryOperation op, ValueEmitter other) {
      ValueEmitter.Pair pair = this.commonNumeric(other);
      return this.binaryOp(op, pair.first, pair.second, pair.first.type);
   }

   private ValueEmitter binaryOp(BinaryOperation op, ValueEmitter a, ValueEmitter b, ValueType type) {
      Variable var = this.pe.getProgram().createVariable();
      PrimitiveType common = ((ValueType.Primitive)a.type).getKind();
      BinaryInstruction insn = new BinaryInstruction(op, this.convertToNumeric(common));
      insn.setFirstOperand(a.getVariable());
      insn.setSecondOperand(b.getVariable());
      insn.setReceiver(var);
      this.pe.addInstruction(insn);
      return this.pe.var(var, type);
   }

   private IntegerSubtype convertToIntegerSubtype(PrimitiveType type) {
      switch (type) {
         case BYTE:
            return IntegerSubtype.BYTE;
         case SHORT:
            return IntegerSubtype.SHORT;
         case CHARACTER:
            return IntegerSubtype.CHAR;
         default:
            return null;
      }
   }

   private NumericOperandType convertToNumeric(PrimitiveType type) {
      switch (type) {
         case BYTE:
         case SHORT:
         case CHARACTER:
         case INTEGER:
            return NumericOperandType.INT;
         case LONG:
            return NumericOperandType.LONG;
         case FLOAT:
            return NumericOperandType.FLOAT;
         case DOUBLE:
            return NumericOperandType.DOUBLE;
         default:
            throw new AssertionError("Unexpected type: " + type);
      }
   }

   private PrimitiveType convertNumeric(NumericOperandType type) {
      switch (type) {
         case INT:
            return PrimitiveType.INTEGER;
         case LONG:
            return PrimitiveType.LONG;
         case FLOAT:
            return PrimitiveType.FLOAT;
         case DOUBLE:
            return PrimitiveType.DOUBLE;
         default:
            throw new AssertionError("Unknown type: " + type);
      }
   }

   public ValueEmitter add(ValueEmitter other) {
      return this.binary(BinaryOperation.ADD, other);
   }

   public ValueEmitter add(int value) {
      return this.binary(BinaryOperation.ADD, this.pe.constant(value));
   }

   public ValueEmitter sub(ValueEmitter other) {
      return this.binary(BinaryOperation.SUBTRACT, other);
   }

   public ValueEmitter sub(int value) {
      return this.binary(BinaryOperation.SUBTRACT, this.pe.constant(value));
   }

   public ValueEmitter mul(ValueEmitter other) {
      return this.binary(BinaryOperation.MULTIPLY, other);
   }

   public ValueEmitter mul(int value) {
      return this.binary(BinaryOperation.MULTIPLY, this.pe.constant(value));
   }

   public ValueEmitter div(ValueEmitter other) {
      return this.binary(BinaryOperation.DIVIDE, other);
   }

   public ValueEmitter div(int value) {
      return this.binary(BinaryOperation.DIVIDE, this.pe.constant(value));
   }

   public ValueEmitter rem(ValueEmitter other) {
      return this.binary(BinaryOperation.MODULO, other);
   }

   public ValueEmitter rem(int value) {
      return this.binary(BinaryOperation.MODULO, this.pe.constant(value));
   }

   public ValueEmitter compareTo(ValueEmitter other) {
      ValueEmitter.Pair pair = this.commonNumeric(other);
      return this.binaryOp(BinaryOperation.COMPARE_GREATER, pair.first, pair.second, ValueType.INTEGER);
   }

   public ValueEmitter compareTo(int value) {
      return this.compareTo(this.pe.constant(value));
   }

   private ValueEmitter logical(BinaryOperation op, ValueEmitter other) {
      ValueEmitter.Pair pair = this.commonNumeric(other);
      PrimitiveType common = ((ValueType.Primitive)pair.first.type).getKind();
      this.checkInteger(common);
      return this.binaryOp(op, pair.first, pair.second, pair.first.type);
   }

   public ValueEmitter bitAnd(ValueEmitter other) {
      return this.logical(BinaryOperation.AND, other);
   }

   private void checkInteger(PrimitiveType common) {
      switch (common) {
         case FLOAT:
         case DOUBLE:
            throw new EmitException("Can't perform bitwise operation between non-integers: " + common);
      }
   }

   public ValueEmitter bitAnd(int value) {
      return this.bitAnd(this.pe.constant(value));
   }

   public ValueEmitter bitOr(ValueEmitter other) {
      return this.logical(BinaryOperation.OR, other);
   }

   public ValueEmitter bitOr(int value) {
      return this.bitOr(this.pe.constant(value));
   }

   public ValueEmitter bitXor(ValueEmitter other) {
      return this.logical(BinaryOperation.XOR, other);
   }

   public ValueEmitter bitXor(int value) {
      return this.bitXor(this.pe.constant(value));
   }

   public ValueEmitter shl(ValueEmitter other) {
      return this.shift(BinaryOperation.SHIFT_LEFT, other);
   }

   public ValueEmitter shl(int value) {
      return this.shl(this.pe.constant(value));
   }

   public ValueEmitter shr(ValueEmitter other) {
      return this.shift(BinaryOperation.SHIFT_RIGHT, other);
   }

   public ValueEmitter shr(int value) {
      return this.shr(this.pe.constant(value));
   }

   public ValueEmitter shru(ValueEmitter other) {
      return this.shift(BinaryOperation.SHIFT_RIGHT_UNSIGNED, other);
   }

   public ValueEmitter shru(int value) {
      return this.shru(this.pe.constant(value));
   }

   private ValueEmitter shift(BinaryOperation op, ValueEmitter other) {
      if (this.type instanceof ValueType.Primitive && other.type instanceof ValueType.Primitive) {
         ValueType valueType = this.type;
         PrimitiveType kind = ((ValueType.Primitive)this.type).getKind();
         switch (kind) {
            case FLOAT:
            case DOUBLE:
               throw new EmitException("Can't perform bit shift operation over non-integer: " + this.type);
            default:
               PrimitiveType shiftKind = ((ValueType.Primitive)this.type).getKind();
               switch (kind) {
                  case BYTE:
                  case SHORT:
                  case INTEGER:
                     other = other.castToInteger(this.convertToIntegerSubtype(shiftKind));
                     ValueEmitter value = this;
                     IntegerSubtype subtype = this.convertToIntegerSubtype(kind);
                     if (subtype != null) {
                        value = value.castToInteger(subtype);
                        valueType = ValueType.INTEGER;
                     }

                     return this.binaryOp(op, value, other, valueType);
                  case CHARACTER:
                  default:
                     throw new EmitException("Can't perform bit shift operation with non-integer shift: " + this.type);
               }
         }
      } else {
         throw new EmitException("Can't shift " + this.type + " by " + other.type);
      }
   }

   public ValueEmitter invoke(InvocationType invokeType, MethodReference method, ValueEmitter... arguments) {
      if (!(this.type instanceof ValueType.Object)) {
         throw new EmitException("Can't invoke method on non-object type: " + this.type);
      }

      ClassHierarchy hierarchy = this.pe.hierarchy;

      for (int i = 0; i < method.parameterCount(); i++) {
         if (!hierarchy.isSuperType(method.parameterType(i), arguments[i].getType(), false)) {
            throw new EmitException("Argument " + i + " of type " + arguments[i].getType() + " is not compatible with method " + method);
         }
      }

      if (!hierarchy.isSuperType(method.getClassName(), ((ValueType.Object)this.type).getClassName(), true)) {
         throw new EmitException("Can't call " + method + " on non-compatible class " + this.type);
      }

      Variable result = null;
      if (method.getReturnType() != ValueType.VOID) {
         result = this.pe.getProgram().createVariable();
      }

      InvokeInstruction insn = new InvokeInstruction();
      insn.setType(invokeType);
      insn.setMethod(method);
      insn.setInstance(this.variable);
      insn.setReceiver(result);
      Variable[] insnArguments = new Variable[arguments.length];

      for (int i = 0; i < insnArguments.length; i++) {
         insnArguments[i] = arguments[i].variable;
      }

      insn.setArguments(insnArguments);
      this.pe.addInstruction(insn);
      return result != null ? this.pe.var(result, method.getReturnType()) : null;
   }

   public ValueEmitter invoke(InvocationType invokeType, String className, String name, ValueType resultType, ValueEmitter... arguments) {
      if (!(this.type instanceof ValueType.Object)) {
         throw new EmitException("Can't invoke method on non-object type: " + this.type);
      }

      Variable result = null;
      ValueType[] signature = new ValueType[arguments.length + 1];

      for (int i = 0; i < arguments.length; i++) {
         signature[i] = arguments[i].type;
      }

      signature[arguments.length] = resultType;
      ClassReader cls = this.pe.classSource.get(className);
      MethodReader methodReader = cls != null ? cls.getMethod(new MethodDescriptor(name, signature)) : null;
      MethodReference method = methodReader != null ? methodReader.getReference() : new MethodReference(className, name, signature);
      if (method.getReturnType() != ValueType.VOID) {
         result = this.pe.getProgram().createVariable();
      }

      InvokeInstruction insn = new InvokeInstruction();
      insn.setType(invokeType);
      insn.setMethod(method);
      insn.setInstance(this.variable);
      insn.setReceiver(result);
      Variable[] insnArguments = new Variable[arguments.length];

      for (int i = 0; i < insnArguments.length; i++) {
         insnArguments[i] = arguments[i].variable;
      }

      insn.setArguments(insnArguments);
      this.pe.addInstruction(insn);
      return result != null ? this.pe.var(result, resultType) : null;
   }

   public ValueEmitter invoke(InvocationType invokeType, String name, ValueType resultType, ValueEmitter... arguments) {
      return this.invoke(invokeType, ((ValueType.Object)this.type).getClassName(), name, resultType, arguments);
   }

   public ValueEmitter invokeSpecial(MethodReference method, ValueEmitter... arguments) {
      return this.invoke(InvocationType.SPECIAL, method, arguments);
   }

   public ValueEmitter invokeSpecial(String className, String name, ValueType resultType, ValueEmitter... arguments) {
      return this.invoke(InvocationType.SPECIAL, className, name, resultType, arguments);
   }

   public ValueEmitter invokeSpecial(String name, ValueType resultType, ValueEmitter... arguments) {
      return this.invoke(InvocationType.SPECIAL, name, resultType, arguments);
   }

   public ValueEmitter invokeSpecial(String name, Class<?> resultType, ValueEmitter... arguments) {
      return this.invoke(InvocationType.SPECIAL, name, ValueType.parse(resultType), arguments);
   }

   public ProgramEmitter invokeSpecial(String className, String name, ValueEmitter... arguments) {
      this.invokeSpecial(className, name, ValueType.VOID, arguments);
      return this.pe;
   }

   public ProgramEmitter invokeSpecial(Class<?> cls, String name, ValueEmitter... arguments) {
      this.invokeSpecial(cls.getName(), name, ValueType.VOID, arguments);
      return this.pe;
   }

   public ProgramEmitter invokeSpecial(String name, ValueEmitter... arguments) {
      this.invokeSpecial(name, ValueType.VOID, arguments);
      return this.pe;
   }

   public ValueEmitter invokeVirtual(String name, ValueType resultType, ValueEmitter... arguments) {
      return this.invoke(InvocationType.VIRTUAL, name, resultType, arguments);
   }

   public ValueEmitter invokeVirtual(MethodReference method, ValueEmitter... arguments) {
      return this.invoke(InvocationType.VIRTUAL, method, arguments);
   }

   public ValueEmitter invokeVirtual(String name, Class<?> resultType, ValueEmitter... arguments) {
      return this.invoke(InvocationType.VIRTUAL, name, ValueType.parse(resultType), arguments);
   }

   public ProgramEmitter invokeVirtual(String name, ValueEmitter... arguments) {
      this.invokeVirtual(name, ValueType.VOID, arguments);
      return this.pe;
   }

   public ValueEmitter join(BasicBlock block, ValueEmitter other, BasicBlock otherBlock, ValueType type) {
      Variable var = this.pe.getProgram().createVariable();
      Phi phi = new Phi();
      phi.setReceiver(var);
      Incoming incoming = new Incoming();
      incoming.setSource(block);
      incoming.setValue(this.variable);
      phi.getIncomings().add(incoming);
      incoming = new Incoming();
      incoming.setSource(otherBlock);
      incoming.setValue(other.variable);
      phi.getIncomings().add(incoming);
      this.pe.getBlock().getPhis().add(phi);
      return new ValueEmitter(this.pe, this.pe.getBlock(), var, type);
   }

   public ForkEmitter fork(BinaryBranchingCondition condition, ValueEmitter other) {
      final BinaryBranchingInstruction insn = new BinaryBranchingInstruction(condition);
      insn.setFirstOperand(this.variable);
      insn.setSecondOperand(other.variable);
      this.pe.addInstruction(insn);
      return new ForkEmitter(this.pe) {
         @Override
         public ForkEmitter setThen(BasicBlock block) {
            insn.setConsequent(block);
            return this;
         }

         @Override
         public ForkEmitter setElse(BasicBlock block) {
            insn.setAlternative(block);
            return this;
         }
      };
   }

   public ForkEmitter fork(BranchingCondition condition) {
      final BranchingInstruction insn = new BranchingInstruction(condition);
      insn.setOperand(this.variable);
      this.pe.addInstruction(insn);
      return new ForkEmitter(this.pe) {
         @Override
         public ForkEmitter setThen(BasicBlock block) {
            insn.setConsequent(block);
            return this;
         }

         @Override
         public ForkEmitter setElse(BasicBlock block) {
            insn.setAlternative(block);
            return this;
         }
      };
   }

   public ConditionEmitter isTrue() {
      return new ConditionEmitter(this.pe, this.fork(BranchingCondition.NOT_EQUAL));
   }

   public ConditionEmitter isFalse() {
      return new ConditionEmitter(this.pe, this.fork(BranchingCondition.EQUAL));
   }

   public ConditionEmitter isEqualTo(ValueEmitter other) {
      return new ConditionEmitter(this.pe, this.fork(BinaryBranchingCondition.EQUAL, other));
   }

   public ConditionEmitter isNotEqualTo(ValueEmitter other) {
      return new ConditionEmitter(this.pe, this.fork(BinaryBranchingCondition.NOT_EQUAL, other));
   }

   public ConditionEmitter isSame(ValueEmitter other) {
      return new ConditionEmitter(this.pe, this.fork(BinaryBranchingCondition.REFERENCE_EQUAL, other));
   }

   public ConditionEmitter isNotSame(ValueEmitter other) {
      return new ConditionEmitter(this.pe, this.fork(BinaryBranchingCondition.REFERENCE_NOT_EQUAL, other));
   }

   public ConditionEmitter isNull() {
      return this.isSame(this.pe.constantNull(this.getType()));
   }

   public ConditionEmitter isNotNull() {
      return this.isNotSame(this.pe.constantNull(this.getType()));
   }

   public ConditionEmitter isGreaterThan(ValueEmitter other) {
      return new ConditionEmitter(this.pe, this.compareTo(other).fork(BranchingCondition.GREATER));
   }

   public ConditionEmitter isGreaterOrEqualTo(ValueEmitter other) {
      return new ConditionEmitter(this.pe, this.compareTo(other).fork(BranchingCondition.GREATER_OR_EQUAL));
   }

   public ConditionEmitter isLessThan(ValueEmitter other) {
      return new ConditionEmitter(this.pe, this.compareTo(other).fork(BranchingCondition.LESS));
   }

   public ConditionEmitter isLessOrEqualTo(ValueEmitter other) {
      return new ConditionEmitter(this.pe, this.compareTo(other).fork(BranchingCondition.LESS_OR_EQUAL));
   }

   public void returnValue() {
      ExitInstruction insn = new ExitInstruction();
      insn.setValueToReturn(this.variable);
      this.pe.addInstruction(insn);
   }

   public void raise() {
      if (!this.pe.hierarchy.isSuperType(ValueType.object("java.lang.Throwable"), this.type, true)) {
         throw new EmitException("Can't throw non-exception value: " + this.type);
      }

      RaiseInstruction insn = new RaiseInstruction();
      insn.setException(this.variable);
      this.pe.addInstruction(insn);
   }

   public ValueEmitter cast(Class<?> type) {
      return this.cast(ValueType.parse(type));
   }

   public ValueEmitter cast(ValueType type) {
      if (type.equals(this.type)) {
         return this;
      }

      if (this.pe.hierarchy.isSuperType(type, this.type, false)) {
         return this.pe.var(this.variable.getIndex(), type);
      }

      if (type instanceof ValueType.Primitive) {
         if (!(this.type instanceof ValueType.Primitive)) {
            throw new EmitException("Can't convert " + this.type + " to " + type);
         }

         ValueEmitter value = this;
         PrimitiveType sourceKind = ((ValueType.Primitive)this.type).getKind();
         PrimitiveType targetKind = ((ValueType.Primitive)type).getKind();
         if (sourceKind == PrimitiveType.BOOLEAN) {
            switch (targetKind) {
               case BYTE:
               case SHORT:
               case CHARACTER:
               case INTEGER:
               case BOOLEAN:
                  return this.pe.var(value.getVariable(), type);
               case LONG:
               case FLOAT:
               case DOUBLE:
               default:
                  throw new EmitException("Can't convert " + this.type + " to " + type);
            }
         } else if (targetKind == PrimitiveType.BOOLEAN) {
            switch (sourceKind) {
               case BYTE:
               case SHORT:
               case CHARACTER:
               case INTEGER:
               case BOOLEAN:
                  return this.pe.var(value.getVariable(), type);
               case LONG:
               case FLOAT:
               case DOUBLE:
               default:
                  throw new EmitException("Can't convert " + this.type + " to " + type);
            }
         } else {
            IntegerSubtype sourceSubtype = this.convertToIntegerSubtype(sourceKind);
            if (sourceSubtype != null) {
               sourceKind = PrimitiveType.INTEGER;
               value = this.castToInteger(sourceSubtype);
            }

            NumericOperandType sourceNumeric = this.convertToNumeric(sourceKind);
            NumericOperandType targetNumeric = this.convertToNumeric(targetKind);
            CastNumberInstruction insn = new CastNumberInstruction(sourceNumeric, targetNumeric);
            insn.setValue(value.getVariable());
            value = this.pe.newVar(type);
            insn.setReceiver(value.getVariable());
            this.pe.addInstruction(insn);
            IntegerSubtype targetSubtype = this.convertToIntegerSubtype(targetKind);
            if (targetSubtype != null) {
               value = this.castFromInteger(targetSubtype);
            }

            return value;
         }
      } else {
         if (this.type instanceof ValueType.Primitive) {
            return this.boxPrimitive(type);
         }

         Variable result = this.pe.getProgram().createVariable();
         CastInstruction insn = new CastInstruction();
         insn.setValue(this.variable);
         insn.setReceiver(result);
         insn.setTargetType(type);
         this.pe.addInstruction(insn);
         return this.pe.var(result, type);
      }
   }

   private ValueEmitter boxPrimitive(ValueType type) {
      if (!(type instanceof ValueType.Object)) {
         throw new EmitException("Can't convert " + this.type + " to " + type);
      }

      String targetClass = ((ValueType.Object)type).getClassName();
      PrimitiveType primitiveType = ((ValueType.Primitive)this.type).getKind();
      String boxClassName = this.getPrimitiveClassName(primitiveType);
      ValueEmitter result = this.invokeValueOf(boxClassName);
      if (!this.pe.hierarchy.isSuperType(targetClass, boxClassName, false)) {
         throw new EmitException("Can't convert " + this.type + " to " + targetClass);
      }

      if (!result.type.equals(type)) {
         result.type = type;
      }

      return result;
   }

   private ValueEmitter invokeValueOf(String cls) {
      return this.pe.invoke(cls, "valueOf", ValueType.object(cls), this);
   }

   public ValueEmitter cast(NumericOperandType to) {
      if (!(this.type instanceof ValueType.Primitive)) {
         throw new EmitException("Can't cast non-primitive type: " + this.type);
      }

      ValueEmitter value = this;
      PrimitiveType kind = ((ValueType.Primitive)this.type).getKind();
      IntegerSubtype subtype = this.convertToIntegerSubtype(kind);
      if (subtype != null) {
         value = value.castFromInteger(subtype);
         kind = PrimitiveType.INTEGER;
      }

      ValueEmitter result = this.pe.newVar(ValueType.INTEGER);
      CastNumberInstruction insn = new CastNumberInstruction(this.convertToNumeric(kind), to);
      insn.setValue(value.variable);
      insn.setReceiver(result.getVariable());
      this.pe.addInstruction(insn);
      return result;
   }

   public ValueEmitter castFromInteger(IntegerSubtype subtype) {
      if (this.type != ValueType.INTEGER) {
         throw new EmitException("Can't cast non-integer value: " + this.type);
      }

      CastIntegerInstruction insn = new CastIntegerInstruction(subtype, CastIntegerDirection.TO_INTEGER);
      insn.setValue(this.variable);
      ValueEmitter result = this.pe.newVar(this.convertSubtype(subtype));
      insn.setReceiver(result.getVariable());
      this.pe.addInstruction(insn);
      return result;
   }

   private ValueType convertSubtype(IntegerSubtype subtype) {
      switch (subtype) {
         case BYTE:
            return ValueType.BYTE;
         case SHORT:
            return ValueType.SHORT;
         case CHAR:
            return ValueType.CHARACTER;
         default:
            throw new IllegalArgumentException("Unknown subtype: " + subtype);
      }
   }

   public ValueEmitter castToInteger(IntegerSubtype subtype) {
      switch (subtype) {
         case BYTE:
            if (this.type != ValueType.BYTE) {
               throw new EmitException("Can't cast non-byte value: " + this.type);
            }
            break;
         case SHORT:
            if (this.type != ValueType.SHORT) {
               throw new EmitException("Can't cast non-short value: " + this.type);
            }
            break;
         case CHAR:
            if (this.type != ValueType.CHARACTER) {
               throw new EmitException("Can't cast non-char value: " + this.type);
            }
      }

      CastIntegerInstruction insn = new CastIntegerInstruction(subtype, CastIntegerDirection.FROM_INTEGER);
      insn.setValue(this.variable);
      ValueEmitter result = this.pe.newVar(ValueType.INTEGER);
      insn.setReceiver(result.getVariable());
      this.pe.addInstruction(insn);
      return result;
   }

   public ValueEmitter widenToInteger() {
      if (!(this.type instanceof ValueType.Primitive)) {
         throw new EmitException("Can't widen non-primitive: " + this.type);
      } else {
         PrimitiveType primitive = ((ValueType.Primitive)this.type).getKind();
         if (primitive == PrimitiveType.INTEGER) {
            return this;
         } else {
            IntegerSubtype subtype = this.convertToIntegerSubtype(primitive);
            if (subtype == null) {
               throw new EmitException("Can't widen to int: " + this.type);
            } else {
               return this.castToInteger(subtype);
            }
         }
      }
   }

   public ValueEmitter assertIs(ValueType type) {
      if (!this.pe.hierarchy.isSuperType(type, this.type, true)) {
         throw new EmitException("Value type " + this.type + " is not subtype of " + type);
      } else {
         return this;
      }
   }

   public ValueEmitter assertIs(Class<?> type) {
      return this.assertIs(ValueType.parse(type));
   }

   public ValueEmitter getElement(ValueEmitter index) {
      if (!(this.type instanceof ValueType.Array)) {
         throw new EmitException("Can't get element of non-array type: " + this.type);
      }

      ValueEmitter array = this.unwrapArray();
      Variable result = this.pe.getProgram().createVariable();
      ValueType.Array arrayType = (ValueType.Array)array.getType();
      GetElementInstruction insn = new GetElementInstruction(this.getArrayElementType(arrayType.getItemType()));
      insn.setArray(array.variable);
      insn.setIndex(index.widenToInteger().variable);
      insn.setReceiver(result);
      this.pe.addInstruction(insn);
      return this.pe.var(result, ((ValueType.Array)this.type).getItemType());
   }

   public ValueEmitter getElement(int index) {
      return this.getElement(this.pe.constant(index));
   }

   public ProgramEmitter setElement(ValueEmitter index, ValueEmitter value) {
      if (!(this.type instanceof ValueType.Array)) {
         throw new EmitException("Can't set element of non-array type: " + this.type);
      }

      PutElementInstruction insn = new PutElementInstruction(this.getArrayElementType(value.getType()));
      insn.setArray(this.unwrapArray().variable);
      insn.setIndex(index.widenToInteger().variable);
      insn.setValue(value.variable);
      this.pe.addInstruction(insn);
      return this.pe;
   }

   public ProgramEmitter setElement(int index, ValueEmitter value) {
      this.setElement(this.pe.constant(index), value);
      return this.pe;
   }

   private ValueEmitter unwrapArray() {
      ValueType elementType = ((ValueType.Array)this.type).getItemType();
      Variable result = this.pe.getProgram().createVariable();
      UnwrapArrayInstruction insn = new UnwrapArrayInstruction(this.getArrayElementType(elementType));
      insn.setArray(this.variable);
      insn.setReceiver(result);
      this.pe.addInstruction(insn);
      return this.pe.var(result, this.type);
   }

   public ValueEmitter arrayLength() {
      if (!(this.type instanceof ValueType.Array)) {
         throw new EmitException("Can't get length of non-array type: " + this.type);
      }

      Variable result = this.pe.getProgram().createVariable();
      ArrayLengthInstruction insn = new ArrayLengthInstruction();
      insn.setArray(this.unwrapArray().variable);
      insn.setReceiver(result);
      this.pe.addInstruction(insn);
      return this.pe.var(result, ValueType.INTEGER);
   }

   public ValueEmitter instanceOf(ValueType type) {
      Variable result = this.pe.getProgram().createVariable();
      IsInstanceInstruction insn = new IsInstanceInstruction();
      insn.setValue(this.variable);
      insn.setReceiver(result);
      insn.setType(type);
      this.pe.addInstruction(insn);
      return this.pe.var(result, ValueType.BOOLEAN);
   }

   public ValueEmitter cloneArray() {
      Variable result = this.pe.getProgram().createVariable();
      CloneArrayInstruction insn = new CloneArrayInstruction();
      insn.setArray(this.variable);
      insn.setReceiver(result);
      this.pe.addInstruction(insn);
      return this.pe.var(result, ValueType.object("java.lang.Object"));
   }

   private ArrayElementType getArrayElementType(ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BYTE:
            case BOOLEAN:
               return ArrayElementType.BYTE;
            case SHORT:
               return ArrayElementType.SHORT;
            case CHARACTER:
               return ArrayElementType.CHAR;
            case INTEGER:
               return ArrayElementType.INT;
            case LONG:
               return ArrayElementType.LONG;
            case FLOAT:
               return ArrayElementType.FLOAT;
            case DOUBLE:
               return ArrayElementType.DOUBLE;
         }
      }

      return ArrayElementType.OBJECT;
   }

   public ProgramEmitter propagateTo(PhiEmitter phi) {
      Incoming incoming = new Incoming();
      incoming.setValue(this.variable);
      incoming.setSource(this.pe.getBlock());
      phi.phi.getIncomings().add(incoming);
      return this.pe;
   }

   public ValueEmitter box() {
      if (!(this.type instanceof ValueType.Primitive)) {
         throw new EmitException("Can't box non-primitive: " + this.type);
      }

      String className = this.getPrimitiveClassName(((ValueType.Primitive)this.type).getKind());
      return this.pe.invoke(className, "valueOf", ValueType.object(className), this);
   }

   private String getPrimitiveClassName(PrimitiveType type) {
      switch (type) {
         case BYTE:
            return "java.lang.Byte";
         case SHORT:
            return "java.lang.Short";
         case CHARACTER:
            return "java.lang.Character";
         case INTEGER:
            return "java.lang.Integer";
         case LONG:
            return "java.lang.Long";
         case FLOAT:
            return "java.lang.Float";
         case DOUBLE:
            return "java.lang.Double";
         case BOOLEAN:
            return "java.lang.Boolean";
         default:
            throw new AssertionError("Unexpected primitive type: " + type);
      }
   }

   static class Pair {
      ValueEmitter first;
      ValueEmitter second;

      public Pair(ValueEmitter first, ValueEmitter second) {
         this.first = first;
         this.second = second;
      }
   }
}
