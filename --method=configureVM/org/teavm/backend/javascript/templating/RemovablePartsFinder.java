package org.teavm.backend.javascript.templating;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.backend.javascript.ast.AstVisitor;
import org.teavm.rhino.javascript.ast.Assignment;
import org.teavm.rhino.javascript.ast.AstNode;
import org.teavm.rhino.javascript.ast.ElementGet;
import org.teavm.rhino.javascript.ast.ExpressionStatement;
import org.teavm.rhino.javascript.ast.FunctionNode;
import org.teavm.rhino.javascript.ast.Name;
import org.teavm.rhino.javascript.ast.PropertyGet;
import org.teavm.rhino.javascript.ast.Scope;
import org.teavm.rhino.javascript.ast.VariableDeclaration;
import org.teavm.rhino.javascript.ast.VariableInitializer;

public class RemovablePartsFinder extends AstVisitor {
   private Map<String, List<AstNode>> removableDeclarations = new HashMap<>();
   private Set<Scope> removableDeclarationScopes = new HashSet<>();
   private Map<String, Set<String>> dependencies = new HashMap<>();
   private String insideDeclaration;
   private boolean topLevel = true;

   @Override
   public void visit(FunctionNode node) {
      if (this.topLevel) {
         if (node.getName() != null && !node.getName().isEmpty()) {
            this.removableDeclarations.computeIfAbsent(node.getName(), k -> new ArrayList<>()).add(node);
         }

         this.topLevel = false;
         this.insideDeclaration = node.getName();
         this.visit(node.getBody());
         this.insideDeclaration = null;
         this.topLevel = true;
      } else {
         super.visit(node);
      }
   }

   @Override
   public void visit(VariableDeclaration node) {
      if (this.topLevel) {
         for (VariableInitializer initializer : node.getVariables()) {
            Name name = this.extractName(initializer.getTarget());
            if (name != null) {
               this.removableDeclarations.computeIfAbsent(name.getIdentifier(), k -> new ArrayList<>()).add(initializer);
               if (initializer.getInitializer() != null) {
                  this.topLevel = false;
                  this.insideDeclaration = name.getIdentifier();
                  this.visit(initializer.getInitializer());
                  this.insideDeclaration = null;
                  this.topLevel = true;
               }
            }
         }
      } else {
         super.visit(node);
      }
   }

   @Override
   public void visit(ExpressionStatement node) {
      if (this.topLevel && node.getExpression() instanceof Assignment assign) {
         Name name = this.extractName(assign.getLeft());
         this.removableDeclarations.computeIfAbsent(name.getIdentifier(), k -> new ArrayList<>()).add(node.getExpression());
         if (name != null) {
            this.topLevel = false;
            this.insideDeclaration = name.getIdentifier();
            this.visit(assign.getRight());
            this.insideDeclaration = null;
            this.topLevel = true;
            return;
         }
      }

      super.visit(node);
   }

   @Override
   public void visit(PropertyGet node) {
      this.visit(node.getTarget());
   }

   @Override
   public void visit(Name node) {
      if (this.insideDeclaration != null) {
         Scope actualScope = this.scopeOfId(node.getIdentifier());
         if (actualScope == null || this.removableDeclarationScopes.contains(actualScope)) {
            this.dependencies.computeIfAbsent(this.insideDeclaration, k -> new HashSet<>()).add(node.getIdentifier());
         }
      }
   }

   private Name extractName(AstNode node) {
      if (node instanceof Name) {
         return (Name)node;
      } else if (node instanceof PropertyGet) {
         return this.extractName(((PropertyGet)node).getTarget());
      } else {
         return node instanceof ElementGet ? this.extractName(((ElementGet)node).getTarget()) : null;
      }
   }

   public void markUsedDeclaration(String name) {
      this.removableDeclarations.remove(name);
      Set<String> dependenciesToFollow = this.dependencies.remove(name);
      if (dependenciesToFollow != null) {
         for (String dependency : dependenciesToFollow) {
            this.markUsedDeclaration(dependency);
         }
      }
   }

   public Set<AstNode> getAllRemovableParts() {
      HashSet<AstNode> nodes = new HashSet<>();

      for (List<AstNode> parts : this.removableDeclarations.values()) {
         nodes.addAll(parts);
      }

      return nodes;
   }

   @Override
   protected void onEnterScope(Scope scope) {
      if (this.topLevel) {
         this.removableDeclarationScopes.add(scope);
      }
   }

   @Override
   protected void onLeaveScope(Scope scope) {
      if (this.topLevel) {
         this.removableDeclarationScopes.remove(scope);
      }
   }
}
