package org.teavm.backend.c.analyze;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.ast.AssignmentStatement;
import org.teavm.ast.BlockStatement;
import org.teavm.ast.BreakStatement;
import org.teavm.ast.ConditionalStatement;
import org.teavm.ast.ContinueStatement;
import org.teavm.ast.IdentifiedStatement;
import org.teavm.ast.RecursiveVisitor;
import org.teavm.ast.ReturnStatement;
import org.teavm.ast.Statement;
import org.teavm.ast.SwitchClause;
import org.teavm.ast.SwitchStatement;
import org.teavm.ast.ThrowStatement;
import org.teavm.ast.TryCatchStatement;
import org.teavm.ast.VariableExpr;
import org.teavm.ast.WhileStatement;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntIntHashMap;
import org.teavm.hppc.IntIntMap;
import org.teavm.hppc.IntSet;
import org.teavm.hppc.IntStack;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.hppc.cursors.IntCursor;

public class AstDefinitionUsageAnalysis {
   private GraphBuilder graphBuilder = new GraphBuilder();
   private Graph cfg;
   private List<AstDefinitionUsageAnalysis.Node> nodes = new ArrayList<>();
   private int lastNode = -1;
   private IdentifiedStatement defaultBreakTarget;
   private IdentifiedStatement defaultContinueTarget;
   private ObjectIntMap<IdentifiedStatement> breakTargets = new ObjectIntHashMap();
   private ObjectIntMap<IdentifiedStatement> continueTargets = new ObjectIntHashMap();
   private List<AstDefinitionUsageAnalysis.Definition> definitions = new ArrayList<>();
   private List<? extends AstDefinitionUsageAnalysis.Definition> readonlyDefinitions = Collections.unmodifiableList(this.definitions);
   private ObjectIntMap<AstDefinitionUsageAnalysis.Definition> definitionIds = new ObjectIntHashMap();
   private Map<AssignmentStatement, AstDefinitionUsageAnalysis.Definition> definitionsByStatements = new HashMap<>();
   private List<AstDefinitionUsageAnalysis.Usage> usages = new ArrayList<>();
   private IntStack stack = new IntStack();
   private IntStack exceptionHandlerStack = new IntStack();
   private RecursiveVisitor visitor = new RecursiveVisitor() {
      @Override
      public void visit(ConditionalStatement statement) {
         statement.getCondition().acceptVisitor(this);
         int forkNode = AstDefinitionUsageAnalysis.this.createNode();
         int joinNode = AstDefinitionUsageAnalysis.this.createNode();
         AstDefinitionUsageAnalysis.this.connect(AstDefinitionUsageAnalysis.this.lastNode, forkNode);
         AstDefinitionUsageAnalysis.this.lastNode = forkNode;
         this.visit(statement.getConsequent());
         AstDefinitionUsageAnalysis.this.connect(AstDefinitionUsageAnalysis.this.lastNode, joinNode);
         AstDefinitionUsageAnalysis.this.lastNode = forkNode;
         this.visit(statement.getAlternative());
         AstDefinitionUsageAnalysis.this.connect(AstDefinitionUsageAnalysis.this.lastNode, joinNode);
         AstDefinitionUsageAnalysis.this.lastNode = joinNode;
      }

      @Override
      public void visit(SwitchStatement statement) {
         IdentifiedStatement oldDefaultBreakTarget = AstDefinitionUsageAnalysis.this.defaultBreakTarget;
         AstDefinitionUsageAnalysis.this.defaultBreakTarget = statement;
         statement.getValue().acceptVisitor(this);
         int forkNode = AstDefinitionUsageAnalysis.this.createNode();
         int joinNode = AstDefinitionUsageAnalysis.this.createNode();
         AstDefinitionUsageAnalysis.this.connect(AstDefinitionUsageAnalysis.this.lastNode, forkNode);

         for (SwitchClause clause : statement.getClauses()) {
            AstDefinitionUsageAnalysis.this.lastNode = forkNode;
            this.visit(clause.getBody());
            AstDefinitionUsageAnalysis.this.connect(AstDefinitionUsageAnalysis.this.lastNode, joinNode);
         }

         AstDefinitionUsageAnalysis.this.lastNode = forkNode;
         this.visit(statement.getDefaultClause());
         AstDefinitionUsageAnalysis.this.connect(AstDefinitionUsageAnalysis.this.lastNode, joinNode);
         AstDefinitionUsageAnalysis.this.lastNode = joinNode;
         AstDefinitionUsageAnalysis.this.defaultBreakTarget = oldDefaultBreakTarget;
      }

      @Override
      public void visit(WhileStatement statement) {
         IdentifiedStatement oldDefaultBreakTarget = AstDefinitionUsageAnalysis.this.defaultBreakTarget;
         IdentifiedStatement oldDefaultContinueTarget = AstDefinitionUsageAnalysis.this.defaultContinueTarget;
         AstDefinitionUsageAnalysis.this.defaultBreakTarget = statement;
         AstDefinitionUsageAnalysis.this.defaultContinueTarget = statement;
         int continueNode = AstDefinitionUsageAnalysis.this.createNode();
         int forkNode = AstDefinitionUsageAnalysis.this.createNode();
         int breakNode = AstDefinitionUsageAnalysis.this.createNode();
         AstDefinitionUsageAnalysis.this.breakTargets.put(statement, breakNode);
         AstDefinitionUsageAnalysis.this.continueTargets.put(statement, continueNode);
         AstDefinitionUsageAnalysis.this.connect(AstDefinitionUsageAnalysis.this.lastNode, continueNode);
         AstDefinitionUsageAnalysis.this.lastNode = continueNode;
         if (statement.getCondition() != null) {
            statement.getCondition().acceptVisitor(this);
         }

         AstDefinitionUsageAnalysis.this.connect(AstDefinitionUsageAnalysis.this.lastNode, forkNode);
         AstDefinitionUsageAnalysis.this.connect(forkNode, breakNode);
         AstDefinitionUsageAnalysis.this.lastNode = forkNode;
         this.visit(statement.getBody());
         AstDefinitionUsageAnalysis.this.connect(AstDefinitionUsageAnalysis.this.lastNode, continueNode);
         AstDefinitionUsageAnalysis.this.lastNode = breakNode;
         AstDefinitionUsageAnalysis.this.breakTargets.remove(statement);
         AstDefinitionUsageAnalysis.this.continueTargets.remove(statement);
         AstDefinitionUsageAnalysis.this.defaultBreakTarget = oldDefaultBreakTarget;
         AstDefinitionUsageAnalysis.this.defaultContinueTarget = oldDefaultContinueTarget;
      }

      @Override
      public void visit(BlockStatement statement) {
         int breakNode = AstDefinitionUsageAnalysis.this.createNode();
         AstDefinitionUsageAnalysis.this.breakTargets.put(statement, breakNode);
         this.visit(statement.getBody());
         AstDefinitionUsageAnalysis.this.connect(AstDefinitionUsageAnalysis.this.lastNode, breakNode);
         AstDefinitionUsageAnalysis.this.lastNode = breakNode;
         AstDefinitionUsageAnalysis.this.breakTargets.remove(statement);
      }

      @Override
      public void visit(TryCatchStatement statement) {
         int nodeAfterBody = AstDefinitionUsageAnalysis.this.createNode();
         int handlerNode = AstDefinitionUsageAnalysis.this.createNode();
         int nodeAfter = AstDefinitionUsageAnalysis.this.createNode();
         AstDefinitionUsageAnalysis.Node handlerNodeData = AstDefinitionUsageAnalysis.this.nodes.get(handlerNode);
         handlerNodeData.catchStatement = statement;
         int nodeBefore = AstDefinitionUsageAnalysis.this.lastNode;
         int entryNode = AstDefinitionUsageAnalysis.this.createNode();
         AstDefinitionUsageAnalysis.this.nodes.get(entryNode).leavingCatchStatement = statement;
         AstDefinitionUsageAnalysis.this.connect(nodeBefore, entryNode);
         AstDefinitionUsageAnalysis.this.exceptionHandlerStack.push(nodeAfterBody);
         AstDefinitionUsageAnalysis.this.lastNode = entryNode;
         this.visit(statement.getProtectedBody());
         AstDefinitionUsageAnalysis.this.exceptionHandlerStack.pop();
         AstDefinitionUsageAnalysis.this.connect(AstDefinitionUsageAnalysis.this.lastNode, nodeAfterBody);
         AstDefinitionUsageAnalysis.this.connect(entryNode, nodeAfterBody);
         AstDefinitionUsageAnalysis.this.lastNode = handlerNode;
         AstDefinitionUsageAnalysis.this.connect(nodeAfterBody, handlerNode);
         this.visit(statement.getHandler());
         AstDefinitionUsageAnalysis.this.connect(AstDefinitionUsageAnalysis.this.lastNode, nodeAfter);
         AstDefinitionUsageAnalysis.this.connect(handlerNode, nodeAfter);
         AstDefinitionUsageAnalysis.this.lastNode = nodeAfter;
      }

      @Override
      public void visit(ReturnStatement statement) {
         super.visit(statement);
         AstDefinitionUsageAnalysis.this.lastNode = -1;
      }

      @Override
      public void visit(ThrowStatement statement) {
         super.visit(statement);
         AstDefinitionUsageAnalysis.this.lastNode = -1;
      }

      @Override
      public void visit(BreakStatement statement) {
         IdentifiedStatement target = statement.getTarget();
         if (target == null) {
            target = AstDefinitionUsageAnalysis.this.defaultBreakTarget;
         }

         AstDefinitionUsageAnalysis.this.connect(
            AstDefinitionUsageAnalysis.this.lastNode, AstDefinitionUsageAnalysis.this.breakTargets.getOrDefault(target, -1)
         );
         AstDefinitionUsageAnalysis.this.lastNode = -1;
      }

      @Override
      public void visit(ContinueStatement statement) {
         IdentifiedStatement target = statement.getTarget();
         if (target == null) {
            target = AstDefinitionUsageAnalysis.this.defaultContinueTarget;
         }

         AstDefinitionUsageAnalysis.this.connect(
            AstDefinitionUsageAnalysis.this.lastNode, AstDefinitionUsageAnalysis.this.continueTargets.getOrDefault(target, -1)
         );
         AstDefinitionUsageAnalysis.this.lastNode = -1;
      }

      @Override
      public void visit(AssignmentStatement statement) {
         if (!this.processAssignment(statement)) {
            super.visit(statement);
         }
      }

      private boolean processAssignment(AssignmentStatement statement) {
         if (!(statement.getLeftValue() instanceof VariableExpr)) {
            return false;
         }

         int leftIndex = ((VariableExpr)statement.getLeftValue()).getIndex();
         statement.getRightValue().acceptVisitor(this);
         AstDefinitionUsageAnalysis.Definition definition = new AstDefinitionUsageAnalysis.Definition(
            statement, AstDefinitionUsageAnalysis.this.definitions.size(), leftIndex
         );
         AstDefinitionUsageAnalysis.this.definitions.add(definition);
         AstDefinitionUsageAnalysis.this.definitionsByStatements.put(statement, definition);
         AstDefinitionUsageAnalysis.this.definitionIds.put(definition, definition.id);
         int nodeId = AstDefinitionUsageAnalysis.this.createNode();
         AstDefinitionUsageAnalysis.Node node = AstDefinitionUsageAnalysis.this.nodes.get(nodeId);
         AstDefinitionUsageAnalysis.this.connect(AstDefinitionUsageAnalysis.this.lastNode, nodeId);

         for (IntCursor cursor : AstDefinitionUsageAnalysis.this.exceptionHandlerStack) {
            AstDefinitionUsageAnalysis.this.graphBuilder.addEdge(nodeId, cursor.value);
         }

         AstDefinitionUsageAnalysis.this.lastNode = nodeId;
         node.definitions.put(definition.variableIndex, definition.id);
         return true;
      }

      @Override
      public void visit(VariableExpr expr) {
         if (AstDefinitionUsageAnalysis.this.lastNode >= 0) {
            AstDefinitionUsageAnalysis.Node node = AstDefinitionUsageAnalysis.this.nodes.get(AstDefinitionUsageAnalysis.this.lastNode);
            int definitionId = node.definitions.getOrDefault(expr.getIndex(), -1);
            int id = AstDefinitionUsageAnalysis.this.usages.size();
            AstDefinitionUsageAnalysis.Usage usage = new AstDefinitionUsageAnalysis.Usage(expr);
            AstDefinitionUsageAnalysis.this.usages.add(usage);
            if (definitionId >= 0) {
               AstDefinitionUsageAnalysis.Definition definition = AstDefinitionUsageAnalysis.this.definitions.get(definitionId);
               definition.usages.add(usage);
               node.usages.add(id);
            } else {
               AstDefinitionUsageAnalysis.this.stack.push(id);
               AstDefinitionUsageAnalysis.this.stack.push(AstDefinitionUsageAnalysis.this.lastNode);
            }
         }
      }
   };

