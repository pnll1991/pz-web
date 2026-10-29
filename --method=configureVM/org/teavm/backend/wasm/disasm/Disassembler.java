package org.teavm.backend.wasm.disasm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.teavm.backend.wasm.debug.info.LineInfo;
import org.teavm.backend.wasm.debug.parser.DebugClassParser;
import org.teavm.backend.wasm.debug.parser.DebugFileParser;
import org.teavm.backend.wasm.debug.parser.DebugLinesParser;
import org.teavm.backend.wasm.debug.parser.DebugMethodParser;
import org.teavm.backend.wasm.debug.parser.DebugPackageParser;
import org.teavm.backend.wasm.debug.parser.DebugSectionParser;
import org.teavm.backend.wasm.debug.parser.DebugStringParser;
import org.teavm.backend.wasm.parser.AddressListener;
import org.teavm.backend.wasm.parser.CodeSectionParser;
import org.teavm.backend.wasm.parser.FunctionSectionListener;
import org.teavm.backend.wasm.parser.FunctionSectionParser;
import org.teavm.backend.wasm.parser.GlobalSectionParser;
import org.teavm.backend.wasm.parser.ImportSectionParser;
import org.teavm.backend.wasm.parser.ModuleParser;
import org.teavm.backend.wasm.parser.NameSectionListener;
import org.teavm.backend.wasm.parser.NameSectionParser;
import org.teavm.backend.wasm.parser.TypeSectionParser;
import org.teavm.backend.wasm.parser.WasmHollowFunctionType;
import org.teavm.common.AsyncInputStream;
import org.teavm.common.ByteArrayAsyncInputStream;
import org.teavm.hppc.IntArrayList;

public final class Disassembler {
   private DisassemblyWriter writer;
   private WasmHollowFunctionType[] functionTypes;
   private int[] functionTypeRefs;
   private int importFunctionCount;
   private int importGlobalCount;
   private Map<String, DebugSectionParser> debugSectionParsers = new HashMap<>();
   private DebugLinesParser debugLines;
   private LineInfo lineInfo;

   public Disassembler(DisassemblyWriter writer) {
      this.writer = writer;
      this.installDebugParsers();
   }

   private void installDebugParsers() {
      DebugStringParser strings = this.addDebugSection(new DebugStringParser());
      DebugFileParser files = this.addDebugSection(new DebugFileParser(strings));
      DebugPackageParser packages = this.addDebugSection(new DebugPackageParser(strings));
      DebugClassParser classes = this.addDebugSection(new DebugClassParser(strings, packages));
      DebugMethodParser methods = this.addDebugSection(new DebugMethodParser(strings, classes));
      this.debugLines = this.addDebugSection(new DebugLinesParser(files, methods));
   }

   private <T extends DebugSectionParser> T addDebugSection(T section) {
      this.debugSectionParsers.put(section.name(), section);
      return section;
   }

   public void startModule() {
      this.writer.write("(module").indent().eol();
   }

   public void endModule() {
      this.writer.write(")").eol();
   }

   public void disassemble(byte[] bytes) {
      this.writer.prologue();
      this.startModule();
      this.read(bytes);
      this.endModule();
      this.writer.epilogue();
   }

   public void read(byte[] bytes) {
      NameAccumulatingSectionListener nameAccumulator = new NameAccumulatingSectionListener();
      ByteArrayAsyncInputStream input = new ByteArrayAsyncInputStream(bytes);
      ModuleParser preparationParser = this.createPreparationParser(input, nameAccumulator);
      input.readFully(preparationParser::parse);
      this.lineInfo = this.debugLines.getLineInfo();
      input = new ByteArrayAsyncInputStream(bytes);
      ModuleParser parser = this.createParser(input, nameAccumulator.buildProvider());
      input.readFully(parser::parse);
   }

   public ModuleParser createPreparationParser(AsyncInputStream input, final NameSectionListener listener) {
      return new ModuleParser(input) {
         @Override
         protected Consumer<byte[]> getSectionConsumer(int code, int pos, String name) {
            if (code == 0) {
               DebugSectionParser debugSection = Disassembler.this.debugSectionParsers.get(name);
               if (debugSection != null) {
                  return debugSection::parse;
               }
            }

            return Disassembler.this.getNameSectionConsumer(code, name, listener);
         }
      };
   }

   public ModuleParser createParser(AsyncInputStream input, final NameProvider nameProvider) {
      return new ModuleParser(input) {
         @Override
         protected Consumer<byte[]> getSectionConsumer(int code, int pos, String name) {
            return Disassembler.this.getSectionConsumer(code, pos, nameProvider);
         }
      };
   }

