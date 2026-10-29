package org.teavm.backend.javascript.ast;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.teavm.rhino.javascript.Node;
import org.teavm.rhino.javascript.ast.AbstractObjectProperty;
import org.teavm.rhino.javascript.ast.ArrayComprehension;
import org.teavm.rhino.javascript.ast.ArrayComprehensionLoop;
import org.teavm.rhino.javascript.ast.ArrayLiteral;
import org.teavm.rhino.javascript.ast.AstNode;
import org.teavm.rhino.javascript.ast.AstRoot;
import org.teavm.rhino.javascript.ast.Block;
import org.teavm.rhino.javascript.ast.BreakStatement;
import org.teavm.rhino.javascript.ast.CatchClause;
import org.teavm.rhino.javascript.ast.ConditionalExpression;
import org.teavm.rhino.javascript.ast.ContinueStatement;
import org.teavm.rhino.javascript.ast.DoLoop;
import org.teavm.rhino.javascript.ast.ElementGet;
import org.teavm.rhino.javascript.ast.EmptyExpression;
import org.teavm.rhino.javascript.ast.EmptyStatement;
import org.teavm.rhino.javascript.ast.ExpressionStatement;
import org.teavm.rhino.javascript.ast.ForInLoop;
import org.teavm.rhino.javascript.ast.ForLoop;
import org.teavm.rhino.javascript.ast.FunctionCall;
import org.teavm.rhino.javascript.ast.FunctionNode;
import org.teavm.rhino.javascript.ast.GeneratorExpression;
import org.teavm.rhino.javascript.ast.GeneratorExpressionLoop;
import org.teavm.rhino.javascript.ast.IfStatement;
import org.teavm.rhino.javascript.ast.InfixExpression;
import org.teavm.rhino.javascript.ast.KeywordLiteral;
import org.teavm.rhino.javascript.ast.LabeledStatement;
import org.teavm.rhino.javascript.ast.LetNode;
import org.teavm.rhino.javascript.ast.Loop;
import org.teavm.rhino.javascript.ast.Name;
import org.teavm.rhino.javascript.ast.NewExpression;
import org.teavm.rhino.javascript.ast.NumberLiteral;
import org.teavm.rhino.javascript.ast.ObjectLiteral;
import org.teavm.rhino.javascript.ast.ObjectProperty;
import org.teavm.rhino.javascript.ast.ParenthesizedExpression;
import org.teavm.rhino.javascript.ast.PropertyGet;
import org.teavm.rhino.javascript.ast.RegExpLiteral;
import org.teavm.rhino.javascript.ast.ReturnStatement;
import org.teavm.rhino.javascript.ast.Scope;
import org.teavm.rhino.javascript.ast.Spread;
import org.teavm.rhino.javascript.ast.SpreadObjectProperty;
import org.teavm.rhino.javascript.ast.StringLiteral;
import org.teavm.rhino.javascript.ast.SwitchCase;
import org.teavm.rhino.javascript.ast.SwitchStatement;
import org.teavm.rhino.javascript.ast.ThrowStatement;
import org.teavm.rhino.javascript.ast.TryStatement;
import org.teavm.rhino.javascript.ast.UnaryExpression;
import org.teavm.rhino.javascript.ast.UpdateExpression;
import org.teavm.rhino.javascript.ast.VariableDeclaration;
import org.teavm.rhino.javascript.ast.VariableInitializer;
import org.teavm.rhino.javascript.ast.WhileLoop;

public class AstVisitor {
   protected AstNode replacement;
   protected boolean hasReplacement;
   protected final Map<String, Scope> currentScopes = new HashMap<>();
   private static final Supplier<AstNode> NULL_DEFAULT = () -> new KeywordLiteral(0, 0, 47);
   private static final Supplier<AstNode> EMPTY_DEFAULT = () -> new EmptyStatement(0, 0);
   private static final Supplier<AstNode> EMPTY_EXPR_DEFAULT = () -> new EmptyExpression(0, 0);