   public List<? extends AstDefinitionUsageAnalysis.Definition> getDefinitions() {
      return this.readonlyDefinitions;
   }

   public AstDefinitionUsageAnalysis.Definition getDefinition(AssignmentStatement statement) {
      return this.definitionsByStatements.get(statement);
   }

   public void analyze(Statement statement) {
      this.prepare(statement);
      this.propagate();
      this.cleanup();
   }

   private void prepare(Statement statement) {
      this.lastNode = this.createNode();
      statement.acceptVisitor(this.visitor);
      this.cfg = this.graphBuilder.build();
      this.graphBuilder = null;
      this.breakTargets = null;
      this.continueTargets = null;
      this.exceptionHandlerStack = null;
   }

   private void connect(int from, int to) {
      if (from >= 0 && to >= 0) {
         this.graphBuilder.addEdge(from, to);
      }
   }

   private int createNode() {
      int id = this.nodes.size();
      this.nodes.add(new AstDefinitionUsageAnalysis.Node());
      return id;
   }

   private void propagate() {
      while (!this.stack.isEmpty()) {
         int nodeId = this.stack.pop();
         int usageId = this.stack.pop();
         AstDefinitionUsageAnalysis.Usage usage = this.usages.get(usageId);
         AstDefinitionUsageAnalysis.Node node = this.nodes.get(nodeId);
         if (node.usages.add(usageId)) {
            int variableId = usage.getExpr().getIndex();
            if (node.catchStatement != null) {
               if (node.catchStatement.getExceptionVariable() != null && node.catchStatement.getExceptionVariable() == variableId) {
                  continue;
               }

               usage.liveInCatches.add(node.catchStatement);
            }

            int definitionId = node.definitions.getOrDefault(variableId, -1);
            if (definitionId >= 0) {
               AstDefinitionUsageAnalysis.Definition definition = this.definitions.get(definitionId);
               definition.usages.add(this.usages.get(usageId));
            } else if (nodeId < this.cfg.size()) {
               for (int predecessorId : this.cfg.incomingEdges(nodeId)) {
                  if (!this.nodes.get(predecessorId).usages.contains(usageId)) {
                     this.stack.push(usageId);
                     this.stack.push(predecessorId);
                  }
               }
            }
         }
      }
   }

