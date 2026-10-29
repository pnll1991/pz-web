package org.teavm.backend.wasm.debug.parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.teavm.backend.wasm.debug.info.ControlFlowInfo;
import org.teavm.backend.wasm.debug.info.DebugInfo;
import org.teavm.backend.wasm.parser.CodeSectionParser;
import org.teavm.backend.wasm.parser.ModuleParser;
import org.teavm.common.AsyncInputStream;
import org.teavm.common.ByteArrayAsyncInputStream;

public class DebugInfoParser extends ModuleParser {
   private Map<String, DebugSectionParser> sectionParsers = new HashMap<>();
   private DebugLinesParser lines;
   private DebugVariablesParser variables;
   private ControlFlowInfo controlFlow;
   private DebugClassLayoutParser classLayoutInfo;
   private int offset;

   public DebugInfoParser(AsyncInputStream reader) {
      super(reader);
      DebugStringParser strings = this.addSection(new DebugStringParser());
      DebugFileParser files = this.addSection(new DebugFileParser(strings));
      DebugPackageParser packages = this.addSection(new DebugPackageParser(strings));
      DebugClassParser classes = this.addSection(new DebugClassParser(strings, packages));
      DebugMethodParser methods = this.addSection(new DebugMethodParser(strings, classes));
      this.variables = this.addSection(new DebugVariablesParser(strings));
      this.lines = this.addSection(new DebugLinesParser(files, methods));
      this.classLayoutInfo = this.addSection(new DebugClassLayoutParser(strings, classes));
   }

   private <T extends DebugSectionParser> T addSection(T section) {
      this.sectionParsers.put(section.name(), section);
      return section;
   }

   public DebugInfo getDebugInfo() {
      return new DebugInfo(this.variables.getVariablesInfo(), this.lines.getLineInfo(), this.controlFlow, this.classLayoutInfo.getInfo(), this.offset);
   }

   @Override
   protected Consumer<byte[]> getSectionConsumer(int code, int pos, String name) {
      if (code == 0) {
         DebugSectionParser parser = this.sectionParsers.get(name);
         return parser != null ? parser::parse : null;
      } else if (code == 10) {
         this.offset = pos;
         return this::parseCode;
      } else {
         return null;
      }
   }

   private void parseCode(byte[] data) {
      ControlFlowParser builder = new ControlFlowParser();
      CodeSectionParser codeParser = new CodeSectionParser(builder);
      codeParser.parse(builder, data);
      this.controlFlow = builder.build();
   }

   public static void main(String[] args) throws IOException {
      if (args.length != 1) {
         System.err.println("Pass single argument - path to wasm file");
         System.exit(1);
      }

      File file = new File(args[0]);
      ByteArrayAsyncInputStream input = new ByteArrayAsyncInputStream(Files.readAllBytes(file.toPath()));
      DebugInfoParser parser = new DebugInfoParser(input);
      input.readFully(parser::parse);
      DebugInfo debugInfo = parser.getDebugInfo();
      if (debugInfo != null) {
         debugInfo.dump(System.out);
      } else {
         System.out.println("No debug information found");
      }
   }
}
