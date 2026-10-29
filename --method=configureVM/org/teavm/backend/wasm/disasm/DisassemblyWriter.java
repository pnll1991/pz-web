package org.teavm.backend.wasm.disasm;

import java.io.PrintWriter;
import org.teavm.backend.wasm.debug.info.LineInfo;
import org.teavm.backend.wasm.debug.info.LineInfoCommand;
import org.teavm.backend.wasm.debug.info.LineInfoCommandVisitor;
import org.teavm.backend.wasm.debug.info.LineInfoEnterCommand;
import org.teavm.backend.wasm.debug.info.LineInfoExitCommand;
import org.teavm.backend.wasm.debug.info.LineInfoFileCommand;
import org.teavm.backend.wasm.debug.info.LineInfoLineCommand;
import org.teavm.backend.wasm.debug.info.LineInfoSequence;
import org.teavm.backend.wasm.parser.AddressListener;

public abstract class DisassemblyWriter {
   private PrintWriter out;
   private boolean withAddress;
   private int indentLevel;
   private int address;
   private boolean hasAddress;
   private boolean lineStarted;
   private int addressOffset;
   private LineInfo debugLines;
   private int currentSequenceIndex;
   private int currentCommandIndex = -1;
   private int lineInfoIndent;
   public final AddressListener addressListener = new AddressListener() {
      @Override
      public void address(int address) {
         DisassemblyWriter.this.address = address + DisassemblyWriter.this.addressOffset;
      }
   };

   public DisassemblyWriter(PrintWriter out) {
      this.out = out;
   }

   public void setWithAddress(boolean withAddress) {
      this.withAddress = withAddress;
   }

   public void setAddressOffset(int addressOffset) {
      this.addressOffset = addressOffset;
   }

   public void setDebugLines(LineInfo debugLines) {
      this.debugLines = debugLines;
   }

   public void startSection() {
      this.currentSequenceIndex = 0;
   }

   public DisassemblyWriter address() {
      this.hasAddress = true;
      return this;
   }

   public DisassemblyWriter indent() {
      this.indentLevel++;
      return this;
   }

   public DisassemblyWriter outdent() {
      this.indentLevel--;
      return this;
   }

   public DisassemblyWriter eol() {
      this.out.println();
      this.lineStarted = false;
      return this;
   }

   private void startLine() {
      if (!this.lineStarted) {
         this.lineStarted = true;
         if (this.debugLines != null) {
            this.printDebugLine();
         }

         if (this.withAddress) {
            if (this.hasAddress) {
               this.hasAddress = false;
               this.printAddress();
            } else {
               this.out.print("                ");
            }
         }

         for (int i = 0; i < this.indentLevel; i++) {
            this.out.print("  ");
         }
      }
   }

   private void printDebugLine() {
      if (this.currentSequenceIndex < this.debugLines.sequences().size()) {
         if (this.currentCommandIndex < 0) {
            if (this.address < this.debugLines.sequences().get(this.currentSequenceIndex).startAddress()) {
               return;
            }

            this.currentCommandIndex = 0;
            this.printSingleDebugAnnotation("start debug line sequence");
         } else if (this.address >= this.debugLines.sequences().get(this.currentSequenceIndex).endAddress()) {
            this.printSingleDebugAnnotation("end debug line sequence");
            this.currentSequenceIndex++;
            this.currentCommandIndex = -1;
            this.lineInfoIndent = 0;
            return;
         }

         for (LineInfoSequence sequence = this.debugLines.sequences().get(this.currentSequenceIndex);
            this.currentCommandIndex < sequence.commands().size();
            this.currentCommandIndex++
         ) {
            LineInfoCommand command = sequence.commands().get(this.currentCommandIndex);
            if (this.address < command.address()) {
               break;
            }

            command.acceptVisitor(new LineInfoCommandVisitor() {
               @Override
               public void visit(LineInfoEnterCommand command) {
                  DisassemblyWriter.this.printSingleDebugAnnotation("enter inline " + command.method().cls().name() + "." + command.method().name());
                  DisassemblyWriter.this.lineInfoIndent++;
               }

               @Override
               public void visit(LineInfoExitCommand command) {
                  DisassemblyWriter.this.lineInfoIndent--;
                  DisassemblyWriter.this.printSingleDebugAnnotation("exit inline");
               }

               @Override
               public void visit(LineInfoFileCommand command) {
                  if (command.file() == null) {
                     DisassemblyWriter.this.printSingleDebugAnnotation("at <unknown>:" + command.line());
                  } else {
                     DisassemblyWriter.this.printSingleDebugAnnotation("at " + command.file().name() + ":" + command.line());
                  }
               }

               @Override
               public void visit(LineInfoLineCommand command) {
                  DisassemblyWriter.this.printSingleDebugAnnotation("at " + command.line());
               }
            });
         }
      }
   }

   private void printSingleDebugAnnotation(String text) {
      this.out.print("                ");

      for (int i = 0; i < this.indentLevel; i++) {
         this.out.print("  ");
      }

      for (int i = 0; i < this.lineInfoIndent; i++) {
         this.out.print("  ");
      }

      this.startAnnotation();
      this.out.print("(; ");
      this.write(text);
      this.out.print(" ;)");
      this.endAnnotation();
      this.out.print("\n");
   }

   private void printAddress() {
      this.out.print("(; ");

      for (int i = 7; i >= 0; i--) {
         int digit = this.address >>> i * 4 & 15;
         this.out.print(Character.forDigit(digit, 16));
      }

      this.out.print(" ;)  ");
   }

   public DisassemblyWriter write(String s) {
      return this.writeExact(s);
   }

   protected DisassemblyWriter writeExact(String s) {
      this.startLine();
      this.out.print(s);
      return this;
   }

   public abstract DisassemblyWriter startLink(String var1);

   public abstract DisassemblyWriter endLink();

   public abstract DisassemblyWriter startLinkTarget(String var1);

   public abstract DisassemblyWriter endLinkTarget();

   public abstract DisassemblyWriter prologue();

   public abstract DisassemblyWriter epilogue();

   protected void startAnnotation() {
   }

   protected void endAnnotation() {
   }

   public void flush() {
      this.out.flush();
   }
}
