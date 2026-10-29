package org.teavm.model.text;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.teavm.model.BasicBlock;
import org.teavm.model.FieldReference;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.TextLocation;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BoundCheckInstruction;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.CastIntegerDirection;
import org.teavm.model.instructions.CastIntegerInstruction;
import org.teavm.model.instructions.CastNumberInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.EmptyInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.IntegerSubtype;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NegateInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.PutElementInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.instructions.SwitchInstruction;
import org.teavm.model.instructions.SwitchTableEntry;
import org.teavm.model.instructions.UnwrapArrayInstruction;

public class ListingParser {
   private Program program;
   private ListingLexer lexer;
   private Map<String, Variable> variableMap;
   private Map<String, BasicBlock> blockMap;
   private Map<String, Integer> blockFirstOccurrence;
   private Set<String> declaredBlocks = new HashSet<>();
   private List<BasicBlock> orderedBlocks = new ArrayList<>();
   private TextLocation currentLocation;
   private BasicBlock currentBlock;

   public Program parse(Reader reader) throws IOException, ListingParseException {
      try {
         this.program = new Program();
         this.lexer = new ListingLexer(reader);
         this.variableMap = new HashMap<>();
         this.blockMap = new HashMap<>();
         this.blockFirstOccurrence = new LinkedHashMap<>();
         this.lexer.nextToken();
         this.parsePrologue();

         do {
            this.parseBasicBlock();
         } while (this.lexer.getToken() != ListingToken.EOF);

         if (!this.blockFirstOccurrence.isEmpty()) {
            String blockName = this.blockFirstOccurrence.keySet().iterator().next();
            int blockIndex = this.blockFirstOccurrence.get(blockName);
            throw new ListingParseException("Block not defined: " + blockName, blockIndex);
         } else {
            this.program.pack();
            this.program.rearrangeBasicBlocks(this.orderedBlocks);
            return this.program;
         }
      } finally {
         this.program = null;
         this.lexer = null;
         this.variableMap = null;
         this.blockMap = null;
         this.blockFirstOccurrence = null;
      }
   }

   private void parsePrologue() throws IOException, ListingParseException {
      while (true) {
         if (this.lexer.getToken() == ListingToken.EOL) {
            this.lexer.nextToken();
         } else {
            if (this.lexer.getToken() != ListingToken.IDENTIFIER || !this.lexer.getTokenValue().equals("var")) {
               return;
            }

            this.lexer.nextToken();
            this.expect(ListingToken.VARIABLE);
            String variableName = (String)this.lexer.getTokenValue();
            if (this.variableMap.containsKey(variableName)) {
               throw new ListingParseException("Variable " + variableName + " already declared", this.lexer.getTokenStart());
            }

            this.lexer.nextToken();
            this.expectKeyword("as");
            this.expect(ListingToken.IDENTIFIER);
            String variableAlias = (String)this.lexer.getTokenValue();
            this.lexer.nextToken();
            this.expectEofOrEol();
            Variable variable = this.program.createVariable();
            variable.setLabel(variableName);
            variable.setDebugName(variableAlias);
            this.variableMap.put(variableName, variable);
         }
      }
   }

   private void parseBasicBlock() throws IOException, ListingParseException {
      this.expect(ListingToken.LABEL);
      String label = (String)this.lexer.getTokenValue();
      if (!this.declaredBlocks.add(label)) {
         throw new ListingParseException("Block with label " + label + " already exists", this.lexer.getTokenStart());
      }

      this.blockFirstOccurrence.remove(label);
      this.lexer.nextToken();
      this.expect(ListingToken.EOL);

      while (this.lexer.getToken() == ListingToken.EOL) {
         this.lexer.nextToken();
      }

      this.currentBlock = this.blockMap.computeIfAbsent(label, k -> {
         BasicBlock b = this.program.createBasicBlock();
         b.setLabel(k);
         return b;
      });
      this.orderedBlocks.add(this.currentBlock);
      this.currentLocation = null;

      do {
         this.parseInstruction();
      } while (this.lexer.getToken() != ListingToken.LABEL && this.lexer.getToken() != ListingToken.EOF);

      while (this.lexer.getToken() == ListingToken.EOL) {
         this.lexer.nextToken();
      }
   }

