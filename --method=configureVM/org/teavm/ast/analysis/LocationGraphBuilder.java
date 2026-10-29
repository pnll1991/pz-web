package org.teavm.ast.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.teavm.ast.AssignmentStatement;
import org.teavm.ast.BlockStatement;
import org.teavm.ast.BoundCheckExpr;
import org.teavm.ast.BreakStatement;
import org.teavm.ast.ConditionalExpr;
import org.teavm.ast.ConditionalStatement;
import org.teavm.ast.ContinueStatement;
import org.teavm.ast.ControlFlowEntry;
import org.teavm.ast.Expr;
import org.teavm.ast.IdentifiedStatement;
import org.teavm.ast.InitClassStatement;
import org.teavm.ast.MonitorEnterStatement;
import org.teavm.ast.MonitorExitStatement;
import org.teavm.ast.RecursiveVisitor;
import org.teavm.ast.ReturnStatement;
import org.teavm.ast.Statement;
import org.teavm.ast.SwitchClause;
import org.teavm.ast.SwitchStatement;
import org.teavm.ast.ThrowStatement;
import org.teavm.ast.TryCatchStatement;
import org.teavm.ast.WhileStatement;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.hppc.IntArrayDeque;
import org.teavm.hppc.IntArrayList;
import org.teavm.hppc.IntDeque;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.model.TextLocation;

public final class LocationGraphBuilder {
   private LocationGraphBuilder() {
   }

   public static ControlFlowEntry[] build(Statement node) {
      LocationGraphBuilder.Visitor visitor = new LocationGraphBuilder.Visitor();
      node.acceptVisitor(visitor);
      Graph graph = visitor.builder.build();

      for (int terminal : visitor.nodes) {
         visitor.terminalNodes.set(terminal);
      }

      TextLocation[][] locations = propagate(visitor.locations.toArray(new TextLocation[0]), graph);
      Map<TextLocation, Set<TextLocation>> builder = new LinkedHashMap<>();

      for (int i = 0; i < graph.size(); i++) {
         for (int j : graph.outgoingEdges(i)) {
            for (TextLocation from : locations[i]) {
               for (TextLocation to : locations[j]) {
                  builder.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
               }
            }
         }

         if (visitor.terminalNodes.get(i)) {
            for (TextLocation loc : locations[i]) {
               builder.computeIfAbsent(loc, k -> new LinkedHashSet<>()).add(null);
            }
         }
      }

      ControlFlowEntry[] result = new ControlFlowEntry[builder.size()];
      int index = 0;

      for (Entry<TextLocation, Set<TextLocation>> entry : builder.entrySet()) {
         result[index++] = new ControlFlowEntry(entry.getKey(), entry.getValue().toArray(new TextLocation[0]));
      }

      return result;
   }

   private static TextLocation[][] propagate(TextLocation[] locations, Graph graph) {
      List<Set<TextLocation>> result = new ArrayList<>();
      boolean[] stop = new boolean[graph.size()];
      IntDeque queue = new IntArrayDeque();

      for (int i = 0; i < stop.length; i++) {
         Set<TextLocation> set = new LinkedHashSet<>();
         result.add(set);
         if (locations[i] != null) {
            stop[i] = true;
            queue.addLast(i);
            set.add(locations[i]);
         }
      }

      while (!queue.isEmpty()) {
         int node = queue.removeFirst();

         for (int successor : graph.outgoingEdges(node)) {
            if (!stop[successor] && result.get(successor).addAll(result.get(node))) {
               queue.addLast(successor);
            }
         }
      }

      return result.stream().map(s -> s.toArray(new TextLocation[0])).toArray(TextLocation[][]::new);
   }

   static class Visitor extends RecursiveVisitor {
      static final int[] EMPTY = new int[0];
      int[] nodes = EMPTY;
      ObjectIntMap<IdentifiedStatement> breakNodes = new ObjectIntHashMap();
      ObjectIntMap<IdentifiedStatement> continueNodes = new ObjectIntHashMap();
      IdentifiedStatement defaultBreakTarget;
      IdentifiedStatement defaultContinueTarget;
      GraphBuilder builder = new GraphBuilder();
      List<TextLocation> locations = new ArrayList<>();
      BitSet terminalNodes = new BitSet();

      @Override
      protected void afterVisit(Expr expr) {
         this.setLocation(expr.getLocation());
      }

      @Override
      public void visit(BlockStatement statement) {
         int exit = this.createNode(null);
         this.breakNodes.put(statement, exit);
         super.visit(statement);
         this.breakNodes.remove(statement);
         this.setNode(exit);
      }

      @Override
      public void visit(WhileStatement statement) {
         IdentifiedStatement oldDefaultBreakTarget = this.defaultBreakTarget;
         IdentifiedStatement oldDefaultContinueTarget = this.defaultContinueTarget;
         int head = this.createNode(null);
         int exit = this.createNode(null);
         this.setNode(head);
         this.breakNodes.put(statement, exit);
         this.continueNodes.put(statement, head);
         this.defaultBreakTarget = statement;
         this.defaultContinueTarget = statement;
         if (statement.getCondition() != null) {
            statement.getCondition().acceptVisitor(this);
         }

         for (int node : this.nodes) {
            this.builder.addEdge(node, exit);
         }

         this.visit(statement.getBody());

         for (int node : this.nodes) {
            this.builder.addEdge(node, head);
         }

         this.nodes = new int[]{exit};
         this.defaultBreakTarget = oldDefaultBreakTarget;
         this.defaultContinueTarget = oldDefaultContinueTarget;
         this.breakNodes.remove(statement);
         this.continueNodes.remove(statement);
      }

