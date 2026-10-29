package org.teavm.backend.c.transform;

import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.Instruction;
import org.teavm.model.MethodHolder;
import org.teavm.model.Program;
import org.teavm.model.TextLocation;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.util.BasicBlockMapper;

public class SynchronizedMethodTransformer implements ClassHolderTransformer {
   @Override
   public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
      for (MethodHolder method : cls.getMethods()) {
         if (method.getProgram() != null && method.hasModifier(ElementModifier.SYNCHRONIZED)) {
            this.transformSynchronized(method.getProgram(), method.hasModifier(ElementModifier.STATIC), cls.getName());
         }
      }
   }

   private void transformSynchronized(Program program, boolean isStatic, String className) {
      BasicBlock firstBlockReplacement = program.createBasicBlock();
      BasicBlock firstBlock = program.basicBlockAt(0);

      while (firstBlock.getFirstInstruction() != null) {
         Instruction insn = firstBlock.getFirstInstruction();
         insn.delete();
         firstBlockReplacement.add(insn);
      }

      MonitorEnterInstruction monitorEnter = new MonitorEnterInstruction();
      monitorEnter.setObjectRef(this.addMonitorInstruction(firstBlock, null, isStatic, className, null));
      firstBlock.add(monitorEnter);
      JumpInstruction jmp = new JumpInstruction();
      jmp.setTarget(firstBlockReplacement);
      firstBlock.add(jmp);
      BasicBlock handler = program.createBasicBlock();
      handler.setExceptionVariable(program.createVariable());
      MonitorExitInstruction monitorExit = new MonitorExitInstruction();
      monitorExit.setObjectRef(this.addMonitorInstruction(handler, null, isStatic, className, null));
      handler.add(monitorExit);
      RaiseInstruction rethrow = new RaiseInstruction();
      rethrow.setException(handler.getExceptionVariable());
      handler.add(rethrow);

      for (int i = 1; i < program.basicBlockCount() - 1; i++) {
         BasicBlock block = program.basicBlockAt(i);
         TryCatchBlock tryCatch = new TryCatchBlock();
         tryCatch.setExceptionType(null);
         tryCatch.setHandler(handler);
         block.getTryCatchBlocks().add(tryCatch);
         if (block.getLastInstruction() instanceof ExitInstruction) {
            monitorExit = new MonitorExitInstruction();
            monitorExit.setObjectRef(
               this.addMonitorInstruction(block, block.getLastInstruction(), isStatic, className, block.getLastInstruction().getLocation())
            );
            monitorExit.setLocation(block.getLastInstruction().getLocation());
            block.getLastInstruction().insertPrevious(monitorExit);
         }
      }

      BasicBlockMapper mapper = new BasicBlockMapper(b -> b == firstBlock ? firstBlockReplacement : b);
      mapper.transform(program);
   }

   private Variable addMonitorInstruction(BasicBlock block, Instruction before, boolean isStatic, String className, TextLocation location) {
      if (isStatic) {
         Variable v = block.getProgram().createVariable();
         ClassConstantInstruction cst = new ClassConstantInstruction();
         cst.setConstant(ValueType.object(className));
         cst.setReceiver(v);
         cst.setLocation(location);
         if (before != null) {
            before.insertPrevious(cst);
         } else {
            block.add(cst);
         }

         return v;
      } else {
         return block.getProgram().variableAt(0);
      }
   }
}
