package org.teavm.dependency;

import java.util.Arrays;
import java.util.List;
import org.teavm.common.DisjointSet;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.common.IntegerStack;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.hppc.cursors.IntCursor;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.FieldReference;
import org.teavm.model.IncomingReader;
import org.teavm.model.MethodReference;
import org.teavm.model.PhiReader;
import org.teavm.model.ProgramReader;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.InvocationType;

public class DataFlowGraphBuilder extends AbstractInstructionReader {
   private int lastIndex;
   private GraphBuilder builder = new GraphBuilder();
   private ObjectIntMap<FieldReference> fieldNodes = new ObjectIntHashMap();
   private int returnIndex = -1;
   private int exceptionIndex;
   private DisjointSet classes = new DisjointSet();
   private int paramCount;
   private IntSet escaping = new IntHashSet();

   private void join(int a, int b) {
      if (a >= this.paramCount && b >= this.paramCount) {
         this.classes.union(a, b);
      }
   }

   public int[] buildMapping(ProgramReader program, boolean[] significantParams, boolean needsReturn) {
      this.lastIndex = program.variableCount();
      this.paramCount = significantParams.length;
      if (needsReturn) {
         this.returnIndex = this.lastIndex++;
         this.escaping.add(this.returnIndex);
      }

      this.exceptionIndex = this.lastIndex++;

      for (int i = 0; i < this.lastIndex; i++) {
         this.classes.create();
      }

      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlockReader block = program.basicBlockAt(i);

         for (PhiReader phi : block.readPhis()) {
            for (IncomingReader incoming : phi.readIncomings()) {
               int from = incoming.getValue().getIndex();
               int to = phi.getReceiver().getIndex();
               this.builder.addEdge(from, to);
            }
         }

         block.readAllInstructions(this);
      }

      Graph graph = this.builder.build();

      for (int i = 0; i < this.paramCount; i++) {
         if (significantParams[i]) {
            this.escaping.add(i);
         }
      }

      this.propagateEscaping(graph);
      int[] classMap = new int[this.classes.size()];
      Arrays.fill(classMap, -1);
      int[] result = new int[program.variableCount()];
      int classCount = 0;

      for (int i = 0; i < program.variableCount(); i++) {
         if (!this.escaping.contains(i) && i >= significantParams.length) {
            result[i] = -1;
         } else {
            int cls = this.classes.find(i);
            int packedCls = classMap[cls];
            if (packedCls < 0) {
               packedCls = classCount++;
               classMap[cls] = packedCls;
            }

            result[i] = packedCls;
         }
      }

      return result;
   }

   private void propagateEscaping(Graph graph) {
      IntegerStack stack = new IntegerStack(graph.size());

      for (IntCursor node : this.escaping) {
         stack.push(node.value);
      }

      this.escaping.clear();

      while (!stack.isEmpty()) {
         int node = stack.pop();
         if (this.escaping.add(node) && node < graph.size()) {
            for (int pred : graph.incomingEdges(node)) {
               if (!this.escaping.contains(pred)) {
                  stack.push(pred);
               }
            }

            for (int succ : graph.outgoingEdges(node)) {
               if (!this.escaping.contains(succ)) {
                  stack.push(succ);
               }
            }
         }
      }
   }

   private void connect(int a, int b) {
      this.builder.addEdge(a, b);
      this.join(a, b);
   }

   @Override
   public void assign(VariableReader receiver, VariableReader assignee) {
      this.connect(assignee.getIndex(), receiver.getIndex());
   }

   @Override
   public void cast(VariableReader receiver, VariableReader value, ValueType targetType, boolean weak) {
      this.builder.addEdge(value.getIndex(), receiver.getIndex());
   }

   @Override
   public void exit(VariableReader valueToReturn) {
      if (valueToReturn != null && this.returnIndex >= 0) {
         this.builder.addEdge(valueToReturn.getIndex(), this.returnIndex);
      }
   }

   @Override
   public void raise(VariableReader exception) {
      this.builder.addEdge(exception.getIndex(), this.exceptionIndex);
      this.escaping.add(this.exceptionIndex);
   }

   private int getFieldNode(FieldReference field) {
      int fieldNode = this.fieldNodes.getOrDefault(field, -1);
      if (fieldNode < 0) {
         fieldNode = this.classes.create();
         this.fieldNodes.put(field, fieldNode);
      }

      this.escaping.add(fieldNode);
      return fieldNode;
   }

   @Override
   public void getField(VariableReader receiver, VariableReader instance, FieldReference field, ValueType fieldType) {
      if (!(fieldType instanceof ValueType.Primitive)) {
         this.builder.addEdge(this.getFieldNode(field), receiver.getIndex());
      }
   }

   @Override
   public void putField(VariableReader instance, FieldReference field, VariableReader value, ValueType fieldType) {
      if (!(fieldType instanceof ValueType.Primitive)) {
         this.builder.addEdge(value.getIndex(), this.getFieldNode(field));
      }
   }

   @Override
   public void cloneArray(VariableReader receiver, VariableReader array) {
      this.builder.addEdge(array.getIndex(), receiver.getIndex());
   }

   @Override
   public void unwrapArray(VariableReader receiver, VariableReader array, ArrayElementType elementType) {
      if (elementType == ArrayElementType.OBJECT) {
         this.connect(array.getIndex(), receiver.getIndex());
      }
   }

   @Override
   public void getElement(VariableReader receiver, VariableReader array, VariableReader index, ArrayElementType type) {
      this.builder.addEdge(array.getIndex(), receiver.getIndex());
   }

   @Override
   public void putElement(VariableReader array, VariableReader index, VariableReader value, ArrayElementType type) {
      this.builder.addEdge(value.getIndex(), array.getIndex());
   }

   @Override
   public void invoke(VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments, InvocationType type) {
      ValueType[] paramTypes = method.getParameterTypes();

      for (int i = 0; i < paramTypes.length; i++) {
         if (!(paramTypes[i] instanceof ValueType.Primitive)) {
            this.escaping.add(arguments.get(i).getIndex());
         }
      }

      if (instance != null) {
         this.escaping.add(instance.getIndex());
      }

      if (receiver != null && !(method.getReturnType() instanceof ValueType.Primitive)) {
         this.escaping.add(receiver.getIndex());
      }
   }

   @Override
   public void nullCheck(VariableReader receiver, VariableReader value) {
      this.connect(value.getIndex(), receiver.getIndex());
   }

   @Override
   public void monitorEnter(VariableReader objectRef) {
      this.escaping.add(objectRef.getIndex());
   }

   @Override
   public void monitorExit(VariableReader objectRef) {
      this.escaping.add(objectRef.getIndex());
   }
}
