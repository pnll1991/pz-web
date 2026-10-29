package org.teavm.backend.javascript.rendering;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.teavm.ast.ArrayFromDataExpr;
import org.teavm.ast.AssignmentStatement;
import org.teavm.ast.BinaryExpr;
import org.teavm.ast.BinaryOperation;
import org.teavm.ast.BlockStatement;
import org.teavm.ast.BoundCheckExpr;
import org.teavm.ast.BreakStatement;
import org.teavm.ast.CastExpr;
import org.teavm.ast.ConditionalExpr;
import org.teavm.ast.ConditionalStatement;
import org.teavm.ast.ConstantExpr;
import org.teavm.ast.ContinueStatement;
import org.teavm.ast.Expr;
import org.teavm.ast.ExprVisitor;
import org.teavm.ast.GotoPartStatement;
import org.teavm.ast.InitClassStatement;
import org.teavm.ast.InstanceOfExpr;
import org.teavm.ast.InvocationExpr;
import org.teavm.ast.InvocationType;
import org.teavm.ast.MethodNode;
import org.teavm.ast.MonitorEnterStatement;
import org.teavm.ast.MonitorExitStatement;
import org.teavm.ast.NewArrayExpr;
import org.teavm.ast.NewExpr;
import org.teavm.ast.NewMultiArrayExpr;
import org.teavm.ast.OperationType;
import org.teavm.ast.PrimitiveCastExpr;
import org.teavm.ast.QualificationExpr;
import org.teavm.ast.ReturnStatement;
import org.teavm.ast.SequentialStatement;
import org.teavm.ast.Statement;
import org.teavm.ast.StatementVisitor;
import org.teavm.ast.SubscriptExpr;
import org.teavm.ast.SwitchClause;
import org.teavm.ast.SwitchStatement;
import org.teavm.ast.ThrowStatement;
import org.teavm.ast.TryCatchStatement;
import org.teavm.ast.UnaryExpr;
import org.teavm.ast.UnwrapArrayExpr;
import org.teavm.ast.VariableExpr;
import org.teavm.ast.WhileStatement;
import org.teavm.backend.javascript.codegen.NamingStrategy;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.dependency.DependencyInfo;
import org.teavm.hppc.IntArrayList;
import org.teavm.hppc.IntIndexedContainer;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.InliningInfo;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.parsing.resource.ResourceProvider;
import org.teavm.vm.RenderingException;

public class StatementRenderer implements ExprVisitor, StatementVisitor {
   private RenderingContext context;
   private SourceWriter writer;
   private SourceWriter metadataWriter;
   private ClassReaderSource classSource;
   private boolean async;
   private boolean minifying;
   private Precedence precedence;
   private NamingStrategy naming;
   private boolean end;
   private final Map<String, String> blockIdMap = new HashMap<>();
   private int currentPart;
   private List<String> blockIds = new ArrayList<>();
   private IntIndexedContainer blockIndexMap = new IntArrayList();
   private static final MethodDescriptor CLINIT_METHOD = new MethodDescriptor("<clinit>", ValueType.VOID);
   private VariableNameGenerator variableNameGenerator;
   private final Deque<StatementRenderer.LocationStackEntry> locationStack = new ArrayDeque<>();
   private TextLocation lastEmittedLocation = TextLocation.EMPTY;

   public StatementRenderer(RenderingContext context, SourceWriter writer, SourceWriter metadataWriter, VariableNameGenerator variableNameGenerator) {
      this.context = context;
      this.writer = writer;
      this.metadataWriter = metadataWriter;
      this.classSource = context.getClassSource();
      this.minifying = context.isMinifying();
      this.naming = context.getNaming();
      this.variableNameGenerator = variableNameGenerator;
   }

   public void clear() {
      this.blockIdMap.clear();
      this.blockIds.clear();
      this.blockIndexMap.clear();
      this.currentPart = 0;
      this.end = false;
      this.precedence = null;
      this.variableNameGenerator.setCurrentMethod(null);
      this.locationStack.clear();
      this.lastEmittedLocation = TextLocation.EMPTY;
      this.variableNameGenerator.clear();
   }

   public boolean isAsync() {
      return this.async;
   }

   public void setAsync(boolean async) {
      this.async = async;
   }

   public void setCurrentMethod(MethodNode currentMethod) {
      this.variableNameGenerator.setCurrentMethod(currentMethod);
   }

   public void setCurrentPart(int currentPart) {
      this.currentPart = currentPart;
   }

   public void setEnd(boolean end) {
      this.end = end;
   }

   public void pushLocation(TextLocation location) {
      StatementRenderer.LocationStackEntry prevEntry = this.locationStack.peek();
      if (location != null) {
         if (prevEntry == null || !location.equals(prevEntry.location)) {
            this.emitLocation(location);
         }
      } else if (prevEntry != null) {
         this.emitLocation(TextLocation.EMPTY);
      }

      this.locationStack.push(new StatementRenderer.LocationStackEntry(location));
   }

   public void popLocation() {
      StatementRenderer.LocationStackEntry prevEntry = this.locationStack.pop();
      StatementRenderer.LocationStackEntry entry = this.locationStack.peek();
      if (entry != null) {
         if (!entry.location.equals(prevEntry.location)) {
            this.emitLocation(entry.location);
         }
      } else {
         this.emitLocation(TextLocation.EMPTY);
      }
   }

   private void emitLocation(TextLocation location) {
      if (!this.lastEmittedLocation.equals(location)) {
         String fileName = this.lastEmittedLocation.getFileName();
         int lineNumber = this.lastEmittedLocation.getLine();
         if (this.lastEmittedLocation.getInlining() != location.getInlining()) {
            InliningInfo[] newPath = location.getInliningPath();
            InliningInfo[] prevPath = this.lastEmittedLocation.getInliningPath();
            InliningInfo lastCommonInlining = null;
            int pathIndex = 0;

            while (pathIndex < prevPath.length && pathIndex < newPath.length && prevPath[pathIndex].equals(newPath[pathIndex])) {
               lastCommonInlining = prevPath[pathIndex++];
            }

            for (InliningInfo prevInlining = this.lastEmittedLocation.getInlining();
               prevInlining != lastCommonInlining;
               prevInlining = prevInlining.getParent()
            ) {
               this.writer.exitLocation();
               fileName = prevInlining.getFileName();
               lineNumber = prevInlining.getLine();
            }

            while (pathIndex < newPath.length) {
               InliningInfo inlining = newPath[pathIndex++];
               this.emitSimpleLocation(fileName, lineNumber, inlining.getFileName(), inlining.getLine());
               fileName = null;
               lineNumber = -1;
               this.writer.enterLocation();
               this.writer.emitClass(inlining.getMethod().getClassName());
               this.writer.emitMethod(inlining.getMethod().getDescriptor());
            }
         }

         this.emitSimpleLocation(fileName, lineNumber, location.getFileName(), location.getLine());
         this.lastEmittedLocation = location;
      }
   }

