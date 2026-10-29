package org.teavm.model.lowlevel;

import java.util.List;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.hppc.IntArrayDeque;
import org.teavm.hppc.IntDeque;
import org.teavm.interop.Address;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.FieldReference;
import org.teavm.model.IncomingReader;
import org.teavm.model.MethodReference;
import org.teavm.model.PhiReader;
import org.teavm.model.ProgramReader;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.model.instructions.InvocationType;

public class NativePointerFinder {
   private Characteristics characteristics;

   public NativePointerFinder(Characteristics characteristics) {
      this.characteristics = characteristics;
   }

   public boolean[] findNativePointers(MethodReference method, ProgramReader program) {
      IntDeque stack = new IntArrayDeque();

      for (int i = 0; i < method.parameterCount(); i++) {
         if (this.isNativeType(method.parameterType(i))) {
            stack.addLast(i + 1);
         }
      }

      NativePointerFinder.Analyzer analyzer = new NativePointerFinder.Analyzer(program.variableCount(), stack);

      for (BasicBlockReader block : program.getBasicBlocks()) {
         for (PhiReader phi : block.readPhis()) {
            for (IncomingReader incoming : phi.readIncomings()) {
               analyzer.assignmentGraph.addEdge(incoming.getValue().getIndex(), phi.getReceiver().getIndex());
            }
         }

         block.readAllInstructions(analyzer);
      }

      boolean[] result = new boolean[program.variableCount()];
      Graph graph = analyzer.assignmentGraph.build();

      while (!stack.isEmpty()) {
         int v = stack.removeLast();
         if (!result[v]) {
            result[v] = true;

            for (int succ : graph.outgoingEdges(v)) {
               if (!result[succ]) {
                  stack.addLast(succ);
               }
            }
         }
      }

      return result;
   }

   private boolean isNativeType(ValueType type) {
      if (!(type instanceof ValueType.Object)) {
         return false;
      }

      String className = ((ValueType.Object)type).getClassName();
      return this.characteristics.isStructure(className)
         || className.equals(Address.class.getName())
         || this.characteristics.isFunction(className)
         || this.characteristics.isResource(className)
         || this.characteristics.isReflectionSpecial(className);
   }

   class Analyzer extends AbstractInstructionReader {
      GraphBuilder assignmentGraph;
      IntDeque steps;

      Analyzer(int variableCount, IntDeque steps) {
         this.assignmentGraph = new GraphBuilder(variableCount);
         this.steps = steps;
      }

      @Override
      public void assign(VariableReader receiver, VariableReader assignee) {
         this.assignmentGraph.addEdge(assignee.getIndex(), receiver.getIndex());
      }

      @Override
      public void cast(VariableReader receiver, VariableReader value, ValueType targetType, boolean weak) {
         this.assignmentGraph.addEdge(value.getIndex(), receiver.getIndex());
      }

      @Override
      public void invoke(
         VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments, InvocationType type
      ) {
         if (receiver != null && NativePointerFinder.this.isNativeType(method.getReturnType())) {
            this.steps.addLast(receiver.getIndex());
         }
      }

      @Override
      public void getField(VariableReader receiver, VariableReader instance, FieldReference field, ValueType fieldType) {
         if (NativePointerFinder.this.isNativeType(fieldType)) {
            this.steps.addLast(receiver.getIndex());
         }
      }
   }
}
