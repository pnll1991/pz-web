package org.teavm.ast.optimization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.teavm.ast.ArrayFromDataExpr;
import org.teavm.ast.ArrayType;
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
import org.teavm.ast.IdentifiedStatement;
import org.teavm.ast.InitClassStatement;
import org.teavm.ast.InstanceOfExpr;
import org.teavm.ast.InvocationExpr;
import org.teavm.ast.InvocationType;
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
import org.teavm.ast.UnaryOperation;
import org.teavm.ast.UnwrapArrayExpr;
import org.teavm.ast.VariableExpr;
import org.teavm.ast.WhileStatement;
import org.teavm.interop.NoSideEffects;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;

class OptimizingVisitor implements StatementVisitor, ExprVisitor {
   private static final int MAX_DEPTH = 20;
   private Expr resultExpr;
   Statement resultStmt;
   private final boolean[] preservedVars;
   private final int[] writeFrequencies;
   private final int[] initialWriteFrequencies;
   private final int[] readFrequencies;
   private final Object[] constants;
   private List<Statement> resultSequence;
   private boolean friendlyToDebugger;
   private TextLocation currentLocation;
   private Deque<TextLocation> locationStack = new LinkedList<>();
   private Deque<TextLocation> notNullLocationStack = new ArrayDeque<>();
   private List<OptimizingVisitor.ArrayOptimization> pendingArrayOptimizations;
   private ClassReaderSource classes;

   OptimizingVisitor(
      boolean[] preservedVars, int[] writeFrequencies, int[] readFrequencies, Object[] constants, boolean friendlyToDebugger, ClassReaderSource classes
   ) {
      this.preservedVars = preservedVars;
      this.writeFrequencies = writeFrequencies;
      this.initialWriteFrequencies = (int[])writeFrequencies.clone();
      this.readFrequencies = readFrequencies;
      this.constants = constants;
      this.friendlyToDebugger = friendlyToDebugger;
      this.classes = classes;
   }

   private static boolean isZero(Expr expr) {
      return expr instanceof ConstantExpr && Integer.valueOf(0).equals(((ConstantExpr)expr).getValue());
   }

   private static boolean isComparison(Expr expr) {
      if (!(expr instanceof BinaryExpr binary)) {
         return false;
      } else {
         return binary.getType() != OperationType.INT && binary.getType() != OperationType.LONG
            ? false
            : binary.getOperation() == BinaryOperation.COMPARE_LESS || binary.getOperation() == BinaryOperation.COMPARE_GREATER;
      }
   }

   private static boolean isComparisonLess(Expr expr) {
      if (!(expr instanceof BinaryExpr binary)) {
         return false;
      } else {
         return binary.getType() != OperationType.FLOAT && binary.getType() != OperationType.DOUBLE
            ? false
            : binary.getOperation() == BinaryOperation.COMPARE_LESS;
      }
   }

   private static boolean isComparisonGreater(Expr expr) {
      if (!(expr instanceof BinaryExpr binary)) {
         return false;
      } else {
         return binary.getType() != OperationType.FLOAT && binary.getType() != OperationType.DOUBLE
            ? false
            : binary.getOperation() == BinaryOperation.COMPARE_GREATER;
      }
   }

   private void pushLocation(TextLocation location) {
      this.locationStack.push(location);
      if (location != null) {
         if (this.currentLocation != null) {
            this.notNullLocationStack.push(this.currentLocation);
         }

         this.currentLocation = location;
      }
   }

   private void popLocation() {
      if (this.locationStack.pop() != null) {
         this.currentLocation = this.notNullLocationStack.pollFirst();
      }
   }

