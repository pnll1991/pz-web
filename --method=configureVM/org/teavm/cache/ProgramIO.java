package org.teavm.cache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import org.teavm.model.BasicBlock;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.FieldReference;
import org.teavm.model.Incoming;
import org.teavm.model.IncomingReader;
import org.teavm.model.InliningInfo;
import org.teavm.model.Instruction;
import org.teavm.model.InvokeDynamicInstruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHandle;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.PhiReader;
import org.teavm.model.Program;
import org.teavm.model.ProgramReader;
import org.teavm.model.ReferenceCache;
import org.teavm.model.RuntimeConstant;
import org.teavm.model.TextLocation;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.TryCatchBlockReader;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.VariableReader;
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
import org.teavm.model.instructions.InstructionReader;
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
import org.teavm.model.instructions.SwitchTableEntryReader;
import org.teavm.model.instructions.UnwrapArrayInstruction;
import org.teavm.model.util.ModelUtils;

public class ProgramIO {
   private SymbolTable symbolTable;
   private SymbolTable fileTable;
   private SymbolTable variableTable;
   private ReferenceCache referenceCache;
   private AnnotationIO annotationIO;
   private static BinaryOperation[] binaryOperations = BinaryOperation.values();
   private static NumericOperandType[] numericOperandTypes = NumericOperandType.values();
   private static IntegerSubtype[] integerSubtypes = IntegerSubtype.values();
   private static CastIntegerDirection[] castIntegerDirections = CastIntegerDirection.values();
   private static BranchingCondition[] branchingConditions = BranchingCondition.values();
   private static BinaryBranchingCondition[] binaryBranchingConditions = BinaryBranchingCondition.values();
   private static ArrayElementType[] arrayElementTypes = ArrayElementType.values();

   public ProgramIO(ReferenceCache referenceCache, SymbolTable symbolTable, SymbolTable fileTable, SymbolTable variableTable) {
      this.referenceCache = referenceCache;
      this.symbolTable = symbolTable;
      this.fileTable = fileTable;
      this.variableTable = variableTable;
      this.annotationIO = new AnnotationIO(referenceCache, symbolTable);
   }

   public void write(ProgramReader program, OutputStream output) throws IOException {
      this.write(program, new VarDataOutput(output));
   }

   public void write(ProgramReader program, VarDataOutput data) throws IOException {
      data.writeUnsigned(program.variableCount());
      data.writeUnsigned(program.basicBlockCount());

      for (int i = 0; i < program.variableCount(); i++) {
         VariableReader var = program.variableAt(i);
         data.writeUnsigned(var.getRegister());
         data.writeUnsigned(var.getDebugName() != null ? this.variableTable.lookup(var.getDebugName()) + 1 : 0);
      }

      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlockReader basicBlock = program.basicBlockAt(i);
         data.writeUnsigned(basicBlock.getExceptionVariable() != null ? basicBlock.getExceptionVariable().getIndex() + 1 : 0);
         data.writeUnsigned(basicBlock.readPhis().size());
         data.writeUnsigned(basicBlock.readTryCatchBlocks().size());

         for (PhiReader phi : basicBlock.readPhis()) {
            data.writeUnsigned(phi.getReceiver().getIndex());
            data.writeUnsigned(phi.readIncomings().size());

            for (IncomingReader incoming : phi.readIncomings()) {
               data.writeUnsigned(incoming.getSource().getIndex());
               data.writeUnsigned(incoming.getValue().getIndex());
            }
         }

         for (TryCatchBlockReader tryCatch : basicBlock.readTryCatchBlocks()) {
            data.writeUnsigned(tryCatch.getExceptionType() != null ? this.symbolTable.lookup(tryCatch.getExceptionType()) + 1 : 0);
            data.writeUnsigned(tryCatch.getHandler().getIndex());
         }

         ProgramIO.InstructionWriter insnWriter = new ProgramIO.InstructionWriter(data);

         try {
            basicBlock.readAllInstructions(insnWriter);
         } catch (ProgramIO.IOExceptionWrapper e) {
            throw (IOException)e.getCause();
         }

         data.writeUnsigned(0);
      }

