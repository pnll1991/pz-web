package org.teavm.model.util;

import java.util.List;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.common.IntegerStack;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.FieldReference;
import org.teavm.model.IncomingReader;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHandle;
import org.teavm.model.MethodReference;
import org.teavm.model.PhiReader;
import org.teavm.model.ProgramReader;
import org.teavm.model.RuntimeConstant;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.CastIntegerDirection;
import org.teavm.model.instructions.InstructionReader;
import org.teavm.model.instructions.IntegerSubtype;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.NumericOperandType;

public class TypeInferer {
   private static TypeInferer.InferenceKind[] typesByOrdinal = TypeInferer.InferenceKind.values();
   TypeInferer.InferenceType[] types;
   GraphBuilder builder;
   GraphBuilder arrayElemBuilder;
   InstructionReader reader = new AbstractInstructionReader() {
      @Override
      public void unwrapArray(VariableReader receiver, VariableReader array, ArrayElementType elementType) {
         TypeInferer.this.builder.addEdge(array.getIndex(), receiver.getIndex());
      }

      @Override
      public void stringConstant(VariableReader receiver, String cst) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.OBJECT, 0);
      }

      @Override
      public void nullCheck(VariableReader receiver, VariableReader value) {
         TypeInferer.this.builder.addEdge(value.getIndex(), receiver.getIndex());
      }

      @Override
      public void negate(VariableReader receiver, VariableReader operand, NumericOperandType type) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.this.convert(type), 0);
      }

      @Override
      public void longConstant(VariableReader receiver, long cst) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.LONG, 0);
      }

      @Override
      public void isInstance(VariableReader receiver, VariableReader value, ValueType type) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.BYTE, 0);
      }

      @Override
      public void invoke(
         VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments, InvocationType type
      ) {
         if (receiver != null) {
            TypeInferer.this.types[receiver.getIndex()] = TypeInferer.this.convert(method.getReturnType());
         }
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
         if (receiver != null) {
            TypeInferer.this.types[receiver.getIndex()] = TypeInferer.this.convert(method.getResultType());
         }
      }

      @Override
      public void integerConstant(VariableReader receiver, int cst) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.INT, 0);
      }

      @Override
      public void getField(VariableReader receiver, VariableReader instance, FieldReference field, ValueType fieldType) {
         TypeInferer.this.types[receiver.getIndex()] = TypeInferer.this.convert(fieldType);
      }

      @Override
      public void getElement(VariableReader receiver, VariableReader array, VariableReader index, ArrayElementType type) {
         TypeInferer.this.arrayElemBuilder.addEdge(array.getIndex(), receiver.getIndex());
      }

      @Override
      public void floatConstant(VariableReader receiver, float cst) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.FLOAT, 0);
      }

      @Override
      public void doubleConstant(VariableReader receiver, double cst) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.DOUBLE, 0);
      }

      @Override
      public void createArray(VariableReader receiver, ValueType itemType, List<? extends VariableReader> dimensions) {
         TypeInferer.this.types[receiver.getIndex()] = TypeInferer.this.convert(itemType);
      }

      @Override
      public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
         TypeInferer.this.types[receiver.getIndex()] = TypeInferer.this.convert(ValueType.arrayOf(itemType));
      }

      @Override
      public void create(VariableReader receiver, String type) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.OBJECT, 0);
      }

      @Override
      public void cloneArray(VariableReader receiver, VariableReader array) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.OBJECT, 0);
      }

      @Override
      public void classConstant(VariableReader receiver, ValueType cst) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.OBJECT, 0);
      }

      @Override
      public void cast(VariableReader receiver, VariableReader value, IntegerSubtype type, CastIntegerDirection targetType) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.BYTE, 0);
      }

      @Override
      public void cast(VariableReader receiver, VariableReader value, NumericOperandType sourceType, NumericOperandType targetType) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.this.convert(targetType), 0);
      }

      @Override
      public void cast(VariableReader receiver, VariableReader value, ValueType targetType, boolean weak) {
         TypeInferer.this.types[receiver.getIndex()] = TypeInferer.this.convert(targetType);
      }

      @Override
      public void binary(BinaryOperation op, VariableReader receiver, VariableReader first, VariableReader second, NumericOperandType type) {
         switch (op) {
            case COMPARE_LESS:
            case COMPARE_GREATER:
               TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.INT, 0);
               break;
            default:
               TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.this.convert(type), 0);
         }
      }

      @Override
      public void assign(VariableReader receiver, VariableReader assignee) {
         TypeInferer.this.builder.addEdge(assignee.getIndex(), receiver.getIndex());
      }

      @Override
      public void arrayLength(VariableReader receiver, VariableReader array) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.INT, 0);
      }

      @Override
      public void boundCheck(VariableReader receiver, VariableReader index, VariableReader array, boolean lower) {
         TypeInferer.this.types[receiver.getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.INT, 0);
      }
   };

   public void inferTypes(ProgramReader program, MethodReference method) {
      int sz = program.variableCount();
      this.types = new TypeInferer.InferenceType[sz];
      this.types[0] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.OBJECT, 0);

      for (int i = 0; i < method.parameterCount(); i++) {
         ValueType param = method.parameterType(i);
         this.types[i + 1] = this.convert(param);
      }

      this.builder = new GraphBuilder(sz);
      this.arrayElemBuilder = new GraphBuilder(sz);

      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlockReader block = program.basicBlockAt(i);
         if (block.getExceptionVariable() != null) {
            this.types[block.getExceptionVariable().getIndex()] = new TypeInferer.InferenceType(TypeInferer.InferenceKind.OBJECT, 0);
         }

         block.readAllInstructions(this.reader);

         for (PhiReader phi : block.readPhis()) {
            for (IncomingReader incoming : phi.readIncomings()) {
               this.builder.addEdge(incoming.getValue().getIndex(), phi.getReceiver().getIndex());
            }
         }
      }

      IntegerStack stack = new IntegerStack(sz * 2);
      Graph graph = this.builder.build();
      Graph arrayElemGraph = this.arrayElemBuilder.build();

      for (int i = 0; i < graph.size(); i++) {
         if (this.types[i] != null && graph.outgoingEdgesCount(i) > 0) {
            stack.push(this.types[i].kind.ordinal());
            stack.push(this.types[i].degree);
            stack.push(i);
            this.types[i] = null;
         }
      }

      while (!stack.isEmpty()) {
         int node = stack.pop();
         int degree = stack.pop();
         TypeInferer.InferenceKind kind = typesByOrdinal[stack.pop()];
         if (this.types[node] == null) {
            this.types[node] = new TypeInferer.InferenceType(kind, degree);

            for (int successor : graph.outgoingEdges(node)) {
               if (this.types[successor] == null) {
                  stack.push(kind.ordinal());
                  stack.push(degree);
                  stack.push(successor);
               }
            }

            for (int successor : arrayElemGraph.outgoingEdges(node)) {
               if (this.types[successor] == null) {
                  stack.push(kind.ordinal());
                  stack.push(degree - 1);
                  stack.push(successor);
               }
            }
         }
      }
   }

   public VariableType typeOf(int variableIndex) {
      TypeInferer.InferenceType result = this.types[variableIndex];
      if (result == null) {
         return VariableType.OBJECT;
      }

      if (result.degree == 0) {
         switch (result.kind) {
            case BYTE:
            case SHORT:
            case CHAR:
            case INT:
               return VariableType.INT;
            case LONG:
               return VariableType.LONG;
            case FLOAT:
               return VariableType.FLOAT;
            case DOUBLE:
               return VariableType.DOUBLE;
            case OBJECT:
            default:
               return VariableType.OBJECT;
         }
      } else {
         if (result.degree == 1) {
            switch (result.kind) {
               case BYTE:
                  return VariableType.BYTE_ARRAY;
               case SHORT:
                  return VariableType.SHORT_ARRAY;
               case CHAR:
                  return VariableType.CHAR_ARRAY;
               case INT:
                  return VariableType.INT_ARRAY;
               case LONG:
                  return VariableType.LONG_ARRAY;
               case FLOAT:
                  return VariableType.FLOAT_ARRAY;
               case DOUBLE:
                  return VariableType.DOUBLE_ARRAY;
               case OBJECT:
            }
         }

         return VariableType.OBJECT_ARRAY;
      }
   }

   TypeInferer.InferenceType convert(ValueType type) {
      int degree = 0;

      while (type instanceof ValueType.Array) {
         degree++;
         type = ((ValueType.Array)type).getItemType();
      }

      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
            case BYTE:
               return new TypeInferer.InferenceType(TypeInferer.InferenceKind.BYTE, degree);
            case SHORT:
               return new TypeInferer.InferenceType(TypeInferer.InferenceKind.SHORT, degree);
            case CHARACTER:
               return new TypeInferer.InferenceType(TypeInferer.InferenceKind.CHAR, degree);
            case INTEGER:
               return new TypeInferer.InferenceType(TypeInferer.InferenceKind.INT, degree);
            case FLOAT:
               return new TypeInferer.InferenceType(TypeInferer.InferenceKind.FLOAT, degree);
            case DOUBLE:
               return new TypeInferer.InferenceType(TypeInferer.InferenceKind.DOUBLE, degree);
            case LONG:
               return new TypeInferer.InferenceType(TypeInferer.InferenceKind.LONG, degree);
         }
      }

      return new TypeInferer.InferenceType(TypeInferer.InferenceKind.OBJECT, degree);
   }

   TypeInferer.InferenceKind convert(NumericOperandType type) {
      switch (type) {
         case INT:
            return TypeInferer.InferenceKind.INT;
         case LONG:
            return TypeInferer.InferenceKind.LONG;
         case FLOAT:
            return TypeInferer.InferenceKind.FLOAT;
         case DOUBLE:
            return TypeInferer.InferenceKind.DOUBLE;
         default:
            throw new AssertionError();
      }
   }

   enum InferenceKind {
      BYTE,
      SHORT,
      CHAR,
      INT,
      LONG,
      FLOAT,
      DOUBLE,
      OBJECT;
   }

   static class InferenceType {
      final TypeInferer.InferenceKind kind;
      final int degree;

      InferenceType(TypeInferer.InferenceKind kind, int degree) {
         this.kind = kind;
         this.degree = degree;
      }
   }
}