   private void parseInstruction() throws IOException, ListingParseException {
      label85:
      switch (this.lexer.getToken()) {
         case IDENTIFIER:
            String id = (String)this.lexer.getTokenValue();
            switch (id) {
               case "at":
                  this.lexer.nextToken();
                  this.parseLocation();
                  break label85;
               case "nop": {
                  EmptyInstruction insn = new EmptyInstruction();
                  this.addInstruction(insn);
                  this.lexer.nextToken();
                  break label85;
               }
               case "goto": {
                  this.lexer.nextToken();
                  BasicBlock target = this.expectBlock();
                  JumpInstruction insn = new JumpInstruction();
                  insn.setTarget(target);
                  this.addInstruction(insn);
                  break label85;
               }
               case "return": {
                  this.lexer.nextToken();
                  ExitInstruction insn = new ExitInstruction();
                  if (this.lexer.getToken() == ListingToken.VARIABLE) {
                     insn.setValueToReturn(this.expectVariable());
                  }

                  this.addInstruction(insn);
                  break label85;
               }
               case "throw": {
                  this.lexer.nextToken();
                  RaiseInstruction insn = new RaiseInstruction();
                  insn.setException(this.expectVariable());
                  this.addInstruction(insn);
                  break label85;
               }
               case "if":
                  this.lexer.nextToken();
                  this.parseIf();
                  break label85;
               case "invoke":
               case "invokeStatic":
               case "invokeVirtual":
                  this.parseInvoke(null);
                  break label85;
               case "initClass": {
                  this.lexer.nextToken();
                  InitClassInstruction insn = new InitClassInstruction();
                  this.expect(ListingToken.IDENTIFIER);
                  insn.setClassName((String)this.lexer.getTokenValue());
                  this.lexer.nextToken();
                  this.addInstruction(insn);
                  break label85;
               }
               case "monitorEnter": {
                  this.lexer.nextToken();
                  MonitorEnterInstruction insn = new MonitorEnterInstruction();
                  insn.setObjectRef(this.expectVariable());
                  this.addInstruction(insn);
                  break label85;
               }
               case "monitorExit": {
                  this.lexer.nextToken();
                  MonitorExitInstruction insn = new MonitorExitInstruction();
                  insn.setObjectRef(this.expectVariable());
                  this.addInstruction(insn);
                  break label85;
               }
               case "field":
                  this.lexer.nextToken();
                  this.parseFieldSet();
                  break label85;
               case "switch":
                  this.lexer.nextToken();
                  this.parseSwitch();
                  break label85;
               case "catch":
                  this.lexer.nextToken();
                  this.parseCatch();
                  break label85;
               default:
                  this.unexpected();
                  break label85;
            }
         case VARIABLE:
            Variable receiver = this.getVariable((String)this.lexer.getTokenValue());
            this.lexer.nextToken();
            switch (this.lexer.getToken()) {
               case ASSIGN:
                  this.lexer.nextToken();
                  this.parseAssignment(receiver);
                  break label85;
               case LEFT_SQUARE_BRACKET:
                  this.lexer.nextToken();
                  this.parseArrayAssignment(receiver);
                  break label85;
               default:
                  this.unexpected();
                  break label85;
            }
         default:
            this.unexpected();
      }

      this.expectEofOrEol();

      while (this.lexer.getToken() == ListingToken.EOL) {
         this.lexer.nextToken();
      }
   }

   private void parseLocation() throws IOException, ListingParseException {
      if (this.lexer.getToken() == ListingToken.IDENTIFIER) {
         if (this.lexer.getTokenValue().equals("unknown")) {
            this.lexer.nextToken();
            this.expectKeyword("location");
            this.currentLocation = null;
            return;
         }
      } else if (this.lexer.getToken() == ListingToken.STRING) {
         String fileName = (String)this.lexer.getTokenValue();
         this.lexer.nextToken();
         if (this.lexer.getToken() == ListingToken.INTEGER) {
            int lineNumber = (Integer)this.lexer.getTokenValue();
            this.lexer.nextToken();
            this.currentLocation = new TextLocation(fileName, lineNumber);
            return;
         }
      }

      throw new ListingParseException(
         "Unexpected token " + this.lexer.getToken() + ". Expected 'unknown location' or '<string> : <number>'", this.lexer.getTokenStart()
      );
   }

