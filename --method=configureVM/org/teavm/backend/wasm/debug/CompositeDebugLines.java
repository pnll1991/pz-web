package org.teavm.backend.wasm.debug;

import org.teavm.backend.wasm.debug.sourcemap.SourceMapBuilder;
import org.teavm.model.MethodReference;

public class CompositeDebugLines implements DebugLines {
   private DebugLines debugLinesBuilder;
   private SourceMapBuilder sourceMapBuilder;

   public CompositeDebugLines(DebugLines debugLinesBuilder, SourceMapBuilder sourceMapBuilder) {
      this.debugLinesBuilder = debugLinesBuilder;
      this.sourceMapBuilder = sourceMapBuilder;
   }

   @Override
   public void advance(int ptr) {
      this.debugLinesBuilder.advance(ptr);
      this.sourceMapBuilder.advance(ptr);
   }

   @Override
   public void location(String file, int line) {
      this.debugLinesBuilder.location(file, line);
      this.sourceMapBuilder.location(file, line);
   }

   @Override
   public void emptyLocation() {
      this.debugLinesBuilder.emptyLocation();
      this.sourceMapBuilder.emptyLocation();
   }

   @Override
   public void start(MethodReference methodReference) {
      this.debugLinesBuilder.start(methodReference);
      this.sourceMapBuilder.start(methodReference);
   }

   @Override
   public void end() {
      this.debugLinesBuilder.end();
      this.sourceMapBuilder.end();
   }
}
