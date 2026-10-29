package org.teavm.backend.wasm.render;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.teavm.backend.wasm.debug.DebugLines;
import org.teavm.backend.wasm.debug.DebugVariables;
import org.teavm.backend.wasm.generate.DwarfClassGenerator;
import org.teavm.backend.wasm.generate.DwarfGenerator;
import org.teavm.backend.wasm.model.WasmCompositeType;
import org.teavm.backend.wasm.model.WasmCustomSection;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmMemorySegment;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmTag;
import org.teavm.backend.wasm.model.WasmType;

public class WasmBinaryRenderer {
   private static final int SECTION_UNKNOWN = 0;
   private static final int SECTION_TYPE = 1;
   private static final int SECTION_IMPORT = 2;
   private static final int SECTION_FUNCTION = 3;
   private static final int SECTION_TABLE = 4;
   private static final int SECTION_MEMORY = 5;
   private static final int SECTION_GLOBAL = 6;
   private static final int SECTION_EXPORT = 7;
   private static final int SECTION_START = 8;
   private static final int SECTION_ELEMENT = 9;
   private static final int SECTION_CODE = 10;
   private static final int SECTION_DATA = 11;
   private static final int SECTION_TAGS = 13;
   private static final int EXTERNAL_KIND_FUNCTION = 0;
   private static final int EXTERNAL_KIND_MEMORY = 2;
   private static final int EXTERNAL_KIND_GLOBAL = 3;
   private static final int EXTERNAL_KIND_TAG = 4;
   private WasmBinaryWriter output;
   private WasmBinaryVersion version;
   private boolean obfuscated;
   private DwarfGenerator dwarfGenerator;
   private DwarfClassGenerator dwarfClassGen;
   private DebugLines debugLines;
   private DebugVariables debugVariables;
   private WasmBinaryStatsCollector statsCollector;

   public WasmBinaryRenderer(
      WasmBinaryWriter output,
      WasmBinaryVersion version,
      boolean obfuscated,
      DwarfGenerator dwarfGenerator,
      DwarfClassGenerator dwarfClassGen,
      DebugLines debugLines,
      DebugVariables debugVariables,
      WasmBinaryStatsCollector statsCollector
   ) {
      this.output = output;
      this.version = version;
      this.obfuscated = obfuscated;
      this.dwarfGenerator = dwarfGenerator;
      this.dwarfClassGen = dwarfClassGen;
      this.debugLines = debugLines;
      this.debugVariables = debugVariables;
      this.statsCollector = statsCollector;
   }

   public void render(WasmModule module) {
      this.render(module, Collections::emptyList);
   }

   public void render(WasmModule module, Supplier<Collection<? extends WasmCustomSection>> customSectionSupplier) {
      this.output.writeInt32(1836278016);
      switch (this.version) {
         case V_0x1:
            this.output.writeInt32(1);
         default:
            this.renderTypes(module);
            this.renderImports(module);
            this.renderFunctions(module);
            this.renderTable(module);
            if (module.memoryImportName == null) {
               this.renderMemory(module);
            }

            this.renderTags(module);
            this.renderGlobals(module);
            this.renderExport(module);
            this.renderStart(module);
            this.renderElement(module);
            this.renderCode(module);
            this.renderData(module);
            if (!this.obfuscated) {
               this.renderNames(module);
            }

            this.renderCustomSections(module, customSectionSupplier);
      }
   }

   private void renderTypes(WasmModule module) {
      WasmBinaryWriter section = new WasmBinaryWriter();
      WasmCompositeTypeBinaryRenderer typeRenderer = new WasmCompositeTypeBinaryRenderer(module, section);
      int recTypeCount = 0;

      for (int i = 0; i < module.types.size(); recTypeCount++) {
         WasmCompositeType type = module.types.get(i);
         if (type.getRecursiveTypeCount() > 0) {
            i += type.getRecursiveTypeCount();
         } else {
            i++;
         }
      }

      section.writeLEB(recTypeCount);
      int i = 0;

      while (i < module.types.size()) {
         WasmCompositeType type = module.types.get(i);
         if (type.getRecursiveTypeCount() > 0) {
            section.writeByte(78);
            section.writeLEB(type.getRecursiveTypeCount());

            for (int j = 0; j < type.getRecursiveTypeCount(); j++) {
               WasmCompositeType subtype = module.types.get(i++);
               subtype.acceptVisitor(typeRenderer);
            }
         } else {
            type.acceptVisitor(typeRenderer);
            i++;
         }
      }

      this.writeSection(1, "type", section.getData());
   }

