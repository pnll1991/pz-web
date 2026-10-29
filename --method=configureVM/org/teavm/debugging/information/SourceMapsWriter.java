package org.teavm.debugging.information;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import org.teavm.common.JsonUtil;

public class SourceMapsWriter {
   private static final String BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
   private Writer output;
   private int lastLine;
   private int lastColumn;
   private int lastSourceLine;
   private int lastSourceFile;
   private boolean first;
   private List<SourceFileResolver> sourceFileResolvers = new ArrayList<>();

   public SourceMapsWriter(Writer output) {
      this.output = output;
   }

   public void addSourceResolver(SourceFileResolver sourceFileResolver) {
      this.sourceFileResolvers.add(sourceFileResolver);
   }

   public void write(String generatedFile, String sourceRoot, DebugInformation debugInfo) throws IOException {
      this.output.write("{\"version\":3");
      this.output.write(",\"file\":\"");
      JsonUtil.writeEscapedString(this.output, generatedFile);
      this.output.write("\"");
      this.output.write(",\"sourceRoot\":\"");
      JsonUtil.writeEscapedString(this.output, sourceRoot);
      this.output.write("\"");
      this.output.write(",\"sources\":[");

      for (int i = 0; i < debugInfo.fileNames.length; i++) {
         if (i > 0) {
            this.output.write(44);
         }

         this.output.write("\"");
         String name = debugInfo.fileNames[i];

         for (SourceFileResolver resolver : this.sourceFileResolvers) {
            String resolvedName = resolver.resolveFile(name);
            if (resolvedName != null) {
               name = resolvedName;
               break;
            }
         }

         JsonUtil.writeEscapedString(this.output, name);
         this.output.write("\"");
      }

      this.output.write("]");
      this.output.write(",\"names\":[]");
      this.output.write(",\"mappings\":\"");
      this.first = true;
      this.lastLine = 0;
      this.lastColumn = 0;
      this.lastSourceFile = 0;
      this.lastSourceLine = 0;
      SourceLocationIterator iter = debugInfo.iterateOverSourceLocations();

      while (!iter.isEndReached()) {
         this.writeSegment(iter.getLocation(), iter.getFileNameId(), iter.getLine() - 1);
         iter.next();
      }

      this.output.write("\"}");
   }

   private void writeSegment(GeneratedLocation loc, int sourceFile, int sourceLine) throws IOException {
      while (loc.getLine() > this.lastLine) {
         this.output.write(59);
         this.lastLine++;
         this.first = true;
         this.lastColumn = 0;
      }

      if (!this.first) {
         this.output.write(44);
      }

      this.writeVLQ(loc.getColumn() - this.lastColumn);
      if (sourceFile >= 0 && sourceLine >= 0) {
         this.writeVLQ(sourceFile - this.lastSourceFile);
         this.writeVLQ(sourceLine - this.lastSourceLine);
         this.writeVLQ(0);
         this.lastSourceFile = sourceFile;
         this.lastSourceLine = sourceLine;
      }

      this.lastColumn = loc.getColumn();
      this.first = false;
   }

   private void writeVLQ(int number) throws IOException {
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

         this.output.write("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".charAt(digit));
         number = next;
      } while (number != 0);
   }
}
