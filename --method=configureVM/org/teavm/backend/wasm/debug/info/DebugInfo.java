package org.teavm.backend.wasm.debug.info;

import java.io.PrintStream;

public class DebugInfo {
   private VariablesInfo variables;
   private LineInfo lines;
   private ControlFlowInfo controlFlow;
   private ClassLayoutInfo classLayoutInfo;
   private int offset;

   public DebugInfo(VariablesInfo variables, LineInfo lines, ControlFlowInfo controlFlow, ClassLayoutInfo classLayoutInfo, int offset) {
      this.variables = variables;
      this.lines = lines;
      this.controlFlow = controlFlow;
      this.classLayoutInfo = classLayoutInfo;
      this.offset = offset;
   }

   public VariablesInfo variables() {
      return this.variables;
   }

   public LineInfo lines() {
      return this.lines;
   }

   public ControlFlowInfo controlFlow() {
      return this.controlFlow;
   }

   public int offset() {
      return this.offset;
   }

   public ClassLayoutInfo classLayoutInfo() {
      return this.classLayoutInfo;
   }

   public void dump(PrintStream out) {
      if (this.offset != 0) {
         out.println("Code section offset: " + Integer.toHexString(this.offset));
      }

      if (this.lines != null) {
         out.println("LINES");
         this.lines.dump(out);
      }

      if (this.controlFlow != null) {
         out.println("CONTROL FLOW");
         this.controlFlow.dump(out);
      }

      if (this.variables != null) {
         out.println("VARIABLES");
         this.variables.dump(out);
      }

      if (this.classLayoutInfo != null) {
         out.println("CLASS LAYOUT:");
         this.classLayoutInfo.dump(out);
      }
   }
}