   public final void visit(AstNode node) {
      switch (node.getType()) {
         case 4:
            this.visit((ReturnStatement)node);
            break;
         case 5:
         case 6:
         case 7:
         case 8:
         case 9:
         case 10:
         case 11:
         case 12:
         case 13:
         case 14:
         case 15:
         case 16:
         case 17:
         case 18:
         case 19:
         case 20:
         case 21:
         case 22:
         case 23:
         case 24:
         case 25:
         case 26:
         case 27:
         case 28:
         case 29:
         case 31:
         case 32:
         case 34:
         case 35:
         case 36:
         case 37:
         case 38:
         case 40:
         case 41:
         case 42:
         case 48:
         case 52:
         case 53:
         case 55:
         case 57:
         case 58:
         case 59:
         case 60:
         case 61:
         case 62:
         case 63:
         case 64:
         case 65:
         case 66:
         case 67:
         case 68:
         case 69:
         case 70:
         case 71:
         case 74:
         case 75:
         case 76:
         case 77:
         case 78:
         case 79:
         case 80:
         case 81:
         case 82:
         case 83:
         case 84:
         case 85:
         case 86:
         case 87:
         case 88:
         case 89:
         case 90:
         case 91:
         case 93:
         case 94:
         case 95:
         case 96:
         case 97:
         case 99:
         case 100:
         case 101:
         case 102:
         case 103:
         case 104:
         case 105:
         case 106:
         case 107:
         case 108:
         case 109:
         case 110:
         case 111:
         case 112:
         case 113:
         case 114:
         case 115:
         case 116:
         case 118:
         case 119:
         case 120:
         case 121:
         case 122:
         case 123:
         case 125:
         case 126:
         case 128:
         case 130:
         case 131:
         case 138:
         case 139:
         case 140:
         case 141:
         case 142:
         case 144:
         case 146:
         case 147:
         case 148:
         case 151:
         case 153:
         case 154:
         case 155:
         case 156:
         case 157:
         case 158:
         case 159:
         case 160:
         case 161:
         case 162:
         case 163:
         case 164:
         case 165:
         case 166:
         case 167:
         case 168:
         case 171:
         case 172:
         case 175:
         case 176:
         case 177:
         default:
            if (node instanceof InfixExpression) {
               this.visit((InfixExpression)node);
            } else if (node instanceof UnaryExpression) {
               this.visit((UnaryExpression)node);
            } else if (node instanceof UpdateExpression) {
               this.visit((UpdateExpression)node);
            }
            break;
         case 30:
         case 43:
            this.visit((FunctionCall)node);
            break;
         case 33:
            this.visit((PropertyGet)node);
            break;
         case 39:
            this.visit((ElementGet)node);
            break;
         case 44:
            this.visit((Name)node);
            break;
         case 45:
            this.visit((NumberLiteral)node);
            break;
         case 46:
            this.visit((StringLiteral)node);
            break;
         case 47:
         case 49:
         case 50:
         case 51:
            this.visit((KeywordLiteral)node);
            break;
         case 54:
            this.visit((RegExpLiteral)node);
            break;
         case 56:
            this.visit((ThrowStatement)node);
            break;
         case 72:
            this.visit((ArrayLiteral)node);
            break;
         case 73:
            this.visit((ObjectLiteral)node);
            break;
         case 92:
            this.visit((TryStatement)node);
            break;
         case 98:
            this.visit((ParenthesizedExpression)node);
            break;
         case 117:
            this.visit((ConditionalExpression)node);
            break;
         case 124:
            this.visit((FunctionNode)node);
            break;
         case 127:
            this.visit((IfStatement)node);
            break;
         case 129:
            this.visit((SwitchStatement)node);
            break;
         case 132:
            this.visit((WhileLoop)node);
            break;
         case 133:
            this.visit((DoLoop)node);
            break;
         case 134:
            if (node instanceof ForInLoop) {
               this.visit((ForInLoop)node);
            } else if (node instanceof ForLoop) {
               this.visit((ForLoop)node);
            }
            break;
         case 135:
            this.visit((BreakStatement)node);
            break;
         case 136:
            this.visit((ContinueStatement)node);
            break;
         case 137:
         case 169:
         case 170:
            this.visit((VariableDeclaration)node);
            break;
         case 143:
            if (node instanceof EmptyExpression) {
               this.visit((EmptyExpression)node);
            } else if (node instanceof EmptyStatement) {
               this.visit((EmptyStatement)node);
            }
            break;
         case 145:
            if (node instanceof Block) {
               this.visit((Block)node);
            } else if (node instanceof Scope) {
               this.visit((Scope)node);
            }
            break;
         case 149:
         case 150:
            if (node instanceof ExpressionStatement) {
               this.visit((ExpressionStatement)node);
            } else if (node instanceof LabeledStatement) {
               this.visit((LabeledStatement)node);
            }
            break;
         case 152:
            this.visit((AstRoot)node);
            break;
         case 173:
            this.visit((ArrayComprehension)node);
            break;
         case 174:
            this.visit((LetNode)node);
            break;
         case 178:
            this.visit((GeneratorExpression)node);
      }
   }

