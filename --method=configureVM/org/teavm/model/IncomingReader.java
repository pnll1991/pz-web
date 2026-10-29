package org.teavm.model;

public interface IncomingReader {
   VariableReader getValue();

   BasicBlockReader getSource();

   PhiReader getPhi();
}
