package org.teavm.model.lowlevel;

import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.util.BasicBlockSplitter;
import org.teavm.runtime.Allocator;

public class ClassInitializerTransformer {
   public void transform(Program program) {
      BasicBlockSplitter splitter = new BasicBlockSplitter(program);
      int count = program.basicBlockCount();

      for (int i = 0; i < count; i++) {
         BasicBlock next = program.basicBlockAt(i);

         while (next != null) {
            BasicBlock block = next;
            next = null;

            for (Instruction instruction : block) {
               if (instruction instanceof InitClassInstruction) {
                  String className = ((InitClassInstruction)instruction).getClassName();
                  BasicBlock continueBlock = splitter.split(block, instruction);
                  BasicBlock initBlock = program.createBasicBlock();
                  instruction.delete();
                  initBlock.add(instruction);
                  JumpInstruction jumpToContinue = new JumpInstruction();
                  jumpToContinue.setTarget(continueBlock);
                  initBlock.add(jumpToContinue);
                  this.createInitCheck(program, block, className, continueBlock, initBlock, instruction.getLocation());
                  next = continueBlock;
                  break;
               }
            }
         }
      }

      splitter.fixProgram();
   }

   private void createInitCheck(Program program, BasicBlock block, String className, BasicBlock continueBlock, BasicBlock initBlock, TextLocation location) {
      Variable clsVariable = program.createVariable();
      Variable initializedVariable = program.createVariable();
      ClassConstantInstruction clsConstant = new ClassConstantInstruction();
      clsConstant.setReceiver(clsVariable);
      clsConstant.setConstant(ValueType.object(className));
      clsConstant.setLocation(location);
      block.add(clsConstant);
      InvokeInstruction checkInitialized = new InvokeInstruction();
      checkInitialized.setType(InvocationType.SPECIAL);
      checkInitialized.setMethod(new MethodReference(Allocator.class, "isInitialized", Class.class, boolean.class));
      checkInitialized.setArguments(clsVariable);
      checkInitialized.setReceiver(initializedVariable);
      checkInitialized.setLocation(location);
      block.add(checkInitialized);
      BranchingInstruction branching = new BranchingInstruction(BranchingCondition.NOT_EQUAL);
      branching.setOperand(initializedVariable);
      branching.setConsequent(continueBlock);
      branching.setAlternative(initBlock);
      branching.setLocation(location);
      block.add(branching);
   }
}
