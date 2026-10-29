package org.teavm.backend.c.generate;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.teavm.backend.c.analyze.VolatileDefinitionFinder;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.backend.c.util.InteropUtil;
import org.teavm.backend.lowlevel.generate.NameProvider;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.hppc.IntContainer;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.interop.Address;
import org.teavm.interop.DelegateTo;
import org.teavm.interop.c.Char16;
import org.teavm.interop.c.Variable;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.model.classes.VirtualTable;
import org.teavm.model.lowlevel.CallSiteDescriptor;
import org.teavm.model.lowlevel.CallSiteLocation;
import org.teavm.model.lowlevel.ExceptionHandlerDescriptor;
import org.teavm.model.lowlevel.ExceptionHandlingUtil;
import org.teavm.runtime.Allocator;
import org.teavm.runtime.ExceptionHandling;
import org.teavm.runtime.RuntimeArray;
import org.teavm.runtime.RuntimeClass;
import org.teavm.runtime.RuntimeObject;

public class CodeGenerationVisitor implements ExprVisitor, StatementVisitor {
   public static final MethodReference ALLOC_METHOD = new MethodReference(Allocator.class, "allocate", RuntimeClass.class, Address.class);
   private static final MethodReference ALLOC_ARRAY_METHOD = new MethodReference(Allocator.class, "allocateArray", RuntimeClass.class, int.class, Address.class);
   private static final MethodReference ALLOC_MULTI_ARRAY_METHOD = new MethodReference(
      Allocator.class, "allocateMultiArray", RuntimeClass.class, Address.class, int.class, RuntimeArray.class
   );
   private static final MethodReference THROW_EXCEPTION_METHOD = new MethodReference(ExceptionHandling.class, "throwException", Throwable.class, void.class);
   private static final MethodReference MONITOR_ENTER = new MethodReference(Object.class, "monitorEnter", Object.class, void.class);
   private static final MethodReference MONITOR_EXIT = new MethodReference(Object.class, "monitorExit", Object.class, void.class);
   private static final MethodReference MONITOR_ENTER_SYNC = new MethodReference(Object.class, "monitorEnterSync", Object.class, void.class);
   private static final MethodReference MONITOR_EXIT_SYNC = new MethodReference(Object.class, "monitorExitSync", Object.class, void.class);
   private static final MethodReference CATCH_EXCEPTION = new MethodReference(ExceptionHandling.class, "catchException", Throwable.class);
   private static final Map<String, String> BUFFER_TYPES = new HashMap<>();
   private GenerationContext context;
   private ClassGenerationContext classContext;
   private NameProvider names;
   private CodeWriter writer;
   private VolatileDefinitionFinder volatileDefinitions;
   private int[] temporaryVariableLevel = new int[5];
   private IntSet spilledVariables = new IntHashSet();
   private int[] maxTemporaryVariableLevel = new int[5];
   private MethodReference callingMethod;
   private IncludeManager includes;
   private boolean end;
   private boolean async;
   private final Deque<CodeGenerationVisitor.LocationStackEntry> locationStack = new ArrayDeque<>();
   private List<CallSiteDescriptor> callSites;
   private List<ExceptionHandlerDescriptor> handlers = new ArrayList<>();
   private boolean managed;
   private IdentifiedStatement defaultBreakTarget;
   private IdentifiedStatement defaultContinueTarget;
   private ObjectIntMap<IdentifiedStatement> labelMap = new ObjectIntHashMap();
   private Set<IdentifiedStatement> usedAsBreakTarget = new HashSet<>();
   private Set<IdentifiedStatement> usedAsContinueTarget = new HashSet<>();
   private Map<IdentifiedStatement, Integer> tryDepthByStatements = new HashMap<>();
   private int tryDepth;
   private IntrinsicContext intrinsicContext = new IntrinsicContext() {
      @Override
      public CodeWriter writer() {
         return CodeGenerationVisitor.this.writer;
      }

      @Override
      public NameProvider names() {
         return CodeGenerationVisitor.this.names;
      }

      @Override
      public void emit(Expr expr) {
         expr.acceptVisitor(CodeGenerationVisitor.this);
      }

      @Override
      public Diagnostics diagnotics() {
         return CodeGenerationVisitor.this.context.getDiagnostics();
      }

      @Override
      public MethodReference callingMethod() {
         return CodeGenerationVisitor.this.callingMethod;
      }

      @Override
      public StringPool stringPool() {
         return CodeGenerationVisitor.this.context.getStringPool();
      }

      @Override
      public IncludeManager includes() {
         return CodeGenerationVisitor.this.includes;
      }

      @Override
      public String escapeFileName(String name) {
         return CodeGenerationVisitor.this.context.getFileNames().escapeName(name);
      }

      @Override
      public boolean isIncremental() {
         return CodeGenerationVisitor.this.context.isIncremental();
      }

      @Override
      public ClassReaderSource classes() {
         return CodeGenerationVisitor.this.context.getClassSource();
      }

      @Override
      public void importMethod(MethodReference method, boolean isStatic) {
         CodeGenerationVisitor.this.classContext.importMethod(method, isStatic);
      }

      @Override
      public void emitCallSite() {
         CodeGenerationVisitor.this.withCallSite();
      }
   };

   public CodeGenerationVisitor(
      ClassGenerationContext classContext,
      CodeWriter writer,
      IncludeManager includes,
      List<CallSiteDescriptor> callSites,
      VolatileDefinitionFinder volatileDefinitions
   ) {
      this.classContext = classContext;
      this.context = classContext.getContext();
      this.writer = writer;
      this.names = this.context.getNames();
      this.includes = includes;
      this.callSites = callSites;
      this.volatileDefinitions = volatileDefinitions;
   }

   public void setAsync(boolean async) {
      this.async = async;
   }

   public int[] getTemporaries() {
      return this.maxTemporaryVariableLevel;
   }

   public IntContainer getSpilledVariables() {
      return this.spilledVariables;
   }

   public void setCallingMethod(MethodReference callingMethod) {
      this.callingMethod = callingMethod;
      this.managed = this.context.getCharacteristics().isManaged(callingMethod);
   }

