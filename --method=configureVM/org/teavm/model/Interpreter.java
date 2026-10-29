package org.teavm.model;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.CastIntegerDirection;
import org.teavm.model.instructions.InstructionReader;
import org.teavm.model.instructions.IntegerSubtype;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.SwitchTableEntryReader;

public class Interpreter {
   private ClassLoader classLoader;
   private BasicBlockReader currentBlock;
   private List<List<IncomingReader>> outgoings;
   private Object[] variables;
   private Object result;
   private Interpreter.State state;
   private InstructionReader reader = new InstructionReader() {
      @Override
      public void location(TextLocation location) {
      }

      @Override
      public void nop() {
      }

      @Override
      public void classConstant(VariableReader receiver, ValueType cst) {
         Interpreter.this.variables[receiver.getIndex()] = this.asJvmClass(cst);
      }

      @Override
      public void nullConstant(VariableReader receiver) {
         Interpreter.this.variables[receiver.getIndex()] = null;
      }

      @Override
      public void integerConstant(VariableReader receiver, int cst) {
         Interpreter.this.variables[receiver.getIndex()] = cst;
      }

      @Override
      public void longConstant(VariableReader receiver, long cst) {
         Interpreter.this.variables[receiver.getIndex()] = cst;
      }

      @Override
      public void floatConstant(VariableReader receiver, float cst) {
         Interpreter.this.variables[receiver.getIndex()] = cst;
      }

      @Override
      public void doubleConstant(VariableReader receiver, double cst) {
         Interpreter.this.variables[receiver.getIndex()] = cst;
      }

      @Override
      public void stringConstant(VariableReader receiver, String cst) {
         Interpreter.this.variables[receiver.getIndex()] = cst;
      }

      @Override
      public void binary(BinaryOperation op, VariableReader receiver, VariableReader first, VariableReader second, NumericOperandType type) {
         switch (type) {
            case INT:
               int a = (Integer)Interpreter.this.variables[first.getIndex()];
               int b = (Integer)Interpreter.this.variables[second.getIndex()];

               Interpreter.this.variables[receiver.getIndex()] = switch (op) {
                  case ADD -> a + b;
                  case SUBTRACT -> a - b;
                  case MULTIPLY -> a * b;
                  case DIVIDE -> a * b;
                  case MODULO -> a % b;
                  case COMPARE_LESS, COMPARE_GREATER -> Integer.compare(a, b);
                  case AND -> a & b;
                  case OR -> a | b;
                  case XOR -> a ^ b;
                  default -> throw new IllegalArgumentException("Unknown operation: " + op);
               };
               break;
            case LONG:
               long a = (Long)Interpreter.this.variables[first.getIndex()];
               long b = (Long)Interpreter.this.variables[second.getIndex()];

               Interpreter.this.variables[receiver.getIndex()] = switch (op) {
                  case ADD -> a + b;
                  case SUBTRACT -> a - b;
                  case MULTIPLY -> a * b;
                  case DIVIDE -> a * b;
                  case MODULO -> a % b;
                  case COMPARE_LESS, COMPARE_GREATER -> Long.compare(a, b);
                  case AND -> a & b;
                  case OR -> a | b;
                  case XOR -> a ^ b;
                  default -> throw new IllegalArgumentException("Unknown operation: " + op);
               };
               break;
            case FLOAT:
               float a = (Float)Interpreter.this.variables[first.getIndex()];
               float b = (Float)Interpreter.this.variables[second.getIndex()];

               Interpreter.this.variables[receiver.getIndex()] = switch (op) {
                  case ADD -> a + b;
                  case SUBTRACT -> a - b;
                  case MULTIPLY -> a * b;
                  case DIVIDE -> a * b;
                  case MODULO -> a % b;
                  case COMPARE_LESS, COMPARE_GREATER -> Float.compare(a, b);
                  case AND, OR, XOR -> throw new IllegalArgumentException("Unsupported operation " + op + " for operands of type" + type);
                  default -> throw new IllegalArgumentException("Unknown operation: " + op);
               };
               break;
            case DOUBLE:
               double a = (Double)Interpreter.this.variables[first.getIndex()];
               double b = (Double)Interpreter.this.variables[second.getIndex()];

               Interpreter.this.variables[receiver.getIndex()] = switch (op) {
                  case ADD -> a + b;
                  case SUBTRACT -> a - b;
                  case MULTIPLY -> a * b;
                  case DIVIDE -> a * b;
                  case MODULO -> a % b;
                  case COMPARE_LESS, COMPARE_GREATER -> Double.compare(a, b);
                  case AND, OR, XOR -> throw new IllegalArgumentException("Unsupported operation " + op + " for operands of type" + type);
                  default -> throw new IllegalArgumentException("Unknown operation: " + op);
               };
         }
      }

      @Override
      public void negate(VariableReader receiver, VariableReader operand, NumericOperandType type) {
         Object a = Interpreter.this.variables[operand.getIndex()];

         Interpreter.this.variables[receiver.getIndex()] = switch (type) {
            case INT -> -(Integer)a;
            case LONG -> -(Long)a;
            case FLOAT -> -(Float)a;
            case DOUBLE -> -(Double)a;
            default -> throw new IllegalArgumentException("Unknown type: " + type);
         };
      }

      @Override
      public void assign(VariableReader receiver, VariableReader assignee) {
         Interpreter.this.variables[receiver.getIndex()] = Interpreter.this.variables[assignee.getIndex()];
      }

      @Override
      public void cast(VariableReader receiver, VariableReader value, ValueType targetType, boolean cast) {
         Interpreter.this.variables[receiver.getIndex()] = this.asJvmClass(targetType).cast(Interpreter.this.variables[value.getIndex()]);
      }

      @Override
      public void cast(VariableReader receiver, VariableReader value, NumericOperandType sourceType, NumericOperandType targetType) {
         Interpreter.this.variables[receiver.getIndex()] = switch (sourceType) {
            case INT -> {
               int a = (Integer)Interpreter.this.variables[value.getIndex()];
               switch (targetType) {
                  case INT:
                     yield a;
                  case LONG:
                     yield (long)a;
                  case FLOAT:
                     yield (float)a;
                  case DOUBLE:
                     yield (double)a;
                  default:
                     throw new IllegalArgumentException("Can't cast " + sourceType + " to " + targetType);
               }
            }
            case LONG -> {
               long a = (Long)Interpreter.this.variables[value.getIndex()];
               switch (targetType) {
                  case INT:
                     yield (int)a;
                  case LONG:
                     yield a;
                  case FLOAT:
                     yield (float)a;
                  case DOUBLE:
                     yield (double)a;
                  default:
                     throw new IllegalArgumentException("Can't cast " + sourceType + " to " + targetType);
               }
            }
            case FLOAT -> {
               float a = (Float)Interpreter.this.variables[value.getIndex()];
               switch (targetType) {
                  case INT:
                     yield (int)a;
                  case LONG:
                     yield (long)a;
                  case FLOAT:
                     yield a;
                  case DOUBLE:
                     yield (double)a;
                  default:
                     throw new IllegalArgumentException("Can't cast " + sourceType + " to " + targetType);
               }
            }
            case DOUBLE -> {
               double a = (Double)Interpreter.this.variables[value.getIndex()];
               switch (targetType) {
                  case INT:
                     yield (int)a;
                  case LONG:
                     yield (long)a;
                  case FLOAT:
                     yield (float)a;
                  case DOUBLE:
                     yield a;
                  default:
                     throw new IllegalArgumentException("Can't cast " + sourceType + " to " + targetType);
               }
            }
            default -> throw new IllegalArgumentException("Can't cast " + sourceType + " to " + targetType);
         };
      }

      @Override
      public void cast(VariableReader receiver, VariableReader value, IntegerSubtype type, CastIntegerDirection direction) {
         switch (direction) {
            case FROM_INTEGER:
               int a = (Integer)Interpreter.this.variables[value.getIndex()];

               Interpreter.this.variables[receiver.getIndex()] = switch (type) {
                  case BYTE -> (byte)a;
                  case SHORT -> (short)a;
                  case CHAR -> (char)a;
                  default -> throw new IllegalArgumentException("Unknown type: " + type);
               };
               break;
            case TO_INTEGER:
               Object a = Interpreter.this.variables[value.getIndex()];

               Interpreter.this.variables[receiver.getIndex()] = switch (type) {
                  case BYTE -> (Byte)a;
                  case SHORT -> (Short)a;
                  case CHAR -> (Character)a;
                  default -> throw new IllegalArgumentException("Unknown type: " + type);
               };
         }
      }

      @Override
      public void jumpIf(BranchingCondition cond, VariableReader operand, BasicBlockReader consequent, BasicBlockReader alternative) {
         Object a = Interpreter.this.variables[operand.getIndex()];

         this.jump(switch (cond) {
            case EQUAL -> (Integer)a == 0;
            case NOT_EQUAL -> (Integer)a != 0;
            case LESS -> (Integer)a < 0;
            case LESS_OR_EQUAL -> (Integer)a <= 0;
            case GREATER -> (Integer)a > 0;
            case GREATER_OR_EQUAL -> (Integer)a >= 0;
            case NULL -> a == null;
            case NOT_NULL -> a != null;
            default -> throw new IllegalArgumentException("Unknown condition: " + cond);
         } ? consequent : alternative);
      }

      @Override
      public void jumpIf(BinaryBranchingCondition cond, VariableReader first, VariableReader second, BasicBlockReader consequent, BasicBlockReader alternative) {
         Object a = Interpreter.this.variables[first.getIndex()];
         Object b = Interpreter.this.variables[second.getIndex()];

         this.jump(switch (cond) {
            case EQUAL -> (Integer)a == (Integer)b;
            case NOT_EQUAL -> (Integer)a != (Integer)b;
            case REFERENCE_EQUAL -> a == b;
            case REFERENCE_NOT_EQUAL -> a != b;
            default -> throw new IllegalArgumentException("Unknown condition: " + cond);
         } ? consequent : alternative);
      }

      @Override
      public void jump(BasicBlockReader target) {
         Object[] newVariables = (Object[])Interpreter.this.variables.clone();

         for (IncomingReader outgoing : Interpreter.this.outgoings.get(Interpreter.this.currentBlock.getIndex())) {
            if (outgoing.getPhi().getBasicBlock() == target) {
               newVariables[outgoing.getPhi().getReceiver().getIndex()] = Interpreter.this.variables[outgoing.getValue().getIndex()];
            }
         }

         Interpreter.this.variables = newVariables;
         Interpreter.this.currentBlock = target;
      }

      @Override
      public void choose(VariableReader condition, List<? extends SwitchTableEntryReader> table, BasicBlockReader defaultTarget) {
         int value = (Integer)Interpreter.this.variables[condition.getIndex()];

         for (SwitchTableEntryReader entry : table) {
            if (value == entry.getCondition()) {
               this.jump(entry.getTarget());
               return;
            }
         }

         this.jump(defaultTarget);
      }

      @Override
      public void exit(VariableReader valueToReturn) {
         Interpreter.this.state = Interpreter.State.EXITED;
         Interpreter.this.result = Interpreter.this.variables[valueToReturn.getIndex()];
      }

      @Override
      public void raise(VariableReader exception) {
         Throwable e = (Throwable)Interpreter.this.variables[exception.getIndex()];
         if (!Interpreter.this.pickExceptionHandler(e)) {
            Interpreter.this.state = Interpreter.State.EXITED;
            Interpreter.this.result = e;
         }
      }

      @Override
      public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
         Class<?> itemJvmType = this.asJvmClass(itemType);
         int sizeValue = (Integer)Interpreter.this.variables[size.getIndex()];
         Interpreter.this.variables[receiver.getIndex()] = Array.newInstance(itemJvmType, sizeValue);
      }

