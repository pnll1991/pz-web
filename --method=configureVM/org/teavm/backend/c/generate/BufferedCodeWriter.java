package org.teavm.backend.c.generate;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BufferedCodeWriter extends CodeWriter {
   private List<BufferedCodeWriter.Fragment> fragments = new ArrayList<>();
   private int currentIndent;
   private int lastIndent;
   private StringBuilder buffer = new StringBuilder();
   private boolean lineNumbersEmitted;
   private String lastFileName;
   private int lastLineNumber;
   private boolean locationDirty;

   public BufferedCodeWriter(boolean lineNumbersEmitted) {
      this.lineNumbersEmitted = lineNumbersEmitted;
   }

   public void writeTo(PrintWriter writer, String fileName) {
      BufferedCodeWriter.WriterWithContext writerWithContext = new BufferedCodeWriter.WriterWithContext(writer, fileName);

      for (BufferedCodeWriter.Fragment fragment : this.fragments) {
         fragment.writeTo(writerWithContext);
      }
   }

   @Override
   public CodeWriter fragment() {
      this.flush();
      BufferedCodeWriter innerWriter = new BufferedCodeWriter(this.lineNumbersEmitted);
      innerWriter.lastFileName = this.lastFileName;
      innerWriter.lastLineNumber = this.lastLineNumber;
      innerWriter.locationDirty = this.locationDirty;
      this.fragments.add(new BufferedCodeWriter.InnerWriterFragment(innerWriter.fragments));
      this.locationDirty = true;
      return innerWriter;
   }

   @Override
   protected void newLine() {
      this.fragments.add(new BufferedCodeWriter.SimpleFragment(true, this.lastIndent, this.buffer.toString()));
      this.buffer.setLength(0);
      this.lastIndent = this.currentIndent;
      this.currentIndent = 0;
      if (this.lineNumbersEmitted) {
         this.lastLineNumber++;
      }
   }

   @Override
   protected void append(String text) {
      this.buffer.append(text);
   }

   @Override
   protected void indentBy(int amount) {
      if (this.buffer.length() == 0) {
         this.lastIndent += amount;
      } else {
         this.currentIndent += amount;
      }
   }

   @Override
   public void source(String fileName, int lineNumber) {
      if (this.lineNumbersEmitted) {
         if (!Objects.equals(this.lastFileName, fileName) || this.lastLineNumber != lineNumber || this.locationDirty) {
            this.flush();
            this.fragments.add(new BufferedCodeWriter.SourceFragment(fileName, lineNumber));
            this.lastFileName = fileName;
            this.lastLineNumber = lineNumber;
            this.locationDirty = false;
         }
      }
   }

   @Override
   public void nosource() {
      this.source(null, 0);
   }

   @Override
   public void flush() {
      if (this.buffer.length() > 0 || this.lastIndent != 0 || this.currentIndent != 0) {
         this.fragments.add(new BufferedCodeWriter.SimpleFragment(false, this.lastIndent, this.buffer.toString()));
         this.lastIndent = this.currentIndent;
         this.currentIndent = 0;
         this.buffer.setLength(0);
      }
   }

   private static void escape(PrintWriter writer, String string) {
      int chunkSize = 256;

      for (int i = 0; i < string.length(); i += chunkSize) {
         int last = Math.min(i + chunkSize, string.length());

         for (int j = i; j < last; j++) {
            char c = string.charAt(j);
            switch (c) {
               case '\t':
                  writer.print("\\t");
                  break;
               case '\n':
                  writer.print("\\n");
                  break;
               case '\r':
                  writer.print("\\r");
                  break;
               case '"':
                  writer.print("\\\"");
                  break;
               case '\\':
                  writer.print("\\\\");
                  break;
               default:
                  if (c < ' ') {
                     writer.print("\\0" + Character.forDigit(c >> 3, 8) + Character.forDigit(c & 7, 8));
                  } else if (c > 127) {
                     writer.print(
                        "\\u"
                           + Character.forDigit(c >> '\f', 16)
                           + Character.forDigit(c >> '\b' & 15, 16)
                           + Character.forDigit(c >> 4 & 15, 16)
                           + Character.forDigit(c & 15, 16)
                     );
                  } else {
                     writer.print(c);
                  }
            }
         }
      }
   }

   abstract static class Fragment {
      abstract void writeTo(BufferedCodeWriter.WriterWithContext var1);
   }

   static class InnerWriterFragment extends BufferedCodeWriter.Fragment {
      List<BufferedCodeWriter.Fragment> fragments;

      InnerWriterFragment(List<BufferedCodeWriter.Fragment> fragments) {
         this.fragments = fragments;
      }

      @Override
      void writeTo(BufferedCodeWriter.WriterWithContext writer) {
         for (BufferedCodeWriter.Fragment fragment : this.fragments) {
            fragment.writeTo(writer);
         }
      }
   }

   static class SimpleFragment extends BufferedCodeWriter.Fragment {
      boolean newLine;
      int indentLevel;
      String text;

      SimpleFragment(boolean newLine, int indentLevel, String text) {
         this.newLine = newLine;
         this.indentLevel = indentLevel;
         this.text = text;
      }

      @Override
      void writeTo(BufferedCodeWriter.WriterWithContext writer) {
         writer.indentLevel = writer.indentLevel + this.indentLevel;
         writer.append(this.text);
         if (this.newLine) {
            writer.newLine();
         }
      }
   }

   static class SourceFragment extends BufferedCodeWriter.Fragment {
      private String fileName;
      private int lineNumber;

      SourceFragment(String fileName, int lineNumber) {
         this.fileName = fileName;
         this.lineNumber = lineNumber;
      }

      @Override
      void writeTo(BufferedCodeWriter.WriterWithContext writer) {
         writer.source(this.fileName, this.lineNumber);
      }
   }

   static class WriterWithContext {
      PrintWriter writer;
      boolean isNewLine = true;
      int indentLevel;
      String initialFileName;
      String fileName;
      int lineNumber;
      int absLineNumber = 1;
      String pendingFileName;
      int pendingLineNumber = -1;

      WriterWithContext(PrintWriter writer, String fileName) {
         this.writer = writer;
         this.fileName = fileName;
         this.initialFileName = fileName;
         this.lineNumber = 1;
      }

      void append(String text) {
         if (!text.isEmpty()) {
            if (this.isNewLine) {
               if (this.pendingFileName != null && this.pendingLineNumber >= 0) {
                  this.printLineDirective(this.pendingFileName, this.pendingLineNumber);
                  this.pendingLineNumber = -1;
                  this.pendingFileName = null;
               }

               this.printIndent();
               this.isNewLine = false;
            }

            this.writer.print(text);
         }
      }

      private void printLineDirective(String fileName, int lineNumber) {
         if (!Objects.equals(this.fileName, fileName) || lineNumber != this.lineNumber) {
            this.printIndent();
            this.writer.print("#line ");
            if (Objects.equals(fileName, this.initialFileName)) {
               lineNumber = this.absLineNumber + 1;
            }

            this.writer.print(lineNumber);
            if (!Objects.equals(fileName, this.fileName)) {
               this.writer.print(" \"");
               BufferedCodeWriter.escape(this.writer, fileName);
               this.writer.print("\"");
            }

            this.writer.println();
            this.absLineNumber++;
            this.fileName = fileName;
            this.lineNumber = lineNumber;
         }
      }

      void newLine() {
         this.lineNumber++;
         this.absLineNumber++;
         this.writer.println();
         this.isNewLine = true;
      }

      private void printIndent() {
         for (int i = 0; i < this.indentLevel; i++) {
            this.writer.print("    ");
         }
      }

      void source(String fileName, int lineNumber) {
         if (fileName == null) {
            fileName = this.initialFileName;
            lineNumber = this.absLineNumber;
         }

         if (this.isNewLine) {
            this.pendingFileName = fileName;
            this.pendingLineNumber = lineNumber;
         } else if (!Objects.equals(this.fileName, fileName) || this.lineNumber != lineNumber) {
            this.lineNumber++;
            this.absLineNumber++;
            this.writer.println();
            this.printLineDirective(fileName, lineNumber);
            this.isNewLine = true;
         }
      }
   }
}
