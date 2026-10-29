package org.teavm.model;

import java.util.List;

public interface PhiReader {
   BasicBlockReader getBasicBlock();

   VariableReader getReceiver();

   List<? extends IncomingReader> readIncomings();
}