   private void renderImports(WasmModule module) {
      ArrayList<WasmFunction> functions = new ArrayList<>();

      for (WasmFunction function : module.functions) {
         if (function.getImportName() != null) {
            functions.add(function);
         }
      }

      ArrayList<WasmGlobal> globals = new ArrayList<>();

      for (WasmGlobal global : module.globals) {
         if (global.getImportName() != null) {
            globals.add(global);
         }
      }

      if (!functions.isEmpty() || !globals.isEmpty() || module.memoryImportName != null) {
         WasmBinaryWriter section = new WasmBinaryWriter();
         int total = functions.size() + globals.size();
         if (module.memoryImportName != null) {
            total++;
         }

         section.writeLEB(total);
         if (module.memoryImportName != null) {
            String moduleName = module.memoryImportModule;
            if (moduleName == null) {
               moduleName = "";
            }

            section.writeAsciiString(moduleName);
            section.writeAsciiString(module.memoryImportName);
            section.writeByte(2);
            if (module.sharedMemory) {
               section.writeByte(3);
               section.writeLEB(module.getMinMemorySize());
               section.writeLEB(module.getMinMemorySize());
            } else if (module.getMaxMemorySize() == 0) {
               section.writeByte(0);
               section.writeLEB(module.getMinMemorySize());
            } else {
               section.writeByte(1);
               section.writeLEB(module.getMinMemorySize());
               section.writeLEB(Math.max(module.getMaxMemorySize(), module.getMinMemorySize()));
            }
         }

         for (WasmFunction function : functions) {
            int signatureIndex = module.types.indexOf(function.getType());
            String moduleName = function.getImportModule();
            if (moduleName == null) {
               moduleName = "";
            }

            section.writeAsciiString(moduleName);
            section.writeAsciiString(function.getImportName());
            section.writeByte(0);
            section.writeLEB(signatureIndex);
         }

         for (WasmGlobal global : globals) {
            String moduleName = global.getImportModule();
            if (moduleName == null) {
               moduleName = "";
            }

            section.writeAsciiString(moduleName);
            section.writeAsciiString(global.getImportName());
            section.writeByte(3);
            section.writeType(global.getType(), module);
            section.writeByte(global.isImmutable() ? 0 : 1);
         }

         this.writeSection(2, "import", section.getData());
      }
   }

   private void renderFunctions(WasmModule module) {
      WasmBinaryWriter section = new WasmBinaryWriter();
      List<WasmFunction> functions = module.functions.stream().filter(functionx -> functionx.getImportName() == null).collect(Collectors.toList());
      section.writeLEB(functions.size());

      for (WasmFunction function : functions) {
         section.writeLEB(module.types.indexOf(function.getType()));
      }

      this.writeSection(3, "function", section.getData());
   }

   private void renderTable(WasmModule module) {
      if (!module.getFunctionTable().isEmpty()) {
         WasmBinaryWriter section = new WasmBinaryWriter();
         section.writeByte(1);
         section.writeByte(112);
         section.writeByte(0);
         section.writeLEB(module.functions.size());
         this.writeSection(4, "table", section.getData());
      }
   }

   private void renderMemory(WasmModule module) {
      WasmBinaryWriter section = new WasmBinaryWriter();
      section.writeByte(1);
      if (module.sharedMemory) {
         section.writeByte(3);
         section.writeLEB(module.getMinMemorySize());
         section.writeLEB(module.getMinMemorySize());
      } else if (module.getMaxMemorySize() == 0) {
         section.writeByte(0);
         section.writeLEB(module.getMinMemorySize());
      } else {
         section.writeByte(1);
         section.writeLEB(module.getMinMemorySize());
         section.writeLEB(module.getMaxMemorySize());
      }

      this.writeSection(5, "memory", section.getData());
   }

   private void renderGlobals(WasmModule module) {
      List<WasmGlobal> globals = module.globals.stream().filter(globalx -> globalx.getImportName() == null).collect(Collectors.toList());
      if (!globals.isEmpty()) {
         WasmBinaryWriter section = new WasmBinaryWriter();
         WasmBinaryRenderingVisitor visitor = new WasmBinaryRenderingVisitor(section, module, null, null, 0);
         section.writeLEB(globals.size());

         for (WasmGlobal global : globals) {
            section.writeType(global.getType(), module);
            section.writeByte(global.isImmutable() ? 0 : 1);
            visitor.render(global.getInitialValue());
            section.writeByte(11);
         }

         this.writeSection(6, "global", section.getData());
      }
   }

