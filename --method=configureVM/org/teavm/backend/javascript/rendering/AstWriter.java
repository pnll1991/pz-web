package org.teavm.backend.javascript.rendering;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.model.MethodReference;
import org.teavm.rhino.javascript.Node;
import org.teavm.rhino.javascript.ScriptRuntime;
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
import org.teavm.rhino.javascript.ast.Label;
import org.teavm.rhino.javascript.ast.LabeledStatement;
import org.teavm.rhino.javascript.ast.LetNode;
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

public class AstWriter {
   public static final int PRECEDENCE_MEMBER = 2;
   public static final int PRECEDENCE_FUNCTION = 3;
   public static final int PRECEDENCE_POSTFIX = 4;
   public static final int PRECEDENCE_PREFIX = 5;
   public static final int PRECEDENCE_MUL = 6;
   public static final int PRECEDENCE_ADD = 7;
   public static final int PRECEDENCE_SHIFT = 8;
   public static final int PRECEDENCE_RELATION = 9;
   public static final int PRECEDENCE_EQUALITY = 10;
   public static final int PRECEDENCE_BITWISE_AND = 11;
   public static final int PRECEDENCE_BITWISE_XOR = 12;
   public static final int PRECEDENCE_BITWISE_OR = 13;
   public static final int PRECEDENCE_AND = 14;
   public static final int PRECEDENCE_OR = 15;
   public static final int PRECEDENCE_COND = 16;
   public static final int PRECEDENCE_ASSIGN = 17;
   public static final int PRECEDENCE_COMMA = 18;
   protected final SourceWriter writer;
   private Map<String, NameEmitter> nameMap = new HashMap<>();
   protected boolean rootScope = true;
   private Set<String> aliases = new HashSet<>();
   private Function<String, NameEmitter> globalNameWriter;
   private Set<String> nonTopLevels = new HashSet<>();
   private boolean inFunction;
   private int scopeLevel;

   public AstWriter(SourceWriter writer, Function<String, NameEmitter> globalNameWriter) {
      this.writer = writer;
      this.globalNameWriter = globalNameWriter;
   }

   protected final boolean inFunction() {
      return this.inFunction;
   }

   public void declareName(String name) {
      if (!this.nameMap.containsKey(name)) {
         if (this.aliases.add(name)) {
            this.nameMap.put(name, (w, p) -> w.append(name));
         } else {
            int i = 0;

            while (true) {
               String alias = name + "_" + i;
               if (this.aliases.add(alias)) {
                  this.nameMap.put(name, (w, p) -> w.append(alias));
                  return;
               }

               i++;
            }
         }
      }
   }

   public void declareNameEmitter(String name, NameEmitter emitter) {
      this.nameMap.put(name, emitter);
   }

   public void hoist(AstNode node) {
      this.declareName("arguments");
      node.visit(n -> {
         if (n instanceof Scope scope) {
            if (scope.getSymbolTable() != null) {
               for (String name : scope.getSymbolTable().keySet()) {
                  this.declareName(name);
               }
            }
         } else if (n instanceof CatchClause clause) {
            String name = clause.getVarName().shortName();
            this.declareName(name);
         } else if (n instanceof VariableInitializer initializer && initializer.getTarget() instanceof Name) {
            String id = ((Name)initializer.getTarget()).getIdentifier();
            this.declareName(id);
         }

         return true;
      });
   }

   public void print(Object node) {
      this.print((AstNode)node);
   }

   public void print(Object node, int precedence) {
      this.print((AstNode)node, precedence);
   }

   public boolean print(AstNode node) {
      return this.print(node, 18);
   }

