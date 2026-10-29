package org.teavm.model.util;

import org.teavm.common.DominatorTree;
import org.teavm.common.Graph;
import org.teavm.common.GraphUtils;
import org.teavm.hppc.IntStack;
import org.teavm.model.BasicBlock;
import org.teavm.model.Program;
import org.teavm.model.TryCatchBlock;

public class DominatorWalker {
   private Program program;
   private DominatorTree dom;
   private Graph cfg;
   private Graph domGraph;
   private int[] order;

   public DominatorWalker(Program program) {
      this.program = program;
      this.cfg = ProgramUtils.buildControlFlowGraph(program);
      this.dom = GraphUtils.buildDominatorTree(this.cfg);
      this.domGraph = GraphUtils.buildDominatorGraph(this.dom, this.cfg.size());
      this.order = this.dfs(this.cfg);
   }

   private int[] dfs(Graph graph) {
      if (graph.size() == 0) {
         return new int[0];
      }

      int index = 0;
      int[] result = new int[graph.size()];
      IntStack stack = new IntStack(graph.size());
      byte[] state = new byte[graph.size()];
      stack.push(0);

      while (!stack.isEmpty()) {
         int node = stack.pop();
         switch (state[node]) {
            case 0:
               state[node] = 1;
               stack.push(node);

               for (int succ : graph.outgoingEdges(node)) {
                  if (state[succ] == 0) {
                     stack.push(succ);
                  }
               }
               break;
            case 1:
               state[node] = 2;
               result[node] = index++;
         }
      }

      return result;
   }

   public <T> void walk(DominatorWalkerCallback<T> callback) {
      int[] stack = new int[this.program.basicBlockCount() * 2];
      Object[] stateStack = new Object[stack.length];
      boolean[] backward = new boolean[stack.length];
      DominatorWalker.ContextImpl context = new DominatorWalker.ContextImpl(this.dom, this.cfg, this.findExceptionHandlers());
      callback.setContext(context);
      int head = 1;

      while (head > 0) {
         int node = stack[--head];
         BasicBlock block = this.program.basicBlockAt(node);
         if (backward[head]) {
            T state = (T)stateStack[head];
            callback.endVisit(block, state);
            context.visited[block.getIndex()] = true;
         } else if (callback.filter(block)) {
            stack[head] = node;
            backward[head] = true;
            stateStack[head] = callback.visit(block);
            head++;
            int[] successors = this.domGraph.outgoingEdges(node);
            this.sort(successors);

            for (int i = successors.length - 1; i >= 0; i--) {
               stack[head] = successors[i];
               backward[head] = false;
               head++;
            }
         }
      }
   }

   private boolean[] findExceptionHandlers() {
      boolean[] exceptionHandlers = new boolean[this.program.basicBlockCount()];

      for (BasicBlock block : this.program.getBasicBlocks()) {
         for (TryCatchBlock tryCatch : block.getTryCatchBlocks()) {
            exceptionHandlers[tryCatch.getHandler().getIndex()] = true;
         }
      }

      return exceptionHandlers;
   }

   private void sort(int[] nodes) {
      for (int i = 0; i < nodes.length - 1; i++) {
         int a = nodes[i];
         int bestIndex = i;
         int best = this.order[a];

         for (int j = i + 1; j < nodes.length; j++) {
            int b = nodes[j];
            int score = this.order[b];
            if (score < best) {
               best = score;
               a = b;
               bestIndex = j;
            }
         }

         if (i != bestIndex) {
            nodes[bestIndex] = nodes[i];
            nodes[i] = a;
         }
      }
   }

   static class ContextImpl implements DominatorWalkerContext {
      private boolean[] visited;
      private boolean[] isExceptionHandler;
      private DominatorTree dom;
      private Graph cfg;

      ContextImpl(DominatorTree dom, Graph cfg, boolean[] isExceptionHandler) {
         this.dom = dom;
         this.cfg = cfg;
         this.isExceptionHandler = isExceptionHandler;
         this.visited = new boolean[cfg.size()];
      }

      @Override
      public DominatorTree getDominatorTree() {
         return this.dom;
      }

      @Override
      public Graph getControlFlowGraph() {
         return this.cfg;
      }

      @Override
      public boolean isVisited(int blockIndex) {
         return this.visited[blockIndex];
      }

      @Override
      public boolean isExceptionHandler(int blockIndex) {
         return this.isExceptionHandler[blockIndex];
      }
   }
}