      @Override
      public void visit(SwitchStatement statement) {
         IdentifiedStatement oldDefaultBreakTarget = this.defaultBreakTarget;
         int exit = this.createNode(null);
         this.breakNodes.put(statement, exit);
         this.defaultBreakTarget = statement;
         statement.getValue().acceptVisitor(this);
         int[] headNodes = this.nodes;

         for (SwitchClause clause : statement.getClauses()) {
            this.nodes = headNodes;
            this.visit(clause.getBody());

            for (int node : this.nodes) {
               this.builder.addEdge(node, exit);
            }
         }

         this.nodes = headNodes;
         this.visit(statement.getDefaultClause());

         for (int node : this.nodes) {
            this.builder.addEdge(node, exit);
         }

         this.nodes = new int[]{exit};
         this.defaultBreakTarget = oldDefaultBreakTarget;
         this.breakNodes.remove(statement);
      }

      @Override
      public void visit(ConditionalStatement statement) {
         statement.getCondition().acceptVisitor(this);
         IntArrayList exit = new IntArrayList();
         int[] head = this.nodes;
         this.visit(statement.getConsequent());
         exit.add(this.nodes);
         this.nodes = head;
         this.visit(statement.getAlternative());
         exit.add(this.nodes);
         this.nodes = this.distinct(exit);
      }

      private int[] distinct(IntArrayList list) {
         IntHashSet set = new IntHashSet();
         int j = 0;
         int[] result = new int[list.size()];

         for (int i = 0; i < list.size(); i++) {
            int e = list.get(i);
            if (set.add(e)) {
               result[j++] = e;
            }
         }

         if (j < result.length) {
            result = Arrays.copyOf(result, j);
         }

         return result;
      }

      @Override
      public void visit(BreakStatement statement) {
         IdentifiedStatement target = statement.getTarget();
         if (target == null) {
            target = this.defaultBreakTarget;
         }

         int targetNode = this.breakNodes.get(target);

         for (int node : this.nodes) {
            this.builder.addEdge(node, targetNode);
         }

         this.nodes = EMPTY;
      }

      @Override
      public void visit(ContinueStatement statement) {
         IdentifiedStatement target = statement.getTarget();
         if (target == null) {
            target = this.defaultContinueTarget;
         }

         int targetNode = this.continueNodes.get(target);

         for (int node : this.nodes) {
            this.builder.addEdge(node, targetNode);
         }

         this.nodes = EMPTY;
      }

      @Override
      public void visit(ThrowStatement statement) {
         super.visit(statement);
         this.setLocation(statement.getLocation());
         this.nodes = EMPTY;
      }

      @Override
      public void visit(ReturnStatement statement) {
         super.visit(statement);
         this.setLocation(statement.getLocation());

         for (int node : this.nodes) {
            this.terminalNodes.set(node);
         }

         this.nodes = EMPTY;
      }

      @Override
      public void visit(TryCatchStatement statement) {
         int catchNode = this.createNode(null);

         for (Statement s : statement.getProtectedBody()) {
            s.acceptVisitor(this);

            for (int node : this.nodes) {
               this.builder.addEdge(node, catchNode);
            }
         }

         this.nodes = new int[]{catchNode};
         this.visit(statement.getHandler());
      }

      @Override
      public void visit(AssignmentStatement statement) {
         super.visit(statement);
         this.setLocation(statement.getLocation());
      }

      @Override
      public void visit(InitClassStatement statement) {
         super.visit(statement);
         this.setLocation(statement.getLocation());
      }

      @Override
      public void visit(MonitorEnterStatement statement) {
         super.visit(statement);
         this.setLocation(statement.getLocation());
      }

      @Override
      public void visit(MonitorExitStatement statement) {
         super.visit(statement);
         this.setLocation(statement.getLocation());
      }

      @Override
      public void visit(ConditionalExpr expr) {
         expr.getCondition().acceptVisitor(this);
         IntArrayList exit = new IntArrayList();
         int[] head = this.nodes;
         expr.getConsequent().acceptVisitor(this);
         exit.add(this.nodes);
         this.nodes = head;
         expr.getAlternative().acceptVisitor(this);
         exit.add(this.nodes);
         this.nodes = this.distinct(exit);
      }

      @Override
      public void visit(BoundCheckExpr expr) {
         super.visit(expr);
         this.setLocation(expr.getLocation());
      }

      private void setNode(int node) {
         for (int prevNode : this.nodes) {
            this.builder.addEdge(prevNode, node);
         }

         this.nodes = new int[]{node};
      }

      private void setLocation(TextLocation location) {
         if (location != null && !location.isEmpty()) {
            int node = this.createNode(location);

            for (int prevNode : this.nodes) {
               this.builder.addEdge(prevNode, node);
            }

            this.nodes = new int[]{node};
         }
      }

      private int createNode(TextLocation location) {
         int index = this.locations.size();
         this.locations.add(location);
         return index;
      }
   }
}