   private void renderExport(WasmModule module) {
      List<WasmFunction> functions = module.functions.stream().filter(function -> function.getExportName() != null).collect(Collectors.toList());
      List<WasmTag> tags = module.tags.stream().filter(tag -> tag.getExportName() != null).collect(Collectors.toList());
      List<WasmGlobal> globals = module.globals.stream().filter(globalx -> globalx.getExportName() != null).collect(Collectors.toList());
      int total = functions.size() + tags.size() + globals.size();
      if (module.memoryExportName != null) {
         total++;
      }

      if (total != 0) {
         WasmBinaryWriter section = new WasmBinaryWriter();
         section.writeLEB(total);

         for (WasmFunction function : functions) {
            int functionIndex = module.functions.indexOf(function);
            section.writeAsciiString(function.getExportName());
            section.writeByte(0);
            section.writeLEB(functionIndex);
         }

         for (WasmTag tag : tags) {
            int tagIndex = module.tags.indexOf(tag);
            section.writeAsciiString(tag.getExportName());
            section.writeByte(4);
            section.writeLEB(tagIndex);
         }

         for (WasmGlobal global : globals) {
            int index = module.globals.indexOf(global);
            section.writeAsciiString(global.getExportName());
            section.writeByte(3);
            section.writeLEB(index);
         }

         if (module.memoryExportName != null) {
            section.writeAsciiString(module.memoryExportName);
            section.writeByte(2);
            section.writeLEB(0);
         }

         this.writeSection(7, "export", section.getData());
      }
   }

   private void renderStart(WasmModule module) {
      if (module.getStartFunction() != null) {
         WasmBinaryWriter section = new WasmBinaryWriter();
         section.writeLEB(module.functions.indexOf(module.getStartFunction()));
         this.writeSection(8, "start", section.getData());
      }
   }

   private void renderElement(WasmModule module) {
      int count = 0;
      if (!module.getFunctionTable().isEmpty()) {
         count++;
      }

      if (module.functions.stream().anyMatch(WasmFunction::isReferenced)) {
         count++;
      }

      if (count != 0) {
         WasmBinaryWriter section = new WasmBinaryWriter();
         section.writeLEB(count);
         if (!module.getFunctionTable().isEmpty()) {
            section.writeLEB(0);
            this.renderInitializer(section, 0);
            section.writeLEB(module.getFunctionTable().size());

            for (WasmFunction function : module.getFunctionTable()) {
               section.writeLEB(module.functions.indexOf(function));
            }
         }

         List<WasmFunction> referencedFunctions = module.functions.stream().filter(WasmFunction::isReferenced).collect(Collectors.toList());
         if (!referencedFunctions.isEmpty()) {
            section.writeLEB(3);
            section.writeByte(0);
            section.writeLEB(referencedFunctions.size());

            for (WasmFunction function : referencedFunctions) {
               section.writeLEB(module.functions.indexOf(function));
            }
         }

         this.writeSection(9, "element", section.getData());
      }
   }

   private void renderCode(WasmModule module) {
      WasmBinaryWriter section = new WasmBinaryWriter();
      List<WasmFunction> functions = module.functions.stream().filter(functionx -> functionx.getImportName() == null).collect(Collectors.toList());
      section.writeLEB(functions.size());
      int sectionOffset = this.output.getPosition() + 5;

      for (WasmFunction function : functions) {
         byte[] body = this.renderFunction(module, function, section.getPosition() + 4, sectionOffset);
         int startPos = section.getPosition();
         section.writeLEB4(body.length);
         section.writeBytes(body);
         int size = section.getPosition() - startPos;
         if (function.getJavaMethod() != null) {
            this.statsCollector.addClassCodeSize(function.getJavaMethod().getClassName(), size);
         }
      }

      if (this.dwarfGenerator != null) {
         this.dwarfGenerator.setCodeSize(section.getPosition());
      }

      this.writeSection(10, "code", section.getData(), true);
   }

