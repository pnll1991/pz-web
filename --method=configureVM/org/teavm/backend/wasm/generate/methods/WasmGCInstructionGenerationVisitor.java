package org.teavm.backend.wasm.generate.methods;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.teavm.ast.ArrayFromDataExpr;
import org.teavm.ast.ArrayType;
import org.teavm.ast.AssignmentStatement;
import org.teavm.ast.BinaryExpr;
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
import org.teavm.ast.UnwrapArrayExpr;
import org.teavm.ast.VariableExpr;
import org.teavm.ast.WhileStatement;
import org.teavm.backend.wasm.WasmRuntime;
import org.teavm.backend.wasm.generate.CachedValue;
import org.teavm.backend.wasm.generate.TemporaryVariablePool;
import org.teavm.backend.wasm.generate.ValueCache;
import org.teavm.backend.wasm.generate.WasmGeneratorUtil;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfo;
import org.teavm.backend.wasm.generate.strings.WasmGCStringConstant;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmCompositeType;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmNumType;
import org.teavm.backend.wasm.model.WasmStorageType;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmBlock;
import org.teavm.backend.wasm.model.instruction.WasmCastCondition;
import org.teavm.backend.wasm.model.instruction.WasmCatchClause;
import org.teavm.backend.wasm.model.instruction.WasmConditional;
import org.teavm.backend.wasm.model.instruction.WasmFloatBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmFloatType;
import org.teavm.backend.wasm.model.instruction.WasmFragmentBuilder;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmInstructionList;
import org.teavm.backend.wasm.model.instruction.WasmInt32Subtype;
import org.teavm.backend.wasm.model.instruction.WasmInt64Subtype;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.backend.wasm.model.instruction.WasmIntUnary;
import org.teavm.backend.wasm.model.instruction.WasmIntUnaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmNullCondition;
import org.teavm.backend.wasm.model.instruction.WasmSignedType;
import org.teavm.backend.wasm.model.instruction.WasmSwitch;
import org.teavm.backend.wasm.model.instruction.WasmTry;
import org.teavm.backend.wasm.runtime.StringInternPool;
import org.teavm.backend.wasm.types.PreciseTypeInference;
import org.teavm.backend.wasm.types.PreciseValueType;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;

public class WasmGCInstructionGenerationVisitor implements StatementVisitor, ExprVisitor {
   private static final MethodReference MONITOR_ENTER_SYNC = new MethodReference(Object.class, "monitorEnterSync", Object.class, void.class);
   private static final MethodReference MONITOR_EXIT_SYNC = new MethodReference(Object.class, "monitorExitSync", Object.class, void.class);
   private static final MethodReference MONITOR_ENTER = new MethodReference(Object.class, "monitorEnter", Object.class, void.class);
   private static final MethodReference MONITOR_EXIT = new MethodReference(Object.class, "monitorExit", Object.class, void.class);
   private static final MethodDescriptor CLINIT = new MethodDescriptor("<clinit>", ValueType.VOID);
   private static final int SWITCH_TABLE_THRESHOLD = 256;
   private WasmGCGenerationContext context;
   private final MethodReference currentMethod;
   private final WasmFunction function;
   private int firstVariable;
   private boolean async;
   private TemporaryVariablePool tempVars;
   private ValueCache valueCache;
   private WasmInstructionList returnBlock;
   private WasmInstructionBuilder builder;
   private WasmType expectedType;
   private PreciseTypeInference types;
   private Set<MethodReference> asyncSplitMethods;
   private Map<IdentifiedStatement, WasmInstructionList> breakTargets = new HashMap<>();
   private Map<IdentifiedStatement, WasmInstructionList> continueTargets = new HashMap<>();
   private IdentifiedStatement currentBreakTarget;
   private IdentifiedStatement currentContinueTarget;
   private int blockLevel;
   private WasmGCVirtualCallGenerator vcallGen;
   private boolean shortenedReturn;
   private WasmGCInlineIntrinsicContext intrinsicContext = new WasmGCInlineIntrinsicContext() {
      @Override
      public void generate(WasmInstructionBuilder builder, Expr expr) {
         WasmGCInstructionGenerationVisitor.this.accept(expr, builder);
      }

      @Override
      public TemporaryVariablePool tempVars() {
         return WasmGCInstructionGenerationVisitor.this.tempVars;
      }

      @Override
      public ValueCache valueCache() {
         return WasmGCInstructionGenerationVisitor.this.valueCache;
      }

      @Override
      public boolean isAsync() {
         return WasmGCInstructionGenerationVisitor.this.async;
      }

      @Override
      public boolean isAsyncMethod(MethodReference method) {
         return WasmGCInstructionGenerationVisitor.this.asyncSplitMethods.contains(method);
      }

      @Override
      public MethodReference currentMethod() {
         return WasmGCInstructionGenerationVisitor.this.currentMethod;
      }

      @Override
      public PreciseTypeInference types() {
         return WasmGCInstructionGenerationVisitor.this.types;
      }
   };

   public WasmGCInstructionGenerationVisitor(
      WasmGCGenerationContext context,
      MethodReference currentMethod,
      WasmFunction function,
      int firstVariable,
      boolean async,
      PreciseTypeInference types,
      Set<MethodReference> asyncSplitMethods
   ) {
      this.context = context;
      this.currentMethod = currentMethod;
      this.function = function;
      this.firstVariable = firstVariable;
      this.tempVars = new TemporaryVariablePool(function);
      this.valueCache = new ValueCache(this.tempVars);
      this.async = async;
      this.types = types;
      this.asyncSplitMethods = asyncSplitMethods;
      this.vcallGen = new WasmGCVirtualCallGenerator(context.virtualTables(), context.classInfoProvider());
   }

   public void setReturnBlock(WasmInstructionList returnBlock) {
      this.returnBlock = returnBlock;
   }

   public void generate(Statement statement, WasmInstructionList target) {
      this.shortenedReturn = false;
      this.builder = target.builder();
      statement.acceptVisitor(this);
      this.builder = null;
   }

   public boolean isShortenedReturn() {
      return this.shortenedReturn;
   }

   private void accept(Expr expr) {
      if (this.expectedType != null) {
         WasmType oldExpectedType = this.expectedType;
         this.expectedType = null;
         expr.acceptVisitor(this);
         this.expectedType = oldExpectedType;
      } else {
         expr.acceptVisitor(this);
      }
   }

   private void accept(Expr expr, WasmInstructionBuilder target) {
      this.accept(expr, target, null);
   }

   private void accept(Expr expr, WasmInstructionBuilder target, WasmType expectedType) {
      WasmType oldExpectedType = this.expectedType;
      WasmInstructionBuilder oldBuilder = this.builder;
      this.builder = target;
      this.expectedType = expectedType;
      expr.acceptVisitor(this);
      this.builder = oldBuilder;
      this.expectedType = oldExpectedType;
   }

   private void accept(Statement statement) {
      statement.acceptVisitor(this);
   }

