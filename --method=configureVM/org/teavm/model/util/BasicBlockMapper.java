package org.teavm.model.util;

import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.instructions.SwitchInstruction;
import org.teavm.model.instructions.SwitchTableEntry;

public class BasicBlockMapper extends AbstractInstructionVisitor {
   private Function<BasicBlock, BasicBlock> mapFunction;

   public BasicBlockMapper(Function<BasicBlock, BasicBlock> mapFunction) {
      this.mapFunction = mapFunction;
   }

   public BasicBlockMapper(IntUnaryOperator mapFunction) {
      this(block -> block.getProgram().basicBlockAt(mapFunction.applyAsInt(block.getIndex())));
   }

   private BasicBlock map(BasicBlock block) {
      return this.mapFunction.apply(block);
   }

   public void transform(Program program) {
      for (int i = 0; i < program.basicBlockCount(); i++) {
         this.transform(program.basicBlockAt(i));
      }
   }

   public void transform(BasicBlock block) {
      this.transformWithoutPhis(block);

      for (Phi phi : block.getPhis()) {
         for (Incoming incoming : phi.getIncomings()) {
            incoming.setSource(this.map(incoming.getSource()));
         }
      }
   }

   public void transformWithoutPhis(BasicBlock block) {
      Instruction lastInsn = block.getLastInstruction();
      if (lastInsn != null) {
         lastInsn.acceptVisitor(this);
      }

      for (TryCatchBlock tryCatch : block.getTryCatchBlocks()) {
         tryCatch.setHandler(this.map(tryCatch.getHandler()));
      }
   }

   @Override
   public void visit(BranchingInstruction insn) {
      insn.setConsequent(this.map(insn.getConsequent()));
      insn.setAlternative(this.map(insn.getAlternative()));
   }

   @Override
   public void visit(BinaryBranchingInstruction insn) {
      insn.setConsequent(this.map(insn.getConsequent()));
      insn.setAlternative(this.map(insn.getAlternative()));
   }

   @Override
   public void visit(JumpInstruction insn) {
      insn.setTarget(this.map(insn.getTarget()));
   }

   @Override
   public void visit(SwitchInstruction insn) {
      for (SwitchTableEntry entry : insn.getEntries()) {
         entry.setTarget(this.map(entry.getTarget()));
      }

      insn.setDefaultTarget(this.map(insn.getDefaultTarget()));
   }
}