   @Override
   public void visit(BinaryExpr expr) {
      this.pushLocation(expr.getLocation());

      try {
         switch (expr.getOperation()) {
            case COMPARE_LESS:
            case COMPARE_GREATER:
               this.writer.print("teavm_compare_");
               switch (expr.getType()) {
                  case INT:
                     this.writer.print("i32");
                     break;
                  case LONG:
                     this.writer.print("i64");
                     break;
                  case FLOAT:
                     this.writer.print("float");
                     if (expr.getOperation() == BinaryOperation.COMPARE_LESS) {
                        this.writer.print("_less");
                     }
                     break;
                  case DOUBLE:
                     this.writer.print("double");
                     if (expr.getOperation() == BinaryOperation.COMPARE_LESS) {
                        this.writer.print("_less");
                     }
               }

               this.writer.print("(");
               expr.getFirstOperand().acceptVisitor(this);
               this.writer.print(", ");
               expr.getSecondOperand().acceptVisitor(this);
               this.writer.print(")");
               return;
            case UNSIGNED_RIGHT_SHIFT:
               String type = expr.getType() == OperationType.LONG ? "int64_t" : "int32_t";
               this.writer.print("((" + type + ") ((u" + type + ") ");
               expr.getFirstOperand().acceptVisitor(this);
               this.writer.print(" >> ");
               expr.getSecondOperand().acceptVisitor(this);
               this.writer.print("))");
               return;
            case MODULO:
               switch (expr.getType()) {
                  case FLOAT:
                     this.writer.print("fmodf(");
                     expr.getFirstOperand().acceptVisitor(this);
                     this.writer.print(", ");
                     expr.getSecondOperand().acceptVisitor(this);
                     this.writer.print(")");
                     return;
                  case DOUBLE:
                     this.writer.print("fmod(");
                     expr.getFirstOperand().acceptVisitor(this);
                     this.writer.print(", ");
                     expr.getSecondOperand().acceptVisitor(this);
                     this.writer.print(")");
                     return;
               }
            default:
               this.writer.print("(");
               expr.getFirstOperand().acceptVisitor(this);

               this.writer.print(" ").print(switch (expr.getOperation()) {
                  case MODULO -> "%";
                  case ADD -> "+";
                  case SUBTRACT -> "-";
                  case MULTIPLY -> "*";
                  case DIVIDE -> "/";
                  case BITWISE_AND -> "&";
                  case BITWISE_OR -> "|";
                  case BITWISE_XOR -> "^";
                  case LEFT_SHIFT -> "<<";
                  case RIGHT_SHIFT -> ">>";
                  case EQUALS -> "==";
                  case NOT_EQUALS -> "!=";
                  case GREATER -> ">";
                  case GREATER_OR_EQUALS -> ">=";
                  case LESS -> "<";
                  case LESS_OR_EQUALS -> "<=";
                  case AND -> "&&";
                  case OR -> "||";
                  default -> throw new AssertionError();
               }).print(" ");
               expr.getSecondOperand().acceptVisitor(this);
               this.writer.print(")");
         }
      } finally {
         this.popLocation(expr.getLocation());
      }
   }

   private void visitReference(Expr expr) {
      if (this.context.isVmAssertions()) {
         this.writer.print("TEAVM_VERIFY(");
      }

      expr.acceptVisitor(this);
      if (this.context.isVmAssertions()) {
         this.writer.print(")");
      }
   }

   @Override
   public void visit(UnaryExpr expr) {
      this.pushLocation(expr.getLocation());
      switch (expr.getOperation()) {
         case NOT:
            this.writer.print("(");
            this.writer.print("!");
            expr.getOperand().acceptVisitor(this);
            this.writer.print(")");
            break;
         case NEGATE:
            this.writer.print("(");
            this.writer.print("-");
            expr.getOperand().acceptVisitor(this);
            this.writer.print(")");
            break;
         case LENGTH:
            this.writer.print("TEAVM_ARRAY_LENGTH(");
            this.visitReference(expr.getOperand());
            this.writer.print(")");
            break;
         case NULL_CHECK:
            boolean needParenthesis = false;
            if (this.needsCallSiteId()) {
               needParenthesis = true;
               this.withCallSite();
            }

            this.writer.print("teavm_nullCheck(");
            this.visitReference(expr.getOperand());
            this.writer.print(")");
            if (needParenthesis) {
               this.writer.print(")");
            }
            break;
         case INT_TO_BYTE:
            this.writer.print("TEAVM_TO_BYTE(");
            expr.getOperand().acceptVisitor(this);
            this.writer.print(")");
            break;
         case INT_TO_SHORT:
            this.writer.print("TEAVM_TO_SHORT(");
            expr.getOperand().acceptVisitor(this);
            this.writer.print(")");
            break;
         case INT_TO_CHAR:
            this.writer.print("TEAVM_TO_CHAR(");
            expr.getOperand().acceptVisitor(this);
            this.writer.print(")");
      }

      this.popLocation(expr.getLocation());
   }

   @Override
   public void visit(ConditionalExpr expr) {
      this.pushLocation(expr.getLocation());
      this.writer.print("(");
      expr.getCondition().acceptVisitor(this);
      this.writer.print(" ? ");
      expr.getConsequent().acceptVisitor(this);
      this.writer.print(" : ");
      expr.getAlternative().acceptVisitor(this);
      this.writer.print(")");
      this.popLocation(expr.getLocation());
   }

   @Override
   public void visit(ConstantExpr expr) {
      this.pushLocation(expr.getLocation());
      CodeGeneratorUtil.writeValue(this.writer, this.context, this.includes, expr.getValue());
      this.popLocation(expr.getLocation());
   }

   @Override
   public void visit(VariableExpr expr) {
      this.pushLocation(expr.getLocation());
      this.writer.print(this.getVariableName(expr.getIndex()));
      this.popLocation(expr.getLocation());
   }

   private String getVariableName(int index) {
      return index == 0 ? "teavm_this_" : "teavm_local_" + index;
   }

   @Override
   public void visit(SubscriptExpr expr) {
      this.pushLocation(expr.getLocation());
      this.writer.print("TEAVM_ARRAY_AT(");
      this.visitReference(expr.getArray());
      this.writer.print(", ").print(getArrayType(expr.getType())).print(", ");
      expr.getIndex().acceptVisitor(this);
      this.writer.print(")");
      this.popLocation(expr.getLocation());
   }

   @Override
   public void visit(UnwrapArrayExpr expr) {
      this.pushLocation(expr.getLocation());
      expr.getArray().acceptVisitor(this);
      this.popLocation(expr.getLocation());
   }

   private static String getArrayType(ArrayType type) {
      switch (type) {
         case BYTE:
            return "int8_t";
         case SHORT:
            return "int16_t";
         case CHAR:
            return "char16_t";
         case INT:
            return "int32_t";
         case LONG:
            return "int64_t";
         case FLOAT:
            return "float";
         case DOUBLE:
            return "double";
         case OBJECT:
            return "void*";
         default:
            throw new AssertionError();
      }
   }

