package org.teavm.backend.wasm.debug.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.teavm.backend.wasm.debug.info.VariableInfo;
import org.teavm.backend.wasm.debug.info.VariableRangeInfo;
import org.teavm.backend.wasm.debug.info.VariableType;
import org.teavm.backend.wasm.debug.info.VariablesInfo;

public class DebugVariablesParser extends DebugSectionParser {
   private static final VariableType[] typeByOrdinal = VariableType.values();
   private DebugStringParser strings;
   private DebugVariablesParser.VariablesInfoImpl variablesInfo;

   public DebugVariablesParser(DebugStringParser strings) {
      super("teavm_var", strings);
      this.strings = strings;
   }

   public DebugVariablesParser.VariablesInfoImpl getVariablesInfo() {
      return this.variablesInfo;
   }

   @Override
   protected void doParse() {
      int lastAddress = 0;
      ArrayList<VariableRangeInfo> ranges = new ArrayList<>();
      ArrayList<DebugVariablesParser.VariableRangeInfoImpl> localRanges = new ArrayList<>();

      while (this.ptr < this.data.length) {
         int baseAddress = lastAddress + this.readLEB();
         lastAddress = baseAddress;
         int variableCount = this.readLEB();

         for (int i = 0; i < variableCount; i++) {
            String name = this.strings.getString(this.readLEB());
            VariableType type = typeByOrdinal[this.readLEB()];
            int rangeCount = this.readLEB();
            DebugVariablesParser.VariableInfoImpl varInfo = new DebugVariablesParser.VariableInfoImpl(name, type);
            int address = baseAddress;
            int lastLocation = 0;

            for (int j = 0; j < rangeCount; j++) {
               int start = address + this.readSignedLEB();
               int size = this.readLEB();
               int end = start + size;
               address = end;
               int location = lastLocation + this.readSignedLEB();
               lastLocation = location;
               DebugVariablesParser.VariableRangeInfoImpl rangeInfo = new DebugVariablesParser.VariableRangeInfoImpl(varInfo, start, end, location);
               ranges.add(rangeInfo);
               localRanges.add(rangeInfo);
            }

            localRanges.clear();
            varInfo.ranges = Collections.unmodifiableList(Arrays.asList(localRanges.toArray(new DebugVariablesParser.VariableRangeInfoImpl[0])));
         }
      }

      ranges.sort(Comparator.comparing(VariableRangeInfo::start));
      this.variablesInfo = new DebugVariablesParser.VariablesInfoImpl(
         Collections.unmodifiableList(Arrays.asList(ranges.toArray(new DebugVariablesParser.VariableRangeInfoImpl[0])))
      );
   }

   private static class VariableInfoImpl extends VariableInfo {
      private String name;
      private VariableType type;
      private List<DebugVariablesParser.VariableRangeInfoImpl> ranges;

      VariableInfoImpl(String name, VariableType type) {
         this.name = name;
         this.type = type;
      }

      @Override
      public String name() {
         return this.name;
      }

      @Override
      public VariableType type() {
         return this.type;
      }

      @Override
      public Collection<? extends VariableRangeInfo> ranges() {
         return this.ranges;
      }
   }

   private static class VariableRangeInfoImpl extends VariableRangeInfo {
      private DebugVariablesParser.VariableInfoImpl variableInfo;
      private int start;
      private int end;
      private int index;

      VariableRangeInfoImpl(DebugVariablesParser.VariableInfoImpl variableInfo, int start, int end, int index) {
         this.variableInfo = variableInfo;
         this.start = start;
         this.end = end;
         this.index = index;
      }

      @Override
      public VariableInfo variable() {
         return this.variableInfo;
      }

      @Override
      public int start() {
         return this.start;
      }

      @Override
      public int end() {
         return this.end;
      }

      @Override
      public int index() {
         return this.index;
      }
   }

   private static class VariablesInfoImpl extends VariablesInfo {
      private List<DebugVariablesParser.VariableRangeInfoImpl> ranges;

      VariablesInfoImpl(List<DebugVariablesParser.VariableRangeInfoImpl> ranges) {
         this.ranges = ranges;
      }

      @Override
      public List<? extends VariableRangeInfo> ranges() {
         return this.ranges;
      }
   }
}
