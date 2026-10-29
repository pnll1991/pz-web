package org.teavm.model;

import org.teavm.model.instructions.InstructionReader;

public interface InstructionIterator {
   boolean hasNext();

   void next();

   boolean hasPrevious();

   void previous();

   void read(InstructionReader var1);
}