   private boolean needsCallSiteId() {
      return this.managed;
   }

   @Override
   public void visit(InvocationExpr expr) {
      ClassReader cls = this.context.getClassSource().get(expr.getMethod().getClassName());
      if (cls != null) {
         InteropUtil.processInclude(cls.getAnnotations(), this.includes);
         MethodReader method = cls.getMethod(expr.getMethod().getDescriptor());
         if (method != null) {
            InteropUtil.processInclude(method.getAnnotations(), this.includes);
         }
      }

      boolean needParenthesis = false;
      Intrinsic intrinsic = this.context.getIntrinsic(expr.getMethod());
      if (intrinsic != null) {
         this.pushLocation(expr.getLocation());
         if (this.needsCallSiteId() && ExceptionHandlingUtil.isManagedMethodCall(this.context.getCharacteristics(), expr.getMethod())) {
            needParenthesis = true;
            this.withCallSite();
         }

         intrinsic.apply(this.intrinsicContext, expr);
         this.popLocation(expr.getLocation());
         if (needParenthesis) {
            this.writer.print(")");
         }
      } else {
         this.pushLocation(expr.getLocation());
         if (this.needsCallSiteId() && ExceptionHandlingUtil.isManagedMethodCall(this.context.getCharacteristics(), expr.getMethod())) {
            needParenthesis = true;
            this.withCallSite();
         }

         switch (expr.getType()) {
            case CONSTRUCTOR:
               this.generateCallToConstructor(expr.getMethod(), expr.getArguments());
               break;
            case SPECIAL:
            case STATIC:
               this.generateDirectCall(expr.getMethod(), expr.getArguments());
               break;
            case DYNAMIC:
               this.generateVirtualCall(expr.getMethod(), expr.getArguments());
         }

         if (needParenthesis) {
            this.writer.print(")");
         }

         this.popLocation(expr.getLocation());
      }
   }

   private void withCallSite() {
      CodeGenerationVisitor.LocationStackEntry locationEntry = this.locationStack.peek();
      TextLocation location = locationEntry != null ? locationEntry.location : null;
      CallSiteLocation[] callSiteLocations = CallSiteLocation.fromTextLocation(location, this.callingMethod);
      CallSiteDescriptor callSite = new CallSiteDescriptor(this.callSites.size(), callSiteLocations);
      List<ExceptionHandlerDescriptor> reverseHandlers = new ArrayList<>(this.handlers);
      Collections.reverse(reverseHandlers);
      callSite.getHandlers().addAll(reverseHandlers);
      this.callSites.add(callSite);
      this.writer.print("TEAVM_WITH_CALL_SITE_ID(").print(String.valueOf(callSite.getId())).print(", ");
   }

   private void generateCallToConstructor(MethodReference reference, List<? extends Expr> arguments) {
      String receiver = this.allocTemporaryVariable(CVariableType.PTR);
      this.writer.print("(" + receiver + " = ");
      this.allocObject(reference.getClassName());
      this.writer.print(", ");
      MethodReader method = this.context.getClassSource().resolve(reference);
      if (method != null) {
         reference = method.getReference();
      }

      this.classContext.importMethod(reference, false);
      this.writer.print(this.names.forMethod(reference));
      this.writer.print("(" + receiver);

      for (Expr arg : arguments) {
         this.writer.print(", ");
         arg.acceptVisitor(this);
      }

      this.writer.print("), " + receiver + ")");
      this.freeTemporaryVariable(CVariableType.PTR);
   }

   private void generateDirectCall(MethodReference reference, List<? extends Expr> arguments) {
      MethodReader method = this.context.getClassSource().resolve(reference);
      if (method != null && this.isWrappedNativeCall(method)) {
         this.generateWrappedNativeCall(method, arguments);
      } else {
         if (method == null || method.hasModifier(ElementModifier.ABSTRACT)) {
            this.generateNoMethodCall(reference, arguments);
            return;
         }

         reference = method.getReference();
         if (!method.hasModifier(ElementModifier.NATIVE)
            || method.getAnnotations().get(DelegateTo.class.getName()) != null
            || this.context.getGenerator(reference) != null) {
            this.classContext.importMethod(reference, method.hasModifier(ElementModifier.STATIC));
         }

         this.writer.print(this.names.forMethod(reference));
         this.writer.print("(");
         if (!arguments.isEmpty()) {
            arguments.get(0).acceptVisitor(this);

            for (int i = 1; i < arguments.size(); i++) {
               this.writer.print(", ");
               arguments.get(i).acceptVisitor(this);
            }
         }

         this.writer.print(")");
      }
   }

   private void generateVirtualCall(MethodReference reference, List<? extends Expr> arguments) {
      if (this.context.isIncremental()) {
         this.generateIncrementalVirtualCall(reference.getDescriptor(), arguments);
      } else {
         this.generateNormalVirtualCall(reference, arguments);
      }
   }

   private void generateNormalVirtualCall(MethodReference reference, List<? extends Expr> arguments) {
      VirtualTable vtable = this.context.getVirtualTableProvider().lookup(reference.getClassName());
      String vtableClass = null;
      if (vtable != null) {
         VirtualTable containingVt = vtable.findMethodContainer(reference.getDescriptor());
         if (containingVt != null) {
            vtableClass = containingVt.getClassName();
         }
      }

      if (vtableClass == null) {
         this.generateNoMethodCall(reference, arguments);
      } else {
         Expr receiverArg = arguments.get(0);
         boolean closingParenthesis = false;
         String receiver;
         if (receiverArg instanceof VariableExpr) {
            receiver = this.getVariableName(((VariableExpr)receiverArg).getIndex());
         } else {
            receiver = this.allocTemporaryVariable(CVariableType.PTR);
            this.writer.print("((").print(receiver).print(" = ");
            this.visitReference(receiverArg);
            this.writer.print("), ");
            closingParenthesis = true;
         }

         this.includes.includeClass(vtableClass);
         this.writer
            .print("TEAVM_METHOD(")
            .print(receiver)
            .print(", ")
            .print(this.names.forClassClass(vtableClass))
            .print(", ")
            .print(this.names.forVirtualMethod(reference.getDescriptor()))
            .print(")(")
            .print(receiver);

         for (int i = 1; i < arguments.size(); i++) {
            this.writer.print(", ");
            arguments.get(i).acceptVisitor(this);
         }

         this.writer.print(")");
         if (closingParenthesis) {
            this.writer.print(")");
            this.freeTemporaryVariable(CVariableType.PTR);
         }
      }
   }