      @Override
      public void createArray(VariableReader receiver, ValueType itemType, List<? extends VariableReader> dimensions) {
         Class<?> itemJvmType = this.asJvmClass(itemType);

         for (int i = 1; i < dimensions.size(); i++) {
            itemJvmType = Array.newInstance(itemJvmType, 0).getClass();
         }

         Interpreter.this.variables[receiver.getIndex()] = this.createArray(itemJvmType, dimensions, 0);
      }

      private Object createArray(Class<?> itemType, List<? extends VariableReader> dimensions, int dimensionIndex) {
         int dimensionValue = (Integer)Interpreter.this.variables[dimensions.get(dimensionIndex).getIndex()];
         Object result = Array.newInstance(itemType, dimensionValue);
         if (dimensionIndex < dimensions.size() - 1) {
            for (int i = 0; i < dimensionValue; i++) {
               Array.set(result, i, this.createArray(itemType.getComponentType(), dimensions, dimensionIndex + 1));
            }
         }

         return result;
      }

      @Override
      public void create(VariableReader receiver, String type) {
         try {
            Class<?> cls = Class.forName(type, false, Interpreter.this.classLoader);
         } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found: " + type);
         }

         Interpreter.this.variables[receiver.getIndex()] = null;
      }

      @Override
      public void getField(VariableReader receiver, VariableReader instance, FieldReference field, ValueType fieldType) {
         Field jvmField = this.getJvmField(field);
         Object jvmInstance = instance != null ? Interpreter.this.variables[instance.getIndex()] : null;

         Object result;
         try {
            result = jvmField.get(jvmInstance);
         } catch (IllegalAccessException e) {
            throw new RuntimeException("Can't get field value: " + field);
         }

         Interpreter.this.variables[receiver.getIndex()] = result;
      }

      @Override
      public void putField(VariableReader instance, FieldReference field, VariableReader value, ValueType fieldType) {
         Field jvmField = this.getJvmField(field);
         Object jvmInstance = instance != null ? Interpreter.this.variables[instance.getIndex()] : null;

         try {
            jvmField.set(jvmInstance, Interpreter.this.variables[value.getIndex()]);
         } catch (IllegalAccessException e) {
            throw new RuntimeException("Can't get field value: " + field);
         }
      }

      private Field getJvmField(FieldReference field) {
         Class<?> cls;
         try {
            cls = Class.forName(field.getClassName(), false, Interpreter.this.classLoader);
         } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found: " + field.getClassName());
         }

         Field jvmField;
         try {
            jvmField = cls.getDeclaredField(field.getFieldName());
         } catch (NoSuchFieldException e) {
            throw new RuntimeException("Field not found: " + field);
         }

         jvmField.setAccessible(true);
         return jvmField;
      }

      @Override
      public void arrayLength(VariableReader receiver, VariableReader array) {
         int value = Array.getLength(Interpreter.this.variables[array.getIndex()]);
         Interpreter.this.variables[receiver.getIndex()] = value;
      }

      @Override
      public void cloneArray(VariableReader receiver, VariableReader array) {
         Object jvmArray = Interpreter.this.variables[array.getIndex()];
         int length = Array.getLength(jvmArray);
         Object copy = Array.newInstance(jvmArray.getClass().getComponentType(), length);

         for (int i = 0; i < length; i++) {
            Array.set(copy, i, Array.get(array, i));
         }

         Interpreter.this.variables[receiver.getIndex()] = copy;
      }

      @Override
      public void unwrapArray(VariableReader receiver, VariableReader array, ArrayElementType elementType) {
         Interpreter.this.variables[receiver.getIndex()] = Interpreter.this.variables[array.getIndex()];
      }

      @Override
      public void getElement(VariableReader receiver, VariableReader array, VariableReader index, ArrayElementType type) {
         Object jvmArray = Interpreter.this.variables[array.getIndex()];
         int indexValue = (Integer)Interpreter.this.variables[index.getIndex()];
         Interpreter.this.variables[receiver.getIndex()] = Array.get(jvmArray, indexValue);
      }

      @Override
      public void putElement(VariableReader array, VariableReader index, VariableReader value, ArrayElementType type) {
         Object jvmArray = Interpreter.this.variables[array.getIndex()];
         int indexValue = (Integer)Interpreter.this.variables[index.getIndex()];
         Array.set(jvmArray, indexValue, Interpreter.this.variables[value.getIndex()]);
      }

      @Override
      public void invoke(
         VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments, InvocationType type
      ) {
         Method jvmMethod = this.asJvmMethod(method);
         Object[] jvmArgs = new Object[arguments.size()];

         for (int i = 0; i < jvmArgs.length; i++) {
            jvmArgs[i] = Interpreter.this.variables[arguments.get(i).getIndex()];
         }

         Object jvmInstance = instance != null ? Interpreter.this.variables[instance.getIndex()] : null;

         Object result;
         try {
            result = jvmMethod.invoke(jvmInstance, jvmArgs);
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Error calling method " + method, e);
         }

         if (receiver != null) {
            Interpreter.this.variables[receiver.getIndex()] = result;
         }
      }

      private Method asJvmMethod(MethodReference method) {
         Class<?> cls;
         try {
            cls = Class.forName(method.getClassName(), false, Interpreter.this.classLoader);
         } catch (ClassNotFoundException e) {
            throw new RuntimeException("Can't find class " + method.getClassName());
         }

         Class<?>[] jvmParameters = new Class[method.parameterCount()];

         for (int i = 0; i < method.parameterCount(); i++) {
            jvmParameters[i] = this.asJvmClass(method.parameterType(i));
         }

         Class<?> jvmReturnType = this.asJvmClass(method.getReturnType());

         for (Method jvmMethod : cls.getDeclaredMethods()) {
            if (Arrays.equals(jvmMethod.getParameterTypes(), jvmParameters) && jvmReturnType.equals(jvmMethod.getReturnType())) {
               return jvmMethod;
            }
         }

         throw new RuntimeException("Method not found: " + method);
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
         throw new RuntimeException("InvokeDynamic is not supported");
      }

      @Override
      public void isInstance(VariableReader receiver, VariableReader value, ValueType type) {
         Object jvmValue = Interpreter.this.variables[value.getIndex()];
         Class<?> jvmType = this.asJvmClass(type);
         Interpreter.this.variables[receiver.getIndex()] = jvmType.isInstance(jvmValue);
      }

      @Override
      public void initClass(String className) {
         try {
            Class.forName(className);
         } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found: " + className);
         }
      }

      @Override
      public void nullCheck(VariableReader receiver, VariableReader value) {
         Object jvmValue = Interpreter.this.variables[value.getIndex()];
         if (jvmValue == null) {
            throw new NullPointerException();
         }

         Interpreter.this.variables[receiver.getIndex()] = jvmValue;
      }

      @Override
      public void monitorEnter(VariableReader objectRef) {
      }

      @Override
      public void monitorExit(VariableReader objectRef) {
      }

      @Override
      public void boundCheck(VariableReader receiver, VariableReader index, VariableReader array, boolean lower) {
         Interpreter.this.variables[receiver.getIndex()] = Interpreter.this.variables[index.getIndex()];
      }

      private Class<?> asJvmClass(ValueType type) {
         if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive)type).getKind()) {
               case BOOLEAN:
                  return boolean.class;
               case BYTE:
                  return byte.class;
               case SHORT:
                  return short.class;
               case CHARACTER:
                  return char.class;
               case INTEGER:
                  return int.class;
               case LONG:
                  return long.class;
               case FLOAT:
                  return float.class;
               case DOUBLE:
                  return double.class;
            }
         } else {
            if (type instanceof ValueType.Void) {
               return void.class;
            }

            if (type instanceof ValueType.Array) {
               Class<?> itemJvmClass = this.asJvmClass(((ValueType.Array)type).getItemType());
               return Array.newInstance(itemJvmClass, 0).getClass();
            }

            if (type instanceof ValueType.Object) {
               try {
                  Class.forName(((ValueType.Object)type).getClassName(), false, Interpreter.this.classLoader);
               } catch (ClassNotFoundException e) {
                  throw new IllegalArgumentException("Class not found: " + type);
               }
            }
         }

         throw new IllegalArgumentException("Unknown type: " + type);
      }
   };

   public Interpreter(ClassLoader classLoader) {
      this.classLoader = classLoader;
   }

   public Object interpret(ProgramReader program, Object[] parameters) throws InterpretException {
      this.variables = new Object[program.variableCount()];
      System.arraycopy(parameters, 0, this.variables, 0, parameters.length);
      this.currentBlock = program.basicBlockAt(0);
      this.state = Interpreter.State.EXECUTING;
      this.outgoings = new ArrayList<>();

      for (int i = 0; i < program.basicBlockCount(); i++) {
         this.outgoings.add(new ArrayList<>());
      }

      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlockReader block = program.basicBlockAt(i);

         for (PhiReader phi : block.readPhis()) {
            for (IncomingReader incoming : phi.readIncomings()) {
               this.outgoings.get(incoming.getSource().getIndex()).add(incoming);
            }
         }
      }

      try {
         while (true) {
            InstructionIterator iterator = this.currentBlock.iterateInstructions();

            try {
               while (iterator.hasNext()) {
                  iterator.next();
                  iterator.read(this.reader);
               }
            } catch (RuntimeException e) {
               if (!this.pickExceptionHandler(e)) {
                  throw new InterpretException(this.currentBlock, e);
               }
            }

            switch (this.state) {
               case EXECUTING:
               default:
                  break;
               case EXITED:
                  return this.result;
               case THROWN:
                  Throwable ex = (Throwable)this.result;
                  throw new InterpretException(this.currentBlock, ex);
            }
         }
      } finally {
         this.currentBlock = null;
         this.variables = null;
         this.outgoings = null;
         this.result = null;
      }
   }

   private boolean pickExceptionHandler(Throwable e) {
      for (TryCatchBlockReader tryCatch : this.currentBlock.readTryCatchBlocks()) {
         Class<?> exceptionType;
         try {
            exceptionType = tryCatch.getExceptionType() != null ? Class.forName(tryCatch.getExceptionType(), false, this.classLoader) : null;
         } catch (ClassNotFoundException cnfe) {
            throw new RuntimeException("Can't find exception class " + tryCatch.getExceptionType());
         }

         if (exceptionType == null || exceptionType.isInstance(e)) {
            this.currentBlock = tryCatch.getProtectedBlock();
            return true;
         }
      }

      return false;
   }

   private enum State {
      EXECUTING,
      EXITED,
      THROWN;
   }
}
