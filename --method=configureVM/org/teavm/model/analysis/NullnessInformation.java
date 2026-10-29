package org.teavm.model.analysis;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.util.DefinitionExtractor;
import org.teavm.model.util.InstructionVariableMapper;
import org.teavm.model.util.PhiUpdater;

public class NullnessInformation {
   private Program program;
   private BitSet synthesizedVariables;
   private PhiUpdater phiUpdater;
   private NullnessInformationBuilder.Nullness[] nullnessArray;

   NullnessInformation(Program program, BitSet synthesizedVariables, PhiUpdater phiUpdater, NullnessInformationBuilder.Nullness[] nullnessArray) {
      this.program = program;
      this.synthesizedVariables = synthesizedVariables;
      this.phiUpdater = phiUpdater;
      this.nullnessArray = nullnessArray;
   }

   public boolean isNotNull(Variable variable) {
      return this.nullnessArray[variable.getIndex()] == NullnessInformationBuilder.Nullness.NOT_NULL;
   }

   public boolean isNull(Variable variable) {
      return this.nullnessArray[variable.getIndex()] == NullnessInformationBuilder.Nullness.NULL;
   }

   public boolean isSynthesized(Variable variable) {
      return this.synthesizedVariables.get(variable.getIndex());
   }

   public void dispose() {
      Set<Phi> phisToRemove = new HashSet<>(this.phiUpdater.getSynthesizedPhis());
      DefinitionExtractor defExtractor = new DefinitionExtractor();
      InstructionVariableMapper variableMapper = new InstructionVariableMapper(var -> {
         int source = this.phiUpdater.getSourceVariable(var.getIndex());
         return source >= 0 ? this.program.variableAt(source) : var;
      });

      for (BasicBlock block : this.program.getBasicBlocks()) {
         block.getPhis().removeIf(phisToRemove::contains);

         for (Instruction insn : block) {
            insn.acceptVisitor(defExtractor);
            if (Arrays.stream(defExtractor.getDefinedVariables()).anyMatch(var -> this.synthesizedVariables.get(var.getIndex()))) {
               insn.delete();
            } else {
               insn.acceptVisitor(variableMapper);
            }
         }

         variableMapper.applyToPhis(block);
         if (block.getExceptionVariable() != null) {
            block.setExceptionVariable(variableMapper.map(block.getExceptionVariable()));
         }
      }

      for (int i = 0; i < this.program.variableCount(); i++) {
         int sourceVar = this.phiUpdater.getSourceVariable(i);
         if (sourceVar >= 0 && sourceVar != i) {
            this.program.deleteVariable(i);
         }
      }

      this.program.pack();
   }

   public static NullnessInformation build(Program program, MethodDescriptor methodDescriptor) {
      NullnessInformationBuilder builder = new NullnessInformationBuilder(program, methodDescriptor);
      builder.build();
      return new NullnessInformation(program, builder.synthesizedVariables, builder.phiUpdater, builder.statuses);
   }
}