   private byte[] renderFunction(WasmModule module, WasmFunction function, int offset, int sectionOffset) {
      WasmBinaryWriter code = new WasmBinaryWriter();
      DwarfClassGenerator.Subprogram dwarfSubprogram = this.dwarfClassGen != null ? this.dwarfClassGen.getSubprogram(function.getName()) : null;
      if (dwarfSubprogram != null) {
         dwarfSubprogram.startOffset = offset - 4;
         dwarfSubprogram.function = function;
      }

      if (this.debugLines != null && function.getJavaMethod() != null) {
         this.debugLines.advance(offset + sectionOffset);
         this.debugLines.start(function.getJavaMethod());
      }

      List<WasmLocal> localVariables = function.getLocalVariables();
      int parameterCount = Math.min(function.getType().getParameterTypes().size(), localVariables.size());
      localVariables = localVariables.subList(parameterCount, localVariables.size());
      if (localVariables.isEmpty()) {
         code.writeLEB(0);
      } else {
         ArrayList<WasmBinaryRenderer.LocalEntry> localEntries = new ArrayList<>();
         WasmBinaryRenderer.LocalEntry currentEntry = new WasmBinaryRenderer.LocalEntry(localVariables.get(0).getType());

         for (int i = 1; i < localVariables.size(); i++) {
            WasmType type = localVariables.get(i).getType();
            if (currentEntry.type == type) {
               currentEntry.count++;
            } else {
               localEntries.add(currentEntry);
               currentEntry = new WasmBinaryRenderer.LocalEntry(type);
            }
         }

         localEntries.add(currentEntry);
         code.writeLEB(localEntries.size());

         for (WasmBinaryRenderer.LocalEntry entry : localEntries) {
            code.writeLEB(entry.count);
            code.writeType(entry.type, module);
         }
      }

      WasmBinaryRenderingVisitor instructionRenderer = new WasmBinaryRenderingVisitor(
         code, module, function.getJavaMethod() != null ? this.debugLines : null, this.dwarfGenerator, offset + sectionOffset
      );
      instructionRenderer.render(function.getBody());
      code.writeByte(11);
      if (this.debugLines != null && function.getJavaMethod() != null) {
         this.debugLines.advance(code.getPosition() + offset + sectionOffset);
         this.debugLines.end();
      }

      if (dwarfSubprogram != null) {
         dwarfSubprogram.endOffset = code.getPosition() + offset;
      }

      if (this.debugVariables != null) {
         this.writeDebugVariables(function, offset + sectionOffset, code.getPosition());
      }

      return code.getData();
   }

   private void writeDebugVariables(WasmFunction function, int offset, int size) {
      this.debugVariables.startSequence(offset);

      for (WasmLocal local : function.getLocalVariables()) {
         if (local.getName() != null && local.getJavaType() != null) {
            this.debugVariables.type(local.getName(), local.getJavaType());
            this.debugVariables.range(local.getName(), offset, offset + size, local.getIndex());
         }
      }

      this.debugVariables.endSequence();
   }

   private void renderInitializer(WasmBinaryWriter output, int value) {
      output.writeByte(65);
      output.writeSignedLEB(value);
      output.writeByte(11);
   }

   private void renderData(WasmModule module) {
      if (!module.getSegments().isEmpty()) {
         WasmBinaryWriter section = new WasmBinaryWriter();
         section.writeLEB(module.getSegments().size());

         for (WasmMemorySegment segment : module.getSegments()) {
            section.writeByte(0);
            this.renderInitializer(section, segment.getOffset());
            section.writeLEB(segment.getLength());
            int chunkSize = 65536;

            for (int i = 0; i < segment.getLength(); i += chunkSize) {
               int next = Math.min(i + chunkSize, segment.getLength());
               section.writeBytes(segment.getData(i, next - i));
            }
         }

         this.writeSection(11, "data", section.getData());
      }
   }

   private void renderTags(WasmModule module) {
      if (!module.tags.isEmpty()) {
         WasmBinaryWriter section = new WasmBinaryWriter();
         section.writeLEB(module.tags.size());

         for (WasmTag tag : module.tags) {
            section.writeByte(0);
            section.writeLEB(module.types.indexOf(tag.getType()));
         }

         this.writeSection(13, "tags", section.getData());
      }
   }