   private void generateIncrementalVirtualCall(MethodDescriptor descriptor, List<? extends Expr> arguments) {
      Expr receiverArg = arguments.get(0);
      boolean closingParenthesis = false;
      String receiver;
      if (receiverArg instanceof VariableExpr) {
         receiver = this.getVariableName(((VariableExpr)receiverArg).getIndex());
      } else {
         receiver = this.allocTemporaryVariable(CVariableType.PTR);
         this.writer.print("((").print(receiver).print(" = ");
         this.visitReference(receiverArg);
         this.writer.print("), ");
         closingParenthesis = true;
      }

      this.writer
         .print("TEAVM_VC_METHOD(")
         .print(receiver)
         .print(", ")
         .print(this.classContext.getVirtualMethodId(descriptor))
         .print(", ")
         .printType(descriptor.getResultType())
         .print(", (");
      CodeGenerator.generateMethodParameters(this.writer, descriptor, false, false);
      this.writer.print("))(").print(receiver);

      for (int i = 1; i < arguments.size(); i++) {
         this.writer.print(", ");
         arguments.get(i).acceptVisitor(this);
      }

      this.writer.print(")");
      if (closingParenthesis) {
         this.writer.print(")");
         this.freeTemporaryVariable(CVariableType.PTR);
      }
   }

   private void generateNoMethodCall(MethodReference reference, List<? extends Expr> arguments) {
      this.writer.print("(");

      for (Expr arg : arguments) {
         arg.acceptVisitor(this);
         this.writer.print(", ");
      }

      this.printDefaultValue(reference.getReturnType());
      this.writer.print(")");
   }

   private void generateWrappedNativeCall(MethodReader method, List<? extends Expr> arguments) {
      List<String> temporaries = new ArrayList<>();
      List<String> stringTemporaries = new ArrayList<>();
      String resultTmp = null;
      if (method.getResultType() != ValueType.VOID) {
         resultTmp = this.allocTemporaryVariable(typeToCType(method.getResultType()));
      }

      for (int i = 0; i < arguments.size(); i++) {
         temporaries.add(this.allocTemporaryVariable(this.parameterTypeForCall(method, i)));
      }

      boolean stringResult = method.getResultType().isObject(String.class);
      boolean string16Result = method.getAnnotations().get(Char16.class.getName()) != null;
      this.writer.print("(");
      AnnotationContainerReader[] parameterAnnotations = method.getParameterAnnotations();

      for (int i = 0; i < arguments.size(); i++) {
         String tmp = temporaries.get(i);
         this.writer.print(tmp + " = ");
         ValueType type = method.hasModifier(ElementModifier.STATIC)
            ? method.parameterType(i)
            : (i == 0 ? ValueType.object(method.getOwnerName()) : method.parameterType(i - 1));
         if (type.isObject(String.class)) {
            int annotIndex = method.hasModifier(ElementModifier.STATIC) ? i : i - 1;
            boolean is16Char = annotIndex >= 0 && parameterAnnotations[annotIndex].get(Char16.class.getName()) != null;
            String functionName = is16Char ? "teavm_stringToC16" : "teavm_stringToC";
            this.writer.print(functionName).print("(");
            arguments.get(i).acceptVisitor(this);
            this.writer.print(")");
            stringTemporaries.add(tmp);
         } else if (isPrimitiveArray(type)) {
            this.writer.print("TEAVM_ARRAY_DATAN(");
            arguments.get(i).acceptVisitor(this);
            this.writer.print(", ").printStrictType(((ValueType.Array)type).getItemType()).print(")");
         } else if (isPrimitiveBuffer(type)) {
            this.withCallSite();
            this.writer
               .print(
                  this.names
                     .forMethod(
                        new MethodReference(
                           "java.nio.NativeBufferUtil", "getAddress", ValueType.object("java.nio.Buffer"), ValueType.object(Address.class.getName())
                        )
                     )
               );
            this.includes.includeClass("java.nio.NativeBufferUtil");
            this.writer.print("(");
            arguments.get(i).acceptVisitor(this);
            this.writer.print("))");
         } else {
            arguments.get(i).acceptVisitor(this);
         }

         this.writer.print(", ");
      }

      if (resultTmp != null) {
         this.writer.print(resultTmp + " = (" + typeToCType(method.getResultType()).text + ") ");
      }

      this.writer.print(this.names.forMethod(method.getReference()));
      if (method.getAnnotations().get(Variable.class.getName()) == null) {
         this.writer.print("(");

         for (int i = 0; i < temporaries.size(); i++) {
            if (i > 0) {
               this.writer.print(", ");
            }

            this.writer.print(temporaries.get(i));
            this.freeTemporaryVariable(this.parameterTypeForCall(method, i));
         }

         this.writer.print(")");
      } else if (method.parameterCount() > 0 || method.getResultType() == ValueType.VOID) {
         this.context
            .getDiagnostics()
            .error(new CallLocation(method.getReference()), "'@Variable' annotation is not applicable to method {{m0}}", method.getReference());
      }

      for (String tmp : stringTemporaries) {
         this.writer.print(", teavm_free(" + tmp + ")");
      }

      if (resultTmp != null) {
         this.writer.print(", ");
         if (stringResult) {
            String functionName = string16Result ? "teavm_c16ToString" : "teavm_cToString";
            this.writer.print(functionName).print("(");
         }

         this.writer.print(resultTmp);
         if (stringResult) {
            this.writer.print(")");
         }

         this.freeTemporaryVariable(typeToCType(method.getResultType()));
      }

      this.writer.print(")");
   }

   private CVariableType parameterTypeForCall(MethodReader method, int index) {
      if (method.hasModifier(ElementModifier.STATIC)) {
         return typeToCType(method.parameterType(index));
      } else {
         return index == 0 ? CVariableType.PTR : typeToCType(method.parameterType(index - 1));
      }
   }

   private static boolean isPrimitiveArray(ValueType type) {
      return !(type instanceof ValueType.Array) ? false : ((ValueType.Array)type).getItemType() instanceof ValueType.Primitive;
   }

   private static boolean isPrimitiveBuffer(ValueType type) {
      return !(type instanceof ValueType.Object) ? false : BUFFER_TYPES.containsKey(((ValueType.Object)type).getClassName());
   }

