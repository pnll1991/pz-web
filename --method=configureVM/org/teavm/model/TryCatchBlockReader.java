package org.teavm.model;

public interface TryCatchBlockReader {
   BasicBlockReader getProtectedBlock();

   BasicBlockReader getHandler();

   String getExceptionType();
}