   protected final void visitMany(List<AstNode> nodes) {
      for (int i = 0; i < nodes.size(); i++) {
         AstNode node = nodes.get(i);
         this.visit(node);
         if (this.hasReplacement) {
            nodes.set(i, this.replacement);
         }

         this.hasReplacement = false;
         this.replacement = null;
      }
   }

   protected final void visitChildren(AstNode node) {
      Node child = node.getFirstChild();

      while (child != null) {
         Node next = child.getNext();
         this.visit((AstNode)child);
         if (this.hasReplacement) {
            if (this.replacement != null) {
               node.replaceChild(child, this.replacement);
            } else {
               node.removeChild(child);
            }

            this.replacement = null;
            this.hasReplacement = false;
         }

         child = next;
      }
   }

   protected final <T extends AstNode, S extends AstNode> void visitProperty(T owner, Function<T, S> getter, BiConsumer<T, S> setter) {
      this.visitProperty(owner, getter, setter, null);
   }

   protected final <T extends AstNode, S extends AstNode> void visitProperty(T owner, Function<T, S> getter, BiConsumer<T, S> setter, Supplier<S> defaultValue) {
      S node = (S)getter.apply(owner);
      if (node != null) {
         this.visit(node);
         if (this.hasReplacement) {
            if (this.replacement == null && defaultValue != null) {
               this.replacement = defaultValue.get();
            }

            setter.accept(owner, (S)this.replacement);
         }

         this.replacement = null;
         this.hasReplacement = false;
      }
   }

   public void visit(AstRoot node) {
      this.visitChildren(node);
   }

   public void visit(Block node) {
      this.visitChildren(node);
   }

   public void visit(Scope node) {
      Map<String, Scope> scope = this.enterScope(node);
      this.visitChildren(node);
      this.leaveScope(node, scope);
   }

   public void visit(LabeledStatement node) {
      this.visitProperty(node, LabeledStatement::getStatement, LabeledStatement::setStatement, EMPTY_DEFAULT);
   }

   public void visit(BreakStatement node) {
   }

   public void visit(ContinueStatement node) {
   }

   public void visit(ReturnStatement node) {
      this.visitProperty(node, ReturnStatement::getReturnValue, ReturnStatement::setReturnValue);
   }

   public void visit(ThrowStatement node) {
      this.visitProperty(node, ThrowStatement::getExpression, ThrowStatement::setExpression, () -> new KeywordLiteral(0, 0, 47));
   }

   public void visit(DoLoop node) {
      Map<String, Scope> scope = this.enterScope(node);
      this.visitProperty(node, Loop::getBody, Loop::setBody, EMPTY_DEFAULT);
      this.visitProperty(node, DoLoop::getCondition, DoLoop::setCondition, NULL_DEFAULT);
      this.leaveScope(node, scope);
   }

   public void visit(ForInLoop node) {
      Map<String, Scope> scope = this.enterScope(node);
      this.visitProperty(node, ForInLoop::getIterator, ForInLoop::setIterator, NULL_DEFAULT);
      this.visitProperty(node, ForInLoop::getIteratedObject, ForInLoop::setIteratedObject, NULL_DEFAULT);
      this.visitProperty(node, Loop::getBody, Loop::setBody, EMPTY_DEFAULT);
      this.leaveScope(node, scope);
   }

   public void visit(ForLoop node) {
      Map<String, Scope> scope = this.enterScope(node);
      this.visitProperty(node, ForLoop::getInitializer, ForLoop::setInitializer, EMPTY_EXPR_DEFAULT);
      this.visitProperty(node, ForLoop::getCondition, ForLoop::setCondition, EMPTY_EXPR_DEFAULT);
      this.visitProperty(node, ForLoop::getIncrement, ForLoop::setIncrement, EMPTY_EXPR_DEFAULT);
      this.visitProperty(node, Loop::getBody, Loop::setBody, EMPTY_DEFAULT);
      this.leaveScope(node, scope);
   }

