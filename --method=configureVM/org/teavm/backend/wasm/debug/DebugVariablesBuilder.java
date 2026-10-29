package org.teavm.backend.wasm.debug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.teavm.backend.wasm.debug.info.VariableType;

public class DebugVariablesBuilder extends DebugSectionBuilder implements DebugVariables {
   private DebugStrings strings;
   private int sequenceStart;
   private int lastSequenceStart;
   private Map<String, DebugVariablesBuilder.VarInfo> variables = new LinkedHashMap<>();

   public DebugVariablesBuilder(DebugStrings strings) {
      super("teavm_var");
      this.strings = strings;
   }

   @Override
   public void startSequence(int pointer) {
      this.sequenceStart = pointer;
   }

   @Override
   public void type(String name, VariableType type) {
      this.getInfo(name).type = type;
   }

   @Override
   public void range(String name, int start, int end, int pointer) {
      this.getInfo(name).ranges.add(new DebugVariablesBuilder.Range(start, end, pointer));
   }

   private DebugVariablesBuilder.VarInfo getInfo(String name) {
      DebugVariablesBuilder.VarInfo info = this.variables.get(name);
      if (info == null) {
         info = new DebugVariablesBuilder.VarInfo();
         this.variables.put(name, info);
      }

      return info;
   }

   @Override
   public void endSequence() {
      if (!this.variables.isEmpty()) {
         this.blob.writeLEB(this.sequenceStart - this.lastSequenceStart);
         this.lastSequenceStart = this.sequenceStart;
         this.blob.writeLEB(this.variables.size());

         for (Entry<String, DebugVariablesBuilder.VarInfo> variable : this.variables.entrySet()) {
            this.blob.writeLEB(this.strings.stringPtr(variable.getKey()));
            DebugVariablesBuilder.VarInfo info = variable.getValue();
            this.blob.writeLEB(info.type.ordinal());
            this.blob.writeLEB(info.ranges.size());
            int lastPtr = this.sequenceStart;
            int lastPointer = 0;

            for (DebugVariablesBuilder.Range range : info.ranges) {
               this.blob.writeSLEB(range.start - lastPtr);
               this.blob.writeLEB(range.end - range.start);
               this.blob.writeSLEB(range.pointer - lastPointer);
               lastPointer = range.pointer;
               lastPointer = range.end;
            }
         }

         this.variables.clear();
      }
   }

   private static class Range {
      int start;
      int end;
      int pointer;

      Range(int start, int end, int pointer) {
         this.start = start;
         this.end = end;
         this.pointer = pointer;
      }
   }

   private static class VarInfo {
      VariableType type = VariableType.UNDEFINED;
      List<DebugVariablesBuilder.Range> ranges = new ArrayList<>();
   }
}