   public boolean print(AstNode node, int precedence) {
      switch (node.getType()) {
         case 4:
            this.print((ReturnStatement)node);
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
         case 177:
         default:
            if (node instanceof InfixExpression) {
               this.printInfix((InfixExpression)node, precedence);
            } else if (node instanceof UnaryExpression) {
               this.printUnary((UnaryExpression)node, precedence);
            } else if (node instanceof UpdateExpression) {
               this.printUnary((UpdateExpression)node, precedence);
            }
            break;
         case 30:
         case 43:
            this.print((FunctionCall)node, precedence);
            break;
         case 33:
            this.print((PropertyGet)node);
            break;
         case 39:
            this.print((ElementGet)node);
            break;
         case 44:
            this.print((Name)node, precedence);
            break;
         case 45:
            this.print((NumberLiteral)node);
            break;
         case 46:
            this.print((StringLiteral)node);
            break;
         case 47:
            this.writer.append("null");
            break;
         case 48:
            this.writer.append("undefined");
            break;
         case 49:
            if (this.nameMap.containsKey("this")) {
               this.nameMap.get("this").emit(this.writer, precedence);
            } else {
               this.writer.append("this");
            }
            break;
         case 50:
            this.writer.append("false");
            break;
         case 51:
            this.writer.append("true");
            break;
         case 54:
            this.print((RegExpLiteral)node);
            break;
         case 56:
            this.print((ThrowStatement)node);
            break;
         case 72:
            this.print((ArrayLiteral)node);
            break;
         case 73:
            this.print((ObjectLiteral)node);
            break;
         case 92:
            this.print((TryStatement)node);
            break;
         case 98:
            this.print((ParenthesizedExpression)node, precedence);
            break;
         case 117:
            this.print((ConditionalExpression)node, precedence);
            break;
         case 124:
            return this.print((FunctionNode)node);
         case 127:
            this.print((IfStatement)node);
            break;
         case 129:
            this.print((SwitchStatement)node);
            break;
         case 132:
            this.print((WhileLoop)node);
            break;
         case 133:
            this.print((DoLoop)node);
            break;
         case 134:
            if (node instanceof ForInLoop) {
               this.print((ForInLoop)node);
            } else if (node instanceof ForLoop) {
               this.print((ForLoop)node);
            }
            break;
         case 135:
            this.print((BreakStatement)node);
            break;
         case 136:
            this.print((ContinueStatement)node);
            break;
         case 137:
         case 169:
         case 170:
            return this.print((VariableDeclaration)node);
         case 143:
            if (node instanceof EmptyStatement) {
               this.writer.append(';');
            }
            break;
         case 145:
            if (node instanceof Block) {
               this.print((Block)node);
            } else if (node instanceof Scope) {
               this.print((Scope)node);
            }
            break;
         case 149:
         case 150:
            if (node instanceof ExpressionStatement) {
               this.print((ExpressionStatement)node);
            } else if (node instanceof LabeledStatement) {
               this.print((LabeledStatement)node);
            }
            break;
         case 152:
            this.print((AstRoot)node);
            break;
         case 173:
            this.print((ArrayComprehension)node);
            break;
         case 174:
            this.print((LetNode)node);
            break;
         case 176:
            this.writer.append("debugger;");
            break;
         case 178:
            this.print((GeneratorExpression)node);
      }

      return false;
   }

   private void print(AstRoot node) {
      for (Node child : node) {
         if (!this.print((AstNode)child)) {
            this.writer.softNewLine();
         }
      }
   }

   private void print(Block node) {
      this.writer.append('{').softNewLine().indent();

      for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
         if (!this.print((AstNode)child)) {
            this.writer.softNewLine();
         }
      }