   @Override
   public void visit(BinaryExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         Expr p;
         Expr q;
         boolean invert;
         Expr b;
         Expr a;
         switch (expr.getOperation()) {
            case AND:
            case OR:
               this.resultExpr = expr;
               return;
            default:
               b = expr.getSecondOperand();
               a = expr.getFirstOperand();
               int barrierPosition = 0;
               if (this.isSideEffectFree(a)) {
                  barrierPosition++;
                  if (this.isSideEffectFree(b)) {
                     barrierPosition++;
                  }
               }

               Statement barrier = this.addBarrier();
               if (barrierPosition == 2) {
                  this.removeBarrier(barrier);
               }

               b.acceptVisitor(this);
               b = this.resultExpr;
               if (b instanceof ConstantExpr && expr.getOperation() == BinaryOperation.SUBTRACT && this.tryMakePositive((ConstantExpr)b)) {
                  expr.setOperation(BinaryOperation.ADD);
               }

               if (barrierPosition == 1) {
                  this.removeBarrier(barrier);
               }

               a.acceptVisitor(this);
               a = this.resultExpr;
               if (barrierPosition == 0) {
                  this.removeBarrier(barrier);
               }

               p = a;
               q = b;
               invert = false;
               if (isZero(p)) {
                  Expr tmp = p;
                  p = q;
                  q = tmp;
                  invert = true;
               }
         }

         label234:
         if (!isZero(q)) {
            expr.setFirstOperand(a);
            expr.setSecondOperand(b);
            this.resultExpr = expr;
         } else {
            if (isComparison(p)) {
               switch (expr.getOperation()) {
                  case EQUALS:
                  case NOT_EQUALS:
                  case LESS:
                  case LESS_OR_EQUALS:
                  case GREATER:
                  case GREATER_OR_EQUALS:
                     BinaryExpr comparison = (BinaryExpr)p;
                     Expr result = BinaryExpr.binary(expr.getOperation(), comparison.getType(), comparison.getFirstOperand(), comparison.getSecondOperand());
                     result.setLocation(comparison.getLocation());
                     result.setVariableIndex(comparison.getVariableIndex());
                     if (invert) {
                        result = ExprOptimizer.invert(result);
                     }

                     this.resultExpr = result;
                     return;
               }
            } else if (isComparisonLess(p)) {
               switch (expr.getOperation()) {
                  case EQUALS:
                  case NOT_EQUALS:
                  case GREATER:
                  case GREATER_OR_EQUALS:
                     BinaryExpr comparison = (BinaryExpr)p;
                     Expr result = BinaryExpr.binary(expr.getOperation(), comparison.getType(), comparison.getFirstOperand(), comparison.getSecondOperand());
                     result.setLocation(comparison.getLocation());
                     result.setVariableIndex(comparison.getVariableIndex());
                     if (invert) {
                        result = ExprOptimizer.invert(result);
                     }

                     this.resultExpr = result;
                     return;
                  case LESS:
                     BinaryExpr comparison = (BinaryExpr)p;
                     Expr result = BinaryExpr.invert(
                        BinaryExpr.binary(BinaryOperation.GREATER_OR_EQUALS, comparison.getType(), comparison.getFirstOperand(), comparison.getSecondOperand())
                     );
                     result.setLocation(comparison.getLocation());
                     result.setVariableIndex(comparison.getVariableIndex());
                     if (invert) {
                        result = ExprOptimizer.invert(result);
                     }

                     this.resultExpr = result;
                     return;
                  case LESS_OR_EQUALS:
                     BinaryExpr comparison = (BinaryExpr)p;
                     Expr result = BinaryExpr.invert(
                        BinaryExpr.binary(BinaryOperation.GREATER, comparison.getType(), comparison.getFirstOperand(), comparison.getSecondOperand())
                     );
                     result.setLocation(comparison.getLocation());
                     result.setVariableIndex(comparison.getVariableIndex());
                     if (invert) {
                        result = ExprOptimizer.invert(result);
                     }

                     this.resultExpr = result;
                     return;
               }
            } else if (isComparisonGreater(p)) {
               switch (expr.getOperation()) {
                  case EQUALS:
                  case NOT_EQUALS:
                  case LESS:
                  case LESS_OR_EQUALS:
                     BinaryExpr comparison = (BinaryExpr)p;
                     Expr result = BinaryExpr.binary(expr.getOperation(), comparison.getType(), comparison.getFirstOperand(), comparison.getSecondOperand());
                     result.setLocation(comparison.getLocation());
                     result.setVariableIndex(comparison.getVariableIndex());
                     if (invert) {
                        result = ExprOptimizer.invert(result);
                     }

                     this.resultExpr = result;
                     return;
                  case GREATER:
                     BinaryExpr comparison = (BinaryExpr)p;
                     Expr result = BinaryExpr.invert(
                        BinaryExpr.binary(BinaryOperation.LESS_OR_EQUALS, comparison.getType(), comparison.getFirstOperand(), comparison.getSecondOperand())
                     );
                     result.setLocation(comparison.getLocation());
                     result.setVariableIndex(comparison.getVariableIndex());
                     if (invert) {
                        result = ExprOptimizer.invert(result);
                     }

                     this.resultExpr = result;
                     return;
                  case GREATER_OR_EQUALS:
                     BinaryExpr comparison = (BinaryExpr)p;
                     Expr result = BinaryExpr.invert(
                        BinaryExpr.binary(BinaryOperation.LESS, comparison.getType(), comparison.getFirstOperand(), comparison.getSecondOperand())
                     );
                     result.setLocation(comparison.getLocation());
                     result.setVariableIndex(comparison.getVariableIndex());
                     if (invert) {
                        result = ExprOptimizer.invert(result);
                     }

                     this.resultExpr = result;
                     return;
               }
            }
            break label234;
         }
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(UnaryExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         expr.getOperand().acceptVisitor(this);
         Expr operand = this.resultExpr;
         if (!(expr.getOperation() == UnaryOperation.NEGATE && operand instanceof ConstantExpr constantExpr) || !this.tryMakePositive(constantExpr)) {
            expr.setOperand(operand);
            this.resultExpr = expr;
            return;
         }

         this.resultExpr = expr;
      } finally {
         this.popLocation();
      }
   }

