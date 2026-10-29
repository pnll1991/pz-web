package org.teavm.model;

import java.util.List;
import org.teavm.model.instructions.InstructionReader;

public interface BasicBlockReader {
   ProgramReader getProgram();

   int getIndex();

   List<? extends PhiReader> readPhis();

   int instructionCount();

   InstructionIterator iterateInstructions();

   void readAllInstructions(InstructionReader var1);

   List<? extends TryCatchBlockReader> readTryCatchBlocks();

   VariableReader getExceptionVariable();
}