   private void parseAssignment(Variable receiver) throws IOException, ListingParseException {
      switch (this.lexer.getToken()) {
         case IDENTIFIER:
            String keyword = (String)this.lexer.getTokenValue();
            switch (keyword) {
               case "null": {
                  this.lexer.nextToken();
                  NullConstantInstruction insn = new NullConstantInstruction();
                  insn.setReceiver(receiver);
                  this.addInstruction(insn);
                  return;
               }
               case "phi":
                  this.lexer.nextToken();
                  this.parsePhi(receiver);
                  return;
               case "classOf":
                  this.lexer.nextToken();
                  this.parseClassLiteral(receiver);
                  return;
               case "invoke":
               case "invokeVirtual":
               case "invokeStatic":
                  this.parseInvoke(receiver);
                  return;
               case "cast":
                  this.parseCast(receiver);
                  return;
               case "new":
                  this.parseNew(receiver);
                  return;
               case "newArray":
                  this.parseNewArray(receiver);
                  return;
               case "nullCheck": {
                  this.lexer.nextToken();
                  NullCheckInstruction insn = new NullCheckInstruction();
                  insn.setReceiver(receiver);
                  insn.setValue(this.expectVariable());
                  this.addInstruction(insn);
                  return;
               }
               case "data": {
                  this.lexer.nextToken();
                  Variable value = this.expectVariable();
                  this.expectKeyword("as");
                  ArrayElementType type = this.expectArrayType();
                  UnwrapArrayInstruction insn = new UnwrapArrayInstruction(type);
                  insn.setArray(value);
                  insn.setReceiver(receiver);
                  this.addInstruction(insn);
                  return;
               }
               case "lengthOf": {
                  this.lexer.nextToken();
                  ArrayLengthInstruction insn = new ArrayLengthInstruction();
                  insn.setArray(this.expectVariable());
                  insn.setReceiver(receiver);
                  this.addInstruction(insn);
                  return;
               }
               case "clone": {
                  this.lexer.nextToken();
                  CloneArrayInstruction insn = new CloneArrayInstruction();
                  insn.setArray(this.expectVariable());
                  insn.setReceiver(receiver);
                  this.addInstruction(insn);
                  return;
               }
               case "field":
                  this.lexer.nextToken();
                  this.parseFieldGet(receiver);
                  return;
               case "exception":
                  this.lexer.nextToken();
                  if (this.currentBlock.instructionCount() > 0 || this.currentBlock.getExceptionVariable() != null) {
                     throw new ListingParseException("Exception can be read as a first instruction", this.lexer.getTokenStart());
                  }

                  this.currentBlock.setExceptionVariable(receiver);
                  return;
               case "boundCheck": {
                  this.lexer.nextToken();
                  BoundCheckInstruction insn = new BoundCheckInstruction();
                  insn.setReceiver(receiver);
                  insn.setIndex(this.expectVariable());
                  if (this.lexer.tryConsumeIdentifier("upper")) {
                     insn.setArray(this.expectVariable());
                  }

                  if (this.lexer.tryConsumeIdentifier("lower")) {
                     insn.setLower(true);
                  }

                  this.addInstruction(insn);
                  return;
               }
               default:
                  this.unexpected();
                  return;
            }
         case VARIABLE:
            Variable variable = this.getVariable((String)this.lexer.getTokenValue());
            this.lexer.nextToken();
            this.parseAssignmentVariable(receiver, variable);
            break;
         case INTEGER:
            this.parseIntConstant(receiver);
            break;
         case LONG:
            this.parseLongConstant(receiver);
            break;
         case FLOAT:
            this.parseFloatConstant(receiver);
            break;
         case DOUBLE:
            this.parseDoubleConstant(receiver);
            break;
         case STRING:
            this.parseStringConstant(receiver);
            break;
         case SUBTRACT:
            this.parseNegate(receiver);
            break;
         default:
            this.unexpected();
      }
   }

   private void parseAssignmentVariable(Variable receiver, Variable variable) throws IOException, ListingParseException {
      switch (this.lexer.getToken()) {
         case LEFT_SQUARE_BRACKET:
            this.parseSubscript(receiver, variable);
            break;
         case IDENTIFIER:
            switch ((String)this.lexer.getTokenValue()) {
               case "compareTo":
                  this.parseBinary(receiver, variable, BinaryOperation.COMPARE_GREATER);
                  return;
               case "instanceOf":
                  this.lexer.nextToken();
                  ValueType type = this.expectValueType();
                  IsInstanceInstruction insnx = new IsInstanceInstruction();
                  insnx.setValue(variable);
                  insnx.setReceiver(receiver);
                  insnx.setType(type);
                  this.addInstruction(insnx);
                  return;
               default:
                  this.unexpected();
                  return;
            }
         case VARIABLE:
         case INTEGER:
         case LONG:
         case FLOAT:
         case DOUBLE:
         case STRING:
         default:
            this.unexpected();
            break;
         case SUBTRACT:
            this.parseBinary(receiver, variable, BinaryOperation.SUBTRACT);
            break;
         case EOL:
         case EOF:
            AssignInstruction insn = new AssignInstruction();
            insn.setReceiver(receiver);
            insn.setAssignee(variable);
            this.addInstruction(insn);
            break;
         case ADD:
            this.parseBinary(receiver, variable, BinaryOperation.ADD);
            break;
         case MULTIPLY:
            this.parseBinary(receiver, variable, BinaryOperation.MULTIPLY);
            break;
         case DIVIDE:
            this.parseBinary(receiver, variable, BinaryOperation.DIVIDE);
            break;
         case REMAINDER:
            this.parseBinary(receiver, variable, BinaryOperation.MODULO);
            break;
         case AND:
            this.parseBinary(receiver, variable, BinaryOperation.AND);
            break;
         case OR:
            this.parseBinary(receiver, variable, BinaryOperation.OR);
            break;
         case XOR:
            this.parseBinary(receiver, variable, BinaryOperation.XOR);
            break;
         case SHIFT_LEFT:
            this.parseBinary(receiver, variable, BinaryOperation.SHIFT_LEFT);
            break;
         case SHIFT_RIGHT:
            this.parseBinary(receiver, variable, BinaryOperation.SHIFT_RIGHT);
            break;
         case SHIFT_RIGHT_UNSIGNED:
            this.parseBinary(receiver, variable, BinaryOperation.SHIFT_RIGHT_UNSIGNED);
      }
   }

