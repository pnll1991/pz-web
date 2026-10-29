package org.teavm.cache;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.teavm.ast.ArrayFromDataExpr;
import org.teavm.ast.ArrayType;
import org.teavm.ast.AssignmentStatement;
import org.teavm.ast.AsyncMethodNode;
import org.teavm.ast.AsyncMethodPart;
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
import org.teavm.ast.ControlFlowEntry;
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
import org.teavm.ast.RegularMethodNode;
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
import org.teavm.ast.VariableNode;
import org.teavm.ast.WhileStatement;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReference;
import org.teavm.model.InliningInfo;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.ReferenceCache;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.model.util.VariableType;

public class AstIO {
   private static final ElementModifier[] nodeModifiers = ElementModifier.values();
   private static final BinaryOperation[] binaryOperations = BinaryOperation.values();
   private static final UnaryOperation[] unaryOperations = UnaryOperation.values();
   private final SymbolTable symbolTable;
   private final SymbolTable fileTable;
   private final SymbolTable variableTable;
   private final Map<String, IdentifiedStatement> statementMap = new HashMap<>();
   private ReferenceCache referenceCache;
   private TextLocation lastWrittenLocation;
   private TextLocation lastReadLocation;
   private InliningInfo lastReadInlining;

   public AstIO(ReferenceCache referenceCache, SymbolTable symbolTable, SymbolTable fileTable, SymbolTable variableTable) {
      this.referenceCache = referenceCache;
      this.symbolTable = symbolTable;
      this.fileTable = fileTable;
      this.variableTable = variableTable;
   }

   public void write(VarDataOutput output, ControlFlowEntry[] cfg) throws IOException {
      this.lastWrittenLocation = TextLocation.EMPTY;
      output.writeUnsigned(cfg.length);

      for (ControlFlowEntry entry : cfg) {
         this.writeLocation(output, entry.from);
         output.writeUnsigned(entry.to.length);

         for (TextLocation loc : entry.to) {
            this.writeLocation(output, loc);
         }
      }
   }

   public void write(VarDataOutput output, RegularMethodNode method) throws IOException {
      output.writeUnsigned(ElementModifier.pack(method.getModifiers()));
      output.writeUnsigned(method.getVariables().size());

      for (VariableNode var : method.getVariables()) {
         this.write(output, var);
      }

      try {
         method.getBody().acceptVisitor(new AstIO.NodeWriter(output));
      } catch (AstIO.IOExceptionWrapper e) {
         throw new IOException("Error writing method body", e.getCause());
      }
   }

   private void write(VarDataOutput output, VariableNode variable) throws IOException {
      output.writeUnsigned(variable.getIndex());
      output.writeUnsigned(variable.getType().ordinal());
      output.writeUnsigned(variable.getName() != null ? this.variableTable.lookup(variable.getName()) + 1 : 0);
   }

   public ControlFlowEntry[] readControlFlow(VarDataInput input) throws IOException {
      this.lastReadLocation = TextLocation.EMPTY;
      int size = input.readUnsigned();
      ControlFlowEntry[] result = new ControlFlowEntry[size];

      for (int i = 0; i < size; i++) {
         TextLocation from = this.readLocation(input);
         int toSize = input.readUnsigned();
         TextLocation[] to = new TextLocation[toSize];

         for (int j = 0; j < toSize; j++) {
            to[j] = this.readLocation(input);
         }

         result[i] = new ControlFlowEntry(from, to);
      }

      return result;
   }

   public RegularMethodNode read(VarDataInput input, MethodReference method) throws IOException {
      RegularMethodNode node = new RegularMethodNode(method);
      node.getModifiers().addAll(this.unpackModifiers(input.readUnsigned()));
      int varCount = input.readUnsigned();

      for (int i = 0; i < varCount; i++) {
         node.getVariables().add(this.readVariable(input));
      }

      this.lastReadLocation = TextLocation.EMPTY;
      node.setBody(this.readStatement(input));
      return node;
   }

   private VariableNode readVariable(VarDataInput input) throws IOException {
      int index = input.readUnsigned();
      VariableType type = VariableType.values()[input.readUnsigned()];
      VariableNode variable = new VariableNode(index, type);
      int nameIndex = input.readUnsigned();
      variable.setName(nameIndex != 0 ? this.variableTable.at(nameIndex - 1) : null);
      return variable;
   }

