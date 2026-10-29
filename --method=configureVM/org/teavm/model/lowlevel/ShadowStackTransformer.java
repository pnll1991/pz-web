package org.teavm.model.lowlevel;

import java.util.ArrayList;
import java.util.List;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.util.BasicBlockMapper;
import org.teavm.runtime.ShadowStack;

public class ShadowStackTransformer {
   private Characteristics characteristics;
   private GCShadowStackContributor gcContributor;

   public ShadowStackTransformer(Characteristics characteristics) {
      this.gcContributor = new GCShadowStackContributor(characteristics);
      this.characteristics = characteristics;
   }

   public void apply(Program program, MethodReader method) {
      if (this.characteristics.isManaged(method.getReference())) {
         int shadowStackSize = this.gcContributor.contribute(program, method);
         boolean exceptions = false;

         label37:
         for (BasicBlock block : program.getBasicBlocks()) {
            if (!block.getTryCatchBlocks().isEmpty()) {
               exceptions = true;
               break;
            }

            for (Instruction insn : block) {
               if (ExceptionHandlingUtil.isCallInstruction(this.characteristics, insn)) {
                  exceptions = true;
                  break label37;
               }
            }
         }

         if (shadowStackSize > 0 || exceptions) {
            this.addStackAllocation(program, shadowStackSize);
            this.addStackRelease(program, shadowStackSize);
         }
      }
   }

   private void addStackAllocation(Program program, int maxDepth) {
      BasicBlock block = program.basicBlockAt(0);
      if (!block.getTryCatchBlocks().isEmpty()) {
         this.splitFirstBlock(program);
      }

      List<Instruction> instructionsToAdd = new ArrayList<>();
      Variable sizeVariable = program.createVariable();
      IntegerConstantInstruction sizeConstant = new IntegerConstantInstruction();
      sizeConstant.setReceiver(sizeVariable);
      sizeConstant.setConstant(maxDepth);
      instructionsToAdd.add(sizeConstant);
      InvokeInstruction invocation = new InvokeInstruction();
      invocation.setType(InvocationType.SPECIAL);
      invocation.setMethod(new MethodReference(ShadowStack.class, "allocStack", int.class, void.class));
      invocation.setArguments(sizeVariable);
      instructionsToAdd.add(invocation);
      block.addFirstAll(instructionsToAdd);
   }

   private void splitFirstBlock(Program program) {
      BasicBlock block = program.basicBlockAt(0);
      BasicBlock split = program.createBasicBlock();

      while (block.getFirstInstruction() != null) {
         Instruction instruction = block.getFirstInstruction();
         instruction.delete();
         split.add(instruction);
      }

      JumpInstruction jump = new JumpInstruction();
      jump.setLocation(split.getFirstInstruction().getLocation());
      jump.setTarget(split);
      block.add(jump);
      List<TryCatchBlock> tryCatchBlocks = new ArrayList<>(block.getTryCatchBlocks());
      block.getTryCatchBlocks().clear();
      split.getTryCatchBlocks().addAll(tryCatchBlocks);
      new BasicBlockMapper(b -> b == block ? split : b).transform(program);
   }

   private void addStackRelease(Program program, int maxDepth) {
      List<BasicBlock> blocks = new ArrayList<>();
      boolean hasResult = false;

      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlock block = program.basicBlockAt(i);
         Instruction instruction = block.getLastInstruction();
         if (instruction instanceof ExitInstruction) {
            blocks.add(block);
            if (((ExitInstruction)instruction).getValueToReturn() != null) {
               hasResult = true;
            }
         }
      }

      BasicBlock exitBlock;
      if (blocks.size() == 1) {
         exitBlock = blocks.get(0);
      } else {
         exitBlock = program.createBasicBlock();
         ExitInstruction exit = new ExitInstruction();
         exitBlock.add(exit);
         if (hasResult) {
            Phi phi = new Phi();
            phi.setReceiver(program.createVariable());
            exitBlock.getPhis().add(phi);
            exit.setValueToReturn(phi.getReceiver());

            for (BasicBlock block : blocks) {
               ExitInstruction oldExit = (ExitInstruction)block.getLastInstruction();
               Incoming incoming = new Incoming();
               incoming.setSource(block);
               incoming.setValue(oldExit.getValueToReturn());
               phi.getIncomings().add(incoming);
            }
         }

         for (BasicBlock block : blocks) {
            ExitInstruction oldExit = (ExitInstruction)block.getLastInstruction();
            JumpInstruction jumpToExit = new JumpInstruction();
            jumpToExit.setTarget(exitBlock);
            jumpToExit.setLocation(oldExit.getLocation());
            jumpToExit.setLocation(oldExit.getLocation());
            block.getLastInstruction().replace(jumpToExit);
         }
      }

      List<Instruction> instructionsToAdd = new ArrayList<>();
      Variable sizeVariable = program.createVariable();
      IntegerConstantInstruction sizeConstant = new IntegerConstantInstruction();
      sizeConstant.setReceiver(sizeVariable);
      sizeConstant.setConstant(maxDepth);
      instructionsToAdd.add(sizeConstant);
      InvokeInstruction invocation = new InvokeInstruction();
      invocation.setType(InvocationType.SPECIAL);
      invocation.setMethod(new MethodReference(ShadowStack.class, "releaseStack", int.class, void.class));
      invocation.setArguments(sizeVariable);
      instructionsToAdd.add(invocation);
      exitBlock.getLastInstruction().insertPreviousAll(instructionsToAdd);
   }
}