   private void parseBinary(Variable receiver, Variable first, BinaryOperation operation) throws IOException, ListingParseException {
      this.lexer.nextToken();
      Variable second = this.expectVariable();
      this.expectKeyword("as");
      NumericOperandType type = this.expectNumericType();
      BinaryInstruction insn = new BinaryInstruction(operation, type);
      insn.setFirstOperand(first);
      insn.setSecondOperand(second);
      insn.setReceiver(receiver);
      this.addInstruction(insn);
   }

   private void parseSubscript(Variable receiver, Variable array) throws IOException, ListingParseException {
      this.lexer.nextToken();
      Variable index = this.expectVariable();
      this.expect(ListingToken.RIGHT_SQUARE_BRACKET);
      this.lexer.nextToken();
      this.expectKeyword("as");
      ArrayElementType type = this.expectArrayType();
      GetElementInstruction insn = new GetElementInstruction(type);
      insn.setReceiver(receiver);
      insn.setArray(array);
      insn.setIndex(index);
      this.addInstruction(insn);
   }

   private void parseArrayAssignment(Variable array) throws IOException, ListingParseException {
      Variable index = this.expectVariable();
      this.expect(ListingToken.RIGHT_SQUARE_BRACKET);
      this.lexer.nextToken();
      this.expect(ListingToken.ASSIGN);
      this.lexer.nextToken();
      Variable value = this.expectVariable();
      this.expectKeyword("as");
      ArrayElementType type = this.expectArrayType();
      PutElementInstruction insn = new PutElementInstruction(type);
      insn.setArray(array);
      insn.setIndex(index);
      insn.setValue(value);
      this.addInstruction(insn);
   }

   private void parsePhi(Variable receiver) throws IOException, ListingParseException {
      int phiStart = this.lexer.getIndex();
      Phi phi = new Phi();
      phi.setReceiver(receiver);

      while (true) {
         Incoming incoming = new Incoming();
         incoming.setValue(this.expectVariable());
         this.expectKeyword("from");
         incoming.setSource(this.expectBlock());
         phi.getIncomings().add(incoming);
         if (this.lexer.getToken() != ListingToken.COMMA) {
            if (this.currentBlock.instructionCount() <= 0 && this.currentBlock.getExceptionVariable() == null) {
               this.currentBlock.getPhis().add(phi);
               return;
            } else {
               throw new ListingParseException("Phi must be first instruction in block", phiStart);
            }
         }

         this.lexer.nextToken();
      }
   }

   private void parseClassLiteral(Variable receiver) throws IOException, ListingParseException {
      ValueType type = this.expectValueType();
      ClassConstantInstruction insn = new ClassConstantInstruction();
      insn.setReceiver(receiver);
      insn.setConstant(type);
      this.addInstruction(insn);
   }

   private void parseIntConstant(Variable receiver) throws IOException, ListingParseException {
      IntegerConstantInstruction insn = new IntegerConstantInstruction();
      insn.setReceiver(receiver);
      insn.setConstant((Integer)this.lexer.getTokenValue());
      this.lexer.nextToken();
      this.addInstruction(insn);
   }

   private void parseLongConstant(Variable receiver) throws IOException, ListingParseException {
      LongConstantInstruction insn = new LongConstantInstruction();
      insn.setReceiver(receiver);
      insn.setConstant((Long)this.lexer.getTokenValue());
      this.lexer.nextToken();
      this.addInstruction(insn);
   }