   @Override
   public void visit(BinaryExpr expr) {
      if (!this.builder.isTerminating()) {
         switch (expr.getOperation()) {
            case ADD:
               this.generateBinary(WasmIntBinaryOperation.ADD, WasmFloatBinaryOperation.ADD, expr);
               break;
            case SUBTRACT:
               this.generateBinary(WasmIntBinaryOperation.SUB, WasmFloatBinaryOperation.SUB, expr);
               break;
            case MULTIPLY:
               this.generateBinary(WasmIntBinaryOperation.MUL, WasmFloatBinaryOperation.MUL, expr);
               break;
            case DIVIDE:
               this.generateBinary(WasmIntBinaryOperation.DIV_SIGNED, WasmFloatBinaryOperation.DIV, expr);
               break;
            case MODULO:
               switch (expr.getType()) {
                  case INT:
                  case LONG:
                     this.generateBinary(WasmIntBinaryOperation.REM_SIGNED, expr);
                     return;
                  default:
                     this.builder.pushLocation(expr.getLocation());
                     this.accept(expr.getFirstOperand());
                     this.accept(expr.getSecondOperand());
                     Class<?> typex = this.convertType(expr.getType());
                     MethodReference methodx = new MethodReference(WasmRuntime.class, "remainder", typex, typex, typex);
                     this.builder.call(this.context.functions().forStaticMethod(methodx));
                     this.builder.popLocation();
                     return;
               }
            case BITWISE_AND:
               this.generateBinary(WasmIntBinaryOperation.AND, expr);
               break;
            case BITWISE_OR:
               this.generateBinary(WasmIntBinaryOperation.OR, expr);
               break;
            case BITWISE_XOR:
               this.generateBinary(WasmIntBinaryOperation.XOR, expr);
               break;
            case EQUALS:
               if (expr.getType() == null) {
                  this.builder.pushLocation(expr.getLocation());
                  this.generateReferenceEq(expr);
                  this.builder.popLocation();
               } else {
                  this.generateBinary(WasmIntBinaryOperation.EQ, WasmFloatBinaryOperation.EQ, expr);
               }
               break;
            case NOT_EQUALS:
               if (expr.getType() == null) {
                  this.builder.pushLocation(expr.getLocation());
                  this.generateReferenceEq(expr);
                  this.builder.negate();
                  this.builder.popLocation();
               } else {
                  this.generateBinary(WasmIntBinaryOperation.NE, WasmFloatBinaryOperation.NE, expr);
               }
               break;
            case GREATER:
               this.generateBinary(WasmIntBinaryOperation.GT_SIGNED, WasmFloatBinaryOperation.GT, expr);
               break;
            case GREATER_OR_EQUALS:
               this.generateBinary(WasmIntBinaryOperation.GE_SIGNED, WasmFloatBinaryOperation.GE, expr);
               break;
            case LESS:
               this.generateBinary(WasmIntBinaryOperation.LT_SIGNED, WasmFloatBinaryOperation.LT, expr);
               break;
            case LESS_OR_EQUALS:
               this.generateBinary(WasmIntBinaryOperation.LE_SIGNED, WasmFloatBinaryOperation.LE, expr);
               break;
            case LEFT_SHIFT:
               this.generateBinary(WasmIntBinaryOperation.SHL, expr);
               break;
            case RIGHT_SHIFT:
               this.generateBinary(WasmIntBinaryOperation.SHR_SIGNED, expr);
               break;
            case UNSIGNED_RIGHT_SHIFT:
               this.generateBinary(WasmIntBinaryOperation.SHR_UNSIGNED, expr);
               break;
            case COMPARE_GREATER: {
               this.builder.pushLocation(expr.getLocation());
               this.accept(expr.getFirstOperand());
               this.accept(expr.getSecondOperand());
               Class<?> type = this.convertType(expr.getType());
               MethodReference method = new MethodReference(WasmRuntime.class, "compare", type, type, int.class);
               this.builder.call(this.context.functions().forStaticMethod(method));
               this.builder.popLocation();
               break;
            }
            case COMPARE_LESS: {
               this.builder.pushLocation(expr.getLocation());
               this.accept(expr.getFirstOperand());
               this.accept(expr.getSecondOperand());
               Class<?> type = this.convertType(expr.getType());
               String name = expr.getType() != OperationType.INT && expr.getType() != OperationType.LONG ? "compareLess" : "compare";
               MethodReference method = new MethodReference(WasmRuntime.class, name, type, type, int.class);
               this.builder.call(this.context.functions().forStaticMethod(method));
               this.builder.popLocation();
               break;
            }
            case AND:
               this.generateAnd(expr);
               break;
            case OR:
               this.generateOr(expr);
         }
      }
   }

   private void generateReferenceEq(BinaryExpr expr) {
      if (this.isNull(expr.getFirstOperand())) {
         this.accept(expr.getSecondOperand());
         if (!this.builder.isTerminating()) {
            List<WasmType> typeStack = this.builder.typeInference.typeStack;
            if (!typeStack.isEmpty() && typeStack.get(typeStack.size() - 1) == WasmType.INT32) {
               this.builder.i32Const(0);
               this.builder.intBinary(WasmIntType.INT32, WasmIntBinaryOperation.EQ);
            } else {
               this.builder.isNull();
            }
         }
      } else if (this.isNull(expr.getSecondOperand())) {
         this.accept(expr.getFirstOperand());
         if (!this.builder.isTerminating()) {
            List<WasmType> typeStack = this.builder.typeInference.typeStack;
            if (!typeStack.isEmpty() && typeStack.get(typeStack.size() - 1) == WasmType.INT32) {
               this.builder.i32Const(0);
               this.builder.intBinary(WasmIntType.INT32, WasmIntBinaryOperation.EQ);
            } else {
               this.builder.isNull();
            }
         }
      } else {
         this.accept(expr.getFirstOperand());
         this.accept(expr.getSecondOperand());
         if (!this.builder.isTerminating()) {
            List<WasmType> typeStack = this.builder.typeInference.typeStack;
            if (typeStack.size() >= 2 && typeStack.get(typeStack.size() - 2) == WasmType.INT32) {
               this.builder.intBinary(WasmIntType.INT32, WasmIntBinaryOperation.EQ);
            } else {
               this.builder.refEqual();
            }
         }
      }
   }

   private boolean isNull(Expr expr) {
      return !(expr instanceof ConstantExpr) ? false : ((ConstantExpr)expr).getValue() == null;
   }

   private void generateBinary(WasmIntBinaryOperation intOp, WasmFloatBinaryOperation floatOp, BinaryExpr expr) {
      this.builder.pushLocation(expr.getLocation());
      this.accept(expr.getFirstOperand());
      this.accept(expr.getSecondOperand());
      if (expr.getType() == null) {
         this.builder.intBinary(WasmIntType.INT32, intOp);
      } else {
         switch (expr.getType()) {
            case INT:
               this.builder.intBinary(WasmIntType.INT32, intOp);
               break;
            case LONG:
               this.builder.intBinary(WasmIntType.INT64, intOp);
               break;
            case FLOAT:
               this.builder.floatBinary(WasmFloatType.FLOAT32, floatOp);
               break;
            case DOUBLE:
               this.builder.floatBinary(WasmFloatType.FLOAT64, floatOp);
         }
      }

      this.builder.popLocation();
   }

   private void generateBinary(WasmIntBinaryOperation intOp, BinaryExpr expr) {
      this.builder.pushLocation(expr.getLocation());
      this.accept(expr.getFirstOperand());
      this.accept(expr.getSecondOperand());
      if (expr.getType() == OperationType.LONG) {
         switch (expr.getOperation()) {
            case LEFT_SHIFT:
            case RIGHT_SHIFT:
            case UNSIGNED_RIGHT_SHIFT:
               this.builder.convert(WasmNumType.INT32, WasmNumType.INT64, false);
         }
      }

      switch (expr.getType()) {
         case INT:
            this.builder.intBinary(WasmIntType.INT32, intOp);
            break;
         case LONG:
            this.builder.intBinary(WasmIntType.INT64, intOp);
            break;
         case FLOAT:
         case DOUBLE:
            throw new IllegalStateException("Can't translate operation " + intOp + " for type " + expr.getType());
      }

      this.builder.popLocation();
   }

   private Class<?> convertType(OperationType type) {
      switch (type) {
         case INT:
            return int.class;
         case LONG:
            return long.class;
         case FLOAT:
            return float.class;
         case DOUBLE:
            return double.class;
         default:
            throw new AssertionError(type.toString());
      }
   }

   private void generateAnd(BinaryExpr expr) {
      WasmInstructionBuilder first = new WasmInstructionList().builder();
      first.pushLocation(expr.getLocation());
      this.accept(expr.getFirstOperand(), first);
      first.popLocation();
      if (first.isTerminating()) {
         this.builder.transferFrom(first);
      } else {
         this.builder.pushLocation(expr.getLocation());
         WasmInstructionBuilder block = this.builder.block(WasmType.INT32);
         block.i32Const(0).transferFrom(first).negate().branch(block).drop();
         this.accept(expr.getSecondOperand(), block);
         this.builder.popLocation();
      }
   }

   private void generateOr(BinaryExpr expr) {
      WasmInstructionBuilder first = new WasmInstructionList().builder();
      first.pushLocation(expr.getLocation());
      this.accept(expr.getFirstOperand(), first);
      if (first.isTerminating()) {
         this.builder.transferFrom(first);
      } else {
         this.builder.pushLocation(expr.getLocation());
         WasmInstructionBuilder block = this.builder.block(WasmType.INT32);
         block.i32Const(1).transferFrom(first).branch(block).drop();
         this.accept(expr.getSecondOperand(), block);
         this.builder.popLocation();
      }
   }