   private void renderNames(WasmModule module) {
      WasmBinaryWriter section = new WasmBinaryWriter();
      WasmBinaryWriter functionsSubsection = new WasmBinaryWriter();
      List<WasmFunction> functions = module.functions.stream().filter(f -> f.getName() != null).collect(Collectors.toList());
      functionsSubsection.writeLEB(functions.size());

      for (WasmFunction function : functions) {
         functionsSubsection.writeLEB(module.functions.indexOf(function));
         functionsSubsection.writeAsciiString(function.getName());
      }

      byte[] payload = functionsSubsection.getData();
      section.writeLEB(1);
      section.writeLEB(payload.length);
      section.writeBytes(payload);
      List<WasmFunction> functionsWithLocalNames = module.functions
         .stream()
         .filter(fn -> fn.getLocalVariables().stream().anyMatch(v -> v.getName() != null))
         .collect(Collectors.toList());
      if (!functionsWithLocalNames.isEmpty()) {
         WasmBinaryWriter subsection = new WasmBinaryWriter();
         subsection.writeLEB(functionsWithLocalNames.size());

         for (WasmFunction function : functionsWithLocalNames) {
            subsection.writeLEB(module.functions.indexOf(function));
            List<WasmLocal> locals = function.getLocalVariables().stream().filter(t -> t.getName() != null).collect(Collectors.toList());
            subsection.writeLEB(locals.size());

            for (WasmLocal local : locals) {
               subsection.writeLEB(local.getIndex());
               subsection.writeAsciiString(local.getName());
            }
         }

         payload = subsection.getData();
         section.writeLEB(2);
         section.writeLEB(payload.length);
         section.writeBytes(payload);
      }

      List<WasmCompositeType> types = module.types.stream().filter(t -> t.getName() != null).collect(Collectors.toList());
      if (!types.isEmpty()) {
         WasmBinaryWriter typesSubsection = new WasmBinaryWriter();
         typesSubsection.writeLEB(types.size());

         for (WasmCompositeType type : types) {
            typesSubsection.writeLEB(module.types.indexOf(type));
            typesSubsection.writeAsciiString(type.getName());
         }

         payload = typesSubsection.getData();
         section.writeLEB(4);
         section.writeLEB(payload.length);
         section.writeBytes(payload);
      }

      List<WasmGlobal> globals = module.globals.stream().filter(g -> g.getName() != null).collect(Collectors.toList());
      if (!globals.isEmpty()) {
         WasmBinaryWriter globalsSubsection = new WasmBinaryWriter();
         globalsSubsection.writeLEB(globals.size());

         for (WasmGlobal global : globals) {
            globalsSubsection.writeLEB(module.globals.indexOf(global));
            globalsSubsection.writeAsciiString(global.getName());
         }

         payload = globalsSubsection.getData();
         section.writeLEB(7);
         section.writeLEB(payload.length);
         section.writeBytes(payload);
      }

      List<WasmCompositeType> typesWithNamedFields = module.types
         .stream()
         .filter(t -> t instanceof WasmStructure)
         .filter(t -> ((WasmStructure)t).getFields().stream().anyMatch(f -> f.getName() != null))
         .collect(Collectors.toList());
      if (!typesWithNamedFields.isEmpty()) {
         WasmBinaryWriter subsection = new WasmBinaryWriter();
         subsection.writeLEB(typesWithNamedFields.size());

         for (WasmCompositeType type : typesWithNamedFields) {
            subsection.writeLEB(module.types.indexOf(type));
            List<WasmField> fields = ((WasmStructure)type).getFields().stream().filter(t -> t.getName() != null).collect(Collectors.toList());
            subsection.writeLEB(fields.size());

            for (WasmField field : fields) {
               subsection.writeLEB(field.getIndex());
               subsection.writeAsciiString(field.getName());
            }
         }

         payload = subsection.getData();
         section.writeLEB(10);
         section.writeLEB(payload.length);
         section.writeBytes(payload);
      }

      this.writeSection(0, "name", section.getData());
   }

   private void renderCustomSections(WasmModule module, Supplier<Collection<? extends WasmCustomSection>> sectionSupplier) {
      for (WasmCustomSection customSection : module.getCustomSections().values()) {
         this.renderCustomSection(customSection);
      }

      if (sectionSupplier != null) {
         for (WasmCustomSection customSection : sectionSupplier.get()) {
            this.renderCustomSection(customSection);
         }
      }
   }

   private void renderCustomSection(WasmCustomSection customSection) {
      this.writeSection(0, customSection.getName(), customSection.getData());
   }

   private void writeSection(int id, String name, byte[] data) {
      this.writeSection(id, name, data, false);
   }

   private void writeSection(int id, String name, byte[] data, boolean constantSizeLength) {
      int start = this.output.getPosition();
      this.output.writeByte(id);
      int length = data.length;
      if (id == 0) {
         length += name.length() + 1;
      }

      if (constantSizeLength) {
         this.output.writeLEB4(length);
      } else {
         this.output.writeLEB(length);
      }

      if (id == 0) {
         this.output.writeAsciiString(name);
      }

      this.output.writeBytes(data);
      this.statsCollector.addSectionSize(name, this.output.getPosition() - start);
   }

   static class LocalEntry {
      WasmType type;
      int count = 1;

      LocalEntry(WasmType type) {
         this.type = type;
      }
   }
}
