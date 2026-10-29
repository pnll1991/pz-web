package org.teavm.model.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.teavm.common.MutableGraphNode;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.Variable;

class InterferenceGraphBuilder {
   public List<MutableGraphNode> build(Program program, int paramCount, LivenessAnalyzer liveness) {
      List<MutableGraphNode> nodes = new ArrayList<>();

      for (int i = 0; i < program.variableCount(); i++) {
         nodes.add(new MutableGraphNode(i));
      }

      UsageExtractor useExtractor = new UsageExtractor();
      DefinitionExtractor defExtractor = new DefinitionExtractor();

      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlock block = program.basicBlockAt(i);
         BitSet live = liveness.liveOut(i);

         for (Instruction insn = block.getLastInstruction(); insn != null; insn = insn.getPrevious()) {
            insn.acceptVisitor(useExtractor);
            insn.acceptVisitor(defExtractor);

            for (Variable var : defExtractor.getDefinedVariables()) {
               this.connect(nodes, var.getIndex(), live);
            }

            for (Variable var : defExtractor.getDefinedVariables()) {
               live.clear(var.getIndex());
            }

            for (Variable var : useExtractor.getUsedVariables()) {
               live.set(var.getIndex());
            }
         }

         if (block.getExceptionVariable() != null) {
            this.connect(nodes, block.getExceptionVariable().getIndex(), live);
            live.clear(block.getExceptionVariable().getIndex());
         }

         if (block.getIndex() == 0) {
            for (int j = 0; j <= paramCount; j++) {
               this.connect(nodes, j, live);
            }
         }

         for (Phi phi : block.getPhis()) {
            this.connect(nodes, phi.getReceiver().getIndex(), live);
         }
      }

      return nodes;
   }

   private void connect(List<MutableGraphNode> nodes, int fromIndex, BitSet to) {
      MutableGraphNode from = nodes.get(fromIndex);
      List<MutableGraphNode> toList = new ArrayList<>(to.cardinality());

      for (int i = to.nextSetBit(0); i >= 0; i = to.nextSetBit(i + 1)) {
         toList.add(nodes.get(i));
      }

      from.connectAll(toList);
   }
}