   public void writeAsync(VarDataOutput output, AsyncMethodNode method) throws IOException {
      output.writeUnsigned(ElementModifier.pack(method.getModifiers()));
      output.writeUnsigned(method.getVariables().size());

      for (VariableNode var : method.getVariables()) {
         this.write(output, var);
      }

      try {
         output.writeUnsigned(method.getBody().size());
         AstIO.NodeWriter writer = new AstIO.NodeWriter(output);

         for (int i = 0; i < method.getBody().size(); i++) {
            method.getBody().get(i).getStatement().acceptVisitor(writer);
         }
      } catch (AstIO.IOExceptionWrapper e) {
         throw new IOException("Error writing method body", e.getCause());
      }
   }

   public AsyncMethodNode readAsync(VarDataInput input, MethodReference method) throws IOException {
      AsyncMethodNode node = new AsyncMethodNode(method);
      node.getModifiers().addAll(this.unpackModifiers(input.readUnsigned()));
      int varCount = input.readUnsigned();

      for (int i = 0; i < varCount; i++) {
         node.getVariables().add(this.readVariable(input));
      }

      int partCount = input.readUnsigned();
      this.lastReadLocation = null;

      for (int i = 0; i < partCount; i++) {
         AsyncMethodPart part = new AsyncMethodPart();
         part.setStatement(this.readStatement(input));
         node.getBody().add(part);
      }

      return node;
   }

   private Set<ElementModifier> unpackModifiers(int packed) {
      EnumSet<ElementModifier> modifiers = EnumSet.noneOf(ElementModifier.class);

      while (packed != 0) {
         int shift = Integer.numberOfTrailingZeros(packed);
         modifiers.add(nodeModifiers[shift]);
         packed ^= 1 << shift;
      }

      return modifiers;
   }

   private void writeLocation(VarDataOutput output, TextLocation location) throws IOException {
      if (location == null) {
         location = TextLocation.EMPTY;
      }

      if (location.isEmpty()) {
         output.writeUnsigned(0);
      } else if (!this.lastWrittenLocation.isEmpty() && this.lastWrittenLocation.getFileName().equals(location.getFileName())) {
         output.writeUnsigned(1);
         output.writeSigned(location.getLine() - this.lastWrittenLocation.getLine());
      } else {
         output.writeUnsigned(this.fileTable.lookup(location.getFileName()) + 2);
         output.writeUnsigned(location.getLine());
      }

      this.lastWrittenLocation = location;
   }

   private TextLocation readLocation(VarDataInput input) throws IOException {
      int fileIndex = input.readUnsigned();
      if (fileIndex == 0) {
         this.lastReadLocation = null;
      } else {
         if (fileIndex != 1) {
            this.lastReadLocation = new TextLocation(this.fileTable.at(fileIndex - 2), input.readUnsigned());
            return this.lastReadLocation;
         }

         this.lastReadLocation = new TextLocation(this.lastReadLocation.getFileName(), this.lastReadLocation.getLine() + input.readSigned());
      }

      return this.lastReadLocation;
   }

   private int readNodeLocation(int type, VarDataInput input) throws IOException {
      switch (type) {
         case 123:
            this.lastReadLocation = new TextLocation(this.lastReadInlining.getFileName(), this.lastReadInlining.getLine());
            this.lastReadInlining = this.lastReadInlining.getParent();
            break;
         case 124:
            String className = this.symbolTable.at(input.readUnsigned());
            MethodDescriptor methodDescriptor = MethodDescriptor.parse(this.symbolTable.at(input.readUnsigned()));
            methodDescriptor = this.referenceCache.getCached(methodDescriptor);
            this.lastReadInlining = new InliningInfo(
               this.referenceCache.getCached(className, methodDescriptor),
               this.lastReadLocation.getFileName(),
               this.lastReadLocation.getLine(),
               this.lastReadInlining
            );
            this.lastReadLocation = new TextLocation(null, -1, this.lastReadInlining);
            break;
         case 125:
            this.lastReadLocation = new TextLocation(this.fileTable.at(input.readUnsigned()), input.readUnsigned(), this.lastReadInlining);
            break;
         case 126:
            this.lastReadLocation = new TextLocation(
               this.lastReadLocation.getFileName(), this.lastReadLocation.getLine() + input.readSigned(), this.lastReadInlining
            );
            break;
         case 127:
            this.lastReadLocation = null;
            break;
         default:
            return type;
      }

      return input.readUnsigned();
   }