   private void emitSimpleLocation(String fileName, int lineNumber, String newFileName, int newLineNumber) {
      if (!Objects.equals(fileName, newFileName) || lineNumber != newLineNumber) {
         this.writer.emitLocation(newFileName, newLineNumber);
      }
   }

   @Override
   public void visit(AssignmentStatement statement) throws RenderingException {
      this.writer.emitStatementStart();
      if (statement.getLocation() != null) {
         this.pushLocation(statement.getLocation());
      }

      if (statement.getLeftValue() != null) {
         if (statement.isAsync()) {
            this.writer.append(this.context.tempVarName());
         } else {
            this.precedence = Precedence.COMMA;
            statement.getLeftValue().acceptVisitor(this);
         }

         this.writer.ws().append("=").ws();
      }

      this.precedence = Precedence.COMMA;
      statement.getRightValue().acceptVisitor(this);
      this.writer.append(";").softNewLine();
      if (statement.isAsync()) {
         this.emitSuspendChecker();
         if (statement.getLeftValue() != null) {
            this.precedence = Precedence.COMMA;
            statement.getLeftValue().acceptVisitor(this);
            this.writer.ws().append("=").ws().append(this.context.tempVarName()).append(";").softNewLine();
         }
      }

      if (statement.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(SequentialStatement statement) {
      this.visitStatements(statement.getSequence());
   }

   @Override
   public void visit(ConditionalStatement statement) {
      while (true) {
         this.writer.emitStatementStart();
         if (statement.getCondition().getLocation() != null) {
            this.pushLocation(statement.getCondition().getLocation());
         }

         this.writer.append("if").ws().append("(");
         this.precedence = Precedence.COMMA;
         statement.getCondition().acceptVisitor(this);
         if (statement.getCondition().getLocation() != null) {
            this.popLocation();
         }

         this.writer.append(")");
         boolean needClosingBracket;
         if (this.isSimpleIfContent(statement.getConsequent())) {
            needClosingBracket = false;
         } else {
            this.writer.ws().append("{");
            needClosingBracket = true;
         }

         this.writer.softNewLine().indent();
         this.visitStatements(statement.getConsequent());
         if (!statement.getAlternative().isEmpty()) {
            this.writer.outdent();
            if (needClosingBracket) {
               this.writer.append("}").ws();
            }

            if (statement.getAlternative().size() == 1 && statement.getAlternative().get(0) instanceof ConditionalStatement) {
               statement = (ConditionalStatement)statement.getAlternative().get(0);
               this.writer.append("else ");
               continue;
            }

            this.writer.append("else");
            if (this.isSimpleIfContent(statement.getAlternative())) {
               if (this.minifying) {
                  this.writer.append(" ");
               }

               needClosingBracket = false;
            } else {
               this.writer.ws().append("{");
               needClosingBracket = true;
            }

            this.writer.indent().softNewLine();
            this.visitStatements(statement.getAlternative());
         }

         this.writer.outdent();
         if (needClosingBracket) {
            this.writer.append("}").softNewLine();
         }

         return;
      }
   }

   private boolean isSimpleIfContent(List<Statement> statements) {
      if (statements.size() != 1) {
         return false;
      }

      Statement statement = statements.get(0);
      return !(statement instanceof ConditionalStatement) && !(statement instanceof GotoPartStatement);
   }

   @Override
   public void visit(SwitchStatement statement) {
      this.writer.emitStatementStart();
      if (statement.getValue().getLocation() != null) {
         this.pushLocation(statement.getValue().getLocation());
      }

      if (statement.getId() != null) {
         this.writer.append(this.mapBlockId(statement.getId())).append(":").ws();
      }

      this.writer.append("switch").ws().append("(");
      this.precedence = Precedence.min();
      statement.getValue().acceptVisitor(this);
      if (statement.getValue().getLocation() != null) {
         this.popLocation();
      }

      this.writer.append(")").ws().append("{").softNewLine().indent();

      for (SwitchClause clause : statement.getClauses()) {
         for (int condition : clause.getConditions()) {
            this.writer.append("case ").append(condition).append(":").softNewLine();
         }

         this.writer.indent();
         boolean oldEnd = this.end;

         for (Statement part : clause.getBody()) {
            this.end = false;
            part.acceptVisitor(this);
         }

         this.end = oldEnd;
         this.writer.outdent();
      }

      if (statement.getDefaultClause() != null) {
         this.writer.append("default:").softNewLine().indent();
         boolean oldEnd = this.end;

         for (Statement part : statement.getDefaultClause()) {
            this.end = false;
            part.acceptVisitor(this);
         }

         this.end = oldEnd;
         this.writer.outdent();
      }

      this.writer.outdent().append("}").softNewLine();
   }

   @Override
   public void visit(WhileStatement statement) {
      this.writer.emitStatementStart();
      if (statement.getId() != null) {
         this.writer.append(this.mapBlockId(statement.getId())).append(":").ws();
      }

      this.writer.append("while");
      this.writer.ws().append("(");
      if (statement.getCondition() != null) {
         this.precedence = Precedence.min();
         statement.getCondition().acceptVisitor(this);
      } else {
         this.writer.append("true");
      }

      this.writer.append(")").ws().append("{").softNewLine().indent();
      boolean oldEnd = this.end;

      for (Statement part : statement.getBody()) {
         this.end = false;
         part.acceptVisitor(this);
      }

      this.end = oldEnd;
      this.writer.outdent().append("}").softNewLine();
   }

   private String mapBlockId(String id) {
      String name = this.blockIdMap.get(id);
      if (name == null) {
         int index = this.blockIdMap.size();
         name = this.generateBlockId(index);
         this.blockIdMap.put(id, name);
      }

      return name;
   }

   private String generateBlockId(int index) {
      while (this.blockIds.size() <= index) {
         int mappedIndex = this.blockIndexMap.isEmpty() ? -1 : this.blockIndexMap.get(this.blockIds.size() - 1);
         mappedIndex++;

         while (RenderingUtil.KEYWORDS.contains(RenderingUtil.indexToId(mappedIndex))) {
            mappedIndex++;
         }

         this.blockIndexMap.add(mappedIndex);
         this.blockIds.add(RenderingUtil.indexToId(mappedIndex));
      }

      return this.blockIds.get(index);
   }

   @Override
   public void visit(BlockStatement statement) {
      this.writer.append(this.mapBlockId(statement.getId())).append(":").ws().append("{").softNewLine().indent();
      this.visitStatements(statement.getBody());
      this.writer.outdent().append("}").softNewLine();
   }

   @Override
   public void visit(BreakStatement statement) {
      this.writer.emitStatementStart();
      if (statement.getLocation() != null) {
         this.pushLocation(statement.getLocation());
      }

      this.writer.append("break");
      if (statement.getTarget() != null) {
         this.writer.append(' ').append(this.mapBlockId(statement.getTarget().getId()));
      }

      this.writer.append(";").softNewLine();
      if (statement.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(ContinueStatement statement) {
      this.writer.emitStatementStart();
      if (statement.getLocation() != null) {
         this.pushLocation(statement.getLocation());
      }

      this.writer.append("continue");
      if (statement.getTarget() != null) {
         this.writer.append(' ').append(this.mapBlockId(statement.getTarget().getId()));
      }

      this.writer.append(";").softNewLine();
      if (statement.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(ReturnStatement statement) {
      this.writer.emitStatementStart();
      if (statement.getLocation() != null) {
         this.pushLocation(statement.getLocation());
      }

      this.writer.append("return");
      if (statement.getResult() != null) {
         this.writer.append(' ');
         this.precedence = Precedence.min();
         statement.getResult().acceptVisitor(this);
      }

      this.writer.append(";").softNewLine();
      if (statement.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(ThrowStatement statement) {
      this.writer.emitStatementStart();
      if (statement.getLocation() != null) {
         this.pushLocation(statement.getLocation());
      }

      this.writer.appendFunction("$rt_throw").append("(");
      this.precedence = Precedence.min();
      statement.getException().acceptVisitor(this);
      this.writer.append(");").softNewLine();
      if (statement.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(InitClassStatement statement) {
      ClassReader cls = this.classSource.get(statement.getClassName());
      if (cls != null) {
         MethodReader method = cls.getMethod(CLINIT_METHOD);
         if (method != null) {
            this.writer.emitStatementStart();
            if (statement.getLocation() != null) {
               this.pushLocation(statement.getLocation());
            }

            this.writer.appendClassInit(statement.getClassName()).append("();").softNewLine();
            if (statement.isAsync()) {
               this.emitSuspendChecker();
            }

            if (statement.getLocation() != null) {
               this.popLocation();
            }
         }
      }
   }

   public String variableName(int index) {
      return this.variableNameGenerator.variableName(index);
   }

   private void visitBinary(BinaryExpr expr, String op, boolean guarded) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      if (guarded) {
         this.visitBinary(BinaryOperation.OR, "|", () -> this.visitBinary(expr, op, false), () -> this.writer.append("0"));
      } else {
         this.visitBinary(expr.getOperation(), op, () -> expr.getFirstOperand().acceptVisitor(this), () -> expr.getSecondOperand().acceptVisitor(this));
      }

      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   private void visitBinary(BinaryOperation operation, String infixText, Runnable a, Runnable b) {
      Precedence outerPrecedence = this.precedence;
      Precedence innerPrecedence = getPrecedence(operation);
      if (innerPrecedence.ordinal() < outerPrecedence.ordinal()) {
         this.writer.append('(');
      }

      switch (operation) {
         case ADD:
         case SUBTRACT:
         case MULTIPLY:
         case DIVIDE:
         case AND:
         case OR:
         case BITWISE_AND:
         case BITWISE_OR:
         case BITWISE_XOR:
         case LEFT_SHIFT:
         case RIGHT_SHIFT:
         case UNSIGNED_RIGHT_SHIFT:
            this.precedence = innerPrecedence;
            break;
         default:
            this.precedence = innerPrecedence.next();
      }

      a.run();
      this.writer.ws().append(infixText).ws();
      switch (operation) {
         case ADD:
         case MULTIPLY:
         case AND:
         case OR:
         case BITWISE_AND:
         case BITWISE_OR:
         case BITWISE_XOR:
            this.precedence = innerPrecedence;
            break;
         case SUBTRACT:
         case DIVIDE:
         default:
            this.precedence = innerPrecedence.next();
      }

      b.run();
      if (innerPrecedence.ordinal() < outerPrecedence.ordinal()) {
         this.writer.append(')');
      }
   }

   private static Precedence getPrecedence(BinaryOperation op) {
      switch (op) {
         case ADD:
         case SUBTRACT:
            return Precedence.ADDITION;
         case MULTIPLY:
         case DIVIDE:
            return Precedence.MULTIPLICATION;
         case AND:
            return Precedence.LOGICAL_AND;
         case OR:
            return Precedence.LOGICAL_OR;
         case BITWISE_AND:
            return Precedence.BITWISE_AND;
         case BITWISE_OR:
            return Precedence.BITWISE_OR;
         case BITWISE_XOR:
            return Precedence.BITWISE_XOR;
         case LEFT_SHIFT:
         case RIGHT_SHIFT:
         case UNSIGNED_RIGHT_SHIFT:
            return Precedence.BITWISE_SHIFT;
         case MODULO:
            return Precedence.MODULO;
         case EQUALS:
         case NOT_EQUALS:
            return Precedence.EQUALITY;
         case GREATER:
         case GREATER_OR_EQUALS:
         case LESS:
         case LESS_OR_EQUALS:
            return Precedence.COMPARISON;
         default:
            return Precedence.GROUPING;
      }
   }

   private void visitBinaryFunction(BinaryExpr expr, String function) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      this.writer.appendFunction(function);
      this.writer.append('(');
      this.precedence = Precedence.min();
      expr.getFirstOperand().acceptVisitor(this);
      this.writer.append(",").ws();
      this.precedence = Precedence.min();
      expr.getSecondOperand().acceptVisitor(this);
      this.writer.append(')');
      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(BinaryExpr expr) {
      if (expr.getType() == OperationType.LONG) {
         switch (expr.getOperation()) {
            case ADD:
               this.visitBinaryFunction(expr, "Long_add");
               break;
            case SUBTRACT:
               this.visitBinaryFunction(expr, "Long_sub");
               break;
            case MULTIPLY:
               this.visitBinaryFunction(expr, "Long_mul");
               break;
            case DIVIDE:
               this.visitBinaryFunction(expr, "Long_div");
            case AND:
            case OR:
            default:
               break;
            case BITWISE_AND:
               this.visitBinaryFunction(expr, "Long_and");
               break;
            case BITWISE_OR:
               this.visitBinaryFunction(expr, "Long_or");
               break;
            case BITWISE_XOR:
               this.visitBinaryFunction(expr, "Long_xor");
               break;
            case LEFT_SHIFT:
               this.visitBinaryFunction(expr, "Long_shl");
               break;
            case RIGHT_SHIFT:
               this.visitBinaryFunction(expr, "Long_shr");
               break;
            case UNSIGNED_RIGHT_SHIFT:
               this.visitBinaryFunction(expr, "Long_shru");
               break;
            case MODULO:
               this.visitBinaryFunction(expr, "Long_rem");
               break;
            case EQUALS:
               this.visitBinaryFunction(expr, "Long_eq");
               break;
            case NOT_EQUALS:
               this.visitBinaryFunction(expr, "Long_ne");
               break;
            case GREATER:
               this.visitBinaryFunction(expr, "Long_gt");
               break;
            case GREATER_OR_EQUALS:
               this.visitBinaryFunction(expr, "Long_ge");
               break;
            case LESS:
               this.visitBinaryFunction(expr, "Long_lt");
               break;
            case LESS_OR_EQUALS:
               this.visitBinaryFunction(expr, "Long_le");
               break;
            case COMPARE_LESS:
            case COMPARE_GREATER:
               this.visitBinaryFunction(expr, "Long_compare");
         }
      } else {
         switch (expr.getOperation()) {
            case ADD:
               this.visitBinary(expr, "+", expr.getType() == OperationType.INT);
               break;
            case SUBTRACT:
               this.visitBinary(expr, "-", expr.getType() == OperationType.INT);
               break;
            case MULTIPLY:
               if (expr.getType() == OperationType.INT
                  && !RenderingUtil.isSmallInteger(expr.getFirstOperand())
                  && !RenderingUtil.isSmallInteger(expr.getSecondOperand())) {
                  this.visitBinaryFunction(expr, "$rt_imul");
               } else {
                  this.visitBinary(expr, "*", expr.getType() == OperationType.INT);
               }
               break;
            case DIVIDE:
               this.visitBinary(expr, "/", expr.getType() == OperationType.INT);
               break;
            case AND:
               this.visitBinary(expr, "&&", false);
               break;
            case OR:
               this.visitBinary(expr, "||", false);
               break;
            case BITWISE_AND:
               this.visitBinary(expr, "&", false);
               break;
            case BITWISE_OR:
               this.visitBinary(expr, "|", false);
               break;
            case BITWISE_XOR:
               this.visitBinary(expr, "^", false);
               break;
            case LEFT_SHIFT:
               this.visitBinary(expr, "<<", false);
               break;
            case RIGHT_SHIFT:
               this.visitBinary(expr, ">>", false);
               break;
            case UNSIGNED_RIGHT_SHIFT:
               this.visitBinary(expr, ">>>", true);
               break;
            case MODULO:
               this.visitBinary(expr, "%", expr.getType() == OperationType.INT);
               break;
            case EQUALS:
               if (expr.getType() == OperationType.INT) {
                  this.visitBinary(expr, "==", false);
               } else {
                  this.visitBinary(expr, "===", false);
               }
               break;
            case NOT_EQUALS:
               if (expr.getType() == OperationType.INT) {
                  this.visitBinary(expr, "!=", false);
               } else {
                  this.visitBinary(expr, "!==", false);
               }
               break;
            case GREATER:
               this.visitBinary(expr, ">", false);
               break;
            case GREATER_OR_EQUALS:
               this.visitBinary(expr, ">=", false);
               break;
            case LESS:
               this.visitBinary(expr, "<", false);
               break;
            case LESS_OR_EQUALS:
               this.visitBinary(expr, "<=", false);
               break;
            case COMPARE_LESS:
               this.visitBinaryFunction(expr, "$rt_compare_less");
               break;
            case COMPARE_GREATER:
               this.visitBinaryFunction(expr, "$rt_compare");
         }
      }
   }

   @Override
   public void visit(UnaryExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      Precedence outerPrecedence = this.precedence;
      switch (expr.getOperation()) {
         case NOT:
            if (expr.getType() == OperationType.LONG) {
               this.writer.appendFunction("Long_not").append("(");
               this.precedence = Precedence.min();
               expr.getOperand().acceptVisitor(this);
               this.writer.append(')');
            } else {
               if (outerPrecedence.ordinal() > Precedence.UNARY.ordinal()) {
                  this.writer.append('(');
               }

               this.writer.append(expr.getType() == null ? "!" : "~");
               this.precedence = Precedence.UNARY;
               expr.getOperand().acceptVisitor(this);
               if (outerPrecedence.ordinal() > Precedence.UNARY.ordinal()) {
                  this.writer.append(')');
               }
            }
            break;
         case NEGATE:
            if (expr.getType() == OperationType.LONG) {
               this.writer.appendFunction("Long_neg").append("(");
               this.precedence = Precedence.min();
               expr.getOperand().acceptVisitor(this);
               this.writer.append(')');
            } else if (expr.getType() == OperationType.INT) {
               if (outerPrecedence.ordinal() > Precedence.BITWISE_OR.ordinal()) {
                  this.writer.append('(');
               }

               this.writer.append(" -");
               this.precedence = Precedence.UNARY;
               expr.getOperand().acceptVisitor(this);
               this.writer.ws().append("|").ws();
               this.writer.append("0");
               if (outerPrecedence.ordinal() > Precedence.BITWISE_OR.ordinal()) {
                  this.writer.append(')');
               }
            } else {
               if (outerPrecedence.ordinal() > Precedence.UNARY.ordinal()) {
                  this.writer.append('(');
               }

               this.writer.append(" -");
               this.precedence = Precedence.UNARY;
               expr.getOperand().acceptVisitor(this);
               if (outerPrecedence.ordinal() > Precedence.UNARY.ordinal()) {
                  this.writer.append(')');
               }
            }
            break;
         case LENGTH:
            this.precedence = Precedence.MEMBER_ACCESS;
            expr.getOperand().acceptVisitor(this);
            this.writer.append(".length");
            break;
         case INT_TO_BYTE:
            if (outerPrecedence.ordinal() > Precedence.BITWISE_SHIFT.ordinal()) {
               this.writer.append('(');
            }

            this.precedence = Precedence.BITWISE_SHIFT;
            expr.getOperand().acceptVisitor(this);
            this.writer.ws().append("<<").ws().append("24").ws().append(">>").ws().append("24");
            if (outerPrecedence.ordinal() > Precedence.BITWISE_SHIFT.ordinal()) {
               this.writer.append(')');
            }
            break;
         case INT_TO_SHORT:
            if (outerPrecedence.ordinal() > Precedence.BITWISE_SHIFT.ordinal()) {
               this.writer.append('(');
            }

            this.precedence = Precedence.BITWISE_SHIFT;
            expr.getOperand().acceptVisitor(this);
            this.writer.ws().append("<<").ws().append("16").ws().append(">>").ws().append("16");
            if (outerPrecedence.ordinal() > Precedence.BITWISE_SHIFT.ordinal()) {
               this.writer.append(')');
            }
            break;
         case INT_TO_CHAR:
            if (outerPrecedence.ordinal() > Precedence.BITWISE_AND.ordinal()) {
               this.writer.append('(');
            }

            this.precedence = Precedence.BITWISE_AND;
            expr.getOperand().acceptVisitor(this);
            this.writer.ws().append("&").ws().append("65535");
            if (outerPrecedence.ordinal() > Precedence.BITWISE_AND.ordinal()) {
               this.writer.append(')');
            }
            break;
         case NULL_CHECK:
            if (outerPrecedence.ordinal() > Precedence.FUNCTION_CALL.ordinal()) {
               this.writer.append('(');
            }

            this.writer.appendFunction("$rt_nullCheck").append("(");
            this.precedence = Precedence.min();
            expr.getOperand().acceptVisitor(this);
            this.writer.append(')');
            if (outerPrecedence.ordinal() > Precedence.FUNCTION_CALL.ordinal()) {
               this.writer.append(')');
            }
      }

      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(CastExpr expr) {
      if (this.context.isStrict() && !expr.isWeak()) {
         if (expr.getLocation() != null) {
            this.pushLocation(expr.getLocation());
         }

         if (isClass(expr.getTarget(), this.context.getClassSource())) {
            this.writer.appendFunction("$rt_castToClass");
         } else {
            this.writer.appendFunction("$rt_castToInterface");
         }

         this.writer.append("(");
         this.precedence = Precedence.min();
         expr.getValue().acceptVisitor(this);
         this.writer.append(",").ws();
         RenderingUtil.typeToClsString(this.writer, expr.getTarget());
         this.writer.append(")");
         if (expr.getLocation() != null) {
            this.popLocation();
         }
      } else {
         expr.getValue().acceptVisitor(this);
      }
   }

   static boolean isClass(ValueType type, ClassReaderSource classSource) {
      if (!(type instanceof ValueType.Object)) {
         return false;
      }

      String className = ((ValueType.Object)type).getClassName();
      ClassReader cls = classSource.get(className);
      return cls != null && !cls.hasModifier(ElementModifier.INTERFACE);
   }

   @Override
   public void visit(PrimitiveCastExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      label29:
      switch (expr.getSource()) {
         case INT:
            if (expr.getTarget() == OperationType.LONG) {
               this.writer.appendFunction("Long_fromInt").append("(");
               this.precedence = Precedence.min();
               expr.getValue().acceptVisitor(this);
               this.writer.append(')');
            } else {
               expr.getValue().acceptVisitor(this);
            }
            break;
         case FLOAT:
         case DOUBLE:
            switch (expr.getTarget()) {
               case INT:
                  this.visitBinary(BinaryOperation.BITWISE_OR, "|", () -> expr.getValue().acceptVisitor(this), () -> this.writer.append("0"));
                  break label29;
               case LONG:
                  this.writer.appendFunction("Long_fromNumber").append("(");
                  this.precedence = Precedence.min();
                  expr.getValue().acceptVisitor(this);
                  this.writer.append(')');
                  break label29;
               default:
                  expr.getValue().acceptVisitor(this);
                  break label29;
            }
         case LONG:
            switch (expr.getTarget()) {
               case INT:
                  this.precedence = Precedence.MEMBER_ACCESS;
                  Expr longShifted = this.extractLongRightShiftedBy32(expr.getValue());
                  if (longShifted != null) {
                     this.writer.appendFunction("Long_hi").append("(");
                     longShifted.acceptVisitor(this);
                     this.writer.append(")");
                  } else {
                     this.writer.appendFunction("Long_lo").append("(");
                     expr.getValue().acceptVisitor(this);
                     this.writer.append(")");
                  }
                  break;
               case FLOAT:
               case DOUBLE:
                  this.writer.appendFunction("Long_toNumber").append("(");
                  this.precedence = Precedence.min();
                  expr.getValue().acceptVisitor(this);
                  this.writer.append(')');
                  break;
               default:
                  expr.getValue().acceptVisitor(this);
            }
      }

      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   private Expr extractLongRightShiftedBy32(Expr expr) {
      if (!(expr instanceof BinaryExpr binary)) {
         return null;
      } else {
         if (binary.getOperation() != BinaryOperation.RIGHT_SHIFT && binary.getOperation() != BinaryOperation.UNSIGNED_RIGHT_SHIFT) {
            return null;
         }

         if (binary.getType() != OperationType.LONG) {
            return null;
         }

         if (!(binary.getSecondOperand() instanceof ConstantExpr)) {
            return null;
         }

         Object rightConstant = ((ConstantExpr)binary.getSecondOperand()).getValue();
         return !rightConstant.equals(32) && !rightConstant.equals(32L) ? null : binary.getFirstOperand();
      }
   }

   @Override
   public void visit(ConditionalExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      Precedence outerPrecedence = this.precedence;
      if (outerPrecedence.ordinal() > Precedence.CONDITIONAL.ordinal()) {
         this.writer.append('(');
      }

      this.precedence = Precedence.CONDITIONAL.next();
      expr.getCondition().acceptVisitor(this);
      this.writer.ws().append("?").ws();
      this.precedence = Precedence.CONDITIONAL.next();
      expr.getConsequent().acceptVisitor(this);
      this.writer.ws().append(":").ws();
      this.precedence = Precedence.CONDITIONAL;
      expr.getAlternative().acceptVisitor(this);
      if (outerPrecedence.ordinal() > Precedence.CONDITIONAL.ordinal()) {
         this.writer.append(')');
      }

      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(ConstantExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      this.context.constantToString(this.writer, expr.getValue());
      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(VariableExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      this.writer.append(this.variableName(expr.getIndex()));
      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(SubscriptExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      this.precedence = Precedence.MEMBER_ACCESS;
      expr.getArray().acceptVisitor(this);
      this.writer.append('[');
      this.precedence = Precedence.min();
      expr.getIndex().acceptVisitor(this);
      this.writer.append(']');
      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(UnwrapArrayExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      this.precedence = Precedence.MEMBER_ACCESS;
      expr.getArray().acceptVisitor(this);
      this.writer.append(".data");
      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(InvocationExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      Injector injector = this.context.getInjector(expr.getMethod());
      if (injector != null) {
         injector.generate(new StatementRenderer.InjectorContextImpl(expr.getArguments()), expr.getMethod());
      } else {
         Precedence outerPrecedence = this.precedence;
         if (outerPrecedence.ordinal() > Precedence.FUNCTION_CALL.ordinal()) {
            this.writer.append('(');
         }

         if (expr.getType() == InvocationType.DYNAMIC) {
            this.precedence = Precedence.MEMBER_ACCESS;
            expr.getArguments().get(0).acceptVisitor(this);
         }

         MethodReference method = expr.getMethod();
         String name = this.naming.instanceMethodName(method.getDescriptor());
         switch (expr.getType()) {
            case STATIC:
               this.writer.appendMethod(method).append("(");

               for (int i = 0; i < expr.getArguments().size(); i++) {
                  if (i > 0) {
                     this.writer.append(",").ws();
                  }

                  this.precedence = Precedence.min();
                  expr.getArguments().get(i).acceptVisitor(this);
               }
               break;
            case SPECIAL:
               this.writer.appendMethod(method).append("(");
               this.precedence = Precedence.min();
               expr.getArguments().get(0).acceptVisitor(this);

               for (int i = 1; i < expr.getArguments().size(); i++) {
                  this.writer.append(",").ws();
                  this.precedence = Precedence.min();
                  expr.getArguments().get(i).acceptVisitor(this);
               }
               break;
            case DYNAMIC:
               this.writer.append(".").append(name).append("(");

               for (int i = 1; i < expr.getArguments().size(); i++) {
                  if (i > 1) {
                     this.writer.append(",").ws();
                  }

                  this.precedence = Precedence.min();
                  expr.getArguments().get(i).acceptVisitor(this);
               }
               break;
            case CONSTRUCTOR:
               this.writer.appendInit(expr.getMethod()).append("(");

               for (int i = 0; i < expr.getArguments().size(); i++) {
                  if (i > 0) {
                     this.writer.append(",").ws();
                  }

                  this.precedence = Precedence.min();
                  expr.getArguments().get(i).acceptVisitor(this);
               }
         }

         this.writer.append(')');
         if (outerPrecedence.ordinal() > Precedence.FUNCTION_CALL.ordinal()) {
            this.writer.append(')');
         }
      }

      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(QualificationExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      this.precedence = Precedence.MEMBER_ACCESS;
      if (expr.getQualified() != null) {
         expr.getQualified().acceptVisitor(this);
         this.writer.append('.').appendField(expr.getField());
      } else {
         this.writer.appendStaticField(expr.getField());
      }

      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(NewExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      Precedence outerPrecedence = this.precedence;
      if (outerPrecedence.ordinal() > Precedence.NEW.ordinal()) {
         this.writer.append('(');
      }

      this.precedence = Precedence.NEW;
      this.writer.append("new ").appendClass(expr.getConstructedClass());
      if (outerPrecedence.ordinal() > Precedence.NEW.ordinal()) {
         this.writer.append(')');
      }

      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(NewArrayExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      ValueType type = expr.getType();
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
               this.writer.appendFunction("$rt_createBooleanArray").append("(");
               this.precedence = Precedence.min();
               expr.getLength().acceptVisitor(this);
               this.writer.append(")");
               break;
            case BYTE:
               this.writer.appendFunction("$rt_createByteArray").append("(");
               this.precedence = Precedence.min();
               expr.getLength().acceptVisitor(this);
               this.writer.append(")");
               break;
            case SHORT:
               this.writer.appendFunction("$rt_createShortArray").append("(");
               this.precedence = Precedence.min();
               expr.getLength().acceptVisitor(this);
               this.writer.append(")");
               break;
            case INTEGER:
               this.writer.appendFunction("$rt_createIntArray").append("(");
               this.precedence = Precedence.min();
               expr.getLength().acceptVisitor(this);
               this.writer.append(")");
               break;
            case LONG:
               this.writer.appendFunction("$rt_createLongArray").append("(");
               this.precedence = Precedence.min();
               expr.getLength().acceptVisitor(this);
               this.writer.append(")");
               break;
            case FLOAT:
               this.writer.appendFunction("$rt_createFloatArray").append("(");
               this.precedence = Precedence.min();
               expr.getLength().acceptVisitor(this);
               this.writer.append(")");
               break;
            case DOUBLE:
               this.writer.appendFunction("$rt_createDoubleArray").append("(");
               this.precedence = Precedence.min();
               expr.getLength().acceptVisitor(this);
               this.writer.append(")");
               break;
            case CHARACTER:
               this.writer.appendFunction("$rt_createCharArray").append("(");
               this.precedence = Precedence.min();
               expr.getLength().acceptVisitor(this);
               this.writer.append(")");
         }
      } else {
         this.writer.appendFunction("$rt_createArray").append("(");
         RenderingUtil.typeToClsString(this.writer, expr.getType());
         this.writer.append(",").ws();
         this.precedence = Precedence.min();
         expr.getLength().acceptVisitor(this);
         this.writer.append(")");
      }

      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(ArrayFromDataExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      ValueType type = expr.getType();
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
               this.writer.appendFunction("$rt_createBooleanArrayFromData");
               break;
            case BYTE:
               this.writer.appendFunction("$rt_createByteArrayFromData");
               break;
            case SHORT:
               this.writer.appendFunction("$rt_createShortArrayFromData");
               break;
            case INTEGER:
               this.writer.appendFunction("$rt_createIntArrayFromData");
               break;
            case LONG:
               this.writer.appendFunction("$rt_createLongArrayFromData");
               break;
            case FLOAT:
               this.writer.appendFunction("$rt_createFloatArrayFromData");
               break;
            case DOUBLE:
               this.writer.appendFunction("$rt_createDoubleArrayFromData");
               break;
            case CHARACTER:
               this.writer.appendFunction("$rt_createCharArrayFromData");
         }

         this.writer.append("(");
      } else {
         this.writer.appendFunction("$rt_wrapArray").append("(");
         RenderingUtil.typeToClsString(this.writer, expr.getType());
         this.writer.append(",").ws();
      }

      this.writer.append("[");
      this.writeCommaSeparated(expr.getData());
      this.writer.append("])");
      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   private void writeCommaSeparated(List<Expr> expressions) {
      boolean first = true;

      for (Expr element : expressions) {
         if (!first) {
            this.writer.append(",").ws();
         }

         first = false;
         this.precedence = Precedence.min();
         element.acceptVisitor(this);
      }
   }

   @Override
   public void visit(NewMultiArrayExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      ValueType type = expr.getType();

      for (int i = 0; i < expr.getDimensions().size(); i++) {
         type = ((ValueType.Array)type).getItemType();
      }

      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
               this.writer.appendFunction("$rt_createBooleanMultiArray").append("(");
               break;
            case BYTE:
               this.writer.appendFunction("$rt_createByteMultiArray").append("(");
               break;
            case SHORT:
               this.writer.appendFunction("$rt_createShortMultiArray").append("(");
               break;
            case INTEGER:
               this.writer.appendFunction("$rt_createIntMultiArray").append("(");
               break;
            case LONG:
               this.writer.appendFunction("$rt_createLongMultiArray").append("(");
               break;
            case FLOAT:
               this.writer.appendFunction("$rt_createFloatMultiArray").append("(");
               break;
            case DOUBLE:
               this.writer.appendFunction("$rt_createDoubleMultiArray").append("(");
               break;
            case CHARACTER:
               this.writer.appendFunction("$rt_createCharMultiArray").append("(");
         }
      } else {
         this.writer.appendFunction("$rt_createMultiArray").append("(");
         RenderingUtil.typeToClsString(this.writer, type);
         this.writer.append(",").ws();
      }

      this.writer.append("[");
      boolean first = true;
      List<Expr> dimensions = new ArrayList<>(expr.getDimensions());
      Collections.reverse(dimensions);

      for (Expr dimension : dimensions) {
         if (!first) {
            this.writer.append(",").ws();
         }

         first = false;
         this.precedence = Precedence.min();
         dimension.acceptVisitor(this);
      }

      this.writer.append("])");
      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   @Override
   public void visit(InstanceOfExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      if (isClass(expr.getType(), this.context.getClassSource())) {
         boolean needsParentheses = Precedence.COMPARISON.ordinal() < this.precedence.ordinal();
         if (needsParentheses) {
            this.writer.append('(');
         }

         this.precedence = Precedence.CONDITIONAL.next();
         expr.getExpr().acceptVisitor(this);
         this.writer.append(" instanceof ");
         RenderingUtil.typeToClsString(this.writer, expr.getType());
         if (needsParentheses) {
            this.writer.append(')');
         }
      } else {
         this.writer.appendFunction("$rt_isInstance").append("(");
         this.precedence = Precedence.min();
         expr.getExpr().acceptVisitor(this);
         this.writer.append(",").ws();
         RenderingUtil.typeToClsString(this.writer, expr.getType());
         this.writer.append(")");
      }

      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   private void visitStatements(List<Statement> statements) {
      if (!statements.isEmpty()) {
         boolean oldEnd = this.end;

         for (int i = 0; i < statements.size() - 1; i++) {
            this.end = false;
            statements.get(i).acceptVisitor(this);
         }

         this.end = oldEnd;
         statements.get(statements.size() - 1).acceptVisitor(this);
         this.end = oldEnd;
      }
   }

   @Override
   public void visit(TryCatchStatement statement) {
      this.writer.append("try").ws().append("{").softNewLine().indent();
      List<TryCatchStatement> sequence = new ArrayList<>();
      sequence.add(statement);
      List<Statement> protectedBody = statement.getProtectedBody();

      while (protectedBody.size() == 1 && protectedBody.get(0) instanceof TryCatchStatement) {
         TryCatchStatement nextStatement = (TryCatchStatement)protectedBody.get(0);
         sequence.add(nextStatement);
         protectedBody = nextStatement.getProtectedBody();
      }

      this.visitStatements(protectedBody);
      this.writer.outdent().append("}").ws().append("catch").ws().append("($$e)").ws().append("{").indent().softNewLine();
      this.writer.append("$$je").ws().append("=").ws().appendFunction("$rt_wrapException").append("($$e);").softNewLine();
      boolean first = true;
      boolean defaultHandlerOccurred = false;

      for (int i = sequence.size() - 1; i >= 0; i--) {
         TryCatchStatement catchClause = sequence.get(i);
         if (!first) {
            this.writer.ws().append("else");
         }

         if (catchClause.getExceptionType() != null) {
            if (!first) {
               this.writer.append(" ");
            }

            this.writer.append("if").ws().append("($$je instanceof ").appendClass(catchClause.getExceptionType());
            this.writer.append(")").ws();
         } else {
            defaultHandlerOccurred = true;
         }

         if (catchClause.getExceptionType() != null || !first) {
            this.writer.append("{").indent().softNewLine();
         }

         if (catchClause.getExceptionVariable() != null) {
            this.writer.append(this.variableName(catchClause.getExceptionVariable())).ws().append("=").ws().append("$$je;").softNewLine();
         }

         this.visitStatements(catchClause.getHandler());
         if (catchClause.getExceptionType() != null || !first) {
            this.writer.outdent().append("}");
         }

         first = false;
         if (defaultHandlerOccurred) {
            break;
         }
      }

      if (!defaultHandlerOccurred) {
         this.writer.ws().append("else").ws().append("{").indent().softNewLine();
         this.writer.append("throw $$e;").softNewLine();
         this.writer.outdent().append("}").softNewLine();
      } else {
         this.writer.softNewLine();
      }

      this.writer.outdent().append("}").softNewLine();
   }

   @Override
   public void visit(GotoPartStatement statement) {
      if (statement.getPart() != this.currentPart) {
         this.writer.append(this.context.pointerName()).ws().append("=").ws().append(statement.getPart()).append(";").softNewLine();
      }

      if (!this.end || statement.getPart() != this.currentPart + 1) {
         this.writer.append("continue ").append(this.context.mainLoopName()).append(";").softNewLine();
      }
   }

   @Override
   public void visit(MonitorEnterStatement statement) {
      if (this.async) {
         this.writer.appendMethod(NameFrequencyEstimator.MONITOR_ENTER_METHOD).append("(");
         this.precedence = Precedence.min();
         statement.getObjectRef().acceptVisitor(this);
         this.writer.append(");").softNewLine();
         this.emitSuspendChecker();
      } else {
         this.writer.appendMethod(NameFrequencyEstimator.MONITOR_ENTER_SYNC_METHOD).append('(');
         this.precedence = Precedence.min();
         statement.getObjectRef().acceptVisitor(this);
         this.writer.append(");").softNewLine();
      }
   }

   public void emitSuspendChecker() {
      this.writer.append("if").ws().append("(").appendFunction("$rt_suspending").append("())").ws().append("{").indent().softNewLine();
      this.writer.append("break ").append(this.context.mainLoopName()).append(";").softNewLine();
      this.writer.outdent().append("}").softNewLine();
   }

   @Override
   public void visit(MonitorExitStatement statement) {
      if (this.async) {
         this.writer.appendMethod(NameFrequencyEstimator.MONITOR_EXIT_METHOD).append("(");
         this.precedence = Precedence.min();
         statement.getObjectRef().acceptVisitor(this);
         this.writer.append(");").softNewLine();
      } else {
         this.writer.appendMethod(NameFrequencyEstimator.MONITOR_EXIT_SYNC_METHOD).append('(');
         this.precedence = Precedence.min();
         statement.getObjectRef().acceptVisitor(this);
         this.writer.append(");").softNewLine();
      }
   }

   @Override
   public void visit(BoundCheckExpr expr) {
      if (expr.getLocation() != null) {
         this.pushLocation(expr.getLocation());
      }

      if (expr.getArray() != null && expr.isLower()) {
         this.writer.appendFunction("$rt_checkBounds").append("(");
      } else if (expr.getArray() != null) {
         this.writer.appendFunction("$rt_checkUpperBound").append("(");
      } else if (expr.isLower()) {
         this.writer.appendFunction("$rt_checkLowerBound").append("(");
      }

      expr.getIndex().acceptVisitor(this);
      if (expr.getArray() != null) {
         this.writer.append(",").ws();
         expr.getArray().acceptVisitor(this);
      }

      if (expr.getArray() != null || expr.isLower()) {
         this.writer.append(")");
      }

      if (expr.getLocation() != null) {
         this.popLocation();
      }
   }

   private class InjectorContextImpl implements InjectorContext {
      private final List<Expr> arguments;
      private final Precedence precedence;

      InjectorContextImpl(List<Expr> arguments) {
         this.precedence = StatementRenderer.this.precedence;
         this.arguments = arguments;
      }

      @Override
      public Expr getArgument(int index) {
         return this.arguments.get(index);
      }

      @Override
      public boolean isMinifying() {
         return StatementRenderer.this.minifying;
      }

      @Override
      public SourceWriter getWriter() {
         return StatementRenderer.this.writer;
      }

      @Override
      public SourceWriter getMetadataWriter() {
         return StatementRenderer.this.metadataWriter;
      }

      @Override
      public void writeEscaped(String str) {
         StatementRenderer.this.writer.append(RenderingUtil.escapeString(str));
      }

      @Override
      public void writeType(ValueType type) {
         RenderingUtil.typeToClsString(StatementRenderer.this.writer, type);
      }

      @Override
      public void writeExpr(Expr expr) {
         this.writeExpr(expr, Precedence.GROUPING);
      }

      @Override
      public void writeExpr(Expr expr, Precedence precedence) {
         StatementRenderer.this.precedence = precedence;
         expr.acceptVisitor(StatementRenderer.this);
      }

      @Override
      public int argumentCount() {
         return this.arguments.size();
      }

      @Override
      public <T> T getService(Class<T> type) {
         return StatementRenderer.this.context.getServices().getService(type);
      }

      @Override
      public Properties getProperties() {
         return new Properties(StatementRenderer.this.context.getProperties());
      }

      @Override
      public Precedence getPrecedence() {
         return this.precedence;
      }

      @Override
      public ClassLoader getClassLoader() {
         return StatementRenderer.this.context.getClassLoader();
      }

      @Override
      public ListableClassReaderSource getClassSource() {
         return StatementRenderer.this.context.getClassSource();
      }

      @Override
      public DependencyInfo getDependencies() {
         return StatementRenderer.this.context.getDependencyInfo();
      }

      @Override
      public ResourceProvider getResources() {
         return StatementRenderer.this.context.getResourceProvider();
      }

      @Override
      public String importModule(String name) {
         return StatementRenderer.this.context.importModule(name);
      }
   }

   private static class LocationStackEntry {
      final TextLocation location;

      LocationStackEntry(TextLocation location) {
         this.location = location;
      }
   }
}