   private boolean isWrappedNativeCall(MethodReader method) {
      if (!method.hasModifier(ElementModifier.NATIVE) || method.getAnnotations().get(DelegateTo.class.getName()) != null) {
         return false;
      }

      if (method.getAnnotations().get(Variable.class.getName()) != null) {
         return true;
      }

      for (ValueType type : method.getParameterTypes()) {
         if (type.isObject(String.class) || isPrimitiveArray(type) || isPrimitiveBuffer(type)) {
            return true;
         }
      }

      return method.getResultType().isObject(String.class);
   }

   private String allocTemporaryVariable(CVariableType type) {
      int index = type.ordinal();
      int result = this.temporaryVariableLevel[index]++;
      this.maxTemporaryVariableLevel[index] = Math.max(this.maxTemporaryVariableLevel[index], this.temporaryVariableLevel[index]);
      return "teavm_tmp_" + type.name().toLowerCase() + "_" + result;
   }

   private void freeTemporaryVariable(CVariableType type) {
      this.temporaryVariableLevel[type.ordinal()]--;
   }

   private void printDefaultValue(ValueType type) {
      if (type instanceof ValueType.Primitive) {
         this.writer.print("0");
      } else {
         this.writer.print("NULL");
      }
   }

   @Override
   public void visit(QualificationExpr expr) {
      FieldReference field = expr.getField();
      if (this.isMonitorField(field)) {
         this.pushLocation(expr.getLocation());
         String tmp = this.allocTemporaryVariable(CVariableType.INT);
         this.writer.print("(" + tmp + " = TEAVM_FIELD(");
         expr.getQualified().acceptVisitor(this);
         field = new FieldReference(RuntimeObject.class.getName(), "hashCode");
         this.writer.print(", ").print(this.names.forClass(field.getClassName()) + ", " + this.names.forMemberField(field) + ")");
         this.writer.print(", TEAVM_UNPACK_MONITOR(" + tmp + "))");
         this.popLocation(expr.getLocation());
      } else {
         this.pushLocation(expr.getLocation());
         this.printFieldRef(expr.getQualified(), field);
         this.popLocation(expr.getLocation());
      }
   }

   private void printFieldRef(Expr qualified, FieldReference field) {
      if (qualified != null) {
         ClassReader cls = this.context.getClassSource().get(field.getClassName());
         this.writer.print("TEAVM_FIELD(");
         boolean shouldVerify = this.context.isVmAssertions() && this.context.getCharacteristics().isManaged(field.getClassName());
         if (shouldVerify) {
            this.writer.print("TEAVM_VERIFY(");
         }

         qualified.acceptVisitor(this);
         if (shouldVerify) {
            this.writer.print(")");
         }

         this.writer.print(", ");
         if (cls != null && this.isNative(cls)) {
            InteropUtil.processInclude(cls.getAnnotations(), this.includes);
            InteropUtil.printNativeReference(this.writer, cls);
            this.writer.print(", ").print(InteropUtil.getNativeName(cls, field.getFieldName()));
         } else {
            this.includes.includeClass(field.getClassName());
            this.writer.print(this.names.forClass(field.getClassName())).print(", ").print(this.names.forMemberField(field));
         }

         this.writer.print(")");
      } else {
         this.includes.includeClass(field.getClassName());
         this.writer.print(this.names.forStaticField(field));
      }
   }

   private boolean isNative(ClassReader cls) {
      return this.context.getCharacteristics().isStructure(cls.getName()) && InteropUtil.isNative(cls);
   }

   private boolean isMonitorField(FieldReference field) {
      return field.getClassName().equals("java.lang.Object") && field.getFieldName().equals("monitor");
   }

   @Override
   public void visit(NewExpr expr) {
      this.pushLocation(expr.getLocation());
      boolean needParenthesis = false;
      if (this.needsCallSiteId()) {
         needParenthesis = true;
         this.withCallSite();
      }

      this.allocObject(expr.getConstructedClass());
      if (needParenthesis) {
         this.writer.print(")");
      }

      this.popLocation(expr.getLocation());
   }

   private void allocObject(String className) {
      this.includes.includeClass(className);
      this.classContext.importMethod(ALLOC_METHOD, true);
      this.writer.print(this.names.forMethod(ALLOC_METHOD)).print("(&").print(this.names.forClassInstance(ValueType.object(className))).print(")");
   }

   @Override
   public void visit(NewArrayExpr expr) {
      this.pushLocation(expr.getLocation());
      boolean needParenthesis = false;
      if (this.needsCallSiteId()) {
         needParenthesis = true;
         this.withCallSite();
      }

      ValueType type = ValueType.arrayOf(expr.getType());
      this.writer.print(this.names.forMethod(ALLOC_ARRAY_METHOD)).print("(");
      CodeGeneratorUtil.writeTypeReference(this.writer, this.context, this.includes, type);
      this.writer.print(", ");
      this.classContext.importMethod(ALLOC_ARRAY_METHOD, true);
      expr.getLength().acceptVisitor(this);
      this.writer.print(")");
      if (needParenthesis) {
         this.writer.print(")");
      }

      this.popLocation(expr.getLocation());
   }