   private Statement readStatement(VarDataInput input) throws IOException {
      int type = this.readNodeLocation(input.readUnsigned(), input);
      switch (type) {
         case 0: {
            AssignmentStatement stmt = new AssignmentStatement();
            stmt.setLocation(this.lastReadLocation);
            stmt.setLeftValue(this.readExpr(input));
            stmt.setRightValue(this.readExpr(input));
            stmt.setAsync(input.readUnsigned() != 0);
            return stmt;
         }
         case 1: {
            AssignmentStatement stmt = new AssignmentStatement();
            stmt.setLocation(this.lastReadLocation);
            stmt.setRightValue(this.readExpr(input));
            stmt.setAsync(input.readUnsigned() != 0);
            return stmt;
         }
         case 2: {
            SequentialStatement stmt = new SequentialStatement();
            this.readSequence(input, stmt.getSequence());
            return stmt;
         }
         case 3: {
            ConditionalStatement stmt = new ConditionalStatement();
            stmt.setCondition(this.readExpr(input));
            this.readSequence(input, stmt.getConsequent());
            this.readSequence(input, stmt.getAlternative());
            return stmt;
         }
         case 4: {
            SwitchStatement stmt = new SwitchStatement();
            stmt.setId(input.read());
            stmt.setValue(this.readExpr(input));
            int clauseCount = input.readUnsigned();

            for (int i = 0; i < clauseCount; i++) {
               SwitchClause clause = new SwitchClause();
               int conditionCount = input.readUnsigned();
               int[] conditions = new int[conditionCount];

               for (int j = 0; j < conditionCount; j++) {
                  conditions[j] = input.readSigned();
               }

               clause.setConditions(conditions);
               this.readSequence(input, clause.getBody());
               stmt.getClauses().add(clause);
            }

            this.readSequence(input, stmt.getDefaultClause());
            return stmt;
         }
         case 5: {
            WhileStatement stmt = new WhileStatement();
            stmt.setId(input.read());
            stmt.setCondition(this.readExpr(input));
            if (stmt.getId() != null) {
               this.statementMap.put(stmt.getId(), stmt);
            }

            this.readSequence(input, stmt.getBody());
            return stmt;
         }
         case 6: {
            WhileStatement stmt = new WhileStatement();
            stmt.setId(input.read());
            if (stmt.getId() != null) {
               this.statementMap.put(stmt.getId(), stmt);
            }

            this.readSequence(input, stmt.getBody());
            return stmt;
         }
         case 7: {
            BlockStatement stmt = new BlockStatement();
            stmt.setId(input.read());
            if (stmt.getId() != null) {
               this.statementMap.put(stmt.getId(), stmt);
            }

            this.readSequence(input, stmt.getBody());
            return stmt;
         }
         case 8: {
            BreakStatement stmt = new BreakStatement();
            stmt.setLocation(this.lastReadLocation);
            stmt.setTarget(this.statementMap.get(input.read()));
            return stmt;
         }
         case 9: {
            BreakStatement stmt = new BreakStatement();
            stmt.setLocation(this.lastReadLocation);
            return stmt;
         }
         case 10: {
            ContinueStatement stmt = new ContinueStatement();
            stmt.setLocation(this.lastReadLocation);
            stmt.setTarget(this.statementMap.get(input.read()));
            return stmt;
         }
         case 11: {
            ContinueStatement stmt = new ContinueStatement();
            stmt.setLocation(this.lastReadLocation);
            return stmt;
         }
         case 12: {
            ReturnStatement stmt = new ReturnStatement();
            stmt.setLocation(this.lastReadLocation);
            stmt.setResult(this.readExpr(input));
            return stmt;
         }
         case 13: {
            ReturnStatement stmt = new ReturnStatement();
            stmt.setLocation(this.lastReadLocation);
            return stmt;
         }
         case 14: {
            ThrowStatement stmt = new ThrowStatement();
            stmt.setLocation(this.lastReadLocation);
            stmt.setException(this.readExpr(input));
            return stmt;
         }
         case 15: {
            InitClassStatement stmt = new InitClassStatement();
            stmt.setLocation(this.lastReadLocation);
            stmt.setClassName(this.symbolTable.at(input.readUnsigned()));
            return stmt;
         }
         case 16: {
            TryCatchStatement stmt = new TryCatchStatement();
            this.readSequence(input, stmt.getProtectedBody());
            int exceptionTypeIndex = input.readUnsigned();
            if (exceptionTypeIndex > 0) {
               stmt.setExceptionType(this.symbolTable.at(exceptionTypeIndex - 1));
            }

            int exceptionVarIndex = input.readUnsigned();
            if (exceptionVarIndex > 0) {
               stmt.setExceptionVariable(exceptionVarIndex - 1);
            }

            this.readSequence(input, stmt.getHandler());
            return stmt;
         }
         case 17: {
            GotoPartStatement stmt = new GotoPartStatement();
            stmt.setPart(input.readUnsigned());
            return stmt;
         }
         case 18: {
            MonitorEnterStatement stmt = new MonitorEnterStatement();
            stmt.setLocation(this.lastReadLocation);
            stmt.setObjectRef(this.readExpr(input));
            return stmt;
         }
         case 19: {
            MonitorExitStatement stmt = new MonitorExitStatement();
            stmt.setLocation(this.lastReadLocation);
            stmt.setObjectRef(this.readExpr(input));
            return stmt;
         }
         default:
            throw new RuntimeException("Unexpected statement type: " + type);
      }
   }