      this.annotationIO.writeAnnotations(data, program.getAnnotations());
   }

   public Program read(InputStream input) throws IOException {
      return this.read(new VarDataInput(input));
   }

   public Program read(VarDataInput data) throws IOException {
      Program program = new Program();
      int varCount = data.readUnsigned();
      int basicBlockCount = data.readUnsigned();

      for (int i = 0; i < varCount; i++) {
         Variable var = program.createVariable();
         var.setRegister(data.readUnsigned());
         int nameIndex = data.readUnsigned();
         var.setDebugName(nameIndex != 0 ? this.referenceCache.getCached(this.variableTable.at(nameIndex - 1)) : null);
      }

      for (int i = 0; i < basicBlockCount; i++) {
         program.createBasicBlock();
      }

      label76:
      for (int i = 0; i < basicBlockCount; i++) {
         BasicBlock block = program.basicBlockAt(i);
         int varIndex = data.readUnsigned();
         if (varIndex > 0) {
            block.setExceptionVariable(program.variableAt(varIndex - 1));
         }

         int phiCount = data.readUnsigned();
         int tryCatchCount = data.readUnsigned();

         for (int j = 0; j < phiCount; j++) {
            Phi phi = new Phi();
            phi.setReceiver(program.variableAt(data.readUnsigned()));
            int incomingCount = data.readUnsigned();

            for (int k = 0; k < incomingCount; k++) {
               Incoming incoming = new Incoming();
               incoming.setSource(program.basicBlockAt(data.readUnsigned()));
               incoming.setValue(program.variableAt(data.readUnsigned()));
               phi.getIncomings().add(incoming);
            }

            block.getPhis().add(phi);
         }

         for (int j = 0; j < tryCatchCount; j++) {
            TryCatchBlock tryCatch = new TryCatchBlock();
            int typeIndex = data.readUnsigned();
            if (typeIndex > 0) {
               tryCatch.setExceptionType(this.symbolTable.at(typeIndex - 1));
            }

            tryCatch.setHandler(program.basicBlockAt(data.readUnsigned()));
            block.getTryCatchBlocks().add(tryCatch);
         }

         InliningInfo inliningInfo = null;
         TextLocation location = TextLocation.EMPTY;

         while (true) {
            int insnType = data.readUnsigned();
            switch (insnType) {
               case 0:
                  continue label76;
               case 1:
                  location = new TextLocation(null, -1, inliningInfo);
                  break;
               case 2: {
                  String file = this.fileTable.at(data.readUnsigned());
                  int line = data.readUnsigned();
                  location = new TextLocation(file, line, inliningInfo);
                  break;
               }
               case 125:
                  String className = this.symbolTable.at(data.readUnsigned());
                  MethodDescriptor methodDescriptor = this.parseMethodDescriptor(this.symbolTable.at(data.readUnsigned()));
                  inliningInfo = new InliningInfo(
                     this.createMethodReference(className, methodDescriptor), location.getFileName(), location.getLine(), inliningInfo
                  );
                  location = new TextLocation(null, -1, inliningInfo);
                  break;
               case 126:
                  location = new TextLocation(inliningInfo.getFileName(), inliningInfo.getLine());
                  inliningInfo = inliningInfo.getParent();
                  break;
               case 127: {
                  int line = location.getLine() + data.readSigned();
                  location = new TextLocation(location.getFileName(), line, inliningInfo);
                  break;
               }
               default:
                  Instruction insn = this.readInstruction(insnType, program, data);
                  insn.setLocation(location);
                  block.add(insn);
            }
         }
      }

      ModelUtils.copyAnnotations(this.annotationIO.readAnnotations(data), program.getAnnotations());
      return program;
   }

   private Instruction readInstruction(int insnType, Program program, VarDataInput input) throws IOException {
      switch (insnType) {
         case 3:
            return new EmptyInstruction();
         case 4: {
            ClassConstantInstruction insn = new ClassConstantInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setConstant(this.parseValueType(this.symbolTable.at(input.readUnsigned())));
            return insn;
         }
         case 5: {
            NullConstantInstruction insn = new NullConstantInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 6: {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setConstant(input.readSigned());
            return insn;
         }
         case 7: {
            LongConstantInstruction insn = new LongConstantInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setConstant(input.readSignedLong());
            return insn;
         }
         case 8: {
            FloatConstantInstruction insn = new FloatConstantInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setConstant(input.readFloat());
            return insn;
         }
         case 9: {
            DoubleConstantInstruction insn = new DoubleConstantInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setConstant(input.readDouble());
            return insn;
         }
         case 10: {
            StringConstantInstruction insn = new StringConstantInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setConstant(input.read());
            return insn;
         }
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
         case 23: {
            Variable receiver = program.variableAt(input.readUnsigned());
            BinaryOperation operation = binaryOperations[insnType - 11];
            NumericOperandType operandType = numericOperandTypes[input.readUnsigned()];
            BinaryInstruction insn = new BinaryInstruction(operation, operandType);
            insn.setReceiver(receiver);
            insn.setFirstOperand(program.variableAt(input.readUnsigned()));
            insn.setSecondOperand(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 24: {
            Variable receiver = program.variableAt(input.readUnsigned());
            NumericOperandType operandType = numericOperandTypes[input.readUnsigned()];
            NegateInstruction insn = new NegateInstruction(operandType);
            insn.setReceiver(receiver);
            insn.setOperand(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 25: {
            AssignInstruction insn = new AssignInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setAssignee(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 26: {
            CastInstruction insn = new CastInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setTargetType(this.parseValueType(this.symbolTable.at(input.readUnsigned())));
            insn.setValue(program.variableAt(input.readUnsigned()));
            insn.setWeak(input.readUnsigned() != 0);
            return insn;
         }
         case 27: {
            Variable receiver = program.variableAt(input.readUnsigned());
            int types = input.readUnsigned();
            NumericOperandType sourceType = numericOperandTypes[types & 3];
            NumericOperandType targetType = numericOperandTypes[types >> 2];
            CastNumberInstruction insn = new CastNumberInstruction(sourceType, targetType);
            insn.setReceiver(receiver);
            insn.setValue(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 28: {
            Variable receiver = program.variableAt(input.readUnsigned());
            int types = input.readUnsigned();
            CastIntegerDirection direction = castIntegerDirections[types & 1];
            IntegerSubtype targetType = integerSubtypes[types >> 1];
            CastIntegerInstruction insn = new CastIntegerInstruction(targetType, direction);
            insn.setReceiver(receiver);
            insn.setValue(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 29:
         case 30:
         case 31:
         case 32:
         case 33:
         case 34:
         case 35:
         case 36: {
            BranchingInstruction insn = new BranchingInstruction(branchingConditions[insnType - 29]);
            insn.setOperand(program.variableAt(input.readUnsigned()));
            insn.setConsequent(program.basicBlockAt(input.readUnsigned()));
            insn.setAlternative(program.basicBlockAt(input.readUnsigned()));
            return insn;
         }
         case 37:
         case 38:
         case 39:
         case 40: {
            BinaryBranchingCondition cond = binaryBranchingConditions[insnType - 37];
            BinaryBranchingInstruction insn = new BinaryBranchingInstruction(cond);
            insn.setFirstOperand(program.variableAt(input.readUnsigned()));
            insn.setSecondOperand(program.variableAt(input.readUnsigned()));
            insn.setConsequent(program.basicBlockAt(input.readUnsigned()));
            insn.setAlternative(program.basicBlockAt(input.readUnsigned()));
            return insn;
         }
         case 41: {
            JumpInstruction insn = new JumpInstruction();
            insn.setTarget(program.basicBlockAt(input.readUnsigned()));
            return insn;
         }
         case 42: {
            SwitchInstruction insn = new SwitchInstruction();
            insn.setCondition(program.variableAt(input.readUnsigned()));
            insn.setDefaultTarget(program.basicBlockAt(input.readUnsigned()));
            int entryCount = input.readUnsigned();

            for (int i = 0; i < entryCount; i++) {
               SwitchTableEntry entry = new SwitchTableEntry();
               entry.setCondition(input.readSigned());
               entry.setTarget(program.basicBlockAt(input.readUnsigned()));
               insn.getEntries().add(entry);
            }

            return insn;
         }
         case 43: {
            ExitInstruction insn = new ExitInstruction();
            insn.setValueToReturn(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 44:
            return new ExitInstruction();
         case 45: {
            RaiseInstruction insn = new RaiseInstruction();
            insn.setException(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 46: {
            ConstructArrayInstruction insn = new ConstructArrayInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setItemType(this.parseValueType(this.symbolTable.at(input.readUnsigned())));
            insn.setSize(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 47: {
            ConstructInstruction insn = new ConstructInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setType(this.symbolTable.at(input.readUnsigned()));
            return insn;
         }
         case 48: {
            ConstructMultiArrayInstruction insn = new ConstructMultiArrayInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setItemType(this.parseValueType(this.symbolTable.at(input.readUnsigned())));
            int dimensionCount = input.readUnsigned();

            for (int i = 0; i < dimensionCount; i++) {
               insn.getDimensions().add(program.variableAt(input.readUnsigned()));
            }

            return insn;
         }
         case 49: {
            GetFieldInstruction insn = new GetFieldInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setInstance(program.variableAt(input.readUnsigned()));
            String className = this.symbolTable.at(input.readUnsigned());
            String fieldName = this.symbolTable.at(input.readUnsigned());
            insn.setField(new FieldReference(className, fieldName));
            insn.setFieldType(this.parseValueType(this.symbolTable.at(input.readUnsigned())));
            return insn;
         }
         case 50: {
            GetFieldInstruction insn = new GetFieldInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            String className = this.symbolTable.at(input.readUnsigned());
            String fieldName = this.symbolTable.at(input.readUnsigned());
            insn.setField(new FieldReference(className, fieldName));
            insn.setFieldType(this.parseValueType(this.symbolTable.at(input.readUnsigned())));
            return insn;
         }
         case 51: {
            PutFieldInstruction insn = new PutFieldInstruction();
            insn.setInstance(program.variableAt(input.readUnsigned()));
            String className = this.symbolTable.at(input.readUnsigned());
            String fieldName = this.symbolTable.at(input.readUnsigned());
            ValueType type = this.parseValueType(this.symbolTable.at(input.readUnsigned()));
            insn.setField(new FieldReference(className, fieldName));
            insn.setValue(program.variableAt(input.readUnsigned()));
            insn.setFieldType(type);
            return insn;
         }
         case 52: {
            PutFieldInstruction insn = new PutFieldInstruction();
            String className = this.symbolTable.at(input.readUnsigned());
            String fieldName = this.symbolTable.at(input.readUnsigned());
            ValueType type = this.parseValueType(this.symbolTable.at(input.readUnsigned()));
            insn.setField(new FieldReference(className, fieldName));
            insn.setValue(program.variableAt(input.readUnsigned()));
            insn.setFieldType(type);
            return insn;
         }
         case 53: {
            ArrayLengthInstruction insn = new ArrayLengthInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setArray(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 54: {
            CloneArrayInstruction insn = new CloneArrayInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setArray(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 55:
         case 56:
         case 57:
         case 58:
         case 59:
         case 60:
         case 61:
         case 62: {
            UnwrapArrayInstruction insn = new UnwrapArrayInstruction(arrayElementTypes[insnType - 55]);
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setArray(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 63:
         case 64:
         case 65:
         case 66:
         case 67:
         case 68:
         case 69:
         case 70: {
            GetElementInstruction insn = new GetElementInstruction(arrayElementTypes[insnType - 63]);
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setArray(program.variableAt(input.readUnsigned()));
            insn.setIndex(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 71:
         case 72:
         case 73:
         case 74:
         case 75:
         case 76:
         case 77:
         case 78: {
            PutElementInstruction insn = new PutElementInstruction(arrayElementTypes[insnType - 71]);
            insn.setArray(program.variableAt(input.readUnsigned()));
            insn.setIndex(program.variableAt(input.readUnsigned()));
            insn.setValue(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 79: {
            InvokeInstruction insn = new InvokeInstruction();
            insn.setType(InvocationType.SPECIAL);
            int receiverIndex = input.readUnsigned();
            insn.setReceiver(receiverIndex > 0 ? program.variableAt(receiverIndex - 1) : null);
            String className = this.symbolTable.at(input.readUnsigned());
            MethodDescriptor methodDesc = this.parseMethodDescriptor(this.symbolTable.at(input.readUnsigned()));
            insn.setMethod(this.createMethodReference(className, methodDesc));
            int paramCount = insn.getMethod().getDescriptor().parameterCount();
            Variable[] arguments = new Variable[paramCount];

            for (int i = 0; i < paramCount; i++) {
               arguments[i] = program.variableAt(input.readUnsigned());
            }

            insn.setArguments(arguments);
            return insn;
         }
         case 80: {
            InvokeInstruction insn = new InvokeInstruction();
            insn.setType(InvocationType.SPECIAL);
            int receiverIndex = input.readUnsigned();
            insn.setReceiver(receiverIndex > 0 ? program.variableAt(receiverIndex - 1) : null);
            insn.setInstance(program.variableAt(input.readUnsigned()));
            String className = this.symbolTable.at(input.readUnsigned());
            MethodDescriptor methodDesc = this.parseMethodDescriptor(this.symbolTable.at(input.readUnsigned()));
            insn.setMethod(this.createMethodReference(className, methodDesc));
            int paramCount = insn.getMethod().getDescriptor().parameterCount();
            Variable[] arguments = new Variable[paramCount];

            for (int i = 0; i < paramCount; i++) {
               arguments[i] = program.variableAt(input.readUnsigned());
            }

            insn.setArguments(arguments);
            return insn;
         }
         case 81: {
            InvokeInstruction insn = new InvokeInstruction();
            insn.setType(InvocationType.VIRTUAL);
            int receiverIndex = input.readUnsigned();
            insn.setReceiver(receiverIndex > 0 ? program.variableAt(receiverIndex - 1) : null);
            insn.setInstance(program.variableAt(input.readUnsigned()));
            String className = this.symbolTable.at(input.readUnsigned());
            MethodDescriptor methodDesc = this.parseMethodDescriptor(this.symbolTable.at(input.readUnsigned()));
            insn.setMethod(this.createMethodReference(className, methodDesc));
            int paramCount = insn.getMethod().getDescriptor().parameterCount();
            Variable[] arguments = new Variable[paramCount];

            for (int i = 0; i < paramCount; i++) {
               arguments[i] = program.variableAt(input.readUnsigned());
            }

            insn.setArguments(arguments);
            return insn;
         }
         case 82: {
            InvokeDynamicInstruction insn = new InvokeDynamicInstruction();
            int receiver = input.readUnsigned();
            int instance = input.readUnsigned();
            insn.setReceiver(receiver > 0 ? program.variableAt(receiver - 1) : null);
            insn.setInstance(instance > 0 ? program.variableAt(instance - 1) : null);
            insn.setMethod(this.parseMethodDescriptor(this.symbolTable.at(input.readUnsigned())));
            int argsCount = insn.getMethod().parameterCount();

            for (int i = 0; i < argsCount; i++) {
               insn.getArguments().add(program.variableAt(input.readUnsigned()));
            }

            insn.setBootstrapMethod(this.readMethodHandle(input));
            int bootstrapArgsCount = input.readUnsigned();

            for (int i = 0; i < bootstrapArgsCount; i++) {
               insn.getBootstrapArguments().add(this.readRuntimeConstant(input));
            }

            return insn;
         }
         case 83: {
            IsInstanceInstruction insn = new IsInstanceInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setType(this.parseValueType(this.symbolTable.at(input.readUnsigned())));
            insn.setValue(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 84: {
            InitClassInstruction insn = new InitClassInstruction();
            insn.setClassName(this.symbolTable.at(input.readUnsigned()));
            return insn;
         }
         case 85: {
            NullCheckInstruction insn = new NullCheckInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setValue(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 86: {
            MonitorEnterInstruction insn = new MonitorEnterInstruction();
            insn.setObjectRef(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 87: {
            MonitorExitInstruction insn = new MonitorExitInstruction();
            insn.setObjectRef(program.variableAt(input.readUnsigned()));
            return insn;
         }
         case 88:
         case 89:
         case 90: {
            BoundCheckInstruction insn = new BoundCheckInstruction();
            insn.setReceiver(program.variableAt(input.readUnsigned()));
            insn.setIndex(program.variableAt(input.readUnsigned()));
            if (insnType != 90) {
               insn.setArray(program.variableAt(input.readUnsigned()));
            }

            insn.setLower(insnType != 89);
            return insn;
         }
         default:
            throw new RuntimeException("Unknown instruction type: " + insnType);
      }
   }

   private MethodHandle readMethodHandle(VarDataInput input) throws IOException {
      int kind = input.readUnsigned();
      switch (kind) {
         case 0:
            return MethodHandle.fieldGetter(
               this.symbolTable.at(input.readUnsigned()),
               this.symbolTable.at(input.readUnsigned()),
               this.parseValueType(this.symbolTable.at(input.readUnsigned()))
            );
         case 1:
            return MethodHandle.staticFieldGetter(
               this.symbolTable.at(input.readUnsigned()),
               this.symbolTable.at(input.readUnsigned()),
               this.parseValueType(this.symbolTable.at(input.readUnsigned()))
            );
         case 2:
            return MethodHandle.fieldSetter(
               this.symbolTable.at(input.readUnsigned()),
               this.symbolTable.at(input.readUnsigned()),
               this.parseValueType(this.symbolTable.at(input.readUnsigned()))
            );
         case 3:
            return MethodHandle.staticFieldSetter(
               this.symbolTable.at(input.readUnsigned()),
               this.symbolTable.at(input.readUnsigned()),
               this.parseValueType(this.symbolTable.at(input.readUnsigned()))
            );
         case 4:
            return MethodHandle.virtualCaller(this.symbolTable.at(input.readUnsigned()), this.parseMethodDescriptor(this.symbolTable.at(input.readUnsigned())));
         case 5:
            return MethodHandle.staticCaller(this.symbolTable.at(input.readUnsigned()), this.parseMethodDescriptor(this.symbolTable.at(input.readUnsigned())));
         case 6:
            return MethodHandle.specialCaller(this.symbolTable.at(input.readUnsigned()), this.parseMethodDescriptor(this.symbolTable.at(input.readUnsigned())));
         case 7:
            return MethodHandle.constructorCaller(
               this.symbolTable.at(input.readUnsigned()), this.parseMethodDescriptor(this.symbolTable.at(input.readUnsigned()))
            );
         case 8:
            return MethodHandle.interfaceCaller(
               this.symbolTable.at(input.readUnsigned()), this.parseMethodDescriptor(this.symbolTable.at(input.readUnsigned()))
            );
         default:
            throw new IllegalArgumentException("Unexpected method handle type: " + kind);
      }
   }

   private RuntimeConstant readRuntimeConstant(VarDataInput input) throws IOException {
      int kind = input.readUnsigned();
      switch (kind) {
         case 0:
            return new RuntimeConstant(input.readSigned());
         case 1:
            return new RuntimeConstant(input.readSignedLong());
         case 2:
            return new RuntimeConstant(input.readFloat());
         case 3:
            return new RuntimeConstant(input.readDouble());
         case 4:
            return new RuntimeConstant(input.read());
         case 5:
            return new RuntimeConstant(this.parseValueType(this.symbolTable.at(input.readUnsigned())));
         case 6:
            return new RuntimeConstant(MethodDescriptor.parseSignature(this.symbolTable.at(input.readUnsigned())));
         case 7:
            return new RuntimeConstant(this.readMethodHandle(input));
         default:
            throw new IllegalArgumentException("Unexpected runtime constant type: " + kind);
      }
   }

   private MethodDescriptor parseMethodDescriptor(String key) {
      return this.referenceCache.parseDescriptorCached(key);
   }

   private ValueType parseValueType(String key) {
      return this.referenceCache.parseValueTypeCached(key);
   }

   private MethodReference createMethodReference(String className, MethodDescriptor method) {
      return this.referenceCache.getCached(className, method);
   }

   static class IOExceptionWrapper extends RuntimeException {
      private static final long serialVersionUID = -1765050162629001951L;

      IOExceptionWrapper(Throwable cause) {
         super(cause);
      }
   }

   private class InstructionWriter implements InstructionReader {
      private VarDataOutput output;
      TextLocation location = TextLocation.EMPTY;

      InstructionWriter(VarDataOutput output) {
         this.output = output;
      }

      @Override
      public void location(TextLocation newLocation) {
         try {
            if (newLocation == null) {
               newLocation = TextLocation.EMPTY;
            }

            String fileName = this.location.getFileName();
            int lineNumber = this.location.getLine();
            if (newLocation.getInlining() != this.location.getInlining()) {
               InliningInfo lastCommonInlining = null;
               InliningInfo[] prevPath = this.location.getInliningPath();
               InliningInfo[] newPath = newLocation.getInliningPath();
               int pathIndex = 0;

               while (pathIndex < prevPath.length && pathIndex < newPath.length && prevPath[pathIndex].equals(newPath[pathIndex])) {
                  lastCommonInlining = prevPath[pathIndex++];
               }

               for (InliningInfo prevInlining = this.location.getInlining(); prevInlining != lastCommonInlining; prevInlining = prevInlining.getParent()) {
                  this.output.writeUnsigned(126);
                  fileName = prevInlining.getFileName();
                  lineNumber = prevInlining.getLine();
               }

               while (pathIndex < newPath.length) {
                  InliningInfo inlining = newPath[pathIndex++];
                  this.writeSimpleLocation(fileName, lineNumber, inlining.getFileName(), inlining.getLine());
                  fileName = null;
                  lineNumber = -1;
                  this.output.writeUnsigned(125);
                  MethodReference method = inlining.getMethod();
                  this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(method.getClassName()));
                  this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(method.getDescriptor().toString()));
               }
            }

            this.writeSimpleLocation(fileName, lineNumber, newLocation.getFileName(), newLocation.getLine());
            this.location = newLocation;
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      private void writeSimpleLocation(String fileName, int lineNumber, String newFileName, int newLineNumber) throws IOException {
         if (!Objects.equals(fileName, newFileName) || lineNumber != newLineNumber) {
            if (newFileName == null) {
               this.output.writeUnsigned(1);
            } else if (fileName != null && fileName.equals(newFileName)) {
               this.output.writeUnsigned(127);
               this.output.writeSigned(newLineNumber - lineNumber);
            } else {
               this.output.writeUnsigned(2);
               this.output.writeUnsigned(ProgramIO.this.fileTable.lookup(newFileName));
               this.output.writeUnsigned(newLineNumber);
            }
         }
      }

      @Override
      public void nop() {
         try {
            this.output.writeUnsigned(3);
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void classConstant(VariableReader receiver, ValueType cst) {
         try {
            this.output.writeUnsigned(4);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(cst.toString()));
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void nullConstant(VariableReader receiver) {
         try {
            this.output.writeUnsigned(5);
            this.output.writeUnsigned(receiver.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void integerConstant(VariableReader receiver, int cst) {
         try {
            this.output.writeUnsigned(6);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeSigned(cst);
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void longConstant(VariableReader receiver, long cst) {
         try {
            this.output.writeUnsigned(7);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeSigned(cst);
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void floatConstant(VariableReader receiver, float cst) {
         try {
            this.output.writeUnsigned(8);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeFloat(cst);
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void doubleConstant(VariableReader receiver, double cst) {
         try {
            this.output.writeUnsigned(9);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeDouble(cst);
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void stringConstant(VariableReader receiver, String cst) {
         try {
            this.output.writeUnsigned(10);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.write(cst);
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void binary(BinaryOperation op, VariableReader receiver, VariableReader first, VariableReader second, NumericOperandType type) {
         try {
            this.output.writeUnsigned(11 + op.ordinal());
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(type.ordinal());
            this.output.writeUnsigned(first.getIndex());
            this.output.writeUnsigned(second.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void negate(VariableReader receiver, VariableReader operand, NumericOperandType type) {
         try {
            this.output.writeUnsigned(24);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(type.ordinal());
            this.output.writeUnsigned(operand.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void assign(VariableReader receiver, VariableReader assignee) {
         try {
            this.output.writeUnsigned(25);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(assignee.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void cast(VariableReader receiver, VariableReader value, ValueType targetType, boolean weak) {
         try {
            this.output.writeUnsigned(26);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(targetType.toString()));
            this.output.writeUnsigned(value.getIndex());
            this.output.writeUnsigned(weak ? 1 : 0);
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void cast(VariableReader receiver, VariableReader value, NumericOperandType sourceType, NumericOperandType targetType) {
         try {
            this.output.writeUnsigned(27);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(sourceType.ordinal() | targetType.ordinal() << 2);
            this.output.writeUnsigned(value.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void cast(VariableReader receiver, VariableReader value, IntegerSubtype type, CastIntegerDirection direction) {
         try {
            this.output.writeUnsigned(28);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(direction.ordinal() | type.ordinal() << 1);
            this.output.writeUnsigned(value.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void jumpIf(BranchingCondition cond, VariableReader operand, BasicBlockReader consequent, BasicBlockReader alternative) {
         try {
            this.output.writeUnsigned(29 + cond.ordinal());
            this.output.writeUnsigned(operand.getIndex());
            this.output.writeUnsigned(consequent.getIndex());
            this.output.writeUnsigned(alternative.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void jumpIf(BinaryBranchingCondition cond, VariableReader first, VariableReader second, BasicBlockReader consequent, BasicBlockReader alternative) {
         try {
            this.output.writeUnsigned(37 + cond.ordinal());
            this.output.writeUnsigned(first.getIndex());
            this.output.writeUnsigned(second.getIndex());
            this.output.writeUnsigned(consequent.getIndex());
            this.output.writeUnsigned(alternative.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void jump(BasicBlockReader target) {
         try {
            this.output.writeUnsigned(41);
            this.output.writeUnsigned(target.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void choose(VariableReader condition, List<? extends SwitchTableEntryReader> table, BasicBlockReader defaultTarget) {
         try {
            this.output.writeUnsigned(42);
            this.output.writeUnsigned(condition.getIndex());
            this.output.writeUnsigned(defaultTarget.getIndex());
            this.output.writeUnsigned(table.size());

            for (SwitchTableEntryReader entry : table) {
               this.output.writeSigned(entry.getCondition());
               this.output.writeUnsigned(entry.getTarget().getIndex());
            }
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void exit(VariableReader valueToReturn) {
         try {
            if (valueToReturn != null) {
               this.output.writeUnsigned(43);
               this.output.writeUnsigned(valueToReturn.getIndex());
            } else {
               this.output.writeUnsigned(44);
            }
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void raise(VariableReader exception) {
         try {
            this.output.writeUnsigned(45);
            this.output.writeUnsigned(exception.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
         try {
            this.output.writeUnsigned(46);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(itemType.toString()));
            this.output.writeUnsigned(size.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void createArray(VariableReader receiver, ValueType itemType, List<? extends VariableReader> dimensions) {
         try {
            this.output.writeUnsigned(48);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(itemType.toString()));
            this.output.writeUnsigned(dimensions.size());

            for (VariableReader dimension : dimensions) {
               this.output.writeUnsigned(dimension.getIndex());
            }
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void create(VariableReader receiver, String type) {
         try {
            this.output.writeUnsigned(47);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(type));
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void getField(VariableReader receiver, VariableReader instance, FieldReference field, ValueType fieldType) {
         try {
            this.output.writeUnsigned(instance != null ? 49 : 50);
            this.output.writeUnsigned(receiver.getIndex());
            if (instance != null) {
               this.output.writeUnsigned(instance.getIndex());
            }

            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(field.getClassName()));
            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(field.getFieldName()));
            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(fieldType.toString()));
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void putField(VariableReader instance, FieldReference field, VariableReader value, ValueType fieldType) {
         try {
            this.output.writeUnsigned(instance != null ? 51 : 52);
            if (instance != null) {
               this.output.writeUnsigned(instance.getIndex());
            }

            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(field.getClassName()));
            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(field.getFieldName()));
            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(fieldType.toString()));
            this.output.writeUnsigned(value.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void arrayLength(VariableReader receiver, VariableReader array) {
         try {
            this.output.writeUnsigned(53);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(array.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void cloneArray(VariableReader receiver, VariableReader array) {
         try {
            this.output.writeUnsigned(54);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(array.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void unwrapArray(VariableReader receiver, VariableReader array, ArrayElementType elementType) {
         try {
            this.output.writeUnsigned(55 + elementType.ordinal());
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(array.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void getElement(VariableReader receiver, VariableReader array, VariableReader index, ArrayElementType elementType) {
         try {
            this.output.writeUnsigned(63 + elementType.ordinal());
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(array.getIndex());
            this.output.writeUnsigned(index.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void putElement(VariableReader array, VariableReader index, VariableReader value, ArrayElementType elementType) {
         try {
            this.output.writeUnsigned(71 + elementType.ordinal());
            this.output.writeUnsigned(array.getIndex());
            this.output.writeUnsigned(index.getIndex());
            this.output.writeUnsigned(value.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void invoke(
         VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments, InvocationType type
      ) {
         try {
            switch (type) {
               case SPECIAL:
                  this.output.writeUnsigned(instance == null ? 79 : 80);
                  break;
               case VIRTUAL:
                  this.output.writeUnsigned(81);
            }

            this.output.writeUnsigned(receiver != null ? receiver.getIndex() + 1 : 0);
            if (instance != null) {
               this.output.writeUnsigned(instance.getIndex());
            }

            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(method.getClassName()));
            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(method.getDescriptor().toString()));

            for (int i = 0; i < arguments.size(); i++) {
               this.output.writeUnsigned(arguments.get(i).getIndex());
            }
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void invokeDynamic(
         VariableReader receiver,
         VariableReader instance,
         MethodDescriptor method,
         List<? extends VariableReader> arguments,
         MethodHandle bootstrapMethod,
         List<RuntimeConstant> bootstrapArguments
      ) {
         try {
            this.output.writeUnsigned(82);
            this.output.writeUnsigned(receiver != null ? receiver.getIndex() + 1 : 0);
            this.output.writeUnsigned(instance != null ? instance.getIndex() + 1 : 0);
            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(method.toString()));

            for (int i = 0; i < arguments.size(); i++) {
               this.output.writeUnsigned(arguments.get(i).getIndex());
            }

            this.write(bootstrapMethod);
            this.output.writeUnsigned(bootstrapArguments.size());

            for (int i = 0; i < bootstrapArguments.size(); i++) {
               this.write(bootstrapArguments.get(i));
            }
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void isInstance(VariableReader receiver, VariableReader value, ValueType type) {
         try {
            this.output.writeUnsigned(83);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(type.toString()));
            this.output.writeUnsigned(value.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void initClass(String className) {
         try {
            this.output.writeUnsigned(84);
            this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(className));
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void nullCheck(VariableReader receiver, VariableReader value) {
         try {
            this.output.writeUnsigned(85);
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(value.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void monitorEnter(VariableReader objectRef) {
         try {
            this.output.writeUnsigned(86);
            this.output.writeUnsigned(objectRef.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void monitorExit(VariableReader objectRef) {
         try {
            this.output.writeUnsigned(87);
            this.output.writeUnsigned(objectRef.getIndex());
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      @Override
      public void boundCheck(VariableReader receiver, VariableReader index, VariableReader array, boolean lower) {
         try {
            this.output.writeUnsigned(array == null ? 90 : (!lower ? 89 : 88));
            this.output.writeUnsigned(receiver.getIndex());
            this.output.writeUnsigned(index.getIndex());
            if (array != null) {
               this.output.writeUnsigned(array.getIndex());
            }
         } catch (IOException e) {
            throw new ProgramIO.IOExceptionWrapper(e);
         }
      }

      private void write(MethodHandle handle) throws IOException {
         switch (handle.getKind()) {
            case GET_FIELD:
               this.output.writeUnsigned(0);
               break;
            case GET_STATIC_FIELD:
               this.output.writeUnsigned(1);
               break;
            case PUT_FIELD:
               this.output.writeUnsigned(2);
               break;
            case PUT_STATIC_FIELD:
               this.output.writeUnsigned(3);
               break;
            case INVOKE_VIRTUAL:
               this.output.writeUnsigned(4);
               break;
            case INVOKE_STATIC:
               this.output.writeUnsigned(5);
               break;
            case INVOKE_SPECIAL:
               this.output.writeUnsigned(6);
               break;
            case INVOKE_CONSTRUCTOR:
               this.output.writeUnsigned(7);
               break;
            case INVOKE_INTERFACE:
               this.output.writeUnsigned(8);
         }

         this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(handle.getClassName()));
         switch (handle.getKind()) {
            case GET_FIELD:
            case GET_STATIC_FIELD:
            case PUT_FIELD:
            case PUT_STATIC_FIELD:
               this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(handle.getName()));
               this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(handle.getValueType().toString()));
               break;
            default:
               this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(new MethodDescriptor(handle.getName(), handle.signature()).toString()));
         }
      }

      private void write(RuntimeConstant cst) throws IOException {
         switch (cst.getKind()) {
            case 0:
               this.output.writeUnsigned(0);
               this.output.writeSigned(cst.getInt());
               break;
            case 1:
               this.output.writeUnsigned(1);
               this.output.writeSigned(cst.getLong());
               break;
            case 2:
               this.output.writeUnsigned(2);
               this.output.writeFloat(cst.getFloat());
               break;
            case 3:
               this.output.writeUnsigned(3);
               this.output.writeDouble(cst.getDouble());
               break;
            case 4:
               this.output.writeUnsigned(4);
               this.output.write(cst.getString());
               break;
            case 5:
               this.output.writeUnsigned(5);
               this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(cst.getValueType().toString()));
               break;
            case 6:
               this.output.writeUnsigned(6);
               this.output.writeUnsigned(ProgramIO.this.symbolTable.lookup(ValueType.methodTypeToString(cst.getMethodType())));
               break;
            case 7:
               this.output.writeUnsigned(7);
               this.write(cst.getMethodHandle());
         }
      }
   }
}