   private void parseFloatConstant(Variable receiver) throws IOException, ListingParseException {
      FloatConstantInstruction insn = new FloatConstantInstruction();
      insn.setReceiver(receiver);
      insn.setConstant((Float)this.lexer.getTokenValue());
      this.lexer.nextToken();
      this.addInstruction(insn);
   }

   private void parseDoubleConstant(Variable receiver) throws IOException, ListingParseException {
      DoubleConstantInstruction insn = new DoubleConstantInstruction();
      insn.setReceiver(receiver);
      insn.setConstant((Double)this.lexer.getTokenValue());
      this.lexer.nextToken();
      this.addInstruction(insn);
   }

   private void parseStringConstant(Variable receiver) throws IOException, ListingParseException {
      StringConstantInstruction insn = new StringConstantInstruction();
      insn.setReceiver(receiver);
      insn.setConstant((String)this.lexer.getTokenValue());
      this.lexer.nextToken();
      this.addInstruction(insn);
   }

   private void parseNegate(Variable receiver) throws IOException, ListingParseException {
      this.lexer.nextToken();
      Variable value = this.expectVariable();
      this.expectKeyword("as");
      NumericOperandType type = this.expectNumericType();
      NegateInstruction insn = new NegateInstruction(type);
      insn.setReceiver(receiver);
      insn.setOperand(value);
      this.addInstruction(insn);
   }

   private void parseInvoke(Variable receiver) throws IOException, ListingParseException {
      InvokeInstruction insn = new InvokeInstruction();
      insn.setReceiver(receiver);
      boolean hasInstance = true;
      switch ((String)this.lexer.getTokenValue()) {
         case "invoke":
            insn.setType(InvocationType.SPECIAL);
            break;
         case "invokeStatic":
            insn.setType(InvocationType.SPECIAL);
            hasInstance = false;
            break;
         case "invokeVirtual":
            insn.setType(InvocationType.VIRTUAL);
      }

      this.lexer.nextToken();
      this.expect(ListingToken.IDENTIFIER);
      MethodReference method = MethodReference.parseIfPossible((String)this.lexer.getTokenValue());
      if (method == null) {
         throw new ListingParseException("Unparseable method", this.lexer.getIndex());
      }

      insn.setMethod(method);
      this.lexer.nextToken();
      List<Variable> arguments = new ArrayList<>();
      if (this.lexer.getToken() == ListingToken.VARIABLE) {
         arguments.add(this.expectVariable());

         while (this.lexer.getToken() == ListingToken.COMMA) {
            this.lexer.nextToken();
            arguments.add(this.expectVariable());
         }
      }

      if (hasInstance) {
         if (arguments.isEmpty()) {
            throw new ListingParseException("This kind of invocation requires at least one argument", this.lexer.getIndex());
         }

         insn.setInstance(arguments.get(0));
         insn.setArguments(arguments.subList(1, arguments.size()).toArray(new Variable[0]));
      } else {
         insn.setArguments(arguments.toArray(new Variable[0]));
      }

      this.addInstruction(insn);
   }

   private void parseFieldSet() throws IOException, ListingParseException {
      FieldReference field = this.expectField();
      Variable instance = null;
      if (this.lexer.getToken() == ListingToken.VARIABLE) {
         instance = this.expectVariable();
      }

      this.expect(ListingToken.ASSIGN);
      this.lexer.nextToken();
      Variable value = this.expectVariable();
      this.expectKeyword("as");
      ValueType type = this.expectValueType();
      PutFieldInstruction insn = new PutFieldInstruction();
      insn.setValue(value);
      insn.setField(field);
      insn.setInstance(instance);
      insn.setFieldType(type);
      this.addInstruction(insn);
   }

   private void parseFieldGet(Variable receiver) throws IOException, ListingParseException {
      FieldReference field = this.expectField();
      Variable instance = null;
      if (this.lexer.getToken() == ListingToken.VARIABLE) {
         instance = this.expectVariable();
      }

      this.expectKeyword("as");
      ValueType type = this.expectValueType();
      GetFieldInstruction insn = new GetFieldInstruction();
      insn.setReceiver(receiver);
      insn.setInstance(instance);
      insn.setField(field);
      insn.setFieldType(type);
      this.addInstruction(insn);
   }