   private void readSequence(VarDataInput input, List<Statement> statements) throws IOException {
      int count = input.readUnsigned();

      for (int i = 0; i < count; i++) {
         statements.add(this.readStatement(input));
      }
   }

   private Expr readExpr(VarDataInput input) throws IOException {
      int type = this.readNodeLocation(input.readUnsigned(), input);
      switch (type) {
         case 0: {
            BinaryExpr expr = new BinaryExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setOperation(binaryOperations[input.readUnsigned()]);
            int valueType = input.readUnsigned();
            expr.setType(valueType > 0 ? OperationType.values()[valueType - 1] : null);
            expr.setFirstOperand(this.readExpr(input));
            expr.setSecondOperand(this.readExpr(input));
            return expr;
         }
         case 1: {
            UnaryExpr expr = new UnaryExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setOperation(unaryOperations[input.readUnsigned()]);
            int valueType = input.readUnsigned();
            expr.setType(valueType > 0 ? OperationType.values()[valueType - 1] : null);
            expr.setOperand(this.readExpr(input));
            return expr;
         }
         case 2: {
            ConditionalExpr expr = new ConditionalExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setCondition(this.readExpr(input));
            expr.setConsequent(this.readExpr(input));
            expr.setAlternative(this.readExpr(input));
            return expr;
         }
         case 3: {
            ConstantExpr expr = new ConstantExpr();
            expr.setLocation(this.lastReadLocation);
            return expr;
         }
         case 4: {
            ConstantExpr expr = new ConstantExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setValue(input.readSigned());
            return expr;
         }
         case 5: {
            ConstantExpr expr = new ConstantExpr();
            expr.setValue(input.readSignedLong());
            return expr;
         }
         case 6: {
            ConstantExpr expr = new ConstantExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setValue(input.readFloat());
            return expr;
         }
         case 7: {
            ConstantExpr expr = new ConstantExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setValue(input.readDouble());
            return expr;
         }
         case 8: {
            ConstantExpr expr = new ConstantExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setValue(input.read());
            return expr;
         }
         case 9: {
            ConstantExpr expr = new ConstantExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setValue(ValueType.parse(this.symbolTable.at(input.readUnsigned())));
            return expr;
         }
         case 10: {
            VariableExpr expr = new VariableExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setIndex(input.readUnsigned());
            return expr;
         }
         case 11: {
            SubscriptExpr expr = new SubscriptExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setArray(this.readExpr(input));
            expr.setIndex(this.readExpr(input));
            expr.setType(ArrayType.values()[input.readUnsigned()]);
            return expr;
         }
         case 12: {
            UnwrapArrayExpr expr = new UnwrapArrayExpr(ArrayType.values()[input.readUnsigned()]);
            expr.setLocation(this.lastReadLocation);
            expr.setArray(this.readExpr(input));
            return expr;
         }
         case 13:
            return this.parseInvocationExpr(InvocationType.CONSTRUCTOR, input);
         case 14:
            return this.parseInvocationExpr(InvocationType.STATIC, input);
         case 15:
            return this.parseInvocationExpr(InvocationType.SPECIAL, input);
         case 16:
            return this.parseInvocationExpr(InvocationType.DYNAMIC, input);
         case 17: {
            QualificationExpr expr = new QualificationExpr();
            expr.setLocation(this.lastReadLocation);
            String className = this.symbolTable.at(input.readUnsigned());
            String fieldName = this.symbolTable.at(input.readUnsigned());
            expr.setField(new FieldReference(className, fieldName));
            return expr;
         }
         case 18: {
            QualificationExpr expr = new QualificationExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setQualified(this.readExpr(input));
            String className = this.symbolTable.at(input.readUnsigned());
            String fieldName = this.symbolTable.at(input.readUnsigned());
            expr.setField(new FieldReference(className, fieldName));
            return expr;
         }
         case 19: {
            NewExpr expr = new NewExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setConstructedClass(this.symbolTable.at(input.readUnsigned()));
            return expr;
         }
         case 20: {
            NewArrayExpr expr = new NewArrayExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setLength(this.readExpr(input));
            expr.setType(ValueType.parse(this.symbolTable.at(input.readUnsigned())));
            return expr;
         }
         case 21: {
            NewMultiArrayExpr expr = new NewMultiArrayExpr();
            expr.setLocation(this.lastReadLocation);
            int dimensionCount = input.readUnsigned();

            for (int i = 0; i < dimensionCount; i++) {
               expr.getDimensions().add(this.readExpr(input));
            }

            expr.setType(ValueType.parse(this.symbolTable.at(input.readUnsigned())));
            return expr;
         }
         case 22: {
            InstanceOfExpr expr = new InstanceOfExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setExpr(this.readExpr(input));
            expr.setType(ValueType.parse(this.symbolTable.at(input.readUnsigned())));
            return expr;
         }
         case 23: {
            CastExpr expr = new CastExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setTarget(ValueType.parse(this.symbolTable.at(input.readUnsigned())));
            expr.setValue(this.readExpr(input));
            expr.setWeak(input.readUnsigned() != 0);
            return expr;
         }
         case 24: {
            PrimitiveCastExpr expr = new PrimitiveCastExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setSource(OperationType.values()[input.readUnsigned()]);
            expr.setTarget(OperationType.values()[input.readUnsigned()]);
            expr.setValue(this.readExpr(input));
            return expr;
         }
         case 25:
         case 26: {
            BoundCheckExpr expr = new BoundCheckExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setIndex(this.readExpr(input));
            expr.setArray(this.readExpr(input));
            expr.setLower(type == 25);
            return expr;
         }
         case 27: {
            BoundCheckExpr expr = new BoundCheckExpr();
            expr.setLocation(this.lastReadLocation);
            expr.setIndex(this.readExpr(input));
            expr.setLower(true);
            return expr;
         }
         case 28: {
            ArrayFromDataExpr expr = new ArrayFromDataExpr();
            expr.setType(ValueType.parse(this.symbolTable.at(input.readUnsigned())));
            int count = input.readUnsigned();

            for (int i = 0; i < count; i++) {
               expr.getData().add(this.readExpr(input));
            }

            return expr;
         }
         default:
            throw new RuntimeException("Unknown expression type: " + type);
      }
   }

