package org.teavm.backend.wasm.debug.sourcemap;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import org.teavm.backend.wasm.debug.DebugLines;
import org.teavm.common.JsonUtil;
import org.teavm.debugging.information.SourceFileResolver;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.model.MethodReference;

public class SourceMapBuilder implements DebugLines {
   private static final String BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
   private static final String IGNORE_FILE = "__ignore__";
   private List<String> fileNames = new ArrayList<>();
   private ObjectIntMap<String> fileNameIndexes = new ObjectIntHashMap();
   private int ptr;
   private StringBuilder mappings = new StringBuilder();
   private String currentFile;
   private int currentLine;
   private String lastWrittenFile;
   private int lastWrittenLine;
   private boolean pendingLocation;
   private Deque<SourceMapBuilder.InlineState> inlineStack = new ArrayDeque<>();
   private List<SourceFileResolver> sourceFileResolvers = new ArrayList<>();
   private String lastFileInMappings;
   private int lastLineInMappings;
   private int lastPtrInMappings;

   public void addSourceResolver(SourceFileResolver sourceFileResolver) {
      this.sourceFileResolvers.add(sourceFileResolver);
   }

   public void writeSourceMap(Writer output) throws IOException {
      output.write("{\"version\":3");
      List<String> files = this.resolveFiles();
      if (!files.isEmpty()) {
         String commonPrefix = files.get(0);
         int commonPrefixLength = commonPrefix.length();

         for (int i = 1; i < files.size(); i++) {
            String file = files.get(i);
            commonPrefixLength = Math.min(file.length(), commonPrefixLength);

            for (int j = 0; j < commonPrefixLength; j++) {
               if (commonPrefix.charAt(j) != file.charAt(j)) {
                  commonPrefixLength = j;
                  break;
               }
            }

            if (commonPrefixLength == 0) {
               break;
            }
         }

         if (commonPrefixLength > 0) {
            for (int i = 0; i < files.size(); i++) {
               files.set(i, files.get(i).substring(commonPrefixLength));
            }

            output.write(",\"sourceRoot\":\"");
            JsonUtil.writeEscapedString(output, commonPrefix.substring(0, commonPrefixLength));
            output.write("\"");
         }
      }

      output.write(",\"sources\":[");

      for (int i = 0; i < files.size(); i++) {
         if (i > 0) {
            output.write(44);
         }

         output.write("\"");
         String name = files.get(i);
         JsonUtil.writeEscapedString(output, name);
         output.write("\"");
      }

      output.write("]");
      output.write(",\"names\":[]");
      output.write(",\"mappings\":\"");
      output.write(this.mappings.toString());
      output.write("\"");
      int ignoreFileIndex = this.fileNameIndexes.getOrDefault("__ignore__", -1);
      if (ignoreFileIndex > 0) {
         output.write(",\"x_google_ignoreList\":[" + ignoreFileIndex + "]");
      }

      output.write("}");
   }

   private List<String> resolveFiles() throws IOException {
      ArrayList<String> result = new ArrayList<>();

      for (String file : this.fileNames) {
         String resolvedFile = file;

         for (SourceFileResolver resolver : this.sourceFileResolvers) {
            String candidate = resolver.resolveFile(file);
            if (candidate != null) {
               resolvedFile = candidate;
               break;
            }
         }

         result.add(resolvedFile);
      }

      return result;
   }

   @Override
   public void advance(int ptr) {
      if (ptr != this.ptr) {
         if (this.pendingLocation) {
            this.pendingLocation = false;
            if (!Objects.equals(this.currentFile, this.lastWrittenFile) || this.currentLine != this.lastWrittenLine) {
               this.lastWrittenFile = this.currentFile;
               this.lastWrittenLine = this.currentLine;
               this.writeMapping(this.currentFile, this.currentLine, this.ptr);
            }
         }

         this.ptr = ptr;
      }
   }

   @Override
   public void location(String file, int line) {
      if (file == null) {
         file = "__ignore__";
         line = 1;
      }

      this.currentFile = file;
      this.currentLine = line;
      this.pendingLocation = true;
   }

   @Override
   public void emptyLocation() {
      this.currentLine = 1;
      this.currentFile = "__ignore__";
      this.pendingLocation = true;
   }

   @Override
   public void start(MethodReference methodReference) {
      this.inlineStack.push(new SourceMapBuilder.InlineState(this.currentFile, this.currentLine));
   }

   @Override
   public void end() {
      SourceMapBuilder.InlineState state = this.inlineStack.pop();
      this.location(state.file, state.line);
   }

   private void writeMapping(String file, int line, int ptr) {
      if (this.mappings.length() > 0) {
         this.mappings.append(",");
      }

      this.writeVLQ(ptr - this.lastPtrInMappings);
      if (file != null && line > 0) {
         line--;
         int lastFileIndex = this.fileNameIndexes.get(this.lastFileInMappings);
         int fileIndex = this.fileNameIndexes.getOrDefault(file, -1);
         if (fileIndex < 0) {
            fileIndex = this.fileNames.size();
            this.fileNames.add(file);
            this.fileNameIndexes.put(file, fileIndex);
         }

         this.writeVLQ(fileIndex - lastFileIndex);
         this.writeVLQ(line - this.lastLineInMappings);
         this.writeVLQ(0);
         this.lastLineInMappings = line;
         this.lastFileInMappings = file;
      }

      this.lastPtrInMappings = ptr;
   }

   private void writeVLQ(int number) {
      if (number < 0) {
         number = -number << 1 | 1;
      } else {
         number <<= 1;
      }

      do {
         int digit = number & 31;
         int next = number >>> 5;
         if (next != 0) {
            digit |= 32;
         }

         this.mappings.append("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".charAt(digit));
         number = next;
      } while (number != 0);
   }

   private static class InlineState {
      String file;
      int line;

      InlineState(String file, int line) {
         this.file = file;
         this.line = line;
      }
   }
}