   private void parseSwitch() throws IOException, ListingParseException {
      SwitchInstruction insn = new SwitchInstruction();
      insn.setCondition(this.expectVariable());

      while (true) {
         this.expect(ListingToken.IDENTIFIER);
         switch ((String)this.lexer.getTokenValue()) {
            case "case":
               this.lexer.nextToken();
               SwitchTableEntry entry = new SwitchTableEntry();
               this.expect(ListingToken.INTEGER);
               entry.setCondition((Integer)this.lexer.getTokenValue());
               this.lexer.nextToken();
               this.expectKeyword("goto");
               entry.setTarget(this.expectBlock());
               insn.getEntries().add(entry);
               break;
            case "else":
               this.lexer.nextToken();
               this.expectKeyword("goto");
               insn.setDefaultTarget(this.expectBlock());
               this.addInstruction(insn);
               return;
         }
      }
   }

   private void parseCatch() throws IOException, ListingParseException {
      TryCatchBlock tryCatch = new TryCatchBlock();
      if (this.lexer.getToken() == ListingToken.IDENTIFIER && !this.lexer.getTokenValue().equals("goto")) {
         tryCatch.setExceptionType((String)this.lexer.getTokenValue());
         this.lexer.nextToken();
      }

      this.expectKeyword("goto");
      tryCatch.setHandler(this.expectBlock());
      this.currentBlock.getTryCatchBlocks().add(tryCatch);
   }

   private void parseCast(Variable receiver) throws IOException, ListingParseException {
      this.lexer.nextToken();
      Variable value = this.expectVariable();
      this.expect(ListingToken.IDENTIFIER);
      switch ((String)this.lexer.getTokenValue()) {
         case "from":
            this.parseNumericCast(receiver, value);
            break;
         case "to":
            this.parseObjectCast(receiver, value);
            break;
         default:
            this.unexpected();
      }
   }

   private void parseNumericCast(Variable receiver, Variable value) throws IOException, ListingParseException {
      this.lexer.nextToken();
      ListingParser.NumericTypeOrIntegerSubtype source = this.expectTypeOrIntegerSubtype();
      if (source.subtype != null) {
         CastIntegerInstruction insn = new CastIntegerInstruction(source.subtype, CastIntegerDirection.TO_INTEGER);
         this.expectKeyword("to");
         this.expectKeyword("int");
         insn.setReceiver(receiver);
         insn.setValue(value);
         this.addInstruction(insn);
      } else {
         this.expectKeyword("to");
         this.expect(ListingToken.IDENTIFIER);
         ListingParser.NumericTypeOrIntegerSubtype target = this.expectTypeOrIntegerSubtype();
         if (target.subtype != null) {
            if (source.type != NumericOperandType.INT) {
               throw new ListingParseException("Only int can be cast to " + target.subtype.name().toLowerCase(Locale.ROOT), this.lexer.getIndex());
            }

            CastIntegerInstruction insn = new CastIntegerInstruction(source.subtype, CastIntegerDirection.FROM_INTEGER);
            insn.setReceiver(receiver);
            insn.setValue(value);
            this.addInstruction(insn);
         } else {
            CastNumberInstruction insn = new CastNumberInstruction(source.type, target.type);
            insn.setReceiver(receiver);
            insn.setValue(value);
            this.addInstruction(insn);
         }
      }
   }

   private ListingParser.NumericTypeOrIntegerSubtype expectTypeOrIntegerSubtype() throws IOException, ListingParseException {
      IntegerSubtype subtype = null;
      NumericOperandType type = null;
      this.expect(ListingToken.IDENTIFIER);
      switch ((String)this.lexer.getTokenValue()) {
         case "byte":
            subtype = IntegerSubtype.BYTE;
            break;
         case "short":
            subtype = IntegerSubtype.SHORT;
            break;
         case "char":
            subtype = IntegerSubtype.CHAR;
            break;
         case "int":
            type = NumericOperandType.INT;
            break;
         case "long":
            type = NumericOperandType.LONG;
            break;
         case "float":
            type = NumericOperandType.FLOAT;
            break;
         case "double":
            type = NumericOperandType.DOUBLE;
            break;
         default:
            this.unexpected();
            return null;
      }

      this.lexer.nextToken();
      return new ListingParser.NumericTypeOrIntegerSubtype(type, subtype);
   }

   private void parseObjectCast(Variable receiver, Variable value) throws IOException, ListingParseException {
      this.lexer.nextToken();
      ValueType type = this.expectValueType();
      CastInstruction insn = new CastInstruction();
      insn.setReceiver(receiver);
      insn.setValue(value);
      insn.setTargetType(type);
      this.addInstruction(insn);
   }

   private void parseNew(Variable receiver) throws IOException, ListingParseException {
      this.lexer.nextToken();
      this.expect(ListingToken.IDENTIFIER);
      String type = (String)this.lexer.getTokenValue();
      this.lexer.nextToken();
      ConstructInstruction insn = new ConstructInstruction();
      insn.setReceiver(receiver);
      insn.setType(type);
      this.addInstruction(insn);
   }