   private InvocationExpr parseInvocationExpr(InvocationType invocationType, VarDataInput input) throws IOException {
      InvocationExpr expr = new InvocationExpr();
      expr.setLocation(this.lastReadLocation);
      expr.setType(invocationType);
      String className = this.symbolTable.at(input.readUnsigned());
      String signature = this.symbolTable.at(input.readUnsigned());
      MethodReference methodRef = this.referenceCache.getCached(className, MethodDescriptor.parse(signature));
      expr.setMethod(methodRef);
      int argCount = input.readUnsigned();

      for (int i = 0; i < argCount; i++) {
         expr.getArguments().add(this.readExpr(input));
      }

      return expr;
   }

   private static class IOExceptionWrapper extends RuntimeException {
      private static final long serialVersionUID = -7566355431593608333L;

      IOExceptionWrapper(Throwable cause) {
         super(cause);
      }
   }

   private class NodeWriter implements ExprVisitor, StatementVisitor {
      private final VarDataOutput output;
      private TextLocation lastLocation = TextLocation.EMPTY;

      NodeWriter(VarDataOutput output) {
         this.output = output;
      }

      void writeExpr(Expr expr) throws IOException {
         this.writeLocation(expr.getLocation());
         expr.acceptVisitor(this);
      }

      private void writeLocation(TextLocation location) throws IOException {
         if (location == null) {
            location = TextLocation.EMPTY;
         }

         if (!Objects.equals(location, this.lastLocation)) {
            String fileName = this.lastLocation.getFileName();
            int lineNumber = this.lastLocation.getLine();
            if (location.getInlining() != this.lastLocation.getInlining()) {
               InliningInfo lastCommonInlining = null;
               InliningInfo[] prevPath = this.lastLocation.getInliningPath();
               InliningInfo[] newPath = location.getInliningPath();
               int pathIndex = 0;

               while (pathIndex < prevPath.length && pathIndex < newPath.length && prevPath[pathIndex].equals(newPath[pathIndex])) {
                  lastCommonInlining = prevPath[pathIndex++];
               }

               for (InliningInfo prevInlining = location.getInlining(); prevInlining != lastCommonInlining; prevInlining = prevInlining.getParent()) {
                  this.output.writeUnsigned(123);
                  fileName = prevInlining.getFileName();
                  lineNumber = prevInlining.getLine();
               }

               while (pathIndex < newPath.length) {
                  InliningInfo inlining = newPath[pathIndex++];
                  this.writeSimpleLocation(fileName, lineNumber, inlining.getFileName(), inlining.getLine());
                  fileName = null;
                  lineNumber = -1;
                  this.output.writeUnsigned(124);
                  MethodReference method = inlining.getMethod();
                  this.output.writeUnsigned(AstIO.this.symbolTable.lookup(method.getClassName()));
                  this.output.writeUnsigned(AstIO.this.symbolTable.lookup(method.getDescriptor().toString()));
               }
            }

            this.writeSimpleLocation(fileName, lineNumber, location.getFileName(), location.getLine());
            this.lastLocation = location;
         }
      }