   @Override
   public void visit(ArrayFromDataExpr expr) {
      this.pushLocation(expr.getLocation());
      boolean needParenthesis = false;
      if (this.needsCallSiteId()) {
         needParenthesis = true;
         this.withCallSite();
      }

      if (expr.getType() instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)expr.getType()).getKind()) {
            case BOOLEAN:
               this.writer.print("teavm_fillBooleanArray");
               break;
            case BYTE:
               this.writer.print("teavm_fillByteArray");
               break;
            case SHORT:
               this.writer.print("teavm_fillShortArray");
               break;
            case CHARACTER:
               this.writer.print("teavm_fillCharArray");
               break;
            case INTEGER:
               this.writer.print("teavm_fillIntArray");
               break;
            case LONG:
               this.writer.print("teavm_fillLongArray");
               break;
            case FLOAT:
               this.writer.print("teavm_fillFloatArray");
               break;
            case DOUBLE:
               this.writer.print("teavm_fillDoubleArray");
         }
      } else {
         this.writer.print("teavm_fillArray");
      }

      this.writer.print("(");
      ValueType type = ValueType.arrayOf(expr.getType());
      this.classContext.importMethod(ALLOC_ARRAY_METHOD, true);
      this.writer.print(this.names.forMethod(ALLOC_ARRAY_METHOD)).print("(");
      CodeGeneratorUtil.writeTypeReference(this.writer, this.context, this.includes, type);
      this.writer.print(", ");
      this.writer.print(expr.getData().size() + ")");

      for (Expr element : expr.getData()) {
         this.writer.print(", ");
         element.acceptVisitor(this);
      }

      this.writer.print(")");
      if (needParenthesis) {
         this.writer.print(")");
      }

      this.popLocation(expr.getLocation());
   }

   @Override
   public void visit(NewMultiArrayExpr expr) {
      this.pushLocation(expr.getLocation());
      boolean needParenthesis = false;
      if (this.needsCallSiteId()) {
         needParenthesis = true;
         this.withCallSite();
      }

      this.writer.print(this.names.forMethod(ALLOC_MULTI_ARRAY_METHOD)).print("(");
      CodeGeneratorUtil.writeTypeReference(this.writer, this.context, this.includes, expr.getType());
      this.writer.print(", ");
      this.classContext.importMethod(ALLOC_MULTI_ARRAY_METHOD, true);
      this.writer.print("(int32_t[]) {");
      expr.getDimensions().get(0).acceptVisitor(this);

      for (int i = 1; i < expr.getDimensions().size(); i++) {
         this.writer.print(", ");
         expr.getDimensions().get(i).acceptVisitor(this);
      }

      this.writer.print("}, ").print(String.valueOf(expr.getDimensions().size())).print(")");
      if (needParenthesis) {
         this.writer.print(")");
      }

      this.popLocation(expr.getLocation());
   }

   @Override
   public void visit(InstanceOfExpr expr) {
      this.pushLocation(expr.getLocation());
      this.writer.print("teavm_instanceof(");
      this.visitReference(expr.getExpr());
      this.writer.print(", ");
      CodeGeneratorUtil.writeTypeReference(this.writer, this.context, this.includes, expr.getType());
      this.writer.print(", ");
      CodeGeneratorUtil.writeIsSupertypeFunctionRef(this.writer, this.context, this.includes, expr.getType());
      this.writer.print(")");
      this.popLocation(expr.getLocation());
   }

   @Override
   public void visit(CastExpr expr) {
      if (expr.isWeak()) {
         expr.getValue().acceptVisitor(this);
      } else {
         if (expr.getTarget() instanceof ValueType.Object) {
            String className = ((ValueType.Object)expr.getTarget()).getClassName();
            if (!this.context.getCharacteristics().isManaged(className)) {
               expr.getValue().acceptVisitor(this);
               return;
            }
         }

         this.pushLocation(expr.getLocation());
         boolean needParenthesis = false;
         if (this.needsCallSiteId()) {
            needParenthesis = true;
            this.withCallSite();
         }

         this.writer.print("teavm_checkcast(");
         this.visitReference(expr.getValue());
         this.writer.print(", ");
         CodeGeneratorUtil.writeTypeReference(this.writer, this.context, this.includes, expr.getTarget());
         this.writer.print(", ");
         CodeGeneratorUtil.writeIsSupertypeFunctionRef(this.writer, this.context, this.includes, expr.getTarget());
         this.writer.print(")");
         if (needParenthesis) {
            this.writer.print(")");
         }

         this.popLocation(expr.getLocation());
      }
   }

   @Override
   public void visit(PrimitiveCastExpr expr) {
      this.pushLocation(expr.getLocation());
      this.writer.print("((");
      switch (expr.getTarget()) {
         case INT:
            this.writer.print("int32_t");
            break;
         case LONG:
            this.writer.print("int64_t");
            break;
         case FLOAT:
            this.writer.print("float");
            break;
         case DOUBLE:
            this.writer.print("double");
      }

      this.writer.print(") ");
      expr.getValue().acceptVisitor(this);
      this.writer.print(")");
      this.popLocation(expr.getLocation());
   }

   @Override
   public void visit(AssignmentStatement statement) {
      this.pushLocation(statement.getLocation());
      if (statement.getLeftValue() != null) {
         if (statement.getLeftValue() instanceof QualificationExpr) {
            QualificationExpr qualification = (QualificationExpr)statement.getLeftValue();
            FieldReference field = qualification.getField();
            if (this.isMonitorField(field)) {
               this.writer.print("TEAVM_FIELD(");
               qualification.getQualified().acceptVisitor(this);
               field = new FieldReference(RuntimeObject.class.getName(), "hashCode");
               this.writer.print(", ").print(this.names.forClass(field.getClassName()) + ", " + this.names.forMemberField(field) + ") = TEAVM_PACK_MONITOR(");
               statement.getRightValue().acceptVisitor(this);
               this.writer.println(");");
               this.popLocation(statement.getLocation());
               return;
            }
         }

         statement.getLeftValue().acceptVisitor(this);
         this.writer.print(" = ");
      }

      statement.getRightValue().acceptVisitor(this);
      this.writer.println(";");
      if (this.volatileDefinitions.shouldBackup(statement)) {
         VariableExpr lhs = (VariableExpr)statement.getLeftValue();
         this.spilledVariables.add(lhs.getIndex());
         this.writer.println("teavm_spill_" + lhs.getIndex() + " = " + this.getVariableName(lhs.getIndex()) + ";");
      }

      this.popLocation(statement.getLocation());
   }

   @Override
   public void visit(SequentialStatement statement) {
      this.visitMany(statement.getSequence());
   }

   private void visitMany(List<Statement> statements) {
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
   public void visit(ConditionalStatement statement) {
      while (true) {
         this.pushLocation(statement.getCondition().getLocation());
         this.writer.print("if (");
         statement.getCondition().acceptVisitor(this);
         this.writer.println(") {").indent();
         this.popLocation(statement.getCondition().getLocation());
         this.visitMany(statement.getConsequent());
         this.writer.outdent().print("}");
         if (statement.getAlternative().isEmpty()) {
            this.writer.println();
         } else {
            this.writer.print(" else ");
            if (statement.getAlternative().size() == 1 && statement.getAlternative().get(0) instanceof ConditionalStatement) {
               statement = (ConditionalStatement)statement.getAlternative().get(0);
               continue;
            }

            this.writer.println("{").indent();
            this.visitMany(statement.getAlternative());
            this.writer.outdent().println("}");
         }

         return;
      }
   }

   @Override
   public void visit(SwitchStatement statement) {
      IdentifiedStatement oldDefaultBreakTarget = this.defaultBreakTarget;
      this.defaultBreakTarget = statement;
      int statementId = this.registerIdentifiedStatement(statement);
      this.pushLocation(statement.getValue().getLocation());
      this.writer.print("switch (");
      statement.getValue().acceptVisitor(this);
      this.writer.print(") {").println().indent();
      this.popLocation(statement.getValue().getLocation());

      for (SwitchClause clause : statement.getClauses()) {
         for (int condition : clause.getConditions()) {
            this.writer.println("case " + condition + ":");
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

      if (!statement.getDefaultClause().isEmpty()) {
         this.writer.println("default:").indent();
         this.visitMany(statement.getDefaultClause());
         this.writer.outdent();
      }

      this.writer.outdent().println("}");
      if (this.usedAsBreakTarget.contains(statement)) {
         this.writer.outdent().println("teavm_label_" + statementId + ":;").indent();
      }

      this.defaultBreakTarget = oldDefaultBreakTarget;
   }

   @Override
   public void visit(WhileStatement statement) {
      IdentifiedStatement oldDefaultBreakTarget = this.defaultBreakTarget;
      IdentifiedStatement oldDefaultContinueTarget = this.defaultContinueTarget;
      this.defaultBreakTarget = statement;
      this.defaultContinueTarget = statement;
      int statementId = this.registerIdentifiedStatement(statement);
      this.writer.println("while (1) {").indent();
      if (statement.getCondition() != null) {
         this.writer.print("if (!");
         statement.getCondition().acceptVisitor(this);
         this.writer.println(") break;");
      }

      boolean oldEnd = this.end;

      for (Statement part : statement.getBody()) {
         this.end = false;
         part.acceptVisitor(this);
      }

      this.end = oldEnd;
      if (this.usedAsContinueTarget.contains(statement)) {
         this.writer.outdent().println("teavm_cnt_" + statementId + ":;").indent();
      }

      this.writer.outdent().println("}");
      if (this.usedAsBreakTarget.contains(statement)) {
         this.writer.outdent().println("teavm_label_" + statementId + ":;").indent();
      }

      this.defaultContinueTarget = oldDefaultContinueTarget;
      this.defaultBreakTarget = oldDefaultBreakTarget;
   }

   @Override
   public void visit(BlockStatement statement) {
      int statementId = this.registerIdentifiedStatement(statement);
      this.visitMany(statement.getBody());
      if (this.usedAsBreakTarget.contains(statement)) {
         this.writer.outdent().println("teavm_label_" + statementId + ":;").indent();
      }
   }

   @Override
   public void visit(BreakStatement statement) {
      this.pushLocation(statement.getLocation());
      IdentifiedStatement target = statement.getTarget();
      if (target == null) {
         target = this.defaultBreakTarget;
      }

      this.jumpToTarget(target, "teavm_label_");
      this.usedAsBreakTarget.add(target);
      this.popLocation(statement.getLocation());
   }

   @Override
   public void visit(ContinueStatement statement) {
      this.pushLocation(statement.getLocation());
      IdentifiedStatement target = statement.getTarget();
      if (target == null) {
         target = this.defaultContinueTarget;
      }

      this.jumpToTarget(target, "teavm_cnt_");
      this.usedAsContinueTarget.add(target);
      this.popLocation(statement.getLocation());
   }

   private int registerIdentifiedStatement(IdentifiedStatement statement) {
      this.tryDepthByStatements.put(statement, this.tryDepth);
      int statementId = this.labelMap.size() + 1;
      this.labelMap.put(statement, statementId);
      return statementId;
   }

   private void jumpToTarget(IdentifiedStatement target, String prefix) {
      int targetDepth = this.tryDepthByStatements.get(target);

      while (targetDepth < this.tryDepth) {
         targetDepth++;
         this.writer.println("TEAVM_RESTORE_JUMP_BUFFER;");
      }

      int id = this.labelMap.get(target);
      this.writer.println("goto " + prefix + id + ";");
   }

   @Override
   public void visit(ReturnStatement statement) {
      this.pushLocation(statement.getLocation());
      this.writer.print("return");
      if (statement.getResult() != null) {
         this.writer.print(" ");
         statement.getResult().acceptVisitor(this);
      }

      this.writer.println(";");
      this.popLocation(statement.getLocation());
   }

   @Override
   public void visit(ThrowStatement statement) {
      this.pushLocation(statement.getLocation());
      boolean needParenthesis = false;
      if (this.needsCallSiteId()) {
         needParenthesis = true;
         this.withCallSite();
      }

      this.classContext.importMethod(THROW_EXCEPTION_METHOD, true);
      this.writer.print(this.names.forMethod(THROW_EXCEPTION_METHOD)).print("(");
      statement.getException().acceptVisitor(this);
      this.writer.print(")");
      if (needParenthesis) {
         this.writer.print(")");
      }

      this.writer.println(";");
      this.writer.println("TEAVM_UNREACHABLE");
      this.popLocation(statement.getLocation());
   }

   @Override
   public void visit(InitClassStatement statement) {
      this.pushLocation(statement.getLocation());
      boolean needParenthesis = false;
      if (this.needsCallSiteId()) {
         needParenthesis = true;
         this.withCallSite();
      }

      this.includes.includeClass(statement.getClassName());
      this.writer
         .print("teavm_initializeClass((TeaVM_Class*) &")
         .print(this.names.forClassInstance(ValueType.object(statement.getClassName())))
         .print(", &")
         .print(this.names.forClassInitializer(statement.getClassName()))
         .print(")");
      if (needParenthesis) {
         this.writer.print(")");
      }

      this.writer.println(";");
      this.popLocation(statement.getLocation());
   }

   @Override
   public void visit(TryCatchStatement statement) {
      List<TryCatchStatement> tryCatchStatements = new ArrayList<>();
      List<int[]> restoredVariablesByHandler = new ArrayList<>();
      IntHashSet variablesToBackup = new IntHashSet();

      while (statement.getProtectedBody().size() == 1) {
         Statement next = statement.getProtectedBody().get(0);
         if (!(next instanceof TryCatchStatement)) {
            break;
         }

         tryCatchStatements.add(statement);
         restoredVariablesByHandler.add(this.volatileDefinitions.variablesToRestore(statement));
         statement = (TryCatchStatement)next;
         variablesToBackup.addAll(this.volatileDefinitions.variablesToBackup(statement));
      }

      tryCatchStatements.add(statement);
      restoredVariablesByHandler.add(this.volatileDefinitions.variablesToRestore(statement));
      variablesToBackup.addAll(this.volatileDefinitions.variablesToBackup(statement));
      int firstId = this.handlers.size();

      for (int i = 0; i < tryCatchStatements.size(); i++) {
         TryCatchStatement tryCatch = tryCatchStatements.get(i);
         this.handlers.add(new ExceptionHandlerDescriptor(firstId + i + 1, tryCatch.getExceptionType()));
      }

      this.writer.println("TEAVM_TRY").indent();
      int[] varsToBackupArray = variablesToBackup.toArray();
      Arrays.sort(varsToBackupArray);

      for (int varToBackup : varsToBackupArray) {
         this.spilledVariables.add(varToBackup);
         this.writer.println("teavm_spill_" + varToBackup + " = " + this.getVariableName(varToBackup) + ";");
      }

      this.tryDepth++;
      this.visitMany(statement.getProtectedBody());
      this.tryDepth--;
      this.handlers.subList(firstId, this.handlers.size()).clear();
      this.writer.outdent().println("TEAVM_CATCH").indent();

      for (int i = tryCatchStatements.size() - 1; i >= 0; i--) {
         TryCatchStatement tryCatch = tryCatchStatements.get(i);
         int[] variablesToRestore = restoredVariablesByHandler.get(i);
         this.writer.println("// CATCH " + (tryCatch.getExceptionType() != null ? tryCatch.getExceptionType() : "any"));
         this.writer.println("case " + (i + 1 + firstId) + ": {").indent();

         for (int variableIndex : variablesToRestore) {
            this.writer.println(this.getVariableName(variableIndex) + " = teavm_spill_" + variableIndex + ";");
         }

         if (tryCatch.getExceptionVariable() != null) {
            this.writer.print(this.getVariableName(tryCatch.getExceptionVariable())).print(" = ");
            this.writer.print(this.names.forMethod(CATCH_EXCEPTION)).println("();");
         }

         this.visitMany(tryCatch.getHandler());
         this.writer.println("break;");
         this.writer.outdent().println("}");
      }

      this.writer.outdent().println("TEAVM_END_TRY");
   }

   @Override
   public void visit(GotoPartStatement statement) {
   }

   @Override
   public void visit(MonitorEnterStatement statement) {
      this.pushLocation(statement.getLocation());
      boolean needParenthesis = false;
      if (this.needsCallSiteId()) {
         needParenthesis = true;
         this.withCallSite();
      }

      MethodReference methodRef = this.async ? MONITOR_ENTER : MONITOR_ENTER_SYNC;
      this.classContext.importMethod(methodRef, true);
      this.writer.print(this.names.forMethod(methodRef)).print("(");
      statement.getObjectRef().acceptVisitor(this);
      this.writer.print(")");
      if (needParenthesis) {
         this.writer.print(")");
      }

      this.writer.println(";");
      this.popLocation(statement.getLocation());
   }

   @Override
   public void visit(MonitorExitStatement statement) {
      this.pushLocation(statement.getLocation());
      boolean needParenthesis = false;
      if (this.needsCallSiteId()) {
         needParenthesis = true;
         this.withCallSite();
      }

      MethodReference methodRef = this.async ? MONITOR_EXIT : MONITOR_EXIT_SYNC;
      this.classContext.importMethod(methodRef, true);
      this.writer.print(this.names.forMethod(methodRef)).print("(");
      statement.getObjectRef().acceptVisitor(this);
      this.writer.print(")");
      if (needParenthesis) {
         this.writer.print(")");
      }

      this.writer.println(";");
      this.popLocation(statement.getLocation());
   }

   @Override
   public void visit(BoundCheckExpr expr) {
      if (expr.getArray() == null && !expr.isLower()) {
         expr.getIndex().acceptVisitor(this);
      } else {
         boolean needParenthesis = false;
         if (this.needsCallSiteId()) {
            needParenthesis = true;
            this.withCallSite();
         }

         String functionName;
         if (expr.getArray() == null) {
            functionName = "teavm_checkLowerBound";
         } else if (!expr.isLower()) {
            functionName = "teavm_checkUpperBound";
         } else {
            functionName = "teavm_checkBounds";
         }

         this.writer.print(functionName);
         this.writer.print("(");
         expr.getIndex().acceptVisitor(this);
         if (expr.getArray() != null) {
            this.writer.print(", ");
            this.visitReference(expr.getArray());
         }

         this.writer.print(")");
         if (needParenthesis) {
            this.writer.print(")");
         }
      }
   }

   private static CVariableType typeToCType(ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
            case BYTE:
            case SHORT:
            case CHARACTER:
            case INTEGER:
               return CVariableType.INT;
            case LONG:
               return CVariableType.LONG;
            case FLOAT:
               return CVariableType.FLOAT;
            case DOUBLE:
               return CVariableType.DOUBLE;
         }
      }

      return CVariableType.PTR;
   }

   private void pushLocation(TextLocation location) {
      if (location != null) {
         CodeGenerationVisitor.LocationStackEntry prevEntry = this.locationStack.peek();
         if (prevEntry == null || !location.equals(prevEntry.location)) {
            if (location.getFileName() == null) {
               this.writer.nosource();
            } else {
               this.writer.source(location.getFileName(), location.getLine());
            }
         }

         this.locationStack.push(new CodeGenerationVisitor.LocationStackEntry(location));
      }
   }

   private void popLocation(TextLocation location) {
      if (location != null) {
         CodeGenerationVisitor.LocationStackEntry prevEntry = this.locationStack.pop();
         CodeGenerationVisitor.LocationStackEntry entry = this.locationStack.peek();
         if (entry != null) {
            if (!entry.location.equals(prevEntry.location)) {
               if (entry.location.getFileName() == null) {
                  this.writer.nosource();
               } else {
                  this.writer.source(entry.location.getFileName(), entry.location.getLine());
               }
            }
         } else {
            this.writer.nosource();
         }
      }
   }

   static {
      BUFFER_TYPES.put(ByteBuffer.class.getName(), "int8_t");
      BUFFER_TYPES.put(ShortBuffer.class.getName(), "int16_t");
      BUFFER_TYPES.put(CharBuffer.class.getName(), "char16_t");
      BUFFER_TYPES.put(IntBuffer.class.getName(), "int32_t");
      BUFFER_TYPES.put(LongBuffer.class.getName(), "int64_t");
      BUFFER_TYPES.put(FloatBuffer.class.getName(), "float");
      BUFFER_TYPES.put(DoubleBuffer.class.getName(), "double");
   }

   static class LocationStackEntry {
      final TextLocation location;

      LocationStackEntry(TextLocation location) {
         this.location = location;
      }
   }
}
