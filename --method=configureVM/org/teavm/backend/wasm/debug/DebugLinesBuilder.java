package org.teavm.backend.wasm.debug;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import org.teavm.model.MethodReference;

public class DebugLinesBuilder extends DebugSectionBuilder implements DebugLines {
   private DebugFiles files;
   private DebugMethods methods;
   private int ptr;
   private int lastWrittenPtr;
   private String file;
   private int line = 1;
   private Deque<DebugLinesBuilder.State> states = new ArrayDeque<>();
   private StringBuilder indent = new StringBuilder(" ");

   public DebugLinesBuilder(DebugFiles files, DebugMethods methods) {
      super("teavm_line");
      this.files = files;
      this.methods = methods;
   }

   @Override
   public void advance(int ptr) {
      if (ptr < this.ptr) {
         throw new IllegalArgumentException();
      }

      this.ptr = ptr;
   }

   @Override
   public void location(String file, int line) {
      if (Objects.equals(file, this.file)
         && this.ptr != this.lastWrittenPtr
         && this.line != line
         && this.ptr - this.lastWrittenPtr < 32
         && Math.abs(line - this.line) <= 3) {
         this.blob.writeByte(10 + (this.ptr - this.lastWrittenPtr) + 32 * (line - this.line + 3));
         this.line = line;
         this.lastWrittenPtr = this.ptr;
      } else {
         if (!Objects.equals(file, this.file)) {
            this.flushPtr();
            this.line = 1;
            this.file = file;
            this.blob.writeByte(3).writeLEB(file != null ? this.files.filePtr(file) : 0);
         }

         if (this.line != line) {
            this.flushPtr();
            this.blob.writeByte(2).writeSLEB(line - this.line);
            this.line = line;
         }
      }
   }

   @Override
   public void emptyLocation() {
      this.location(null, -1);
   }

   private void flushPtr() {
      if (this.ptr != this.lastWrittenPtr) {
         this.blob.writeLEB(4);
         this.blob.writeLEB(this.ptr - this.lastWrittenPtr);
         this.lastWrittenPtr = this.ptr;
      }
   }

   @Override
   public void start(MethodReference methodReference) {
      this.flushPtr();
      this.indent.append(".");
      this.blob.writeLEB(0);
      this.blob.writeLEB(this.methods.methodPtr(methodReference));
      this.states.push(new DebugLinesBuilder.State(this.file, this.line));
      this.file = null;
      this.line = 1;
   }

   @Override
   public void end() {
      this.flushPtr();
      this.indent.setLength(this.indent.length() - 1);
      this.blob.writeLEB(1);
      if (!this.states.isEmpty()) {
         DebugLinesBuilder.State state = this.states.pop();
         this.file = state.file;
         this.line = state.line;
      } else {
         this.file = null;
         this.line = 1;
      }
   }

   private static class State {
      String file;
      int line;

      State(String file, int line) {
         this.file = file;
         this.line = line;
      }
   }
}