   @Override
   public void visit(UnaryExpr expr) {
      this.builder.pushLocation(expr.getLocation());
      label28:
      switch (expr.getOperation()) {
         case INT_TO_BYTE:
            this.accept(expr.getOperand());
            this.builder
               .i32Const(24)
               .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SHL)
               .i32Const(24)
               .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SHR_SIGNED);
            break;
         case INT_TO_SHORT:
            this.accept(expr.getOperand());
            this.builder
               .i32Const(16)
               .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SHL)
               .i32Const(16)
               .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SHR_SIGNED);
            break;
         case INT_TO_CHAR:
            this.accept(expr.getOperand());
            this.builder
               .i32Const(16)
               .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SHL)
               .i32Const(16)
               .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SHR_UNSIGNED);
            break;
         case LENGTH:
            this.accept(expr.getOperand());
            this.builder.arrayLength();
            break;
         case NOT:
            this.accept(expr.getOperand());
            this.builder.negate();
            break;
         case NEGATE:
            switch (expr.getType()) {
               case INT:
                  this.builder.i32Const(0);
                  break;
               case LONG:
                  this.builder.i64Const(0L);
                  break;
               case FLOAT:
                  this.builder.f32Const(0.0F);
                  break;
               case DOUBLE:
                  this.builder.f64Const(0.0);
            }

            this.accept(expr.getOperand());
            switch (expr.getType()) {
               case INT:
                  this.builder.intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SUB);
                  break label28;
               case LONG:
                  this.builder.intBinary(WasmIntType.INT64, WasmIntBinaryOperation.SUB);
                  break label28;
               case FLOAT:
                  this.builder.floatBinary(WasmFloatType.FLOAT32, WasmFloatBinaryOperation.SUB);
                  break label28;
               case DOUBLE:
                  this.builder.floatBinary(WasmFloatType.FLOAT64, WasmFloatBinaryOperation.SUB);
               default:
                  break label28;
            }
         case NULL_CHECK:
            this.builder.pushLocation(expr.getLocation());
            this.accept(expr.getOperand());
            this.generateNullCheck();
            this.builder.popLocation();
      }

      this.builder.popLocation();
   }

   private void generateNullCheck() {
      List<WasmType> typeStack = this.builder.typeInference.typeStack;
      if (!typeStack.isEmpty()) {
         WasmType topType = typeStack.get(typeStack.size() - 1);
         if (topType != WasmType.INT32) {
            WasmType.Reference valueType = (WasmType.Reference)topType;
            WasmInstructionBuilder blockBuilder = this.builder.block(this.context.functionTypes().of(valueType, valueType).asBlock());
            blockBuilder.nullBranch(WasmNullCondition.NOT_NULL, blockBuilder);
            this.generateThrowNpe(blockBuilder);
         }
      }
   }

   @Override
   public void visit(ConditionalExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         this.accept(expr.getCondition());
         WasmType resultType = null;
         if (expr.getVariableIndex() >= 0) {
            PreciseValueType javaType = this.types.typeOf(expr.getVariableIndex());
            if (javaType != null) {
               resultType = this.context.typeMapper().mapType(javaType.valueType);
            }
         }

         if (resultType == null
            && expr.getConsequent().getVariableIndex() >= 0
            && expr.getConsequent().getVariableIndex() == expr.getAlternative().getVariableIndex()) {
            PreciseValueType javaType = this.types.typeOf(expr.getConsequent().getVariableIndex());
            if (javaType != null) {
               resultType = this.context.typeMapper().mapType(javaType.valueType);
            }
         }

         if (!this.tryOptimizedConditional(expr)) {
            Expr consequent = expr.getConsequent();
            Expr alternative = expr.getAlternative();
            if (this.builder.list.getLast() instanceof WasmIntUnary unary && unary.getOperation() == WasmIntUnaryOperation.EQZ) {
               this.builder.list.getLast().delete();
               consequent = expr.getAlternative();
               alternative = expr.getConsequent();
            }

            WasmConditional cond = this.builder.conditional(resultType);
            WasmInstructionBuilder thenBuilder = cond.getThenBlock().builder();
            this.accept(consequent, thenBuilder, this.expectedType);
            WasmInstructionBuilder elseBuilder = cond.getElseBlock().builder();
            this.accept(alternative, elseBuilder, this.expectedType);
            if (resultType == null && !thenBuilder.typeInference.typeStack.isEmpty()) {
               List<WasmType> thenStack = thenBuilder.typeInference.typeStack;
               resultType = thenStack.get(thenStack.size() - 1);
               cond.setType(resultType != null ? resultType.asBlock() : null);
            }
         }

         this.builder.popLocation();
      }
   }

   private boolean tryOptimizedConditional(ConditionalExpr expr) {
      if (!(expr.getConsequent() instanceof ConstantExpr cst1 && expr.getAlternative() instanceof ConstantExpr cst2)) {
         return false;
      } else if (Objects.equals(cst1.getValue(), 1) && Objects.equals(cst2.getValue(), 0)) {
         this.builder.intUnary(WasmIntType.INT32, WasmIntUnaryOperation.EQZ);
         this.builder.intUnary(WasmIntType.INT32, WasmIntUnaryOperation.EQZ);
         return true;
      } else if (Objects.equals(cst1.getValue(), 0) && Objects.equals(cst2.getValue(), 1)) {
         this.builder.intUnary(WasmIntType.INT32, WasmIntUnaryOperation.EQZ);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void visit(ConstantExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         if (expr.getValue() == null) {
            WasmType type = this.expectedType;
            if (type == WasmType.INT32) {
               this.builder.i32Const(0);
            } else {
               if (expr.getVariableIndex() >= 0) {
                  PreciseValueType javaType = this.types.typeOf(expr.getVariableIndex());
                  if (javaType != null) {
                     type = this.context.typeMapper().mapType(javaType.valueType);
                  }
               }

               if (type instanceof WasmType.Reference) {
                  this.builder.nullConst((WasmType.Reference)type);
               } else {
                  WasmGCClassInfo objectClass = this.context.classInfoProvider().getClassInfo("java.lang.Object");
                  this.builder.nullConst(objectClass.getType());
               }
            }
         } else if (expr.getValue() instanceof Integer) {
            this.builder.i32Const((Integer)expr.getValue());
         } else if (expr.getValue() instanceof Long) {
            this.builder.i64Const((Long)expr.getValue());
         } else if (expr.getValue() instanceof Float) {
            this.builder.f32Const((Float)expr.getValue());
         } else if (expr.getValue() instanceof Double) {
            this.builder.f64Const((Double)expr.getValue());
         } else if (expr.getValue() instanceof String) {
            WasmGCStringConstant stringConstant = this.context.strings().getStringConstant((String)expr.getValue());
            this.builder.getGlobal(stringConstant.global);
         } else {
            if (!(expr.getValue() instanceof ValueType)) {
               throw new IllegalArgumentException("Constant unsupported: " + expr.getValue());
            }

            WasmGCGenerationUtil.emitClassLiteral(this.context.classInfoProvider(), this.builder, (ValueType)expr.getValue());
         }

         this.builder.popLocation();
      }
   }

   @Override
   public void visit(VariableExpr expr) {
      WasmLocal local = this.function.getLocalVariables().get(expr.getIndex() - this.firstVariable);
      this.builder.pushLocation(expr.getLocation());
      this.builder.getLocal(local);
      this.builder.popLocation();
   }

   @Override
   public void visit(SubscriptExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         this.accept(expr.getArray());
         if (this.builder.isTerminating()) {
            this.builder.popLocation();
         } else {
            List<WasmType> typeStack = this.builder.typeInference.typeStack;
            WasmArray wasmArray = null;
            if (!typeStack.isEmpty()) {
               WasmType arrayType = typeStack.get(typeStack.size() - 1);
               if (arrayType instanceof WasmType.CompositeReference) {
                  WasmCompositeType composite = ((WasmType.CompositeReference)arrayType).composite;
                  if (composite instanceof WasmArray) {
                     wasmArray = (WasmArray)composite;
                  }
               }
            }

            this.accept(expr.getIndex(), this.builder, WasmType.INT32);
            if (wasmArray != null) {
               switch (expr.getType()) {
                  case BYTE:
                     this.builder.arrayGet(wasmArray, WasmSignedType.SIGNED);
                     break;
                  case SHORT:
                     this.builder.arrayGet(wasmArray, WasmSignedType.SIGNED);
                     break;
                  case CHAR:
                     this.builder.arrayGet(wasmArray, WasmSignedType.UNSIGNED);
                     break;
                  default:
                     this.builder.arrayGet(wasmArray);
               }

               if (expr.getType() == ArrayType.OBJECT && expr.getVariableIndex() >= 0) {
                  PreciseValueType targetType = this.types.typeOf(expr.getVariableIndex());
                  if (targetType != null) {
                     WasmType.Reference wasmTargetType = (WasmType.Reference)this.context.typeMapper().mapType(targetType.valueType);
                     if (!this.isExtern(wasmTargetType)) {
                        this.builder.cast(wasmTargetType);
                     }
                  }
               }
            } else {
               this.builder.unreachable();
            }

            this.builder.popLocation();
         }
      }
   }

   @Override
   public void visit(UnwrapArrayExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         this.accept(expr.getArray());
         if (!this.builder.isTerminating()) {
            List<WasmType> typeStack = this.builder.typeInference.typeStack;
            WasmType arrayType = typeStack.get(typeStack.size() - 1);
            if (arrayType instanceof WasmType.CompositeReference) {
               WasmStructure arrayStruct = (WasmStructure)((WasmType.CompositeReference)arrayType).composite;
               this.builder.structGet(arrayStruct, 2);
            }
         }

         this.builder.popLocation();
      }
   }

   @Override
   public void visit(InvocationExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         this.generateInvocation(expr);
         this.builder.popLocation();
      }
   }

   @Override
   public void visit(QualificationExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         if (expr.getQualified() == null) {
            WasmGlobal global = this.context.classInfoProvider().getStaticFieldLocation(expr.getField());
            this.builder.getGlobal(global);
         } else {
            ClassReader cls = this.context.classes().get(expr.getField().getClassName());
            if (cls == null) {
               this.builder.unreachable();
               this.builder.popLocation();
               return;
            }

            WasmGCClassInfo classInfo = this.context.classInfoProvider().getClassInfo(expr.getField().getClassName());
            this.accept(expr.getQualified(), this.builder, this.context.typeMapper().mapType(ValueType.object(expr.getField().getClassName())));
            if (classInfo.isHeapStructure()) {
               int offset = this.context.classInfoProvider().getHeapFieldOffset(expr.getField());
               FieldReader fieldReader = cls.getField(expr.getField().getFieldName());
               if (fieldReader != null) {
                  this.loadHeapField(fieldReader.getType(), offset);
               }
            } else {
               this.loadNormalField(expr, classInfo);
            }
         }

         this.builder.popLocation();
      }
   }

   private void loadNormalField(QualificationExpr expr, WasmGCClassInfo classInfo) {
      int fieldIndex = this.context.classInfoProvider().getFieldIndex(expr.getField());
      if (fieldIndex < 0) {
         this.builder.unreachable();
      } else {
         WasmStructure struct = classInfo.getStructure();
         ClassReader cls = this.context.classes().get(expr.getField().getClassName());
         if (cls != null) {
            FieldReader field = cls.getField(expr.getField().getFieldName());
            if (field != null) {
               ValueType fieldType = field.getType();
               if (fieldType instanceof ValueType.Primitive) {
                  switch (((ValueType.Primitive)fieldType).getKind()) {
                     case BOOLEAN:
                     case CHARACTER:
                        this.builder.structGet(struct, fieldIndex, WasmSignedType.UNSIGNED);
                        return;
                     case BYTE:
                     case SHORT:
                        this.builder.structGet(struct, fieldIndex, WasmSignedType.SIGNED);
                        return;
                  }
               }
            }
         }

         this.builder.structGet(struct, fieldIndex);
      }
   }

   private void loadHeapField(ValueType type, int offset) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
            case BYTE:
               this.builder.loadI32(1, offset, WasmInt32Subtype.INT8);
               return;
            case CHARACTER:
               this.builder.loadI32(2, offset, WasmInt32Subtype.UINT16);
               return;
            case SHORT:
               this.builder.loadI32(2, offset, WasmInt32Subtype.INT16);
               return;
            case INTEGER:
               this.builder.loadI32(4, offset, WasmInt32Subtype.INT32);
               return;
            case LONG:
               this.builder.loadI64(8, offset, WasmInt64Subtype.INT64);
               return;
            case FLOAT:
               this.builder.loadF32(4, offset);
               return;
            case DOUBLE:
               this.builder.loadF64(8, offset);
               return;
         }
      }

      this.builder.loadI32(4, offset, WasmInt32Subtype.INT32);
   }

   @Override
   public void visit(NewExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         WasmLocal tmp = this.tempVars.acquire(this.context.typeMapper().mapType(ValueType.object(expr.getConstructedClass())));
         this.allocateObject(expr.getConstructedClass(), tmp);
         this.builder.getLocal(tmp);
         this.tempVars.release(tmp);
         this.builder.popLocation();
      }
   }

   @Override
   public void visit(NewArrayExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         WasmGCGenerationUtil.emitClassInfoLiteral(this.context.classInfoProvider(), this.builder, expr.getType());
         this.accept(expr.getLength(), this.builder, WasmType.INT32);
         this.builder.call(this.context.classInfoProvider().getArrayConstructor(expr.getType()));
         this.builder.popLocation();
      }
   }

   @Override
   public void visit(NewMultiArrayExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         WasmGCGenerationUtil.emitClassInfoLiteral(this.context.classInfoProvider(), this.builder, ((ValueType.Array)expr.getType()).getItemType());

         for (Expr dimension : expr.getDimensions()) {
            this.accept(dimension, this.builder, WasmType.INT32);
         }

         this.builder.call(this.context.classInfoProvider().getMultiArrayConstructor(expr.getDimensions().size()));
         this.builder.popLocation();
      }
   }

   @Override
   public void visit(ArrayFromDataExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         WasmGCGenerationUtil.allocateArray(this.context.classInfoProvider(), expr.getType(), this.builder, (wasmArray, b) -> {
            WasmType elementType = wasmArray.getElementType().asUnpackedType();

            for (Expr item : expr.getData()) {
               this.accept(item, this.builder, elementType);
            }

            b.arrayNewFixed(wasmArray, expr.getData().size());
         });
         this.builder.popLocation();
      }
   }

   @Override
   public void visit(InstanceOfExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         ValueType type = expr.getType();
         if (this.canCastNatively(type)) {
            WasmType.CompositeReference wasmType = this.context.classInfoProvider().getClassInfo(type).getStructure().getNonNullReference();
            this.accept(expr.getExpr(), this.builder, this.context.typeMapper().mapType(type));
            this.builder.test(wasmType);
            this.builder.popLocation();
         } else {
            WasmInstructionBuilder block = this.builder.block(WasmType.INT32);
            this.accept(expr.getExpr(), block);
            if (block.isTerminating()) {
               this.builder.transferFrom(block);
               this.builder.popLocation();
            } else {
               List<WasmType> typeStack = block.typeInference.typeStack;
               WasmType topType = typeStack.isEmpty() ? null : typeStack.get(typeStack.size() - 1);
               if (topType instanceof WasmType.Reference) {
                  WasmGCClassInfo objectClass = this.context.classInfoProvider().getClassInfo("java.lang.Object");
                  WasmStructure vtStruct = objectClass.getVirtualTableStructure();
                  WasmInstructionBuilder innerBlock = block.block(this.context.functionTypes().of(null, topType).asBlock());
                  innerBlock.nullBranch(WasmNullCondition.NULL, innerBlock).structGet(objectClass.getStructure(), 0).structGet(vtStruct, 0);
                  WasmGCGenerationUtil.emitClassInfoLiteral(this.context.classInfoProvider(), innerBlock, type);
                  innerBlock.call(this.context.supertypeFunctions().getIsSupertypeFunction(type)).breakTo(block);
               }

               block.i32Const(0);
               this.builder.popLocation();
            }
         }
      }
   }

   private boolean canCastNatively(ValueType type) {
      if (!(type instanceof ValueType.Array)) {
         if (!(type instanceof ValueType.Object)) {
            return false;
         }

         String className = ((ValueType.Object)type).getClassName();
         ClassReader cls = this.context.classes().get(className);
         return cls == null ? false : !cls.hasModifier(ElementModifier.INTERFACE);
      } else {
         ValueType itemType = ((ValueType.Array)type).getItemType();
         return itemType instanceof ValueType.Primitive || itemType.equals(ValueType.object("java.lang.Object"));
      }
   }

   @Override
   public void visit(CastExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         if (expr.getTarget() instanceof ValueType.Object) {
            String className = ((ValueType.Object)expr.getTarget()).getClassName();
            if (this.context.classInfoProvider().getClassInfo(className).isHeapStructure()) {
               this.accept(expr.getValue(), this.builder, WasmType.INT32);
               this.builder.popLocation();
               return;
            }
         }

         this.accept(expr.getValue(), this.builder, this.context.typeMapper().mapType(expr.getTarget()));
         if (this.builder.isTerminating()) {
            this.builder.popLocation();
         } else {
            List<WasmType> sourceTypeStack = this.builder.typeInference.typeStack;
            WasmType sourceType = sourceTypeStack.isEmpty() ? null : sourceTypeStack.get(sourceTypeStack.size() - 1);
            if (!(sourceType instanceof WasmType.Reference sourceRef)) {
               this.builder.popLocation();
            } else {
               WasmType.Reference targetType = (WasmType.Reference)this.context.typeMapper().mapType(expr.getTarget());
               WasmStructure targetStruct = null;
               if (targetType instanceof WasmType.CompositeReference) {
                  WasmCompositeType composite = ((WasmType.CompositeReference)targetType).composite;
                  if (composite instanceof WasmStructure) {
                     targetStruct = (WasmStructure)composite;
                  }
               }

               boolean canInsertCast = true;
               if (targetStruct != null && sourceRef instanceof WasmType.CompositeReference sourceComposite) {
                  WasmStructure sourceStruct = sourceComposite.composite instanceof WasmStructure ? (WasmStructure)sourceComposite.composite : null;
                  if (sourceStruct != null) {
                     if (targetStruct.isSupertypeOf(sourceStruct)) {
                        canInsertCast = false;
                     } else if (!sourceStruct.isSupertypeOf(targetStruct)) {
                        this.generateThrowCce(this.builder);
                        this.builder.popLocation();
                        return;
                     }
                  }
               }

               if (!expr.isWeak() && this.context.isStrict()) {
                  if (this.canCastNatively(expr.getTarget())) {
                     if (canInsertCast) {
                        WasmInstructionBuilder block = this.builder.block(this.context.functionTypes().of(targetType, sourceRef).asBlock());
                        block.castBranch(WasmCastCondition.SUCCESS, sourceRef, targetType, block);
                        this.generateThrowCce(block);
                     }
                  } else {
                     WasmGCClassInfo objectClass = this.context.classInfoProvider().getClassInfo("java.lang.Object");
                     WasmStructure vtStruct = objectClass.getVirtualTableStructure();
                     CachedValue srcValue = this.valueCache.create(sourceType, this.builder);
                     WasmInstructionBuilder block = this.builder.block(this.context.functionTypes().of(null, sourceType).asBlock());
                     block.nullBranch(WasmNullCondition.NULL, block).structGet(objectClass.getStructure(), 0).structGet(vtStruct, 0);
                     WasmGCGenerationUtil.emitClassInfoLiteral(this.context.classInfoProvider(), block, expr.getTarget());
                     block.call(this.context.supertypeFunctions().getIsSupertypeFunction(expr.getTarget())).branch(block);
                     this.generateThrowCce(block);
                     this.builder.append(srcValue).cast(targetType);
                     srcValue.release();
                     if (canInsertCast) {
                        this.builder.cast(targetType);
                     }
                  }
               } else if (canInsertCast) {
                  this.builder.cast(targetType);
               }

               this.builder.popLocation();
            }
         }
      }
   }

   @Override
   public void visit(PrimitiveCastExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         this.accept(expr.getValue());
         this.builder.nonTrapConvert(WasmGeneratorUtil.mapType(expr.getSource()), WasmGeneratorUtil.mapType(expr.getTarget()), true);
         this.builder.popLocation();
      }
   }

   @Override
   public void visit(BoundCheckExpr expr) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(expr.getLocation());
         this.accept(expr.getIndex());
         CachedValue indexValue = this.valueCache.create(WasmType.INT32, this.builder);
         this.builder.drop();
         WasmInstructionBuilder block = this.builder.block();
         if (expr.getArray() != null) {
            WasmInstructionBuilder condBlock = block;
            if (expr.isLower()) {
               condBlock = block.block();
               condBlock.append(indexValue).i32Const(0).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.LT_SIGNED).branch(condBlock.list);
            }

            condBlock.append(indexValue);
            this.accept(expr.getArray(), condBlock);
            condBlock.arrayLength().intBinary(WasmIntType.INT32, WasmIntBinaryOperation.LT_SIGNED).branch(block.list);
         } else if (expr.isLower()) {
            block.append(indexValue).i32Const(0).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.GE_SIGNED).branch(block.list);
         }

         this.generateThrowAioobe(block);
         this.builder.append(indexValue);
         indexValue.release();
         this.builder.popLocation();
      }
   }

   @Override
   public void visit(AssignmentStatement statement) {
      if (!this.builder.isTerminating()) {
         Expr left = statement.getLeftValue();
         if (left == null) {
            if (statement.getRightValue() instanceof InvocationExpr) {
               InvocationExpr invocation = (InvocationExpr)statement.getRightValue();
               this.builder.pushLocation(statement.getLocation());
               this.generateInvocation(invocation);
               if (invocation.getMethod().getReturnType() != ValueType.VOID || invocation.getType() == InvocationType.CONSTRUCTOR) {
                  this.builder.drop();
               }

               this.builder.popLocation();
            } else {
               this.builder.pushLocation(statement.getLocation());
               this.accept(statement.getRightValue());
               this.builder.drop();
               this.builder.popLocation();
            }
         } else if (left instanceof VariableExpr varExpr) {
            WasmLocal local = this.function.getLocalVariables().get(varExpr.getIndex() - this.firstVariable);
            this.builder.pushLocation(statement.getLocation());
            this.accept(statement.getRightValue(), this.builder, local.getType());
            this.builder.setLocal(local);
            this.builder.popLocation();
         } else if (left instanceof QualificationExpr lhs) {
            this.builder.pushLocation(statement.getLocation());
            this.storeField(lhs.getQualified(), lhs.getField(), statement.getRightValue());
            this.builder.popLocation();
         } else {
            if (!(left instanceof SubscriptExpr)) {
               throw new UnsupportedOperationException("This expression is not supported yet");
            }

            this.builder.pushLocation(statement.getLocation());
            SubscriptExpr lhs = (SubscriptExpr)left;
            this.storeArrayItem(lhs, statement.getRightValue());
            this.builder.popLocation();
         }
      }
   }

   private void storeField(Expr qualified, FieldReference field, Expr value) {
      if (qualified == null) {
         WasmGlobal global = this.context.classInfoProvider().getStaticFieldLocation(field);
         this.accept(value, this.builder, global.getType());
         this.builder.setGlobal(global);
      } else {
         ClassReader cls = this.context.classes().get(field.getClassName());
         if (cls == null) {
            this.builder.unreachable();
            return;
         }

         FieldReader fieldReader = cls.getField(field.getFieldName());
         if (fieldReader == null) {
            this.builder.unreachable();
            return;
         }

         this.accept(qualified, this.builder, this.context.typeMapper().mapType(ValueType.object(field.getClassName())));
         this.accept(value, this.builder, this.context.typeMapper().mapType(fieldReader.getType()));
         WasmGCClassInfo classInfo = this.context.classInfoProvider().getClassInfo(field.getClassName());
         if (!classInfo.isHeapStructure()) {
            this.storeNormalField(classInfo, field);
         } else {
            this.storeHeapField(field);
         }
      }
   }

   private void storeNormalField(WasmGCClassInfo classInfo, FieldReference fieldRef) {
      int fieldIndex = this.context.classInfoProvider().getFieldIndex(fieldRef);
      if (fieldIndex >= 0) {
         this.builder.structSet(classInfo.getStructure(), fieldIndex);
      } else {
         this.builder.unreachable();
      }
   }

   private void storeHeapField(FieldReference field) {
      ClassReader cls = this.context.classes().get(field.getClassName());
      ValueType type = cls.getField(field.getFieldName()).getType();
      int offset = this.context.classInfoProvider().getHeapFieldOffset(field);
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
            case BYTE:
               this.builder.storeI32(1, offset, WasmInt32Subtype.INT8);
               return;
            case CHARACTER:
               this.builder.storeI32(2, offset, WasmInt32Subtype.UINT16);
               return;
            case SHORT:
               this.builder.storeI32(2, offset, WasmInt32Subtype.INT16);
               return;
            case INTEGER:
               this.builder.storeI32(4, offset, WasmInt32Subtype.INT32);
               return;
            case LONG:
               this.builder.storeI64(8, offset, WasmInt64Subtype.INT64);
               return;
            case FLOAT:
               this.builder.storeF32(4, offset);
               return;
            case DOUBLE:
               this.builder.storeF64(8, offset);
               return;
         }
      }

      this.builder.storeI32(4, offset, WasmInt32Subtype.INT32);
   }

   protected void storeArrayItem(SubscriptExpr subscriptExpr, Expr value) {
      subscriptExpr.getArray().acceptVisitor(this);
      this.accept(subscriptExpr.getIndex(), this.builder, WasmType.INT32);
      value.acceptVisitor(this);

      this.builder.arraySet(switch (subscriptExpr.getType()) {
         case BYTE -> this.context.classInfoProvider().getPrimitiveArrayType(WasmStorageType.INT8);
         case SHORT, CHAR -> this.context.classInfoProvider().getPrimitiveArrayType(WasmStorageType.INT16);
         case INT -> this.context.classInfoProvider().getPrimitiveArrayType(WasmType.INT32.asStorage());
         case LONG -> this.context.classInfoProvider().getPrimitiveArrayType(WasmType.INT64.asStorage());
         case FLOAT -> this.context.classInfoProvider().getPrimitiveArrayType(WasmType.FLOAT32.asStorage());
         case DOUBLE -> this.context.classInfoProvider().getPrimitiveArrayType(WasmType.FLOAT64.asStorage());
         default -> this.context.classInfoProvider().getObjectArrayType();
      });
   }

   @Override
   public void visit(SequentialStatement statement) {
      if (!this.builder.isTerminating()) {
         for (Statement part : statement.getSequence()) {
            part.acceptVisitor(this);
         }
      }
   }

   @Override
   public void visit(ConditionalStatement statement) {
      if (!this.builder.isTerminating()) {
         this.accept(statement.getCondition());
         if (statement.getConsequent().size() == 1 && statement.getAlternative().isEmpty()) {
            Statement onlyStatement = statement.getConsequent().get(0);
            if (onlyStatement instanceof BreakStatement br) {
               this.builder.pushLocation(br.getLocation());
               IdentifiedStatement target = br.getTarget();
               if (target == null) {
                  target = this.currentBreakTarget;
               }

               WasmInstructionList wasmTarget = this.breakTargets.get(target);
               if (wasmTarget != null) {
                  this.builder.branch(wasmTarget);
               } else {
                  this.builder.unreachable();
               }

               this.builder.popLocation();
               return;
            }
         }

         this.blockLevel++;
         WasmConditional cond = this.builder.conditional();
         WasmInstructionBuilder thenBuilder = cond.getThenBlock().builder();
         WasmInstructionBuilder oldBuilder = this.builder;
         this.builder = thenBuilder;

         for (Statement s : statement.getConsequent()) {
            s.acceptVisitor(this);
         }

         if (!statement.getAlternative().isEmpty()) {
            this.builder = cond.getElseBlock().builder();

            for (Statement s : statement.getAlternative()) {
               s.acceptVisitor(this);
            }
         }

         this.builder = oldBuilder;
         this.blockLevel--;
      }
   }

   @Override
   public void visit(SwitchStatement statement) {
      if (!this.builder.isTerminating()) {
         int min = statement.getClauses().stream().flatMapToInt(clause -> Arrays.stream(clause.getConditions())).min().orElse(0);
         int max = statement.getClauses().stream().flatMapToInt(clause -> Arrays.stream(clause.getConditions())).max().orElse(0);
         this.blockLevel++;
         WasmBlock outermostBlock = new WasmBlock(false);
         this.breakTargets.put(statement, outermostBlock.getBody());
         IdentifiedStatement oldBreakTarget = this.currentBreakTarget;
         this.currentBreakTarget = statement;
         WasmBlock innermostBlock = new WasmBlock(false);
         WasmInstructionBuilder innermostBuilder = innermostBlock.getBody().builder();
         WasmInstructionBuilder builderBackup = this.builder;
         this.builder = innermostBuilder;
         WasmBlock currentBlock = innermostBlock;
         this.accept(statement.getValue());
         List<SwitchClause> clauses = statement.getClauses();
         WasmInstructionList[] clauseLists = new WasmInstructionList[clauses.size()];

         for (int i = 0; i < clauses.size(); i++) {
            clauseLists[i] = currentBlock.getBody();
            WasmBlock nextBlock = new WasmBlock(false);
            this.builder = nextBlock.getBody().builder();
            this.builder.add(currentBlock);
            currentBlock = nextBlock;

            for (Statement part : clauses.get(i).getBody()) {
               this.accept(part);
            }

            this.builder.breakTo(outermostBlock.getBody());
         }

         WasmBlock defaultTarget = currentBlock;
         outermostBlock.getBody().add(currentBlock);
         this.builder = outermostBlock.getBody().builder();

         for (Statement part : statement.getDefaultClause()) {
            this.accept(part);
         }

         this.builder = innermostBuilder;
         if ((long)max - min >= 256L) {
            this.translateSwitchToBinarySearch(statement, defaultTarget.getBody(), clauseLists);
         } else {
            this.translateSwitchToWasmSwitch(statement, defaultTarget.getBody(), clauseLists, min, max);
         }

         this.builder = builderBackup;
         this.builder.add(outermostBlock);
         this.breakTargets.remove(statement);
         this.currentBreakTarget = oldBreakTarget;
         this.blockLevel--;
      }
   }

   private void translateSwitchToBinarySearch(SwitchStatement statement, WasmInstructionList defaultList, WasmInstructionList[] clauseLists) {
      ArrayList<WasmGCInstructionGenerationVisitor.TableEntry> entries = new ArrayList<>();

      for (int i = 0; i < statement.getClauses().size(); i++) {
         SwitchClause clause = statement.getClauses().get(i);

         for (int label : clause.getConditions()) {
            entries.add(new WasmGCInstructionGenerationVisitor.TableEntry(label, clauseLists[i]));
         }
      }

      entries.sort(Comparator.comparingInt(e -> e.label));
      CachedValue testValue = this.valueCache.create(WasmType.INT32, this.builder);
      this.generateBinarySearch(entries, 0, entries.size() - 1, this.builder, defaultList, testValue);
      testValue.release();
      this.builder.breakTo(defaultList);
   }

   private void generateBinarySearch(
      List<WasmGCInstructionGenerationVisitor.TableEntry> entries,
      int lower,
      int upper,
      WasmInstructionBuilder builder,
      WasmInstructionList defaultTarget,
      WasmFragmentBuilder testVar
   ) {
      if (upper - lower == 0) {
         int label = entries.get(lower).label;
         builder.append(testVar)
            .i32Const(label)
            .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.EQ)
            .branch(entries.get(lower).target)
            .breakTo(defaultTarget);
      } else if (upper - lower <= 0) {
         builder.breakTo(defaultTarget);
      } else {
         int mid = (upper + lower) / 2;
         int label = entries.get(mid).label;
         builder.append(testVar).i32Const(label).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.GT_SIGNED);
         WasmConditional cond = builder.conditional();
         this.generateBinarySearch(entries, mid + 1, upper, cond.getThenBlock().builder(), defaultTarget, testVar);
         this.generateBinarySearch(entries, lower, mid, cond.getElseBlock().builder(), defaultTarget, testVar);
      }
   }

   private void translateSwitchToWasmSwitch(SwitchStatement statement, WasmInstructionList defaultTarget, WasmInstructionList[] clauseTargets, int min, int max) {
      if (min != 0) {
         this.builder.i32Const(min);
         this.builder.intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SUB);
      }

      WasmSwitch wasmSwitch = this.builder.switch_(defaultTarget);
      WasmInstructionList[] expandedTargets = new WasmInstructionList[max - min + 1];

      for (int i = 0; i < statement.getClauses().size(); i++) {
         SwitchClause clause = statement.getClauses().get(i);

         for (int label : clause.getConditions()) {
            expandedTargets[label - min] = clauseTargets[i];
         }
      }

      for (WasmInstructionList target : expandedTargets) {
         wasmSwitch.getTargets().add(target != null ? target : defaultTarget);
      }
   }

   @Override
   public void visit(WhileStatement statement) {
      if (!this.builder.isTerminating()) {
         this.blockLevel++;
         IdentifiedStatement oldBreakTarget = this.currentBreakTarget;
         IdentifiedStatement oldContinueTarget = this.currentContinueTarget;
         this.currentBreakTarget = statement;
         this.currentContinueTarget = statement;
         WasmInstructionBuilder outerBlockBuilder = this.builder.block();
         WasmInstructionBuilder loopBuilder = outerBlockBuilder.loop();
         this.breakTargets.put(statement, outerBlockBuilder.list);
         this.continueTargets.put(statement, loopBuilder.list);
         if (statement.getCondition() != null) {
            this.accept(statement.getCondition(), loopBuilder);
            loopBuilder.negate().branch(outerBlockBuilder);
         }

         WasmInstructionBuilder oldBuilder = this.builder;
         this.builder = loopBuilder;

         for (Statement s : statement.getBody()) {
            s.acceptVisitor(this);
         }

         TextLocation lastLocation = this.builder.list.getLast() != null ? this.builder.list.getLast().getLocation() : null;
         this.builder.breakTo(loopBuilder.list);
         if (lastLocation != null && this.builder.list.getLast().getLocation() == null) {
            this.builder.list.getLast().setLocation(lastLocation);
         }

         this.builder = oldBuilder;
         this.breakTargets.remove(statement);
         this.continueTargets.remove(statement);
         this.currentBreakTarget = oldBreakTarget;
         this.currentContinueTarget = oldContinueTarget;
         this.blockLevel--;
      }
   }

   @Override
   public void visit(BlockStatement statement) {
      if (!this.builder.isTerminating()) {
         this.blockLevel++;
         WasmInstructionBuilder blockBuilder = this.builder.block();
         if (statement.getId() != null) {
            this.breakTargets.put(statement, blockBuilder.list);
         }

         WasmInstructionBuilder oldBuilder = this.builder;
         this.builder = blockBuilder;

         for (Statement s : statement.getBody()) {
            s.acceptVisitor(this);
         }

         this.builder = oldBuilder;
         if (statement.getId() != null) {
            this.breakTargets.remove(statement);
         }

         this.blockLevel--;
      }
   }

   @Override
   public void visit(BreakStatement statement) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(statement.getLocation());
         IdentifiedStatement target = statement.getTarget();
         if (target == null) {
            target = this.currentBreakTarget;
         }

         WasmInstructionList wasmTarget = this.breakTargets.get(target);
         if (wasmTarget != null) {
            this.builder.breakTo(wasmTarget);
         } else {
            this.builder.unreachable();
         }

         this.builder.popLocation();
      }
   }

   @Override
   public void visit(ContinueStatement statement) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(statement.getLocation());
         IdentifiedStatement target = statement.getTarget();
         if (target == null) {
            target = this.currentContinueTarget;
         }

         WasmInstructionList wasmTarget = this.continueTargets.get(target);
         if (wasmTarget != null) {
            this.builder.breakTo(wasmTarget);
         } else {
            this.builder.unreachable();
         }

         this.builder.popLocation();
      }
   }

   @Override
   public void visit(ReturnStatement statement) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(statement.getLocation());
         if (statement.getResult() != null) {
            WasmType returnType = this.context.typeMapper().mapType(this.currentMethod.getReturnType());
            this.accept(statement.getResult(), this.builder, returnType);
         }

         if (this.blockLevel > 0) {
            if (this.returnBlock == null) {
               this.builder.return_();
            } else {
               this.builder.breakTo(this.returnBlock);
            }
         } else {
            this.shortenedReturn = true;
         }

         this.builder.popLocation();
      }
   }

   @Override
   public void visit(ThrowStatement statement) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(statement.getLocation());
         this.accept(statement.getException());
         this.builder.throw_(this.context.getExceptionTag());
         this.builder.popLocation();
      }
   }

   @Override
   public void visit(InitClassStatement statement) {
      if (!this.builder.isTerminating()) {
         if (this.needsClassInitializer(statement.getClassName())) {
            this.builder.pushLocation(statement.getLocation());
            boolean isSuspend = this.isAsyncSplit(new MethodReference(statement.getClassName(), CLINIT));
            WasmGlobal pointer = this.context.classInfoProvider().getClassInfo(statement.getClassName()).getInitializerPointer();
            this.builder.getGlobal(pointer);
            this.builder.callReference(this.context.functionTypes().of(null), isSuspend);
            this.builder.popLocation();
         }
      }
   }

   private boolean needsClassInitializer(String className) {
      return className.equals(StringInternPool.class.getName())
         ? false
         : this.context.classInfoProvider().getClassInfo(className).getInitializerPointer() != null;
   }

   @Override
   public void visit(TryCatchStatement statement) {
      if (!this.builder.isTerminating()) {
         ArrayList<TryCatchStatement> tryCatchStatements = new ArrayList<>();

         while (statement.getProtectedBody().size() == 1) {
            Statement next = statement.getProtectedBody().get(0);
            if (!(next instanceof TryCatchStatement)) {
               break;
            }

            tryCatchStatements.add(statement);
            statement = (TryCatchStatement)next;
         }

         tryCatchStatements.add(statement);
         Collections.reverse(tryCatchStatements);
         this.blockLevel++;
         WasmInstructionBuilder oldBuilder = this.builder;
         ArrayList<WasmInstructionBuilder> buildersToClose = new ArrayList<>();
         WasmType.Reference throwableType = (WasmType.Reference)this.context.typeMapper().mapType(ValueType.object("java.lang.Throwable"));
         WasmBlock dispatchBlock = new WasmBlock(false);
         WasmInstructionBuilder dispatchBlockBuilder = dispatchBlock.getBody().builder();
         WasmInstructionBuilder tryWrapper = dispatchBlockBuilder.block(throwableType);
         WasmTry tryInsn = tryWrapper.try_();
         tryWrapper.unreachable();
         this.builder = tryInsn.getBody().builder();

         for (Statement part : statement.getProtectedBody()) {
            part.acceptVisitor(this);
         }

         tryInsn.getCatches().add(new WasmCatchClause(this.context.getExceptionTag(), false, tryWrapper.list));
         buildersToClose.add(this.builder);
         Consumer<WasmType> typeConsumer = t -> dispatchBlock.setType(t != null ? t.asBlock() : null);
         this.builder = dispatchBlockBuilder;
         WasmInstructionList innerInsnList = dispatchBlock.getBody();

         for (TryCatchStatement tryCatchStmt : tryCatchStatements) {
            String exType = tryCatchStmt.getExceptionType();
            if (exType == null) {
               exType = "java.lang.Throwable";
            }

            WasmType.Reference exceptionType = this.context.classInfoProvider().getClassInfo(exType).getType();
            typeConsumer.accept(exceptionType);
            WasmBlock catchBlock = new WasmBlock(false);
            catchBlock.getBody().add(innerInsnList.getBreakTarget());
            dispatchBlockBuilder.castBranch(WasmCastCondition.SUCCESS, throwableType, exceptionType, innerInsnList);
            this.builder = catchBlock.getBody().builder();
            this.builder.typeInference.typeStack.add(exceptionType);
            if (tryCatchStmt.getExceptionVariable() != null) {
               this.builder.setLocal(this.function.getLocalVariables().get(tryCatchStmt.getExceptionVariable() - this.firstVariable));
            } else {
               this.builder.drop();
            }

            for (Statement part : tryCatchStmt.getHandler()) {
               part.acceptVisitor(this);
            }

            buildersToClose.add(this.builder);
            innerInsnList = catchBlock.getBody();
            typeConsumer = t -> catchBlock.setType(t != null ? t.asBlock() : null);
         }

         dispatchBlockBuilder.throw_(this.context.getExceptionTag());

         for (int i = 0; i < buildersToClose.size(); i++) {
            WasmInstructionBuilder builderToClose = buildersToClose.get(i);
            TextLocation location = builderToClose.list.getLast() != null ? builderToClose.list.getLast().getLocation() : null;
            builderToClose.breakTo(innerInsnList);
            builderToClose.list.getLast().setLocation(location);
         }

         this.builder = oldBuilder;
         this.builder.add(innerInsnList.getBreakTarget());
         this.blockLevel--;
      }
   }

   @Override
   public void visit(GotoPartStatement statement) {
   }

   @Override
   public void visit(MonitorEnterStatement statement) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(statement.getLocation());
         this.accept(statement.getObjectRef());
         this.monitorEnter(this.builder);
         this.builder.popLocation();
      }
   }

   public void monitorEnter(WasmInstructionBuilder builder) {
      builder.call(this.context.functions().forStaticMethod(this.async ? MONITOR_ENTER : MONITOR_ENTER_SYNC), this.async);
   }

   @Override
   public void visit(MonitorExitStatement statement) {
      if (!this.builder.isTerminating()) {
         this.builder.pushLocation(statement.getLocation());
         this.accept(statement.getObjectRef());
         this.monitorExit(this.builder);
         this.builder.popLocation();
      }
   }

   public void monitorExit(WasmInstructionBuilder builder) {
      builder.call(this.context.functions().forStaticMethod(this.async ? MONITOR_EXIT : MONITOR_EXIT_SYNC), this.async);
   }

   private void generateInvocation(InvocationExpr expr) {
      WasmGCInlineIntrinsic csIntrinsic = this.context.callSiteIntrinsics().getIntrinsic(expr.getMethod());
      if (csIntrinsic != null) {
         csIntrinsic.apply(expr, this.intrinsicContext, this.builder);
      } else {
         if (expr.getType() == InvocationType.STATIC || expr.getType() == InvocationType.SPECIAL) {
            MethodReader method = this.context.classes().resolve(expr.getMethod());
            MethodReference reference = method != null ? method.getReference() : expr.getMethod();
            WasmFunction function = expr.getType() == InvocationType.STATIC
               ? this.context.functions().forStaticMethod(reference)
               : this.context.functions().forInstanceMethod(reference);
            List<Expr> arguments = expr.getArguments();
            int i = 0;

            for (int argumentsSize = arguments.size(); i < argumentsSize; i++) {
               Expr argument = arguments.get(i);
               ValueType type = expr.getType() == InvocationType.STATIC
                  ? reference.parameterType(i)
                  : (i == 0 ? ValueType.object(reference.getClassName()) : reference.parameterType(i - 1));
               WasmType wasmType = this.context.typeMapper().mapType(type);
               this.accept(argument, this.builder, wasmType);
               if (expr.getType() == InvocationType.SPECIAL && i == 0) {
                  this.forceType(wasmType);
               }
            }

            this.builder.call(function, this.isAsyncSplit(expr.getMethod()));
         } else if (expr.getType() == InvocationType.CONSTRUCTOR) {
            WasmType resultType = this.context.typeMapper().mapType(ValueType.object(expr.getMethod().getClassName()));
            WasmLocal tmp = this.tempVars.acquire(resultType);
            this.allocateObject(expr.getMethod().getClassName(), tmp);
            this.builder.getLocal(tmp);

            for (Expr arg : expr.getArguments()) {
               this.accept(arg);
            }

            this.builder.call(this.context.functions().forInstanceMethod(expr.getMethod()), this.isAsyncSplit(expr.getMethod()));
            this.builder.getLocal(tmp);
            this.tempVars.release(tmp);
         } else {
            this.virtualCall(expr);
         }
      }
   }

   private void generateThrowNpe(WasmInstructionBuilder target) {
      target.call(this.context.npeMethod());
      target.throw_(this.context.getExceptionTag());
   }

   private void generateThrowAioobe(WasmInstructionBuilder target) {
      target.call(this.context.aaiobeMethod());
      target.throw_(this.context.getExceptionTag());
   }

   private void generateThrowCce(WasmInstructionBuilder target) {
      target.call(this.context.cceMethod());
      target.throw_(this.context.getExceptionTag());
   }

   private void forceType(WasmType expectedType) {
      if (!this.builder.isTerminating()) {
         List<WasmType> types = this.builder.typeInference.typeStack;
         WasmType top = types.get(types.size() - 1);
         if (top != expectedType && top instanceof WasmType.CompositeReference && expectedType instanceof WasmType.CompositeReference) {
            WasmCompositeType actualComposite = ((WasmType.CompositeReference)top).composite;
            WasmCompositeType expectedComposite = ((WasmType.CompositeReference)expectedType).composite;
            if (actualComposite instanceof WasmStructure && expectedComposite instanceof WasmStructure) {
               WasmStructure actualStruct = (WasmStructure)actualComposite;
               WasmStructure expectedStruct = (WasmStructure)expectedComposite;
               if (actualStruct.isSupertypeOf(expectedStruct)) {
                  this.builder.cast(expectedComposite.getReference());
               }
            }
         }
      }
   }

   private void allocateObject(String className, WasmLocal local) {
      WasmGCClassInfo classInfo = this.context.classInfoProvider().getClassInfo(className);
      this.builder
         .structNewDefault(classInfo.getStructure())
         .teeLocal(local)
         .getGlobal(classInfo.getVirtualTablePointer())
         .structSet(classInfo.getStructure(), 0);
   }

   private void virtualCall(InvocationExpr expr) {
      ValueType.Object instanceType = ValueType.object(expr.getMethod().getClassName());
      WasmType.CompositeReference instanceWasmType = (WasmType.CompositeReference)this.context.typeMapper().mapType(instanceType);
      this.accept(expr.getArguments().get(0), this.builder, instanceWasmType);
      this.vcallGen.generate(this.builder, expr.getMethod(), this.isAsyncSplit(expr.getMethod()), this.valueCache, instanceWasmType, b -> {
         for (int i = 1; i < expr.getArguments().size(); i++) {
            WasmType paramType = this.context.typeMapper().mapType(expr.getMethod().parameterType(i - 1));
            this.accept(expr.getArguments().get(i), this.builder, paramType);
         }
      });
   }

   protected boolean isAsyncSplit(MethodReference methodRef) {
      return !this.async ? false : this.isAsyncSplitImpl(this.findRealMethod(methodRef));
   }

   private boolean isAsyncSplitImpl(MethodReference methodRef) {
      if (this.asyncSplitMethods.isEmpty()) {
         return false;
      }

      if (this.asyncSplitMethods.contains(methodRef)) {
         return true;
      }

      ClassReader cls = this.context.classes().get(methodRef.getClassName());
      if (cls == null) {
         return false;
      }

      if (cls.getParent() != null && this.isAsyncSplitImpl(new MethodReference(cls.getParent(), methodRef.getDescriptor()))) {
         return true;
      }

      for (String itf : cls.getInterfaces()) {
         if (this.isAsyncSplitImpl(new MethodReference(itf, methodRef.getDescriptor()))) {
            return true;
         }
      }

      return false;
   }

   private MethodReference findRealMethod(MethodReference method) {
      String clsName = method.getClassName();

      while (clsName != null) {
         ClassReader cls = this.context.classes().get(clsName);
         if (cls == null) {
            break;
         }

         MethodReader methodReader = cls.getMethod(method.getDescriptor());
         if (methodReader != null) {
            return new MethodReference(clsName, method.getDescriptor());
         }

         clsName = cls.getParent();
         if (clsName != null && clsName.equals(cls.getName())) {
            break;
         }
      }

      return method;
   }

   private boolean isExtern(WasmType.Reference type) {
      return !(type instanceof WasmType.SpecialReference) ? false : ((WasmType.SpecialReference)type).kind == WasmType.SpecialReferenceKind.EXTERN;
   }

   static class TableEntry {
      final int label;
      final WasmInstructionList target;

      TableEntry(int label, WasmInstructionList target) {
         this.label = label;
         this.target = target;
      }
   }
}
