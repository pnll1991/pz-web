package org.teavm.model.analysis;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.hppc.IntStack;
import org.teavm.model.BasicBlock;
import org.teavm.model.FieldReference;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.InvokeDynamicInstruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BoundCheckInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.CastIntegerInstruction;
import org.teavm.model.instructions.CastNumberInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.NegateInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.PutElementInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;

public abstract class BaseTypeInference<T> {
   private Program program;
   private MethodReference reference;
   private Object[] types;
   private Graph graph;
   private Graph arrayGraph;
   private Graph backArrayGraph;
   private Graph arrayUnwrapGraph;
   private boolean phisSkipped;
   private boolean backPropagation;

   public BaseTypeInference(Program program, MethodReference reference) {
      this.program = program;
      this.reference = reference;
   }

   public void setPhisSkipped(boolean phisSkipped) {
      this.phisSkipped = phisSkipped;
   }

   public void setBackPropagation(boolean backPropagation) {
      this.backPropagation = backPropagation;
   }

   private void prepare() {
      this.types = new Object[this.program.variableCount()];
      BaseTypeInference<T>.InitialTypeVisitor visitor = new BaseTypeInference.InitialTypeVisitor(this.program.variableCount());
      int params = Math.min(this.reference.parameterCount(), this.program.variableCount() - 1);

      for (int i = 0; i < params; i++) {
         visitor.type(this.program.variableAt(i + 1), this.reference.parameterType(i));
      }

      visitor.type(this.program.variableAt(0), ValueType.object(this.reference.getClassName()));

      for (BasicBlock block : this.program.getBasicBlocks()) {
         for (Instruction insn : block) {
            insn.acceptVisitor(visitor);
         }

         if (!this.phisSkipped) {
            for (Phi phi : block.getPhis()) {
               for (Incoming incoming : phi.getIncomings()) {
                  visitor.graphBuilder.addEdge(incoming.getValue().getIndex(), phi.getReceiver().getIndex());
               }
            }
         }

         for (TryCatchBlock tryCatch : block.getTryCatchBlocks()) {
            Variable exceptionVar = tryCatch.getHandler().getExceptionVariable();
            if (exceptionVar != null) {
               ValueType.Object exceptionType = tryCatch.getExceptionType() != null
                  ? ValueType.object(tryCatch.getExceptionType())
                  : ValueType.object("java.lang.Throwable");
               visitor.type(exceptionVar, exceptionType);
            }
         }
      }

      this.graph = visitor.graphBuilder.build();
      this.arrayGraph = visitor.arrayGraphBuilder.build();
      this.backArrayGraph = visitor.backArrayGraphBuilder.build();
      this.arrayUnwrapGraph = visitor.arrayUnwrapGraphBuilder.build();
   }

   private void propagate() {
      IntStack stack = new IntStack();
      ArrayDeque<T> typeStack = new ArrayDeque<>();

      for (int i = 0; i < this.types.length; i++) {
         if (this.types[i] != null) {
            stack.push(i);
            typeStack.push((T)this.types[i]);
            this.types[i] = null;
         }
      }

      while (!stack.isEmpty()) {
         int variable = stack.pop();
         T type = typeStack.pop();
         T formerType = (T)this.types[variable];
         if (!Objects.equals(formerType, type)) {
            type = this.doMerge(type, formerType);
            if (!Objects.equals(type, formerType) && type != null) {
               this.types[variable] = type;

               for (int succ : this.graph.outgoingEdges(variable)) {
                  if (!Objects.equals(this.types[succ], type)) {
                     stack.push(succ);
                     typeStack.push(type);
                  }
               }

               for (int succ : this.arrayUnwrapGraph.outgoingEdges(variable)) {
                  if (!Objects.equals(this.types[succ], type)) {
                     stack.push(succ);
                     typeStack.push(this.arrayUnwrapType(type));
                  }
               }

               if (this.arrayGraph.outgoingEdgesCount(variable) > 0) {
                  T elementType = this.elementType(type);

                  for (int succ : this.arrayGraph.outgoingEdges(variable)) {
                     if (!Objects.equals(this.types[succ], elementType)) {
                        stack.push(succ);
                        typeStack.push(elementType);
                     }
                  }
               }
            }
         }
      }
   }