   private void cleanup() {
      this.cfg = null;
      this.stack = null;
   }

   public static class Definition {
      private AssignmentStatement statement;
      private int id;
      private int variableIndex;
      private final Set<AstDefinitionUsageAnalysis.Usage> usages = new LinkedHashSet<>();
      private Set<? extends AstDefinitionUsageAnalysis.Usage> readonlyUsages = Collections.unmodifiableSet(this.usages);

      Definition(AssignmentStatement statement, int id, int variableIndex) {
         this.statement = statement;
         this.id = id;
         this.variableIndex = variableIndex;
      }

      public AssignmentStatement getStatement() {
         return this.statement;
      }

      public int getId() {
         return this.id;
      }

      public int getVariableIndex() {
         return this.variableIndex;
      }

      public Collection<? extends AstDefinitionUsageAnalysis.Usage> getUsages() {
         return this.readonlyUsages;
      }
   }

   private static class Node {
      IntIntMap definitions = new IntIntHashMap();
      IntSet usages = new IntHashSet();
      TryCatchStatement catchStatement;
      TryCatchStatement leavingCatchStatement;
   }

   public static class Usage {
      private final VariableExpr expr;
      private List<TryCatchStatement> liveInCatches = new ArrayList<>();
      private List<? extends TryCatchStatement> readonlyLiveInCatches = Collections.unmodifiableList(this.liveInCatches);

      private Usage(VariableExpr expr) {
         this.expr = expr;
      }

      public VariableExpr getExpr() {
         return this.expr;
      }

      public List<? extends TryCatchStatement> getLiveInCatches() {
         return this.readonlyLiveInCatches;
      }
   }
}