      private void writeSimpleLocation(String fileName, int lineNumber, String newFileName, int newLineNumber) throws IOException {
         if (!Objects.equals(fileName, newFileName) || lineNumber != newLineNumber) {
            if (newFileName == null) {
               this.output.writeUnsigned(127);
            } else if (fileName != null && fileName.equals(newFileName)) {
               this.output.writeUnsigned(126);
               this.output.writeSigned(newLineNumber - lineNumber);
            } else {
               this.output.writeUnsigned(125);
               this.output.writeUnsigned(AstIO.this.fileTable.lookup(newFileName));
               this.output.writeUnsigned(newLineNumber);
            }
         }
      }

      private void writeSequence(List<Statement> sequence) throws IOException {
         this.output.writeUnsigned(sequence.size());

         for (Statement part : sequence) {
            part.acceptVisitor(this);
         }
      }

      @Override
      public void visit(AssignmentStatement statement) {
         try {
            this.writeLocation(statement.getLocation());
            this.output.writeUnsigned(statement.getLeftValue() != null ? 0 : 1);
            if (statement.getLeftValue() != null) {
               this.writeExpr(statement.getLeftValue());
            }

            this.writeExpr(statement.getRightValue());
            this.output.writeUnsigned(statement.isAsync() ? 1 : 0);
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(SequentialStatement statement) {
         try {
            this.output.writeUnsigned(2);
            this.writeSequence(statement.getSequence());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(ConditionalStatement statement) {
         try {
            this.output.writeUnsigned(3);
            this.writeExpr(statement.getCondition());
            this.writeSequence(statement.getConsequent());
            this.writeSequence(statement.getAlternative());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(SwitchStatement statement) {
         try {
            this.output.writeUnsigned(4);
            this.output.write(statement.getId());
            this.writeExpr(statement.getValue());
            this.output.writeUnsigned(statement.getClauses().size());

            for (SwitchClause clause : statement.getClauses()) {
               int[] conditions = clause.getConditions();
               this.output.writeUnsigned(conditions.length);

               for (int condition : conditions) {
                  this.output.writeSigned(condition);
               }

               this.writeSequence(clause.getBody());
            }

            this.writeSequence(statement.getDefaultClause());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(WhileStatement statement) {
         try {
            this.output.writeUnsigned(statement.getCondition() != null ? 5 : 6);
            this.output.write(statement.getId());
            if (statement.getCondition() != null) {
               this.writeExpr(statement.getCondition());
            }

            this.writeSequence(statement.getBody());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(BlockStatement statement) {
         try {
            this.output.writeUnsigned(7);
            this.output.write(statement.getId());
            this.writeSequence(statement.getBody());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(BreakStatement statement) {
         try {
            this.writeLocation(statement.getLocation());
            this.output.writeUnsigned(statement.getTarget() != null && statement.getTarget().getId() != null ? 8 : 9);
            if (statement.getTarget() != null && statement.getTarget().getId() != null) {
               this.output.write(statement.getTarget().getId());
            }
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(ContinueStatement statement) {
         try {
            this.writeLocation(statement.getLocation());
            this.output.writeUnsigned(statement.getTarget() != null && statement.getTarget().getId() != null ? 10 : 11);
            if (statement.getTarget() != null && statement.getTarget().getId() != null) {
               this.output.write(statement.getTarget().getId());
            }
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(ReturnStatement statement) {
         try {
            this.writeLocation(statement.getLocation());
            this.output.writeUnsigned(statement.getResult() != null ? 12 : 13);
            if (statement.getResult() != null) {
               this.writeExpr(statement.getResult());
            }
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(ThrowStatement statement) {
         try {
            this.writeLocation(statement.getLocation());
            this.output.writeUnsigned(14);
            this.writeExpr(statement.getException());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(InitClassStatement statement) {
         try {
            this.writeLocation(statement.getLocation());
            this.output.writeUnsigned(15);
            this.output.writeUnsigned(AstIO.this.symbolTable.lookup(statement.getClassName()));
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(TryCatchStatement statement) {
         try {
            this.output.writeUnsigned(16);
            this.writeSequence(statement.getProtectedBody());
            this.output.writeUnsigned(statement.getExceptionType() != null ? AstIO.this.symbolTable.lookup(statement.getExceptionType()) + 1 : 0);
            this.output.writeUnsigned(statement.getExceptionVariable() != null ? statement.getExceptionVariable() + 1 : 0);
            this.writeSequence(statement.getHandler());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(GotoPartStatement statement) {
         try {
            this.output.writeUnsigned(17);
            this.output.writeUnsigned(statement.getPart());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(MonitorEnterStatement statement) {
         try {
            this.writeLocation(statement.getLocation());
            this.output.writeUnsigned(18);
            this.writeExpr(statement.getObjectRef());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(MonitorExitStatement statement) {
         try {
            this.writeLocation(statement.getLocation());
            this.output.writeUnsigned(19);
            this.writeExpr(statement.getObjectRef());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(BinaryExpr expr) {
         try {
            this.output.writeUnsigned(0);
            this.output.writeUnsigned(expr.getOperation().ordinal());
            this.output.writeUnsigned(expr.getType() != null ? expr.getType().ordinal() + 1 : 0);
            this.writeExpr(expr.getFirstOperand());
            this.writeExpr(expr.getSecondOperand());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(UnaryExpr expr) {
         try {
            this.output.writeUnsigned(1);
            this.output.writeUnsigned(expr.getOperation().ordinal());
            this.output.writeUnsigned(expr.getType() != null ? expr.getType().ordinal() + 1 : 0);
            this.writeExpr(expr.getOperand());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(ConditionalExpr expr) {
         try {
            this.output.writeUnsigned(2);
            this.writeExpr(expr.getCondition());
            this.writeExpr(expr.getConsequent());
            this.writeExpr(expr.getAlternative());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(ConstantExpr expr) {
         try {
            Object value = expr.getValue();
            if (value == null) {
               this.output.writeUnsigned(3);
            } else if (value instanceof Integer) {
               this.output.writeUnsigned(4);
               this.output.writeSigned((Integer)value);
            } else if (value instanceof Long) {
               this.output.writeUnsigned(5);
               this.output.writeSigned((Long)value);
            } else if (value instanceof Float) {
               this.output.writeUnsigned(6);
               this.output.writeFloat((Float)value);
            } else if (value instanceof Double) {
               this.output.writeUnsigned(7);
               this.output.writeDouble((Double)value);
            } else if (value instanceof String) {
               this.output.writeUnsigned(8);
               this.output.write((String)value);
            } else if (value instanceof ValueType) {
               this.output.writeUnsigned(9);
               this.output.writeUnsigned(AstIO.this.symbolTable.lookup(value.toString()));
            }
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(VariableExpr expr) {
         try {
            this.output.writeUnsigned(10);
            this.output.writeUnsigned(expr.getIndex());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(SubscriptExpr expr) {
         try {
            this.output.writeUnsigned(11);
            this.writeExpr(expr.getArray());
            this.writeExpr(expr.getIndex());
            this.output.writeUnsigned(expr.getType().ordinal());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(UnwrapArrayExpr expr) {
         try {
            this.output.writeUnsigned(12);
            this.output.writeUnsigned(expr.getElementType().ordinal());
            this.writeExpr(expr.getArray());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(InvocationExpr expr) {
         try {
            switch (expr.getType()) {
               case CONSTRUCTOR:
                  this.output.writeUnsigned(13);
                  break;
               case STATIC:
                  this.output.writeUnsigned(14);
                  break;
               case SPECIAL:
                  this.output.writeUnsigned(15);
                  break;
               case DYNAMIC:
                  this.output.writeUnsigned(16);
            }

            this.output.writeUnsigned(AstIO.this.symbolTable.lookup(expr.getMethod().getClassName()));
            this.output.writeUnsigned(AstIO.this.symbolTable.lookup(expr.getMethod().getDescriptor().toString()));
            this.output.writeUnsigned(expr.getArguments().size());

            for (int i = 0; i < expr.getArguments().size(); i++) {
               this.writeExpr(expr.getArguments().get(i));
            }
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(QualificationExpr expr) {
         try {
            this.output.writeUnsigned(expr.getQualified() == null ? 17 : 18);
            if (expr.getQualified() != null) {
               this.writeExpr(expr.getQualified());
            }

            this.output.writeUnsigned(AstIO.this.symbolTable.lookup(expr.getField().getClassName()));
            this.output.writeUnsigned(AstIO.this.symbolTable.lookup(expr.getField().getFieldName()));
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(NewExpr expr) {
         try {
            this.output.writeUnsigned(19);
            this.output.writeUnsigned(AstIO.this.symbolTable.lookup(expr.getConstructedClass()));
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(NewArrayExpr expr) {
         try {
            this.output.writeUnsigned(20);
            this.writeExpr(expr.getLength());
            this.output.writeUnsigned(AstIO.this.symbolTable.lookup(expr.getType().toString()));
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(NewMultiArrayExpr expr) {
         try {
            this.output.writeUnsigned(21);
            this.output.writeUnsigned(expr.getDimensions().size());

            for (Expr dimension : expr.getDimensions()) {
               this.writeExpr(dimension);
            }

            this.output.writeUnsigned(AstIO.this.symbolTable.lookup(expr.getType().toString()));
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(ArrayFromDataExpr expr) {
         try {
            this.output.writeUnsigned(28);
            this.output.writeUnsigned(AstIO.this.symbolTable.lookup(expr.getType().toString()));
            this.output.writeUnsigned(expr.getData().size());

            for (Expr element : expr.getData()) {
               this.writeExpr(element);
            }
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(InstanceOfExpr expr) {
         try {
            this.output.writeUnsigned(22);
            this.writeExpr(expr.getExpr());
            this.output.writeUnsigned(AstIO.this.symbolTable.lookup(expr.getType().toString()));
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(CastExpr expr) {
         try {
            this.output.writeUnsigned(23);
            this.output.writeUnsigned(AstIO.this.symbolTable.lookup(expr.getTarget().toString()));
            this.writeExpr(expr.getValue());
            this.output.writeUnsigned(expr.isWeak() ? 1 : 0);
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(PrimitiveCastExpr expr) {
         try {
            this.output.writeUnsigned(24);
            this.output.writeUnsigned(expr.getSource().ordinal());
            this.output.writeUnsigned(expr.getTarget().ordinal());
            this.writeExpr(expr.getValue());
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void visit(BoundCheckExpr expr) {
         try {
            this.output.writeUnsigned(expr.getArray() == null ? 27 : (!expr.isLower() ? 26 : 25));
            this.writeExpr(expr.getIndex());
            if (expr.getArray() != null) {
               this.writeExpr(expr.getArray());
            }
         } catch (IOException e) {
            throw new AstIO.IOExceptionWrapper(e);
         }
      }
   }
}