   private void propagateBack() {
      if (this.backPropagation) {
         boolean hasNullTypes = false;

         for (Object type : this.types) {
            if (type == null) {
               hasNullTypes = true;
               break;
            }
         }

         if (hasNullTypes) {
            boolean[] nullTypes = new boolean[this.program.variableCount()];

            for (int i = 0; i < this.types.length; i++) {
               nullTypes[i] = this.types[i] == null;
            }

            IntStack stack = new IntStack();
            ArrayDeque<T> typeStack = new ArrayDeque<>();

            for (int i = 0; i < this.types.length; i++) {
               if (nullTypes[i]) {
                  for (int j : this.graph.outgoingEdges(i)) {
                     if (!nullTypes[j]) {
                        typeStack.push((T)this.types[j]);
                        stack.push(i);
                     }
                  }

                  for (int j : this.backArrayGraph.incomingEdges(i)) {
                     if (!nullTypes[j]) {
                        typeStack.push(this.elementType((T)this.types[j]));
                        stack.push(i);
                     }
                  }
               }
            }

            BaseTypeInference<T>.BackPropagationVisitor visitor = new BaseTypeInference.BackPropagationVisitor(nullTypes, stack, typeStack);

            for (BasicBlock block : this.program.getBasicBlocks()) {
               for (Instruction insn : block) {
                  insn.acceptVisitor(visitor);
               }
            }

            while (!stack.isEmpty()) {
               int variable = stack.pop();
               T type = typeStack.pop();
               T formerType = (T)this.types[variable];
               if (!Objects.equals(formerType, type)) {
                  type = this.doMerge(type, formerType);
                  if (!Objects.equals(type, formerType) && type != null) {
                     this.types[variable] = type;

                     for (int pred : this.graph.incomingEdges(variable)) {
                        if (nullTypes[pred] && !Objects.equals(this.types[pred], type)) {
                           stack.push(pred);
                           typeStack.push(type);
                        }
                     }

                     for (int pred : this.arrayUnwrapGraph.incomingEdges(variable)) {
                        if (nullTypes[pred]) {
                           T wrapType = this.arrayWrapType(type);
                           if (!Objects.equals(this.types[pred], wrapType)) {
                              stack.push(pred);
                              typeStack.push(wrapType);
                           }
                        }
                     }

                     if (this.arrayGraph.incomingEdgesCount(variable) > 0) {
                        T arrayType = this.arrayUnwrapType(this.arrayType(type));

                        for (int pred : this.arrayGraph.incomingEdges(variable)) {
                           if (nullTypes[pred] && !Objects.equals(this.types[pred], arrayType)) {
                              stack.push(pred);
                              typeStack.push(arrayType);
                           }
                        }
                     }

                     if (this.backArrayGraph.outgoingEdgesCount(variable) > 0) {
                        T elementType = this.elementType(type);

                        for (int succ : this.backArrayGraph.outgoingEdges(variable)) {
                           if (nullTypes[succ] && !Objects.equals(this.types[succ], elementType)) {
                              stack.push(succ);
                              typeStack.push(elementType);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public void ensure() {
      if (this.types == null) {
         this.prepare();
         this.propagate();
         this.propagateBack();
      }
   }

   public T typeOf(Variable variable) {
      return this.typeOf(variable.getIndex());
   }

   public T typeOf(int index) {
      this.ensure();
      return (T)this.types[index];
   }

   protected abstract T mapType(ValueType var1);

   protected abstract T nullType();

   private T doMerge(T a, T b) {
      if (a == null) {
         return b;
      } else {
         return b == null ? a : this.merge(a, b);
      }
   }

   protected abstract T merge(T var1, T var2);

   protected abstract T elementType(T var1);

   protected T arrayType(T t) {
      throw new UnsupportedOperationException();
   }

   protected T methodReturnType(InvocationType invocationType, MethodReference methodRef) {
      return this.mapType(methodRef.getReturnType());
   }

   protected T fieldType(FieldReference fieldRef, ValueType type) {
      return this.mapType(type);
   }

   protected T arrayUnwrapType(T type) {
      return type;
   }

   protected T arrayWrapType(T type) {
      return type;
   }

   private class BackPropagationVisitor extends AbstractInstructionVisitor {
      private boolean[] nullTypes;
      private IntStack stack;
      private Deque<T> typeStack;

      BackPropagationVisitor(boolean[] nullTypes, IntStack stack, Deque<T> typeStack) {
         this.nullTypes = nullTypes;
         this.stack = stack;
         this.typeStack = typeStack;
      }

      @Override
      public void visit(ExitInstruction insn) {
         if (insn.getValueToReturn() != null) {
            this.push(insn.getValueToReturn(), BaseTypeInference.this.reference.getReturnType());
         }
      }

      @Override
      public void visit(InvokeInstruction insn) {
         if (insn.getInstance() != null) {
            this.push(insn.getInstance(), ValueType.object(insn.getMethod().getClassName()));
         }

         for (int i = 0; i < insn.getArguments().size(); i++) {
            this.push(insn.getArguments().get(i), insn.getMethod().parameterType(i));
         }
      }

      @Override
      public void visit(GetFieldInstruction insn) {
         if (insn.getInstance() != null) {
            this.push(insn.getInstance(), ValueType.object(insn.getField().getClassName()));
         }
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         if (insn.getInstance() != null) {
            this.push(insn.getInstance(), ValueType.object(insn.getField().getClassName()));
         }

         this.push(insn.getValue(), insn.getFieldType());
      }

      @Override
      public void visit(CastInstruction insn) {
         this.push(insn.getValue(), insn.getTargetType());
      }

      @Override
      public void visit(RaiseInstruction insn) {
         this.push(insn.getException(), ValueType.object("java.lang.Throwable"));
      }

      private void push(Variable variable, ValueType type) {
         if (this.nullTypes[variable.getIndex()]) {
            this.stack.push(variable.getIndex());
            this.typeStack.push(BaseTypeInference.this.mapType(type));
         }
      }
   }

   private class InitialTypeVisitor extends AbstractInstructionVisitor {
      private GraphBuilder graphBuilder;
      private GraphBuilder arrayGraphBuilder;
      private GraphBuilder backArrayGraphBuilder;
      private GraphBuilder arrayUnwrapGraphBuilder;

      InitialTypeVisitor(int size) {
         this.graphBuilder = new GraphBuilder(size);
         this.arrayGraphBuilder = new GraphBuilder(size);
         this.backArrayGraphBuilder = new GraphBuilder(size);
         this.arrayUnwrapGraphBuilder = new GraphBuilder(size);
      }

      @Override
      public void visit(NullConstantInstruction insn) {
         this.type(insn.getReceiver(), BaseTypeInference.this.nullType());
      }

      @Override
      public void visit(IntegerConstantInstruction insn) {
         this.type(insn.getReceiver(), ValueType.INTEGER);
      }

      @Override
      public void visit(LongConstantInstruction insn) {
         this.type(insn.getReceiver(), ValueType.LONG);
      }

      @Override
      public void visit(FloatConstantInstruction insn) {
         this.type(insn.getReceiver(), ValueType.FLOAT);
      }

      @Override
      public void visit(DoubleConstantInstruction insn) {
         this.type(insn.getReceiver(), ValueType.DOUBLE);
      }

      @Override
      public void visit(ClassConstantInstruction insn) {
         this.type(insn.getReceiver(), ValueType.object("java.lang.Class"));
      }

      @Override
      public void visit(StringConstantInstruction insn) {
         this.type(insn.getReceiver(), ValueType.object("java.lang.String"));
      }

      @Override
      public void visit(ConstructInstruction insn) {
         this.type(insn.getReceiver(), ValueType.object(insn.getType()));
      }

      @Override
      public void visit(ConstructArrayInstruction insn) {
         this.type(insn.getReceiver(), ValueType.arrayOf(insn.getItemType()));
      }

      @Override
      public void visit(ConstructMultiArrayInstruction insn) {
         ValueType type = insn.getItemType();
         this.type(insn.getReceiver(), type);
      }

      @Override
      public void visit(IsInstanceInstruction insn) {
         this.type(insn.getReceiver(), ValueType.BOOLEAN);
      }

      @Override
      public void visit(CastInstruction insn) {
         this.type(insn.getReceiver(), insn.getTargetType());
      }

      @Override
      public void visit(NegateInstruction insn) {
         this.type(insn.getReceiver(), insn.getOperandType());
      }

      @Override
      public void visit(CastNumberInstruction insn) {
         this.type(insn.getReceiver(), insn.getTargetType());
      }

      @Override
      public void visit(BinaryInstruction insn) {
         if (insn.getOperation() != BinaryOperation.COMPARE_GREATER && insn.getOperation() != BinaryOperation.COMPARE_LESS) {
            this.type(insn.getReceiver(), insn.getOperandType());
         } else {
            this.type(insn.getReceiver(), ValueType.INTEGER);
         }
      }

      @Override
      public void visit(CastIntegerInstruction insn) {
         switch (insn.getTargetType()) {
            case BYTE:
               this.type(insn.getReceiver(), ValueType.BYTE);
               break;
            case CHAR:
               this.type(insn.getReceiver(), ValueType.CHARACTER);
               break;
            case SHORT:
               this.type(insn.getReceiver(), ValueType.SHORT);
         }
      }

      @Override
      public void visit(ArrayLengthInstruction insn) {
         this.type(insn.getReceiver(), ValueType.INTEGER);
      }

      @Override
      public void visit(CloneArrayInstruction insn) {
         this.type(insn.getReceiver(), ValueType.object("java.lang.Object"));
      }

      @Override
      public void visit(BoundCheckInstruction insn) {
         this.type(insn.getReceiver(), ValueType.INTEGER);
      }

      @Override
      public void visit(InvokeInstruction insn) {
         this.type(insn.getReceiver(), BaseTypeInference.this.methodReturnType(insn.getType(), insn.getMethod()));
      }

      @Override
      public void visit(InvokeDynamicInstruction insn) {
         this.type(insn.getReceiver(), insn.getMethod().getResultType());
      }

      @Override
      public void visit(GetFieldInstruction insn) {
         this.type(insn.getReceiver(), BaseTypeInference.this.fieldType(insn.getField(), insn.getFieldType()));
      }

      @Override
      public void visit(UnwrapArrayInstruction insn) {
         this.arrayUnwrapGraphBuilder.addEdge(insn.getArray().getIndex(), insn.getReceiver().getIndex());
      }

      @Override
      public void visit(GetElementInstruction insn) {
         this.arrayGraphBuilder.addEdge(insn.getArray().getIndex(), insn.getReceiver().getIndex());
      }

      @Override
      public void visit(PutElementInstruction insn) {
         this.backArrayGraphBuilder.addEdge(insn.getArray().getIndex(), insn.getValue().getIndex());
      }

      @Override
      public void visit(AssignInstruction insn) {
         this.graphBuilder.addEdge(insn.getAssignee().getIndex(), insn.getReceiver().getIndex());
      }

      @Override
      public void visit(NullCheckInstruction insn) {
         this.graphBuilder.addEdge(insn.getValue().getIndex(), insn.getReceiver().getIndex());
      }

      void type(Variable target, NumericOperandType type) {
         switch (type) {
            case INT:
               this.type(target, ValueType.INTEGER);
               break;
            case LONG:
               this.type(target, ValueType.LONG);
               break;
            case FLOAT:
               this.type(target, ValueType.FLOAT);
               break;
            case DOUBLE:
               this.type(target, ValueType.DOUBLE);
         }
      }

      void type(Variable target, ValueType type) {
         if (target != null) {
            T t = BaseTypeInference.this.mapType(type);
            if (t != null) {
               if (BaseTypeInference.this.types[target.getIndex()] != null) {
                  t = BaseTypeInference.this.merge((T)BaseTypeInference.this.types[target.getIndex()], t);
               }

               BaseTypeInference.this.types[target.getIndex()] = t;
            }
         }
      }

      void type(Variable target, T type) {
         if (target != null && type != null) {
            if (BaseTypeInference.this.types[target.getIndex()] != null) {
               type = BaseTypeInference.this.merge((T)BaseTypeInference.this.types[target.getIndex()], type);
            }

            BaseTypeInference.this.types[target.getIndex()] = type;
         }
      }
   }
}
