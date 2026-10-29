package org.teavm.backend.wasm.debug.parser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.teavm.backend.wasm.debug.info.LineInfo;

public class LinesDeobfuscationParser {
   private Map<String, DebugSectionParser> sectionParsers = new LinkedHashMap<>();
   private DebugLinesParser lines;

   public LinesDeobfuscationParser() {
      DebugStringParser strings = this.addSection(new DebugStringParser());
      DebugFileParser files = this.addSection(new DebugFileParser(strings));
      DebugPackageParser packages = this.addSection(new DebugPackageParser(strings));
      DebugClassParser classes = this.addSection(new DebugClassParser(strings, packages));
      DebugMethodParser methods = this.addSection(new DebugMethodParser(strings, classes));
      this.lines = this.addSection(new DebugLinesParser(files, methods));
   }

   private <T extends DebugSectionParser> T addSection(T section) {
      this.sectionParsers.put(section.name(), section);
      return section;
   }

   public boolean canHandleSection(String name) {
      return this.sectionParsers.keySet().contains(name);
   }

   public void applySection(String name, byte[] data) {
      DebugSectionParser parser = this.sectionParsers.get(name);
      if (parser != null) {
         parser.parse(data);
      }
   }

   public void pullSections(Function<String, byte[]> provider) {
      for (DebugSectionParser parser : this.sectionParsers.values()) {
         byte[] section = provider.apply(parser.name());
         if (section != null) {
            parser.parse(section);
         }
      }
   }

   public LineInfo getLineInfo() {
      return this.lines.getLineInfo();
   }
}