   private void parseNewArray(Variable receiver) throws IOException, ListingParseException {
      this.lexer.nextToken();
      ValueType type = this.expectValueType();
      List<Variable> dimensions = new ArrayList<>();
      this.expect(ListingToken.LEFT_SQUARE_BRACKET);

      do {
         this.lexer.nextToken();
         dimensions.add(this.expectVariable());
      } while (this.lexer.getToken() == ListingToken.COMMA);

      this.expect(ListingToken.RIGHT_SQUARE_BRACKET);
      this.lexer.nextToken();
      if (dimensions.size() == 1) {
         ConstructArrayInstruction insn = new ConstructArrayInstruction();
         insn.setReceiver(receiver);
         insn.setItemType(type);
         insn.setSize(dimensions.get(0));
         this.addInstruction(insn);
      } else {
         ConstructMultiArrayInstruction insn = new ConstructMultiArrayInstruction();
         insn.setReceiver(receiver);
         insn.setItemType(type);
         insn.getDimensions().addAll(dimensions);
         this.addInstruction(insn);
      }
   }

   private void parseIf() throws IOException, ListingParseException {
      Variable first = this.expectVariable();
      BinaryBranchingCondition binaryCondition = null;
      int operationIndex = this.lexer.getIndex();
      ListingToken operationToken = this.lexer.getToken();

      BranchingCondition condition = switch (this.lexer.getToken()) {
         case EQUAL -> {
            binaryCondition = BinaryBranchingCondition.EQUAL;
            yield BranchingCondition.EQUAL;
         }
         case NOT_EQUAL -> {
            binaryCondition = BinaryBranchingCondition.NOT_EQUAL;
            yield BranchingCondition.NOT_EQUAL;
         }
         case REFERENCE_EQUAL -> {
            binaryCondition = BinaryBranchingCondition.REFERENCE_EQUAL;
            yield BranchingCondition.NULL;
         }
         case REFERENCE_NOT_EQUAL -> {
            binaryCondition = BinaryBranchingCondition.REFERENCE_NOT_EQUAL;
            yield BranchingCondition.NOT_NULL;
         }
         case LESS -> BranchingCondition.LESS;
         case LESS_OR_EQUAL -> BranchingCondition.LESS_OR_EQUAL;
         case GREATER -> BranchingCondition.GREATER;
         case GREATER_OR_EQUAL -> BranchingCondition.GREATER_OR_EQUAL;
         default -> throw new ListingParseException("Unexpected token" + this.lexer.getToken() + ". Expected comparison operator", this.lexer.getTokenStart());
      };
      this.lexer.nextToken();
      Variable second = null;
      if (this.lexer.getToken() == ListingToken.VARIABLE) {
         second = this.expectVariable();
      } else if (condition != BranchingCondition.NULL && condition != BranchingCondition.NOT_NULL) {
         this.expect(ListingToken.INTEGER);
         if (!this.lexer.getTokenValue().equals(0)) {
            throw new ListingParseException("Only comparison to 0 is supported", this.lexer.getTokenStart());
         }

         this.lexer.nextToken();
      } else {
         this.expectKeyword("null");
      }

      this.expectKeyword("then");
      this.expectKeyword("goto");
      BasicBlock consequent = this.expectBlock();
      this.expectKeyword("else");
      this.expectKeyword("goto");
      BasicBlock alternative = this.expectBlock();
      if (second != null) {
         if (binaryCondition == null) {
            throw new ListingParseException("Unsupported binary operation: " + operationToken, operationIndex);
         }

         BinaryBranchingInstruction insn = new BinaryBranchingInstruction(binaryCondition);
         insn.setFirstOperand(first);
         insn.setSecondOperand(second);
         insn.setConsequent(consequent);
         insn.setAlternative(alternative);
         this.addInstruction(insn);
      } else {
         BranchingInstruction insn = new BranchingInstruction(condition);
         insn.setOperand(first);
         insn.setConsequent(consequent);
         insn.setAlternative(alternative);
         this.addInstruction(insn);
      }
   }

   private ArrayElementType expectArrayType() throws IOException, ListingParseException {
      this.expect(ListingToken.IDENTIFIER);

      ArrayElementType type = switch ((String)this.lexer.getTokenValue()) {
         case "char" -> ArrayElementType.CHAR;
         case "byte" -> ArrayElementType.BYTE;
         case "short" -> ArrayElementType.SHORT;
         case "int" -> ArrayElementType.INT;
         case "long" -> ArrayElementType.LONG;
         case "float" -> ArrayElementType.FLOAT;
         case "double" -> ArrayElementType.DOUBLE;
         case "object" -> ArrayElementType.OBJECT;
         default -> throw new ListingParseException("Unknown array type: " + this.lexer.getTokenValue(), this.lexer.getTokenStart());
      };
      this.lexer.nextToken();
      return type;
   }

