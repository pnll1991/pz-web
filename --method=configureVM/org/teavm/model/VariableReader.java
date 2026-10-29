package org.teavm.model;

public interface VariableReader {
   int getIndex();

   ProgramReader getProgram();

   String getDebugName();

   String getLabel();

   int getRegister();
}