   public void visit(WhileLoop node) {
      Map<String, Scope> scope = this.enterScope(node);
      this.visitProperty(node, WhileLoop::getCondition, WhileLoop::setCondition, NULL_DEFAULT);
      this.visitProperty(node, Loop::getBody, Loop::setBody, EMPTY_DEFAULT);
      this.leaveScope(node, scope);
   }

   public void visit(IfStatement node) {
      this.visitProperty(node, IfStatement::getCondition, IfStatement::setCondition, NULL_DEFAULT);
      this.visitProperty(node, IfStatement::getThenPart, IfStatement::setThenPart, EMPTY_DEFAULT);
      this.visitProperty(node, IfStatement::getElsePart, IfStatement::setElsePart);
   }

   public void visit(SwitchStatement node) {
      this.visitProperty(node, SwitchStatement::getExpression, SwitchStatement::setExpression, NULL_DEFAULT);

      for (SwitchCase sc : node.getCases()) {
         this.visitProperty(sc, SwitchCase::getExpression, SwitchCase::setExpression);
         if (sc.getStatements() != null) {
            this.visitMany(sc.getStatements());
         }
      }
   }

   public void visit(TryStatement node) {
      this.visitProperty(node, TryStatement::getTryBlock, TryStatement::setTryBlock, NULL_DEFAULT);

      for (CatchClause cc : node.getCatchClauses()) {
         this.visitProperty(cc, CatchClause::getVarName, CatchClause::setVarName);
         this.visitProperty(cc, CatchClause::getCatchCondition, CatchClause::setCatchCondition);
         if (cc.getBody() != null) {
            this.visitChildren(cc.getBody());
         }
      }

      this.visitProperty(node, TryStatement::getFinallyBlock, TryStatement::setFinallyBlock);
   }

   public void visit(VariableDeclaration node) {
      for (VariableInitializer variable : node.getVariables()) {
         this.visit(variable);
      }
   }

   public void visit(VariableInitializer node) {
      this.visitProperty(node, VariableInitializer::getTarget, VariableInitializer::setTarget);
      this.visitProperty(node, VariableInitializer::getInitializer, VariableInitializer::setInitializer);
   }

   public void visit(ExpressionStatement node) {
      this.visitProperty(node, ExpressionStatement::getExpression, ExpressionStatement::setExpression, NULL_DEFAULT);
   }

   public void visit(ElementGet node) {
      this.visitProperty(node, ElementGet::getTarget, ElementGet::setTarget, NULL_DEFAULT);
      this.visitProperty(node, ElementGet::getElement, ElementGet::setElement, NULL_DEFAULT);
   }

   public void visit(PropertyGet node) {
      this.visitProperty(node, PropertyGet::getTarget, PropertyGet::setTarget, NULL_DEFAULT);
      this.visitProperty(node, PropertyGet::getProperty, PropertyGet::setProperty);
   }

   public void visit(FunctionCall node) {
      this.visitProperty(node, FunctionCall::getTarget, FunctionCall::setTarget);
      this.visitMany(node.getArguments());
      if (node instanceof NewExpression newExpr) {
         this.visitProperty(newExpr, NewExpression::getInitializer, NewExpression::setInitializer);
      }
   }

   public void visit(ConditionalExpression node) {
      this.visitProperty(node, ConditionalExpression::getTestExpression, ConditionalExpression::setTestExpression);
      this.visitProperty(node, ConditionalExpression::getTrueExpression, ConditionalExpression::setTrueExpression);
      this.visitProperty(node, ConditionalExpression::getFalseExpression, ConditionalExpression::setFalseExpression);
   }

   public void visit(ArrayComprehension node) {
      Map<String, Scope> scope = this.enterScope(node);

      for (ArrayComprehensionLoop loop : node.getLoops()) {
         this.visitProperty(loop, ForInLoop::getIterator, ForInLoop::setIterator);
         this.visitProperty(loop, ForInLoop::getIteratedObject, ForInLoop::setIteratedObject);
      }

      this.visitProperty(node, ArrayComprehension::getFilter, ArrayComprehension::setFilter);
      this.visitProperty(node, ArrayComprehension::getResult, ArrayComprehension::setResult);
      this.leaveScope(node, scope);
   }

   public void visit(GeneratorExpression node) {
      Map<String, Scope> scope = this.enterScope(node);

      for (GeneratorExpressionLoop loop : node.getLoops()) {
         this.visitProperty(loop, ForInLoop::getIterator, ForInLoop::setIterator);
         this.visitProperty(loop, ForInLoop::getIteratedObject, ForInLoop::setIteratedObject);
      }

      this.visitProperty(node, GeneratorExpression::getFilter, GeneratorExpression::setFilter);
      this.visitProperty(node, GeneratorExpression::getResult, GeneratorExpression::setResult);
      this.leaveScope(node, scope);
   }

