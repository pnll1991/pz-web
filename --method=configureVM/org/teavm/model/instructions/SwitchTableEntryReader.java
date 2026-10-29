package org.teavm.model.instructions;

import org.teavm.model.BasicBlockReader;

public interface SwitchTableEntryReader {
   int getCondition();

   BasicBlockReader getTarget();
}
