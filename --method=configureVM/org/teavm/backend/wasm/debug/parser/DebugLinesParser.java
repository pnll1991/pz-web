package org.teavm.backend.wasm.debug.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.teavm.backend.wasm.debug.info.FileInfo;
import org.teavm.backend.wasm.debug.info.LineInfo;
import org.teavm.backend.wasm.debug.info.LineInfoCommand;
import org.teavm.backend.wasm.debug.info.LineInfoEnterCommand;
import org.teavm.backend.wasm.debug.info.LineInfoExitCommand;
import org.teavm.backend.wasm.debug.info.LineInfoFileCommand;
import org.teavm.backend.wasm.debug.info.LineInfoLineCommand;
import org.teavm.backend.wasm.debug.info.LineInfoSequence;
import org.teavm.backend.wasm.debug.info.MethodInfo;

public class DebugLinesParser extends DebugSectionParser {
   private DebugFileParser files;
   private DebugMethodParser methods;
   private LineInfo lineInfo;
   private List<LineInfoSequence> sequences = new ArrayList<>();
   private List<LineInfoCommand> commands = new ArrayList<>();
   private Deque<DebugLinesParser.State> stateStack = new ArrayDeque<>();
   private FileInfo file;
   private int line = 1;
   private int address;
   private MethodInfo currentMethod;
   private int sequenceStartAddress;

   public DebugLinesParser(DebugFileParser files, DebugMethodParser methods) {
      super("teavm_line", files, methods);
      this.files = files;
      this.methods = methods;
   }

   public LineInfo getLineInfo() {
      return this.lineInfo;
   }

   @Override
   protected void doParse() {
      while (this.ptr < this.data.length) {
         int cmd = this.data[this.ptr++] & 255;
         switch (cmd) {
            case 0:
               this.start();
               break;
            case 1:
               this.end();
               break;
            case 2:
               this.moveToLine();
               break;
            case 3:
               this.moveToFile();
               break;
            case 4:
               this.advanceAddress();
               break;
            default:
               if (cmd < 10) {
                  throw new IllegalStateException("Invalid command at " + this.ptr + ": " + cmd);
               }

               this.specialCommand(cmd);
         }
      }

      this.lineInfo = new LineInfo(this.sequences.toArray(new LineInfoSequence[0]));
      this.sequences = null;
      this.commands = null;
      this.stateStack = null;
      this.currentMethod = null;
   }

   private void start() {
      MethodInfo method = this.methods.getMethod(this.readLEB());
      if (this.currentMethod == null) {
         this.currentMethod = method;
         this.sequenceStartAddress = this.address;
      } else {
         this.stateStack.push(new DebugLinesParser.State(this.file, this.line));
         this.commands.add(new LineInfoEnterCommand(this.address, method));
      }

      this.file = null;
      this.line = 1;
   }

   private void end() {
      if (this.stateStack.isEmpty()) {
         if (this.currentMethod != null) {
            this.sequences
               .add(new LineInfoSequence(this.sequenceStartAddress, this.address, this.currentMethod, this.commands.toArray(new LineInfoCommand[0])));
         }

         this.commands.clear();
         this.currentMethod = null;
         this.file = null;
         this.line = 1;
      } else {
         DebugLinesParser.State state = this.stateStack.pop();
         this.file = state.file;
         this.line = state.line;
         this.commands.add(new LineInfoExitCommand(this.address));
      }
   }

   private void moveToLine() {
      this.line = this.line + this.readSignedLEB();
      if (this.currentMethod != null) {
         this.commands.add(new LineInfoLineCommand(this.address, this.line));
      }
   }

   private void moveToFile() {
      this.file = this.files.getFile(this.readLEB());
      this.line = 1;
      if (this.ptr < this.data.length && this.data[this.ptr] == 2) {
         this.ptr++;
         this.line = this.line + this.readSignedLEB();
      }

      if (this.currentMethod != null) {
         this.commands.add(new LineInfoFileCommand(this.address, this.file, this.line));
      }
   }

   private void advanceAddress() {
      this.address = this.address + this.readLEB();
   }

   private void specialCommand(int cmd) {
      cmd -= 10;
      this.address += cmd % 32;
      cmd /= 32;
      this.line += cmd - 3;
      if (this.currentMethod != null) {
         this.commands.add(new LineInfoFileCommand(this.address, this.file, this.line));
      }
   }

   private static class State {
      FileInfo file;
      int line;

      State(FileInfo file, int line) {
         this.file = file;
         this.line = line;
      }
   }
}