   private boolean tryMakePositive(ConstantExpr constantExpr) {
      Object value = constantExpr.getValue();
      if (value instanceof Integer && (Integer)value < 0) {
         constantExpr.setValue(-(Integer)value);
         return true;
      } else if (value instanceof Float && (Float)value < 0.0F) {
         constantExpr.setValue(-(Float)value);
         return true;
      } else if (value instanceof Byte && (Byte)value < 0) {
         constantExpr.setValue(-(Byte)value);
         return true;
      } else if (value instanceof Short && (Short)value < 0) {
         constantExpr.setValue(-(Short)value);
         return true;
      } else if (value instanceof Long && (Long)value < 0L) {
         constantExpr.setValue(-(Long)value);
         return true;
      } else if (value instanceof Double && (Double)value < 0.0) {
         constantExpr.setValue(-(Double)value);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void visit(ConditionalExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         expr.getCondition().acceptVisitor(this);
         Expr cond = this.optimizeCondition(this.resultExpr);
         Statement barrier = this.addBarrier();
         expr.getConsequent().acceptVisitor(this);
         Expr consequent = this.resultExpr;
         expr.getAlternative().acceptVisitor(this);
         Expr alternative = this.resultExpr;
         this.removeBarrier(barrier);
         expr.setCondition(cond);
         expr.setConsequent(consequent);
         expr.setAlternative(alternative);
         this.resultExpr = expr;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(ConstantExpr expr) {
      this.resultExpr = expr;
   }

   @Override
   public void visit(VariableExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         int index = expr.getIndex();
         this.resultExpr = expr;
         if (this.writeFrequencies[index] != 1) {
            return;
         }

         if (!this.preservedVars[index] && this.initialWriteFrequencies[index] == 1 && this.constants[index] != null) {
            ConstantExpr constantExpr = new ConstantExpr();
            constantExpr.setValue(this.constants[index]);
            constantExpr.setLocation(expr.getLocation());
            constantExpr.setVariableIndex(expr.getVariableIndex());
            this.resultExpr = constantExpr;
            return;
         }

         if (this.locationStack.size() > 20) {
            return;
         }

         if (this.readFrequencies[index] != 1 || this.preservedVars[index]) {
            return;
         }

         if (this.resultSequence.isEmpty()) {
            return;
         }

         Statement last = this.resultSequence.get(this.resultSequence.size() - 1);
         if (!(last instanceof AssignmentStatement assignment)) {
            return;
         }

         if (assignment.isAsync()) {
            return;
         }

         if (!(assignment.getLeftValue() instanceof VariableExpr)) {
            return;
         }

         VariableExpr var = (VariableExpr)assignment.getLeftValue();
         if (!this.friendlyToDebugger
            || this.currentLocation == null
            || assignment.getLocation() == null
            || assignment.getLocation().equals(this.currentLocation)) {
            if (var.getIndex() == index) {
               this.resultSequence.remove(this.resultSequence.size() - 1);
               assignment.getRightValue().setLocation(assignment.getLocation());
               assignment.getRightValue().acceptVisitor(this);
            }

            return;
         }
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(SubscriptExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         Expr index = expr.getIndex();
         Expr array = expr.getArray();
         int barrierPosition = 0;
         if (this.isSideEffectFree(array)) {
            barrierPosition++;
            if (this.isSideEffectFree(index)) {
               barrierPosition++;
            }
         }

         Statement barrier = this.addBarrier();
         if (barrierPosition == 2) {
            this.removeBarrier(barrier);
         }

         expr.getIndex().acceptVisitor(this);
         index = this.resultExpr;
         if (barrierPosition == 1) {
            this.removeBarrier(barrier);
         }

         expr.getArray().acceptVisitor(this);
         array = this.resultExpr;
         if (barrierPosition == 0) {
            this.removeBarrier(barrier);
         }

         expr.setArray(array);
         expr.setIndex(index);
         this.resultExpr = expr;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(UnwrapArrayExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         expr.getArray().acceptVisitor(this);
         Expr arrayExpr = this.resultExpr;
         expr.setArray(arrayExpr);
         this.resultExpr = expr;
      } finally {
         this.popLocation();
      }
   }

   private boolean isSideEffectFree(MethodReference method) {
      ClassReader cls = this.classes.get(method.getClassName());
      if (cls == null) {
         return false;
      }

      MethodReader methodReader = cls.getMethod(method.getDescriptor());
      return methodReader == null ? false : methodReader.getAnnotations().get(NoSideEffects.class.getName()) != null;
   }

   private boolean isSideEffectFreeCall(InvocationExpr expr) {
      return (expr.getType() == InvocationType.SPECIAL || expr.getType() == InvocationType.STATIC) && this.isSideEffectFree(expr.getMethod());
   }

   @Override
   public void visit(InvocationExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         Expr[] args = expr.getArguments().toArray(new Expr[0]);
         int barrierPos;
         if (this.isSideEffectFreeCall(expr)) {
            barrierPos = args.length;
         } else {
            barrierPos = 0;

            while (barrierPos < args.length && this.isSideEffectFree(args[barrierPos])) {
               barrierPos++;
            }
         }

         Statement barrier = this.addBarrier();
         if (barrierPos == args.length) {
            this.removeBarrier(barrier);
         }

         for (int i = args.length - 1; i >= 0; i--) {
            args[i].acceptVisitor(this);
            args[i] = this.resultExpr;
            if (i == barrierPos) {
               this.removeBarrier(barrier);
            }
         }

         for (int i = 0; i < args.length; i++) {
            expr.getArguments().set(i, args[i]);
         }

         this.resultExpr = expr;
      } finally {
         this.popLocation();
      }
   }

   private boolean tryApplyConstructor(InvocationExpr expr) {
      if (!expr.getMethod().getName().equals("<init>")) {
         return false;
      }

      if (this.resultSequence != null && !this.resultSequence.isEmpty()) {
         Statement last = this.resultSequence.get(this.resultSequence.size() - 1);
         if (!(last instanceof AssignmentStatement assignment)) {
            return false;
         } else {
            if (!(assignment.getLeftValue() instanceof VariableExpr)) {
               return false;
            }

            VariableExpr var = (VariableExpr)assignment.getLeftValue();
            if (!(expr.getArguments().get(0) instanceof VariableExpr)) {
               return false;
            }

            VariableExpr target = (VariableExpr)expr.getArguments().get(0);
            if (target.getIndex() != var.getIndex()) {
               return false;
            }

            if (!(assignment.getRightValue() instanceof NewExpr)) {
               return false;
            }

            NewExpr constructed = (NewExpr)assignment.getRightValue();
            if (!constructed.getConstructedClass().equals(expr.getMethod().getClassName())) {
               return false;
            }

            Expr[] args = expr.getArguments().toArray(new Expr[0]);
            args = Arrays.copyOfRange(args, 1, args.length);
            InvocationExpr constructrExpr = Expr.constructObject(expr.getMethod(), args);
            constructrExpr.setLocation(expr.getLocation());
            constructrExpr.setVariableIndex(constructed.getVariableIndex());
            assignment.setRightValue(constructrExpr);
            this.readFrequencies[var.getIndex()]--;
            return true;
         }
      } else {
         return false;
      }
   }

   @Override
   public void visit(QualificationExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         if (expr.getQualified() != null) {
            expr.getQualified().acceptVisitor(this);
            Expr qualified = this.resultExpr;
            expr.setQualified(qualified);
         }

         this.resultExpr = expr;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(NewExpr expr) {
      this.resultExpr = expr;
   }

   @Override
   public void visit(NewArrayExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         expr.getLength().acceptVisitor(this);
         Expr length = this.resultExpr;
         expr.setLength(length);
         this.resultExpr = expr;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(NewMultiArrayExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         for (int i = 0; i < expr.getDimensions().size(); i++) {
            Expr dimension = expr.getDimensions().get(i);
            dimension.acceptVisitor(this);
            expr.getDimensions().set(i, this.resultExpr);
         }

         this.resultExpr = expr;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(ArrayFromDataExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         for (int i = 0; i < expr.getData().size(); i++) {
            Expr element = expr.getData().get(i);
            element.acceptVisitor(this);
            expr.getData().set(i, this.resultExpr);
         }

         this.resultExpr = expr;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(InstanceOfExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         expr.getExpr().acceptVisitor(this);
         expr.setExpr(this.resultExpr);
         this.resultExpr = expr;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(CastExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         expr.getValue().acceptVisitor(this);
         expr.setValue(this.resultExpr);
         this.resultExpr = expr;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(PrimitiveCastExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         expr.getValue().acceptVisitor(this);
         expr.setValue(this.resultExpr);
         this.resultExpr = expr;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(AssignmentStatement statement) {
      this.pushLocation(statement.getLocation());

      try {
         if (statement.getLeftValue() == null) {
            statement.getRightValue().acceptVisitor(this);
            if (this.resultExpr instanceof InvocationExpr && this.tryApplyConstructor((InvocationExpr)this.resultExpr)) {
               this.resultStmt = new SequentialStatement();
            } else {
               statement.setRightValue(this.resultExpr);
               this.resultStmt = statement;
            }
         } else {
            statement.getRightValue().acceptVisitor(this);
            Expr right = this.resultExpr;
            Expr left = statement.getLeftValue();
            if (!(statement.getLeftValue() instanceof VariableExpr)) {
               statement.getLeftValue().acceptVisitor(this);
               left = this.resultExpr;
            } else {
               int varIndex = ((VariableExpr)statement.getLeftValue()).getIndex();
               if (!this.preservedVars[varIndex] && this.initialWriteFrequencies[varIndex] == 1 && this.constants[varIndex] != null) {
                  this.resultStmt = new SequentialStatement();
                  return;
               }
            }

            statement.setLeftValue(left);
            statement.setRightValue(right);
            this.resultStmt = statement;
         }
      } finally {
         this.popLocation();
      }
   }

   private List<Statement> processSequence(List<Statement> statements) {
      List<Statement> backup = this.resultSequence;
      this.resultSequence = new ArrayList<>();
      List<OptimizingVisitor.ArrayOptimization> pendingArrayOptimizationsBackup = this.pendingArrayOptimizations;
      this.pendingArrayOptimizations = new ArrayList<>();
      this.processSequenceImpl(statements);
      this.wieldTryCatch(this.resultSequence);
      List<Statement> result = this.resultSequence.stream().filter(part -> part != null).collect(Collectors.toList());
      this.resultSequence = backup;
      this.pendingArrayOptimizations = pendingArrayOptimizationsBackup;
      return result;
   }

   private boolean processSequenceImpl(List<Statement> statements) {
      for (Statement part : statements) {
         if (part instanceof SequentialStatement) {
            if (!this.processSequenceImpl(((SequentialStatement)part).getSequence())) {
               return false;
            }
         } else {
            part.acceptVisitor(this);
            part = this.resultStmt;
            if (part instanceof SequentialStatement) {
               if (!this.processSequenceImpl(((SequentialStatement)part).getSequence())) {
                  return false;
               }
            } else {
               this.resultSequence.add(part);
               this.tryArrayOptimization();
               if (part instanceof BreakStatement) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private void tryArrayOptimization() {
      while (!this.pendingArrayOptimizations.isEmpty()) {
         Statement statement = this.resultSequence.get(this.resultSequence.size() - 1);
         int i = this.pendingArrayOptimizations.size() - 1;
         OptimizingVisitor.ArrayOptimization opt = this.pendingArrayOptimizations.get(i);
         boolean result = opt.isSet ? this.tryArraySet(opt, statement) : this.tryArrayUnwrap(opt, statement);
         if (!result) {
            this.pendingArrayOptimizations.remove(i);
            continue;
         }
         break;
      }

      Statement statement = this.resultSequence.get(this.resultSequence.size() - 1);
      this.tryArrayConstruction(statement);
   }

   private void tryArrayConstruction(Statement statement) {
      if (statement instanceof AssignmentStatement assign) {
         if (assign.getLeftValue() instanceof VariableExpr) {
            int constructedArrayVariable = ((VariableExpr)assign.getLeftValue()).getIndex();
            if (assign.getRightValue() instanceof NewArrayExpr) {
               NewArrayExpr constructedArray = (NewArrayExpr)assign.getRightValue();
               if (constructedArray.getLength() instanceof ConstantExpr) {
                  Object sizeConst = ((ConstantExpr)constructedArray.getLength()).getValue();
                  if (sizeConst instanceof Integer) {
                     int constructedArraySize = (Integer)sizeConst;
                     OptimizingVisitor.ArrayOptimization optimization = new OptimizingVisitor.ArrayOptimization();
                     optimization.index = this.resultSequence.size() - 1;
                     optimization.arrayVariable = constructedArrayVariable;
                     optimization.arraySize = constructedArraySize;
                     optimization.array = constructedArray;
                     this.pendingArrayOptimizations.add(optimization);
                  }
               }
            }
         }
      }
   }

   private boolean tryArrayUnwrap(OptimizingVisitor.ArrayOptimization optimization, Statement statement) {
      if (optimization.unwrappedArray != null) {
         return true;
      } else if (!(statement instanceof AssignmentStatement assign)) {
         return false;
      } else {
         if (!(assign.getLeftValue() instanceof VariableExpr)) {
            return false;
         }

         optimization.unwrappedArrayVariable = ((VariableExpr)assign.getLeftValue()).getIndex();
         if (this.writeFrequencies[optimization.unwrappedArrayVariable] != 1
            || this.preservedVars[optimization.unwrappedArrayVariable]
            || this.readFrequencies[optimization.unwrappedArrayVariable] != optimization.arraySize) {
            return false;
         }

         if (!(assign.getRightValue() instanceof UnwrapArrayExpr)) {
            return false;
         }

         optimization.unwrappedArray = (UnwrapArrayExpr)assign.getRightValue();
         if (!(optimization.unwrappedArray.getArray() instanceof VariableExpr)) {
            return false;
         }

         VariableExpr arrayVar = (VariableExpr)optimization.unwrappedArray.getArray();
         if (arrayVar.getIndex() != optimization.arrayVariable) {
            return false;
         }

         if (!matchArrayType(optimization.array.getType(), optimization.unwrappedArray.getElementType())) {
            return false;
         }

         optimization.arrayElementIndex = 0;
         optimization.remainingUseCount = this.readFrequencies[optimization.unwrappedArrayVariable];
         optimization.isSet = true;
         return true;
      }
   }

   private boolean tryArraySet(OptimizingVisitor.ArrayOptimization optimization, Statement statement) {
      int expectedIndex = optimization.index + 2 + optimization.arrayElementIndex;
      if (this.resultSequence.size() - 1 != expectedIndex) {
         return false;
      } else if (!(statement instanceof AssignmentStatement assign)) {
         return false;
      } else {
         if (!(assign.getLeftValue() instanceof SubscriptExpr)) {
            return false;
         }

         SubscriptExpr subscript = (SubscriptExpr)assign.getLeftValue();
         if (subscript.getType() != optimization.unwrappedArray.getElementType()) {
            return false;
         }

         if (!(subscript.getArray() instanceof VariableExpr)) {
            return false;
         }

         if (((VariableExpr)subscript.getArray()).getIndex() != optimization.unwrappedArrayVariable) {
            return false;
         }

         Expr index = subscript.getIndex();
         if (index instanceof BoundCheckExpr boundCheck) {
            if (!(boundCheck.getArray() instanceof VariableExpr)) {
               return false;
            }

            if (((VariableExpr)boundCheck.getArray()).getIndex() != optimization.unwrappedArrayVariable) {
               return false;
            }

            index = boundCheck.getIndex();
            optimization.remainingUseCount--;
         }

         if (!(index instanceof ConstantExpr)) {
            return false;
         }

         Object constantValue = ((ConstantExpr)index).getValue();
         if (!Integer.valueOf(optimization.arrayElementIndex).equals(constantValue)) {
            return false;
         }

         VariableAccessFinder isVariableAccessed = new VariableAccessFinder(v -> v == optimization.arrayVariable || v == optimization.unwrappedArrayVariable);
         assign.getRightValue().acceptVisitor(isVariableAccessed);
         if (isVariableAccessed.isFound()) {
            return false;
         }

         optimization.elements.add(assign.getRightValue());
         optimization.remainingUseCount--;
         if (++optimization.arrayElementIndex == optimization.arraySize) {
            if (optimization.remainingUseCount == 0) {
               this.applyArrayOptimization(optimization);
            }

            this.pendingArrayOptimizations.remove(this.pendingArrayOptimizations.size() - 1);
         }

         return true;
      }
   }

   private void applyArrayOptimization(OptimizingVisitor.ArrayOptimization optimization) {
      AssignmentStatement assign = (AssignmentStatement)this.resultSequence.get(optimization.index);
      ArrayFromDataExpr arrayFromData = new ArrayFromDataExpr();
      arrayFromData.setVariableIndex(optimization.array.getVariableIndex());
      arrayFromData.setLocation(optimization.array.getLocation());
      arrayFromData.setType(optimization.array.getType());
      arrayFromData.getData().addAll(optimization.elements);
      assign.setRightValue(arrayFromData);
      this.readFrequencies[optimization.arrayVariable]--;
      this.resultSequence.subList(optimization.index + 1, this.resultSequence.size()).clear();
   }

   private void wieldTryCatch(List<Statement> statements) {
      for (int i = 0; i < statements.size() - 1; i++) {
         if (statements.get(i) instanceof TryCatchStatement && statements.get(i + 1) instanceof TryCatchStatement) {
            TryCatchStatement first = (TryCatchStatement)statements.get(i);
            TryCatchStatement second = (TryCatchStatement)statements.get(i + 1);
            if (Objects.equals(first.getExceptionType(), second.getExceptionType())
               && Objects.equals(first.getExceptionVariable(), second.getExceptionVariable())
               && this.briefStatementComparison(first.getHandler(), second.getHandler())) {
               first.getProtectedBody().addAll(second.getProtectedBody());
               statements.remove(i + 1);
               this.wieldTryCatch(first.getProtectedBody());
               i--;
            }
         }
      }
   }

   private boolean briefStatementComparison(List<Statement> firstSeq, List<Statement> secondSeq) {
      if (!firstSeq.isEmpty() && !secondSeq.isEmpty()) {
         if (firstSeq.size() == 1 && secondSeq.size() == 1) {
            Statement first = firstSeq.get(0);
            Statement second = secondSeq.get(0);
            if (first instanceof BreakStatement && second instanceof BreakStatement) {
               BreakStatement firstBreak = (BreakStatement)first;
               BreakStatement secondBreak = (BreakStatement)second;
               return firstBreak.getTarget() == secondBreak.getTarget();
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean matchArrayType(ValueType type, ArrayType arrayType) {
      switch (arrayType) {
         case BYTE:
            return type == ValueType.BYTE || type == ValueType.BOOLEAN;
         case SHORT:
            return type == ValueType.SHORT;
         case CHAR:
            return type == ValueType.CHARACTER;
         case INT:
            return type == ValueType.INTEGER;
         case LONG:
            return type == ValueType.LONG;
         case FLOAT:
            return type == ValueType.FLOAT;
         case DOUBLE:
            return type == ValueType.DOUBLE;
         case OBJECT:
            return type instanceof ValueType.Object || type instanceof ValueType.Array;
         default:
            return false;
      }
   }

   private void eliminateRedundantBreaks(List<Statement> statements, IdentifiedStatement exit) {
      if (!statements.isEmpty()) {
         Statement last = statements.get(statements.size() - 1);
         if (last instanceof BreakStatement && exit != null) {
            IdentifiedStatement target = ((BreakStatement)last).getTarget();
            if (exit == target) {
               statements.remove(statements.size() - 1);
            }
         }

         if (!statements.isEmpty()) {
            boolean shouldOptimizeBreaks = !this.hitsRedundantBreakThreshold(statements, exit);

            for (int i = 0; i < statements.size(); i++) {
               Statement stmt = statements.get(i);
               if (stmt instanceof ConditionalStatement cond) {
                  if (shouldOptimizeBreaks) {
                     if ((cond.getConsequent().isEmpty() ? null : cond.getConsequent().get(cond.getConsequent().size() - 1)) instanceof BreakStatement breakStmt
                        && exit != null
                        && exit == breakStmt.getTarget()) {
                        cond.getConsequent().remove(cond.getConsequent().size() - 1);
                        List<Statement> remaining = statements.subList(i + 1, statements.size());
                        cond.getAlternative().addAll(remaining);
                        remaining.clear();
                     } else if ((cond.getAlternative().isEmpty() ? null : cond.getAlternative().get(cond.getAlternative().size() - 1)) instanceof BreakStatement breakStmt
                        && exit != null
                        && exit == breakStmt.getTarget()) {
                        cond.getAlternative().remove(cond.getAlternative().size() - 1);
                        List<Statement> remaining = statements.subList(i + 1, statements.size());
                        cond.getConsequent().addAll(remaining);
                        remaining.clear();
                     }
                  }

                  if (i == statements.size() - 1) {
                     this.eliminateRedundantBreaks(cond.getConsequent(), exit);
                     this.eliminateRedundantBreaks(cond.getAlternative(), exit);
                  }

                  this.normalizeConditional(cond);
                  if (cond.getConsequent().size() == 1 && cond.getConsequent().get(0) instanceof ConditionalStatement) {
                     ConditionalStatement innerCond = (ConditionalStatement)cond.getConsequent().get(0);
                     if (innerCond.getAlternative().isEmpty()) {
                        if (cond.getAlternative().isEmpty()) {
                           cond.getConsequent().clear();
                           cond.getConsequent().addAll(innerCond.getConsequent());
                           cond.setCondition(
                              Expr.binary(BinaryOperation.AND, null, cond.getCondition(), innerCond.getCondition(), cond.getCondition().getLocation())
                           );
                           i--;
                        } else if (cond.getAlternative().size() != 1 || !(cond.getAlternative().get(0) instanceof ConditionalStatement)) {
                           cond.setCondition(ExprOptimizer.invert(cond.getCondition()));
                           cond.getConsequent().clear();
                           cond.getConsequent().addAll(cond.getAlternative());
                           cond.getAlternative().clear();
                           cond.getAlternative().add(innerCond);
                           i--;
                        }
                     }
                  }
               } else if (stmt instanceof BlockStatement nestedBlock) {
                  this.eliminateRedundantBreaks(nestedBlock.getBody(), nestedBlock);
               } else if (stmt instanceof WhileStatement whileStmt) {
                  this.eliminateRedundantBreaks(whileStmt.getBody(), null);
               } else if (stmt instanceof SwitchStatement switchStmt) {
                  for (SwitchClause clause : switchStmt.getClauses()) {
                     this.eliminateRedundantBreaks(clause.getBody(), null);
                  }

                  this.eliminateRedundantBreaks(switchStmt.getDefaultClause(), null);
               }
            }
         }
      }
   }

   private boolean hitsRedundantBreakThreshold(List<Statement> statements, IdentifiedStatement exit) {
      int count = 0;

      for (int i = 0; i < statements.size(); i++) {
         Statement stmt = statements.get(i);
         if (stmt instanceof ConditionalStatement conditional && (conditional.getConsequent().isEmpty() || conditional.getAlternative().isEmpty())) {
            List<Statement> innerStatements = !conditional.getConsequent().isEmpty() ? conditional.getConsequent() : conditional.getAlternative();
            if (!innerStatements.isEmpty()) {
               Statement last = innerStatements.get(innerStatements.size() - 1);
               if (last instanceof BreakStatement breakStmt && exit != null && exit == breakStmt.getTarget()) {
                  if (++count == 8) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private void normalizeConditional(ConditionalStatement stmt) {
      if (stmt.getConsequent().isEmpty()) {
         if (stmt.getAlternative().isEmpty()) {
            return;
         }

         stmt.getConsequent().addAll(stmt.getAlternative());
         stmt.getAlternative().clear();
         stmt.setCondition(ExprOptimizer.invert(stmt.getCondition()));
      }
   }

   @Override
   public void visit(SequentialStatement statement) {
      List<Statement> statements = this.processSequence(statement.getSequence());
      if (statements.size() == 1) {
         this.resultStmt = statements.get(0);
      } else {
         statement.getSequence().clear();
         statement.getSequence().addAll(statements);
         this.resultStmt = statement;
      }
   }

   @Override
   public void visit(ConditionalStatement statement) {
      statement.getCondition().acceptVisitor(this);
      statement.setCondition(this.optimizeCondition(this.resultExpr));
      List<Statement> consequent = this.processSequence(statement.getConsequent());
      List<Statement> alternative = this.processSequence(statement.getAlternative());
      if (consequent.isEmpty()) {
         consequent.addAll(alternative);
         alternative.clear();
         statement.setCondition(ExprOptimizer.invert(statement.getCondition()));
      }

      if (consequent.isEmpty()) {
         SequentialStatement sequentialStatement = new SequentialStatement();
         this.resultStmt = sequentialStatement;
         statement.getCondition().acceptVisitor(new ExpressionSideEffectDecomposer(sequentialStatement.getSequence()));
      } else {
         statement.getConsequent().clear();
         statement.getConsequent().addAll(consequent);
         statement.getAlternative().clear();
         statement.getAlternative().addAll(alternative);
         Statement asConditional = this.tryConvertToConditionalExpression(statement);
         if (asConditional != null) {
            asConditional.acceptVisitor(this);
         } else {
            this.resultStmt = statement;
         }
      }
   }

   private Statement tryConvertToConditionalExpression(ConditionalStatement statement) {
      if (statement.getConsequent().size() == 1 && statement.getAlternative().size() == 1) {
         Statement first = statement.getConsequent().get(0);
         Statement second = statement.getAlternative().get(0);
         if (first instanceof AssignmentStatement && second instanceof AssignmentStatement) {
            AssignmentStatement firstAssignment = (AssignmentStatement)first;
            AssignmentStatement secondAssignment = (AssignmentStatement)second;
            if (firstAssignment.getLeftValue() == null || secondAssignment.getRightValue() == null) {
               return null;
            }

            if (firstAssignment.isAsync() || secondAssignment.isAsync()) {
               return null;
            }

            if (firstAssignment.getLeftValue() instanceof VariableExpr && secondAssignment.getLeftValue() instanceof VariableExpr) {
               VariableExpr firstLhs = (VariableExpr)firstAssignment.getLeftValue();
               VariableExpr secondLhs = (VariableExpr)secondAssignment.getLeftValue();
               if (firstLhs.getIndex() == secondLhs.getIndex()) {
                  ConditionalExpr conditionalExpr = new ConditionalExpr();
                  conditionalExpr.setCondition(statement.getCondition());
                  conditionalExpr.setConsequent(firstAssignment.getRightValue());
                  conditionalExpr.setAlternative(secondAssignment.getRightValue());
                  conditionalExpr.setLocation(statement.getCondition().getLocation());
                  conditionalExpr.setVariableIndex(firstAssignment.getLeftValue().getVariableIndex());
                  AssignmentStatement assignment = new AssignmentStatement();
                  assignment.setLocation(conditionalExpr.getLocation());
                  VariableExpr lhs = new VariableExpr();
                  lhs.setVariableIndex(firstLhs.getIndex());
                  lhs.setIndex(firstLhs.getIndex());
                  assignment.setLeftValue(lhs);
                  assignment.setRightValue(conditionalExpr);
                  this.writeFrequencies[lhs.getIndex()]--;
                  return assignment;
               } else {
                  return null;
               }
            } else {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Override
   public void visit(SwitchStatement statement) {
      statement.getValue().acceptVisitor(this);
      statement.setValue(this.resultExpr);

      for (SwitchClause clause : statement.getClauses()) {
         List<Statement> newBody = this.processSequence(clause.getBody());
         clause.getBody().clear();
         clause.getBody().addAll(newBody);
      }

      List<Statement> newDefault = this.processSequence(statement.getDefaultClause());
      statement.getDefaultClause().clear();
      statement.getDefaultClause().addAll(newDefault);
      if (statement.getClauses().isEmpty()) {
         SequentialStatement seq = new SequentialStatement();
         seq.getSequence().addAll(statement.getDefaultClause());
         this.resultStmt = seq;
      } else {
         this.resultStmt = statement;
      }
   }

   @Override
   public void visit(WhileStatement statement) {
      if (statement.getBody().size() == 1 && statement.getBody().get(0) instanceof WhileStatement) {
         WhileStatement innerLoop = (WhileStatement)statement.getBody().get(0);
         BreakToContinueReplacer replacer = new BreakToContinueReplacer(innerLoop, statement);
         replacer.visit(innerLoop.getBody());
         statement.getBody().clear();
         statement.getBody().addAll(innerLoop.getBody());
      }

      List<Statement> statements = this.processSequence(statement.getBody());

      for (int i = 0; i < statements.size(); i++) {
         if (statements.get(i) instanceof ContinueStatement) {
            ContinueStatement continueStmt = (ContinueStatement)statements.get(i);
            if (continueStmt.getTarget() == statement) {
               statements.subList(i, statements.size()).clear();
               break;
            }
         }
      }

      statement.getBody().clear();
      statement.getBody().addAll(statements);
      if (statement.getCondition() != null) {
         List<Statement> sequenceBackup = this.resultSequence;
         this.resultSequence = new ArrayList<>();
         statement.getCondition().acceptVisitor(this);
         statement.setCondition(this.resultExpr);
         this.resultSequence = sequenceBackup;
      }

      while (!statement.getBody().isEmpty() && statement.getBody().get(0) instanceof ConditionalStatement) {
         ConditionalStatement cond = (ConditionalStatement)statement.getBody().get(0);
         if (cond.getConsequent().size() != 1 || !(cond.getConsequent().get(0) instanceof BreakStatement)) {
            break;
         }

         BreakStatement breakStmt = (BreakStatement)cond.getConsequent().get(0);
         if (breakStmt.getTarget() != statement) {
            break;
         }

         statement.getBody().remove(0);
         if (statement.getCondition() != null) {
            Expr newCondition = Expr.binary(BinaryOperation.AND, null, statement.getCondition(), ExprOptimizer.invert(cond.getCondition()));
            newCondition.setVariableIndex(statement.getCondition().getVariableIndex());
            newCondition.setLocation(statement.getCondition().getLocation());
            statement.setCondition(newCondition);
         } else {
            statement.setCondition(ExprOptimizer.invert(cond.getCondition()));
         }
      }

      this.resultStmt = statement;
   }

   @Override
   public void visit(BlockStatement statement) {
      List<Statement> statements = this.processSequence(statement.getBody());
      this.eliminateRedundantBreaks(statements, statement);
      BlockCountVisitor usageCounter = new BlockCountVisitor(statement);
      usageCounter.visit(statements);
      if (usageCounter.getCount() == 0) {
         SequentialStatement result = new SequentialStatement();
         result.getSequence().addAll(statements);
         this.resultStmt = result;
      } else {
         statement.getBody().clear();
         statement.getBody().addAll(statements);
         this.resultStmt = statement;
      }
   }

   @Override
   public void visit(BreakStatement statement) {
      this.resultStmt = statement;
   }

   @Override
   public void visit(ContinueStatement statement) {
      this.resultStmt = statement;
   }

   @Override
   public void visit(ReturnStatement statement) {
      this.pushLocation(statement.getLocation());

      try {
         if (statement.getResult() != null) {
            statement.getResult().acceptVisitor(this);
            statement.setResult(this.resultExpr);
         }

         this.resultStmt = statement;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(ThrowStatement statement) {
      this.pushLocation(statement.getLocation());

      try {
         statement.getException().acceptVisitor(this);
         statement.setException(this.resultExpr);
         this.resultStmt = statement;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(InitClassStatement statement) {
      this.pushLocation(statement.getLocation());

      try {
         this.resultStmt = statement;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(TryCatchStatement statement) {
      List<Statement> statements = this.processSequence(statement.getProtectedBody());
      statement.getProtectedBody().clear();
      statement.getProtectedBody().addAll(statements);
      statements = this.processSequence(statement.getHandler());
      statement.getHandler().clear();
      statement.getHandler().addAll(statements);
      this.resultStmt = statement;
   }

   @Override
   public void visit(GotoPartStatement statement) {
      this.resultStmt = statement;
   }

   @Override
   public void visit(MonitorEnterStatement statement) {
      this.pushLocation(statement.getLocation());

      try {
         statement.getObjectRef().acceptVisitor(this);
         statement.setObjectRef(this.resultExpr);
         this.resultStmt = statement;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(MonitorExitStatement statement) {
      this.pushLocation(statement.getLocation());

      try {
         statement.getObjectRef().acceptVisitor(this);
         statement.setObjectRef(this.resultExpr);
         this.resultStmt = statement;
      } finally {
         this.popLocation();
      }
   }

   @Override
   public void visit(BoundCheckExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         expr.getIndex().acceptVisitor(this);
         Expr index = this.resultExpr;
         Expr array = null;
         if (expr.getArray() != null) {
            expr.getArray().acceptVisitor(this);
            array = this.resultExpr;
         }

         expr.setIndex(index);
         expr.setArray(array);
         this.resultExpr = expr;
      } finally {
         this.popLocation();
      }
   }

   private Statement addBarrier() {
      SequentialStatement barrier = new SequentialStatement();
      this.resultSequence.add(barrier);
      return barrier;
   }

   private void removeBarrier(Statement barrier) {
      Statement removedBarrier = this.resultSequence.remove(this.resultSequence.size() - 1);
      if (removedBarrier != barrier) {
         throw new AssertionError();
      }
   }

   private boolean isSideEffectFree(Expr expr) {
      if (expr == null) {
         return true;
      } else if (expr instanceof VariableExpr || expr instanceof ConstantExpr) {
         return true;
      } else if (expr instanceof BinaryExpr binary) {
         return this.isSideEffectFree(binary.getFirstOperand()) && this.isSideEffectFree(binary.getSecondOperand());
      } else {
         if (expr instanceof UnaryExpr) {
            return this.isSideEffectFree(((UnaryExpr)expr).getOperand());
         }

         if (expr instanceof InstanceOfExpr) {
            return this.isSideEffectFree(((InstanceOfExpr)expr).getExpr());
         }

         if (expr instanceof PrimitiveCastExpr) {
            return this.isSideEffectFree(((PrimitiveCastExpr)expr).getValue());
         }

         if (expr instanceof ArrayFromDataExpr) {
            for (Expr element : ((ArrayFromDataExpr)expr).getData()) {
               if (!this.isSideEffectFree(element)) {
                  return false;
               }
            }

            return true;
         } else if (expr instanceof InvocationExpr invocation) {
            if (this.isSideEffectFreeCall(invocation)) {
               for (Expr arg : invocation.getArguments()) {
                  if (!this.isSideEffectFree(arg)) {
                     return false;
                  }
               }

               return true;
            } else {
               return false;
            }
         } else {
            return expr instanceof NewExpr;
         }
      }
   }

   private Expr optimizeCondition(Expr expr) {
      if (expr instanceof BinaryExpr binary && isZero(((BinaryExpr)expr).getSecondOperand())) {
         switch (binary.getOperation()) {
            case EQUALS:
               return ExprOptimizer.invert(binary.getFirstOperand());
            case NOT_EQUALS:
               return binary.getFirstOperand();
         }
      }

      return expr;
   }

   static class ArrayOptimization {
      boolean isSet;
      int index;
      NewArrayExpr array;
      int arrayVariable;
      UnwrapArrayExpr unwrappedArray;
      int unwrappedArrayVariable;
      int arrayElementIndex;
      int arraySize;
      int remainingUseCount;
      List<Expr> elements = new ArrayList<>();
   }
}