   public void visit(NumberLiteral node) {
   }

   public void visit(StringLiteral node) {
   }

   public void visit(KeywordLiteral node) {
   }

   public void visit(Name node) {
   }

   public void visit(RegExpLiteral node) {
   }

   public void visit(ArrayLiteral node) {
      this.visitMany(node.getElements());
   }

   public void visit(ObjectLiteral node) {
      if (node.getElements() != null) {
         for (AbstractObjectProperty element : node.getElements()) {
            this.visit(element);
         }
      }
   }

   public void visit(AbstractObjectProperty node) {
      if (node instanceof ObjectProperty prop) {
         this.visit(prop);
      }
   }

   public void visit(ObjectProperty node) {
      boolean changed = false;
      AstNode key = node.getKey();
      if (key != null) {
         this.visit(node.getKey());
         if (this.hasReplacement && this.replacement == null) {
            key = this.replacement;
            changed = true;
         }

         this.replacement = null;
         this.hasReplacement = false;
      }

      AstNode value = node.getValue();
      if (value != null) {
         this.visit(node.getValue());
         if (this.hasReplacement && this.replacement == null) {
            value = this.replacement;
            changed = true;
         }

         this.replacement = null;
      }

      if (changed) {
         node.setKeyAndValue(key, value);
      }
   }

   public void visit(SpreadObjectProperty node) {
      this.visit(node.getSpreadNode());
   }

   public void visit(Spread node) {
      this.visitProperty(node, Spread::getExpression, Spread::setExpression);
   }

   public void visit(FunctionNode node) {
      Map<String, Scope> scope = this.enterScope(node);
      if (node.getFunctionType() != 4) {
         this.currentScopes.put("arguments", node);
      }

      this.visitProperty(node, FunctionNode::getFunctionName, FunctionNode::setFunctionName);
      this.visitMany(node.getParams());
      this.visitChildren(node.getBody());
      this.leaveScope(node, scope);
   }

   public void visit(LetNode node) {
      Map<String, Scope> scope = this.enterScope(node);
      this.visitProperty(node, LetNode::getVariables, LetNode::setVariables);
      this.visitProperty(node, LetNode::getBody, LetNode::setBody);
      this.leaveScope(node, scope);
   }

   public void visit(ParenthesizedExpression node) {
      this.visitProperty(node, ParenthesizedExpression::getExpression, ParenthesizedExpression::setExpression);
   }

   public void visit(EmptyExpression node) {
   }

   public void visit(EmptyStatement node) {
   }

   public void visit(InfixExpression node) {
      this.visitProperty(node, InfixExpression::getLeft, InfixExpression::setLeft);
      this.visitProperty(node, InfixExpression::getRight, InfixExpression::setRight);
   }

   public void visit(UnaryExpression node) {
      this.visitProperty(node, UnaryExpression::getOperand, UnaryExpression::setOperand);
   }

   public void visit(UpdateExpression node) {
      this.visitProperty(node, UpdateExpression::getOperand, UpdateExpression::setOperand);
   }

   protected final void replaceWith(AstNode node) {
      this.hasReplacement = true;
      this.replacement = node;
   }

   private Map<String, Scope> enterScope(Scope scope) {
      this.onEnterScope(scope);
      if (scope.getSymbolTable() == null) {
         return Collections.emptyMap();
      }

      LinkedHashMap<String, Scope> map = new LinkedHashMap<>();

      for (String name : scope.getSymbolTable().keySet()) {
         map.put(name, this.currentScopes.get(name));
         this.currentScopes.put(name, scope);
      }

      return map;
   }

   protected void onEnterScope(Scope scope) {
   }

   private void leaveScope(Scope scope, Map<String, Scope> backup) {
      this.onLeaveScope(scope);

      for (Entry<String, Scope> entry : backup.entrySet()) {
         if (entry.getValue() == null) {
            this.currentScopes.remove(entry.getKey());
         } else {
            this.currentScopes.put(entry.getKey(), entry.getValue());
         }
      }
   }

   protected void onLeaveScope(Scope scope) {
   }

   protected Scope scopeOfId(String id) {
      return this.currentScopes.get(id);
   }
}