   private NumericOperandType expectNumericType() throws IOException, ListingParseException {
      this.expect(ListingToken.IDENTIFIER);

      NumericOperandType type = switch ((String)this.lexer.getTokenValue()) {
         case "int" -> NumericOperandType.INT;
         case "long" -> NumericOperandType.LONG;
         case "float" -> NumericOperandType.FLOAT;
         case "double" -> NumericOperandType.DOUBLE;
         default -> throw new ListingParseException("Unknown numeric type: " + this.lexer.getTokenValue(), this.lexer.getTokenStart());
      };
      this.lexer.nextToken();
      return type;
   }

   private ValueType expectValueType() throws IOException, ListingParseException {
      this.expect(ListingToken.IDENTIFIER);
      ValueType type = ValueType.parseIfPossible((String)this.lexer.getTokenValue());
      if (type == null) {
         throw new ListingParseException("Unparseable type", this.lexer.getTokenStart());
      }

      this.lexer.nextToken();
      return type;
   }

   private FieldReference expectField() throws IOException, ListingParseException {
      this.expect(ListingToken.IDENTIFIER);
      String s = (String)this.lexer.getTokenValue();
      int index = s.lastIndexOf(46);
      if (index < 0) {
         throw new ListingParseException("Unparseable field", this.lexer.getTokenStart());
      }

      this.lexer.nextToken();
      return new FieldReference(s.substring(0, index), s.substring(index + 1));
   }

   private Variable expectVariable() throws IOException, ListingParseException {
      this.expect(ListingToken.VARIABLE);
      String variableName = (String)this.lexer.getTokenValue();
      Variable variable = this.getVariable(variableName);
      this.lexer.nextToken();
      return variable;
   }

   private Variable getVariable(String name) {
      return this.variableMap.computeIfAbsent(name, k -> {
         Variable variable = this.program.createVariable();
         variable.setLabel(k);
         return variable;
      });
   }

   private BasicBlock expectBlock() throws IOException, ListingParseException {
      this.expect(ListingToken.LABEL);
      String blockName = (String)this.lexer.getTokenValue();
      BasicBlock block = this.getBlock(blockName);
      this.lexer.nextToken();
      return block;
   }

   private BasicBlock getBlock(String name) {
      return this.blockMap.computeIfAbsent(name, k -> {
         BasicBlock block = this.program.createBasicBlock();
         block.setLabel(k);
         this.blockFirstOccurrence.put(k, this.lexer.getTokenStart());
         return block;
      });
   }

   private void expect(ListingToken expected) throws IOException, ListingParseException {
      if (this.lexer.getToken() != expected) {
         throw new ListingParseException("Unexpected token " + this.lexer.getToken() + ". Expected " + expected, this.lexer.getTokenStart());
      }
   }

   private void expectEofOrEol() throws IOException, ListingParseException {
      if (this.lexer.getToken() != ListingToken.EOL && this.lexer.getToken() != ListingToken.EOF) {
         throw new ListingParseException("Unexpected token " + this.lexer.getToken() + ". Expected new line", this.lexer.getTokenStart());
      }

      if (this.lexer.getToken() != ListingToken.EOF) {
         this.lexer.nextToken();
      }
   }

   private String expectKeyword(String expected) throws IOException, ListingParseException {
      if (this.lexer.getToken() == ListingToken.IDENTIFIER && this.lexer.getTokenValue().equals(expected)) {
         String value = (String)this.lexer.getTokenValue();
         this.lexer.nextToken();
         return value;
      } else {
         throw new ListingParseException("Unexpected token " + this.lexer.getToken() + ". Expected " + expected, this.lexer.getTokenStart());
      }
   }

   private void unexpected() throws IOException, ListingParseException {
      throw new ListingParseException("Unexpected token " + this.lexer.getToken(), this.lexer.getTokenStart());
   }

   private void addInstruction(Instruction instruction) throws ListingParseException {
      instruction.setLocation(this.currentLocation);
      this.currentBlock.add(instruction);
   }

   private static class NumericTypeOrIntegerSubtype {
      NumericOperandType type;
      IntegerSubtype subtype;

      NumericTypeOrIntegerSubtype(NumericOperandType type, IntegerSubtype subtype) {
         this.type = type;
         this.subtype = subtype;
      }
   }
}
