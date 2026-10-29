package org.teavm.ast.optimization;

import java.util.BitSet;
import org.teavm.ast.AsyncMethodNode;
import org.teavm.ast.AsyncMethodPart;
import org.teavm.ast.RegularMethodNode;
import org.teavm.common.Graph;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.util.AsyncProgramSplitter;
import org.teavm.model.util.DefinitionExtractor;
import org.teavm.model.util.NonSsaLivenessAnalyzer;
import org.teavm.model.util.ProgramUtils;
import org.teavm.model.util.UsageExtractor;

public class Optimizer {
   private ClassReaderSource classes;

   public Optimizer(ClassReaderSource classes) {
      this.classes = classes;
   }

   public void optimize(RegularMethodNode method, Program program, boolean friendlyToDebugger) {
      ReadWriteStatsBuilder stats = new ReadWriteStatsBuilder(method.getVariables().size());
      stats.analyze(program);
      this.applyParametersToWriteStats(stats, method.getReference());
      boolean[] preservedVars = new boolean[stats.writes.length];
      BreakEliminator breakEliminator = new BreakEliminator();
      breakEliminator.eliminate(method.getBody());
      if (friendlyToDebugger) {
         for (int i = 0; i < method.getVariables().size(); i++) {
            if (method.getVariables().get(i).getName() != null) {
               preservedVars[i] = true;
            }
         }
      }

      OptimizingVisitor optimizer = new OptimizingVisitor(preservedVars, stats.writes, stats.reads, stats.constants, friendlyToDebugger, this.classes);
      method.getBody().acceptVisitor(optimizer);
      method.setBody(optimizer.resultStmt);
      int paramCount = method.getReference().parameterCount();
      UnusedVariableEliminator unusedEliminator = new UnusedVariableEliminator(paramCount, method.getVariables());
      method.getBody().acceptVisitor(unusedEliminator);
      method.getVariables().clear();
      method.getVariables().addAll(unusedEliminator.getReorderedVariables());
      method.getBody().acceptVisitor(new RedundantLabelEliminator());
      method.getBody().acceptVisitor(new RedundantReturnElimination());

      for (int i = 0; i < method.getVariables().size(); i++) {
         method.getVariables().get(i).setIndex(i);
      }
   }

   public void optimize(AsyncMethodNode method, AsyncProgramSplitter splitter, boolean friendlyToDebugger) {
      NonSsaLivenessAnalyzer liveness = new NonSsaLivenessAnalyzer();
      liveness.analyze(splitter.getOriginalProgram(), method.getReference().getDescriptor());
      Graph cfg = ProgramUtils.buildControlFlowGraph(splitter.getOriginalProgram());
      boolean[] preservedVars = new boolean[method.getVariables().size()];

      for (int i = 0; i < splitter.size(); i++) {
         this.findEscapingLiveVars(liveness, cfg, splitter, i, preservedVars);
      }

      for (int i = 0; i < splitter.size(); i++) {
         ReadWriteStatsBuilder stats = new ReadWriteStatsBuilder(method.getVariables().size());
         stats.analyze(splitter.getProgram(i));
         this.applyParametersToWriteStats(stats, method.getReference());
         AsyncMethodPart part = method.getBody().get(i);
         BreakEliminator breakEliminator = new BreakEliminator();
         breakEliminator.eliminate(part.getStatement());
         OptimizingVisitor optimizer = new OptimizingVisitor(preservedVars, stats.writes, stats.reads, stats.constants, friendlyToDebugger, this.classes);
         part.getStatement().acceptVisitor(optimizer);
         part.setStatement(optimizer.resultStmt);
      }

      int paramCount = method.getReference().parameterCount();
      UnusedVariableEliminator unusedEliminator = new UnusedVariableEliminator(paramCount, method.getVariables());

      for (AsyncMethodPart part : method.getBody()) {
         part.getStatement().acceptVisitor(unusedEliminator);
      }

      method.getVariables().clear();
      method.getVariables().addAll(unusedEliminator.getReorderedVariables());
      RedundantLabelEliminator labelEliminator = new RedundantLabelEliminator();

      for (AsyncMethodPart part : method.getBody()) {
         part.getStatement().acceptVisitor(labelEliminator);
      }

      for (int i = 0; i < method.getVariables().size(); i++) {
         method.getVariables().get(i).setIndex(i);
      }
   }

   private void applyParametersToWriteStats(ReadWriteStatsBuilder stats, MethodReference method) {
      for (int i = 0; i <= method.parameterCount(); i++) {
         stats.writes[i]++;
      }
   }

   private void findEscapingLiveVars(NonSsaLivenessAnalyzer liveness, Graph cfg, AsyncProgramSplitter splitter, int partIndex, boolean[] output) {
      Program originalProgram = splitter.getOriginalProgram();
      Program program = splitter.getProgram(partIndex);
      int[] successors = splitter.getBlockSuccessors(partIndex);
      Instruction[] splitPoints = splitter.getSplitPoints(partIndex);
      int[] originalBlocks = splitter.getOriginalBlocks(partIndex);

      for (int i = 0; i < program.basicBlockCount(); i++) {
         if (successors[i] >= 0 && originalBlocks[i] >= 0) {
            BitSet liveVars = new BitSet();

            for (int succ : cfg.outgoingEdges(originalBlocks[i])) {
               liveVars.or(liveness.liveIn(succ));
            }

            DefinitionExtractor defExtractor = new DefinitionExtractor();
            UsageExtractor useExtractor = new UsageExtractor();
            BasicBlock block = originalProgram.basicBlockAt(originalBlocks[i]);
            Instruction splitPoint = splitPoints[i].getPrevious();

            for (Instruction insn = block.getLastInstruction(); insn != splitPoint; insn = insn.getPrevious()) {
               insn.acceptVisitor(defExtractor);
               insn.acceptVisitor(useExtractor);

               for (Variable var : defExtractor.getDefinedVariables()) {
                  liveVars.clear(var.getIndex());
               }

               for (Variable var : useExtractor.getUsedVariables()) {
                  liveVars.set(var.getIndex());
               }
            }

            for (int j = liveVars.nextSetBit(0); j >= 0; j = liveVars.nextSetBit(j + 1)) {
               output[j] = true;
            }
         }
      }
   }
}
