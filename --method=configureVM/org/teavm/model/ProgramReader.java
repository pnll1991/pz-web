package org.teavm.model;

public interface ProgramReader {
   int basicBlockCount();

   BasicBlockReader basicBlockAt(int var1);

   Iterable<? extends BasicBlockReader> getBasicBlocks();

   int variableCount();

   VariableReader variableAt(int var1);

   AnnotationContainerReader getAnnotations();
}