      this.writer.outdent().append('}');
   }

   private void print(Scope node) {
      Set<String> scope = this.enterScope(node, false);
      this.writer.append('{').softNewLine().indent();

      for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
         if (!this.print((AstNode)child)) {
            this.writer.softNewLine();
         }
      }

      this.writer.outdent().append('}');
      this.leaveScope(scope);
   }

   private void print(LabeledStatement node) {
      for (Label label : node.getLabels()) {
         this.writer.append(label.getName()).append(':').ws();
      }

      this.print(node.getStatement());
   }

   private void print(BreakStatement node) {
      this.writer.append("break");
      if (node.getBreakLabel() != null) {
         this.writer.append(' ').append(node.getBreakLabel().getString());
      }

      this.writer.append(';');
   }

   private void print(ContinueStatement node) {
      this.writer.append("continue");
      if (node.getLabel() != null) {
         this.writer.append(' ').append(node.getLabel().getString());
      }

      this.writer.append(';');
   }

   private void print(ReturnStatement node) {
      this.writer.append("return");
      if (node.getReturnValue() != null) {
         this.writer.append(' ');
         this.print(node.getReturnValue());
      }

      this.writer.append(';');
   }

   private void print(ThrowStatement node) {
      this.writer.append("throw ");
      this.print(node.getExpression());
      this.writer.append(';');
   }

   private void print(DoLoop node) {
      Set<String> scope = this.enterScope(node, false);
      this.writer.append("do ").ws();
      this.print(node.getBody());
      this.writer.append("while").ws().append('(');
      this.print(node.getCondition());
      this.writer.append(");");
      this.leaveScope(scope);
   }

   private void print(ForInLoop node) {
      Set<String> scope = this.enterScope(node, false);
      this.writer.append("for");
      if (node.isForEach()) {
         this.writer.append(" each");
      }

      this.writer.ws().append("(");
      this.print(node.getIterator());
      this.writer.append(" in ");
      this.print(node.getIteratedObject());
      this.writer.append(')').ws();
      this.print(node.getBody());
      this.leaveScope(scope);
   }

   private void print(ForLoop node) {
      Set<String> scope = this.enterScope(node, false);
      this.writer.append("for").ws().append('(');
      this.print(node.getInitializer());
      this.writer.append(';');
      this.print(node.getCondition());
      this.writer.append(';');
      this.print(node.getIncrement());
      this.writer.append(')').ws();
      this.print(node.getBody());
      this.leaveScope(scope);
   }

   private void print(WhileLoop node) {
      Set<String> scope = this.enterScope(node, false);
      this.writer.append("while").ws().append('(');
      this.print(node.getCondition());
      this.writer.append(')').ws();
      this.print(node.getBody());
      this.leaveScope(scope);
   }

   private void print(IfStatement node) {
      this.writer.append("if").ws().append('(');
      this.print(node.getCondition());
      this.writer.append(')').ws();
      this.print(node.getThenPart());
      if (node.getElsePart() != null) {
         this.writer.ws().append("else ");
         this.print(node.getElsePart());
      }
   }

   private void print(SwitchStatement node) {
      this.writer.append("switch").ws().append('(');
      this.print(node.getExpression());
      this.writer.append(')').ws().append('{').indent().softNewLine();

      for (SwitchCase sc : node.getCases()) {
         if (sc.getExpression() == null) {
            this.writer.append("default:");
         } else {
            this.writer.append("case ");
            this.print(sc.getExpression());
            this.writer.append(':');
         }

         this.writer.indent().softNewLine();
         if (sc.getStatements() != null) {
            for (AstNode stmt : sc.getStatements()) {
               this.print(stmt);
               this.writer.softNewLine();
            }
         }

         this.writer.outdent();
      }

      this.writer.outdent().append('}');
   }

   private void print(TryStatement node) {
      this.writer.append("try ");
      this.print(node.getTryBlock());

      for (CatchClause cc : node.getCatchClauses()) {
         this.writer.ws().append("catch").ws().append('(');
         Set<String> scope = this.enterScope(false);
         this.includeInScope(scope, cc.getVarName().shortName());
         this.print(cc.getVarName());
         if (cc.getCatchCondition() != null) {
            this.writer.append(" if ");
            this.print(cc.getCatchCondition());
         }

         this.writer.append(')');
         this.print(cc.getBody());
         this.leaveScope(scope);
      }

      if (node.getFinallyBlock() != null) {
         this.writer.ws().append("finally ");
         this.print(node.getFinallyBlock());
      }
   }

   private boolean print(VariableDeclaration node) {
      if (this.isTopLevelOutput()
         && ((VariableInitializer)node.getVariables().get(0)).getTarget() instanceof Name name
         && this.isTopLevelIdentifier(name.getIdentifier())) {
         this.printTopLevel(node);
         return true;
      }

      switch (node.getType()) {
         case 137:
            this.writer.append("var ");
            break;
         case 169:
            this.writer.append("let ");
            break;
         case 170:
            this.writer.append("const ");
      }

      this.print((VariableInitializer)node.getVariables().get(0));

      for (int i = 1; i < node.getVariables().size(); i++) {
         this.writer.append(',').ws();
         this.print((VariableInitializer)node.getVariables().get(i));
      }

      if (node.isStatement()) {
         this.writer.append(';');
      }

      return false;
   }

   private void printTopLevel(VariableDeclaration node) {
      for (VariableInitializer variable : node.getVariables()) {
         AstNode target = variable.getTarget();
         if (target instanceof Name) {
            String id = ((Name)target).getIdentifier();
            if (variable.getInitializer() != null) {
               this.writer.startVariableDeclaration().appendFunction(id);
               this.print(variable.getInitializer());
               this.writer.endDeclaration();
            } else {
               this.writer.declareVariable().appendFunction(id);
            }
         }
      }
   }

   private void print(VariableInitializer node) {
      this.print(node.getTarget());
      if (node.getInitializer() != null) {
         this.writer.ws().append('=').ws();
         this.print(node.getInitializer());
      }
   }

   private void print(ExpressionStatement node) {
      this.print(node.getExpression());
      this.writer.append(';');
   }

   protected void print(ElementGet node) {
      this.print(node.getTarget(), 2);
      this.writer.append('[');
      this.print(node.getElement());
      this.writer.append(']');
   }

   public void print(PropertyGet node) {
      this.print(node.getLeft(), 2);
      this.writer.append('.');
      boolean oldRootScope = this.rootScope;
      this.rootScope = false;
      this.print(node.getRight());
      this.rootScope = oldRootScope;
   }

   private void print(FunctionCall node, int precedence) {
      if (!this.intrinsic(node, precedence)) {
         if (!this.tryJavaInvocation(node)) {
            if (precedence < 3) {
               this.writer.append('(');
            }

            int innerPrecedence = node instanceof NewExpression ? 2 : 3;
            if (node instanceof NewExpression) {
               this.writer.append("new ");
            }

            this.print(node.getTarget(), innerPrecedence);
            this.writer.append('(');
            this.printList(node.getArguments());
            this.writer.append(')');
            if (node instanceof NewExpression newExpr && newExpr.getInitializer() != null) {
               this.writer.ws();
               this.print(newExpr.getInitializer());
            }

            if (precedence < 3) {
               this.writer.append(')');
            }
         }
      }
   }

   protected boolean intrinsic(FunctionCall node, int precedence) {
      return false;
   }

   private boolean tryJavaInvocation(FunctionCall node) {
      if (node.getTarget() instanceof PropertyGet propertyGet) {
         String callMethod = this.getJavaMethod(propertyGet.getTarget());
         if (callMethod != null && propertyGet.getProperty().getIdentifier().equals("invoke")) {
            MethodReference method = MethodReference.parseIfPossible(callMethod);
            if (method == null) {
               return false;
            }

            this.writer.appendMethod(method).append('(');
            this.printList(node.getArguments());
            this.writer.append(')');
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private String getJavaMethod(AstNode node) {
      if (!(node instanceof StringLiteral)) {
         return null;
      }

      String str = ((StringLiteral)node).getValue();
      return !str.startsWith("$$JSO$$_") ? null : str.substring("$$JSO$$_".length());
   }

   private void print(ConditionalExpression node, int precedence) {
      if (precedence < 16) {
         this.writer.append('(');
      }

      this.print(node.getTestExpression(), 15);
      this.writer.ws().append('?').ws();
      this.print(node.getTrueExpression(), 15);
      this.writer.ws().append(':').ws();
      this.print(node.getFalseExpression(), 16);
      if (precedence < 16) {
         this.writer.append(')');
      }
   }

   private void printList(List<? extends AstNode> nodes) {
      if (nodes != null && !nodes.isEmpty()) {
         this.print(nodes.get(0));

         for (int i = 1; i < nodes.size(); i++) {
            this.writer.append(',').ws();
            this.print(nodes.get(i));
         }
      }
   }

   private void print(ArrayComprehension node) {
      Set<String> scope = this.enterScope(node, false);
      this.writer.append("[");

      for (ArrayComprehensionLoop loop : node.getLoops()) {
         this.writer.append("for").ws().append("(");
         this.print(loop.getIterator());
         this.writer.append(" of ");
         this.print(loop.getIteratedObject());
         this.writer.append(')');
      }

      if (node.getFilter() != null) {
         this.writer.append("if").ws().append("(");
         this.print(node.getFilter());
         this.writer.append(")");
      }

      this.print(node.getResult());
      this.writer.append(']');
      this.leaveScope(scope);
   }

   private void print(GeneratorExpression node) {
      Set<String> scope = this.enterScope(node, false);
      this.writer.append("(");

      for (GeneratorExpressionLoop loop : node.getLoops()) {
         this.writer.append("for").ws().append("(");
         this.print(loop.getIterator());
         this.writer.append(" of ");
         this.print(loop.getIteratedObject());
         this.writer.append(')');
      }

      if (node.getFilter() != null) {
         this.writer.append("if").ws().append("(");
         this.print(node.getFilter());
         this.writer.append(")");
      }

      this.print(node.getResult());
      this.writer.append(')');
      this.leaveScope(scope);
   }

   private void print(NumberLiteral node) {
      this.writer.append(node.getValue());
   }

   private void print(StringLiteral node) {
      this.writer.append(node.getQuoteCharacter());
      this.writer.append(ScriptRuntime.escapeString(node.getValue(), node.getQuoteCharacter()));
      this.writer.append(node.getQuoteCharacter());
   }

   public void print(Name node, int precedence) {
      if (this.rootScope && this.isTopLevelIdentifier(node.getIdentifier())) {
         NameEmitter alias = this.nameMap.get(node.getIdentifier());
         if (alias == null) {
            if (this.globalNameWriter != null) {
               alias = this.globalNameWriter.apply(node.getIdentifier());
            } else {
               alias = (w, prec) -> w.append(node.getIdentifier());
            }
         }

         alias.emit(this.writer, precedence);
      } else {
         this.writer.append(node.getIdentifier());
      }
   }

   private void print(RegExpLiteral node) {
      this.writer.append('/').append(node.getValue()).append('/').append(node.getFlags());
   }

   private void print(ArrayLiteral node) {
      this.writer.append('[');
      this.printList(node.getElements());
      this.writer.append(']');
   }

   private void print(ObjectLiteral node) {
      this.writer.append('{').ws();
      if (node.getElements() != null && !node.getElements().isEmpty()) {
         this.print((AbstractObjectProperty)node.getElements().get(0));

         for (int i = 1; i < node.getElements().size(); i++) {
            this.writer.append(',').ws();
            this.print((AbstractObjectProperty)node.getElements().get(i));
         }
      }

      this.writer.ws().append('}');
   }

   private void print(AbstractObjectProperty node) {
      if (node instanceof ObjectProperty objectProperty) {
         if (objectProperty.isGetterMethod()) {
            this.writer.append("get ");
         } else if (objectProperty.isSetterMethod()) {
            this.writer.append("set ");
         }

         boolean oldRootScope = this.rootScope;
         this.rootScope = false;
         this.print(objectProperty.getKey());
         this.rootScope = oldRootScope;
         if (!objectProperty.isMethod()) {
            this.writer.append(':').ws();
         }

         this.print(objectProperty.getValue());
      } else if (node instanceof SpreadObjectProperty spreadProperty) {
         this.writer.append("...");
         this.print(spreadProperty.getSpreadNode().getExpression());
      }
   }

   protected boolean print(FunctionNode node) {
      boolean isArrow = node.getFunctionType() == 4;
      if (this.isTopLevelOutput() && !isArrow && node.getFunctionName() != null && this.isTopLevelIdentifier(node.getFunctionName().getIdentifier())) {
         this.printTopLevel(node);
         return true;
      }

      boolean wasInFunction = this.inFunction;
      this.inFunction = true;
      Set<String> scope = this.enterScope(node, true);
      if (!isArrow) {
         this.includeInScope(scope, "arguments");
      }

      if (!node.isMethod() && !isArrow) {
         this.writer.append("function");
      }

      if (node.getFunctionName() != null) {
         this.writer.append(' ');
         this.print(node.getFunctionName());
      }

      if (isArrow && node.getParams().size() == 1) {
         this.print((AstNode)node.getParams().get(0));
      } else {
         this.writer.append('(');
         this.printList(node.getParams());
         this.writer.append(')');
      }

      if (isArrow) {
         this.writer.sameLineWs().append("=>").ws();
      } else {
         this.writer.ws();
      }

      if (node.isExpressionClosure()) {
         if (node.getBody().getLastChild() instanceof ReturnStatement) {
            this.print(((ReturnStatement)node.getBody().getLastChild()).getReturnValue());
            if (node.getFunctionType() == 1) {
               this.writer.append(";");
            }
         }
      } else {
         this.print(node.getBody());
      }

      this.leaveScope(scope);
      this.inFunction = wasInFunction;
      return false;
   }

   private void printTopLevel(FunctionNode node) {
      boolean wasInFunction = this.inFunction;
      this.inFunction = true;
      Set<String> scope = this.enterScope(node, true);
      this.includeInScope(scope, "arguments");
      this.writer.startFunctionDeclaration().appendFunction(node.getFunctionName().getIdentifier());
      this.writer.append('(');
      this.printList(node.getParams());
      this.writer.append(')').ws();
      this.print(node.getBody());
      this.writer.endDeclaration();
      this.leaveScope(scope);
      this.inFunction = wasInFunction;
   }

   private void print(LetNode node) {
      Set<String> scope = this.enterScope(node, false);
      this.writer.append("let").ws().append('(');
      this.printList(node.getVariables().getVariables());
      this.writer.append(')');
      this.print(node.getBody());
      this.leaveScope(scope);
   }

   private void print(ParenthesizedExpression node, int precedence) {
      this.print(node.getExpression(), precedence);
   }

   private void printUnary(UnaryExpression node, int precedence) {
      int innerPrecedence = 5;
      if (innerPrecedence > precedence) {
         this.writer.append('(');
      }

      String op = AstNode.operatorToString(node.getType());
      if (op.startsWith("-")) {
         this.writer.append(' ');
      }

      this.writer.append(op);
      if (this.requiresWhitespaces(node.getType())) {
         this.writer.append(' ');
      }

      this.print(node.getOperand(), innerPrecedence);
      if (innerPrecedence > precedence) {
         this.writer.append(')');
      }
   }

   private void printUnary(UpdateExpression node, int precedence) {
      int innerPrecedence = node.isPostfix() ? 4 : 5;
      if (innerPrecedence > precedence) {
         this.writer.append('(');
      }

      if (!node.isPostfix()) {
         String op = AstNode.operatorToString(node.getType());
         if (op.startsWith("-")) {
            this.writer.append(' ');
         }

         this.writer.append(op);
         if (this.requiresWhitespaces(node.getType())) {
            this.writer.append(' ');
         }
      }

      this.print(node.getOperand(), innerPrecedence);
      if (node.isPostfix()) {
         this.writer.append(AstNode.operatorToString(node.getType()));
      }

      if (innerPrecedence > precedence) {
         this.writer.append(')');
      }
   }

   private void printInfix(InfixExpression node, int precedence) {
      int innerPrecedence = this.getPrecedence(node.getType());
      if (innerPrecedence > precedence) {
         this.writer.append('(');
      }
      int leftPrecedence = switch (node.getType()) {
         case 101, 102, 104, 105, 107, 108, 109, 110, 111, 112, 113, 114 -> innerPrecedence - 1;
         default -> innerPrecedence;
      };
      this.print(node.getLeft(), leftPrecedence);
      String op = AstNode.operatorToString(node.getType());
      boolean ws = this.requiresWhitespaces(node.getType());
      if (!ws && !op.startsWith("-")) {
         this.writer.ws();
      } else {
         this.writer.append(' ');
      }

      this.writer.append(op);
      if (ws) {
         this.writer.append(' ');
      } else {
         this.writer.ws();
      }
      int rightPrecedence = switch (node.getType()) {
         case 12, 13, 14, 15, 16, 17, 18, 19, 20, 22, 24, 25, 52, 53, 58, 59 -> innerPrecedence - 1;
         default -> innerPrecedence;
      };
      this.print(node.getRight(), rightPrecedence);
      if (innerPrecedence > precedence) {
         this.writer.append(')');
      }
   }

   private int getPrecedence(int token) {
      return switch (token) {
         case 9 -> 13;
         case 10 -> 12;
         case 11 -> 11;
         case 12, 13, 52, 53 -> 10;
         case 14, 15, 16, 17, 58, 59 -> 9;
         case 18, 19, 20 -> 8;
         case 21, 22 -> 7;
         case 23, 24, 25 -> 6;
         default -> 18;
         case 101, 102, 104, 105, 107, 108, 109, 110, 111, 112, 113, 114 -> 17;
         case 119 -> 15;
         case 120 -> 14;
      };
   }

   private boolean requiresWhitespaces(int token) {
      return switch (token) {
         case 31, 32, 58, 59, 76, 141 -> true;
         default -> false;
      };
   }

   private Set<String> enterScope(Scope scope, boolean nesting) {
      Set<String> set = this.enterScope(nesting);
      if (scope.getSymbolTable() != null) {
         for (String name : scope.getSymbolTable().keySet()) {
            this.includeInScope(set, name);
         }
      }

      return set;
   }

   public Set<String> enterScope(boolean nesting) {
      if (this.scopeLevel > 0 || nesting) {
         this.scopeLevel++;
      }

      return new LinkedHashSet<>();
   }

   public final void includeInScope(Set<String> scope, String name) {
      if (this.nonTopLevels.add(name)) {
         scope.add(name);
      }
   }

   public void leaveScope(Set<String> backup) {
      this.nonTopLevels.removeAll(backup);
      if (this.scopeLevel > 0) {
         this.scopeLevel--;
      }
   }

   protected boolean isTopLevelIdentifier(String id) {
      return !this.nonTopLevels.contains(id);
   }

   protected boolean isInTopLevelScope() {
      return this.scopeLevel == 0;
   }

   protected boolean isTopLevelOutput() {
      return false;
   }
}