   public Consumer<byte[]> getSectionConsumer(int code, int pos, NameProvider nameProvider) {
      if (code == 1) {
         return bytes -> {
            this.writer.write("(; type section size: " + bytes.length + " ;)").eol();
            DisassemblyTypeSectionListener typeWriter = new DisassemblyTypeSectionListener(this.writer, nameProvider);
            this.writer.setAddressOffset(pos);
            TypeSectionParser sectionParser = new TypeSectionParser(typeWriter);
            sectionParser.parse(this.writer.addressListener, bytes);
            this.functionTypes = typeWriter.getFunctionTypes();
            this.writer.flush();
         };
      } else if (code == 2) {
         return bytes -> {
            DisassemblyImportSectionListener importListener = new DisassemblyImportSectionListener(this.writer, nameProvider, this.functionTypes);
            ImportSectionParser parser = new ImportSectionParser(importListener);
            parser.parse(AddressListener.EMPTY, bytes);
            this.importFunctionCount = importListener.functionCount();
            this.importGlobalCount = importListener.globalCount();
         };
      } else if (code == 3) {
         return bytes -> {
            final IntArrayList signatures = new IntArrayList();

            for (int i = 0; i < this.importFunctionCount; i++) {
               signatures.add(0);
            }

            FunctionSectionParser parser = new FunctionSectionParser(new FunctionSectionListener() {
               @Override
               public void function(int index, int typeIndex) {
                  signatures.add(typeIndex);
               }
            });
            parser.parse(AddressListener.EMPTY, bytes);
            this.functionTypeRefs = signatures.toArray();
         };
      } else if (code == 6) {
         return bytes -> {
            this.writer.write("(; global section size: " + bytes.length + " ;)").eol();
            DisassemblyGlobalSectionListener globalWriter = new DisassemblyGlobalSectionListener(this.writer, nameProvider);
            this.writer.setAddressOffset(pos);
            GlobalSectionParser sectionParser = new GlobalSectionParser(globalWriter);
            sectionParser.setGlobalIndexOffset(this.importGlobalCount);
            sectionParser.parse(this.writer.addressListener, bytes);
            this.writer.flush();
         };
      } else {
         return code == 10 ? bytes -> {
            DisassemblyCodeSectionListener disassembler = new DisassemblyCodeSectionListener(this.writer, nameProvider);
            disassembler.setFunctionTypes(this.functionTypes);
            disassembler.setFunctionTypeRefs(this.functionTypeRefs);
            this.writer.setAddressOffset(pos);
            this.writer.setDebugLines(this.lineInfo);
            this.writer.startSection();
            this.writer.write("(; code section size: " + bytes.length + " ;)").eol();
            CodeSectionParser sectionParser = new CodeSectionParser(disassembler);
            sectionParser.setFunctionIndexOffset(this.importFunctionCount);
            sectionParser.parse(this.writer.addressListener, bytes);
            this.writer.flush();
            this.writer.setDebugLines(null);
         } : null;
      }
   }

   public Consumer<byte[]> getNameSectionConsumer(int code, String name, NameSectionListener listener) {
      return code == 0 && name.equals("name") ? bytes -> {
         NameSectionParser parser = new NameSectionParser(listener);
         parser.parse(AddressListener.EMPTY, bytes);
      } : null;
   }

   public static void main(String[] args) throws IOException {
      String fileName = null;
      String outFileName = null;
      boolean htmlMode = false;

      for (int i = 0; i < args.length; i++) {
         String arg = args[i];
         if (arg.equals("--html")) {
            htmlMode = true;
         } else if (!arg.equals("--output") && !arg.equals("-o")) {
            fileName = arg;
         } else {
            outFileName = args[++i];
         }
      }

      File file = new File(fileName);
      byte[] bytes = Files.readAllBytes(file.toPath());
      OutputStream output = outFileName != null ? new FileOutputStream(outFileName) : System.out;
      PrintWriter writer = new PrintWriter(output);
      DisassemblyWriter disassemblyWriter = htmlMode ? new DisassemblyHTMLWriter(writer) : new DisassemblyTextWriter(writer);
      disassemblyWriter.setWithAddress(true);
      if (htmlMode) {
         disassemblyWriter.write("<html><body><pre>").eol();
      }

      Disassembler disassembler = new Disassembler(disassemblyWriter);
      disassembler.disassemble(bytes);
      if (htmlMode) {
         disassemblyWriter.write("</pre></body></html>").eol();
      }
   }
}
