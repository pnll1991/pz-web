package org.teavm.backend.javascript.templating;

import org.teavm.backend.javascript.ast.AstVisitor;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.rhino.javascript.ast.AstNode;
import org.teavm.rhino.javascript.ast.Block;
import org.teavm.rhino.javascript.ast.ConditionalExpression;
import org.teavm.rhino.javascript.ast.FunctionCall;
import org.teavm.rhino.javascript.ast.IfStatement;
import org.teavm.rhino.javascript.ast.InfixExpression;
import org.teavm.rhino.javascript.ast.KeywordLiteral;
import org.teavm.rhino.javascript.ast.Name;
import org.teavm.rhino.javascript.ast.Scope;
import org.teavm.rhino.javascript.ast.StringLiteral;
import org.teavm.rhino.javascript.ast.UnaryExpression;

public class TemplatingAstTransformer extends AstVisitor {
   private ClassReaderSource classes;

   public TemplatingAstTransformer(ClassReaderSource classes) {
      this.classes = classes;
   }

   @Override
   public void visit(Block node) {
      super.visit(node);
      if (node.getFirstChild() == null) {
         this.replaceWith(null);
      } else if (node.getFirstChild() == node.getLastChild()) {
         this.replaceWith((AstNode)node.getFirstChild());
      }
   }

   @Override
   public void visit(IfStatement node) {
      super.visit(node);
      if (node.getCondition().getType() == 51) {
         this.replaceWith(node.getThenPart());
      } else if (node.getCondition().getType() == 50) {
         this.replaceWith(node.getElsePart());
      }
   }

   @Override
   public void visit(ConditionalExpression node) {
      super.visit(node);
      if (node.getTestExpression().getType() == 51) {
         this.replaceWith(node.getTrueExpression());
      } else if (node.getTestExpression().getType() == 50) {
         this.replaceWith(node.getFalseExpression());
      }
   }

   @Override
   public void visit(InfixExpression node) {
      super.visit(node);
      if (node.getType() == 120) {
         if (node.getLeft().getType() == 50) {
            this.replaceWith(node.getLeft());
         } else if (node.getRight().getType() == 50) {
            this.replaceWith(node.getRight());
         } else if (node.getLeft().getType() == 51) {
            this.replaceWith(node.getRight());
         } else if (node.getRight().getType() == 51) {
            this.replaceWith(node.getLeft());
         }
      } else if (node.getType() == 119) {
         if (node.getLeft().getType() == 51) {
            this.replaceWith(node.getLeft());
         } else if (node.getRight().getType() == 51) {
            this.replaceWith(node.getRight());
         } else if (node.getLeft().getType() == 50) {
            this.replaceWith(node.getRight());
         } else if (node.getRight().getType() == 50) {
            this.replaceWith(node.getLeft());
         }
      }
   }

   @Override
   public void visit(UnaryExpression node) {
      super.visit(node);
      if (node.getType() == 26) {
         if (node.getOperand().getType() == 51) {
            node.getOperand().setType(50);
         } else if (node.getOperand().getType() == 50) {
            node.getOperand().setType(51);
         }
      }
   }

   @Override
   public void visit(FunctionCall node) {
      super.visit(node);
      if (node.getTarget() instanceof Name) {
         Name name = (Name)node.getTarget();
         Scope scope = this.scopeOfId(name.getIdentifier());
         if (scope == null) {
            this.tryIntrinsicName(node, name.getIdentifier());
         }
      }
   }

   private void tryIntrinsicName(FunctionCall node, String identifier) {
      switch (identifier) {
         case "teavm_javaClassExists":
            this.javaClassExists(node);
            break;
         case "teavm_javaMethodExists":
            this.javaMethodExists(node);
            break;
         case "teavm_javaConstructorExists":
            this.javaConstructorExists(node);
      }
   }

   private void javaClassExists(FunctionCall node) {
      if (node.getArguments().size() == 1) {
         AstNode classArg = (AstNode)node.getArguments().get(0);
         if (classArg instanceof StringLiteral) {
            String className = ((StringLiteral)classArg).getValue();
            boolean exists = this.classes.get(className) != null;
            this.replaceWith(new KeywordLiteral(0, 0, exists ? 51 : 50));
         }
      }
   }

   private void javaMethodExists(FunctionCall node) {
      if (node.getArguments().size() == 2) {
         AstNode classArg = (AstNode)node.getArguments().get(0);
         AstNode methodArg = (AstNode)node.getArguments().get(1);
         if (classArg instanceof StringLiteral && methodArg instanceof StringLiteral) {
            String className = ((StringLiteral)classArg).getValue();
            String methodName = ((StringLiteral)methodArg).getValue();
            MethodReader method = this.classes.resolveImplementation(new MethodReference(className, MethodDescriptor.parse(methodName)));
            boolean exists = method != null && (method.getProgram() != null || method.hasModifier(ElementModifier.NATIVE));
            this.replaceWith(new KeywordLiteral(0, 0, exists ? 51 : 50));
         }
      }
   }

   private void javaConstructorExists(FunctionCall node) {
      if (node.getArguments().size() == 2) {
         AstNode classArg = (AstNode)node.getArguments().get(0);
         AstNode methodArg = (AstNode)node.getArguments().get(1);
         if (classArg instanceof StringLiteral && methodArg instanceof StringLiteral) {
            String className = ((StringLiteral)classArg).getValue();
            String methodName = ((StringLiteral)methodArg).getValue();
            MethodReader method = this.classes.resolveImplementation(new MethodReference(className, "<init>", MethodDescriptor.parseSignature(methodName)));
            boolean exists = method != null && (method.getProgram() != null || method.hasModifier(ElementModifier.NATIVE));
            this.replaceWith(new KeywordLiteral(0, 0, exists ? 51 : 50));
         }
      }
   }
}
