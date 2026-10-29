package org.teavm.backend.javascript.codegen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import org.teavm.debugging.information.DebugInformationEmitter;
import org.teavm.debugging.information.DummyDebugInformationEmitter;
import org.teavm.hppc.IntIntHashMap;
import org.teavm.hppc.IntIntMap;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.hppc.cursors.ObjectCursor;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public class OutputSourceWriter extends SourceWriter implements LocationProvider {
   private static final int LET_SEQUENCE_LIMIT = 50;
   private final Appendable innerWriter;
   private int indentSize;
   private final NamingStrategy naming;
   private boolean lineStart;
   private boolean minified;
   private final int lineWidth;
   private int column;
   private int line;
   private int offset;
   private DebugInformationEmitter debugInformationEmitter = new DummyDebugInformationEmitter();
   private String classMarkClass;
   private int classMarkPos;
   private ObjectIntMap<String> classSizes = new ObjectIntHashMap();
   private int sectionMarkSection = -1;
   private int sectionMarkPos;
   private IntIntMap sectionSizes = new IntIntHashMap();
   private OutputSourceWriter.DeclarationType currentDeclarationType;
   private boolean expectingDeclarationName;
   private int letSequenceSize;

   OutputSourceWriter(NamingStrategy naming, Appendable innerWriter, int lineWidth) {
      this.naming = naming;
      this.innerWriter = innerWriter;
      this.lineWidth = lineWidth;
   }

   public void setDebugInformationEmitter(DebugInformationEmitter debugInformationEmitter) {
      this.debugInformationEmitter = debugInformationEmitter;
      debugInformationEmitter.setLocationProvider(this);
   }

   void setMinified(boolean minified) {
      this.minified = minified;
   }

   public void finish() {
      this.finishLet();
   }

   private void finishLetImplicitly() {
      if (this.currentDeclarationType == null) {
         this.finishLet();
      }
   }

   private void finishLet() {
      if (this.letSequenceSize > 0) {
         this.letSequenceSize = 0;

         try {
            this.innerWriter.append(";\n");
            this.column = 0;
            this.line++;
            this.lineStart = true;
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }
   }

   @Override
   public SourceWriter append(char value) {
      this.appendIndent();
      this.finishLetImplicitly();

      try {
         this.innerWriter.append(value);
      } catch (IOException e) {
         throw new RuntimeException(e);
      }

      if (value == '\n') {
         this.newLine();
      } else {
         this.column++;
         this.offset++;
      }

      return this;
   }

   @Override
   public SourceWriter append(CharSequence csq, int start, int end) {
      int last = start;

      for (int i = start; i < end; i++) {
         if (csq.charAt(i) == '\n') {
            this.appendSingleLine(csq, last, i);
            this.newLine();
            last = i + 1;
         }
      }

      this.appendSingleLine(csq, last, end);
      return this;
   }

   private void appendSingleLine(CharSequence csq, int start, int end) {
      if (start != end) {
         this.finishLetImplicitly();
         this.appendIndent();
         this.column += end - start;
         this.offset += end - start;

         try {
            this.innerWriter.append(csq, start, end);
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }
   }

   @Override
   public SourceWriter appendClass(String cls) {
      return this.appendDeclaration(this.naming.className(cls));
   }

   @Override
   public SourceWriter appendField(FieldReference field) {
      return this.append(this.naming.instanceFieldName(field));
   }

   @Override
   public SourceWriter appendStaticField(FieldReference field) {
      return this.appendDeclaration(this.naming.fieldName(field));
   }

   @Override
   public SourceWriter appendVirtualMethod(MethodDescriptor method) {
      return this.append(this.naming.instanceMethodName(method));
   }

   @Override
   public SourceWriter appendMethod(MethodReference method) {
      return this.appendDeclaration(this.naming.methodName(method));
   }

   @Override
   public SourceWriter appendFunction(String name) {
      return this.appendDeclaration(this.naming.functionName(name));
   }

   @Override
   public SourceWriter appendGlobal(String name) {
      return this.append(name);
   }

   @Override
   public SourceWriter appendInit(MethodReference method) {
      return this.appendDeclaration(this.naming.initializerName(method));
   }

   @Override
   public SourceWriter appendClassInit(String className) {
      return this.appendDeclaration(this.naming.classInitializerName(className));
   }

   @Override
   public SourceWriter startVariableDeclaration() {
      this.checkStartDeclaration();
      this.currentDeclarationType = OutputSourceWriter.DeclarationType.VARIABLE;
      this.expectingDeclarationName = true;
      return this;
   }

   @Override
   public SourceWriter startFunctionDeclaration() {
      this.checkStartDeclaration();
      this.currentDeclarationType = OutputSourceWriter.DeclarationType.FUNCTION;
      this.expectingDeclarationName = true;
      return this;
   }

   @Override
   public SourceWriter declareVariable() {
      this.checkStartDeclaration();
      this.currentDeclarationType = OutputSourceWriter.DeclarationType.VARIABLE_WITHOUT_VALUE;
      this.expectingDeclarationName = true;
      return this;
   }

   private void checkStartDeclaration() {
      if (this.currentDeclarationType != null) {
         throw new IllegalStateException();
      }
   }

   @Override
   public SourceWriter endDeclaration() {
      if (this.currentDeclarationType != null && !this.expectingDeclarationName) {
         switch (this.currentDeclarationType) {
            case FUNCTION:
               this.newLine();
               break;
            case VARIABLE:
               if (this.letSequenceSize == 0) {
                  this.append(';').softNewLine();
               } else if (this.letSequenceSize >= 50) {
                  this.finishLet();
               }
               break;
            case VARIABLE_WITHOUT_VALUE:
               throw new IllegalStateException();
         }

         this.currentDeclarationType = null;
         return this;
      } else {
         throw new IllegalStateException();
      }
   }

   private SourceWriter appendDeclaration(ScopedName name) {
      if (!this.expectingDeclarationName) {
         return this.appendName(name);
      }

      this.expectingDeclarationName = false;
      switch (this.currentDeclarationType) {
         case FUNCTION:
            this.finishLet();
            if (name.scoped) {
               this.append(this.naming.additionalScopeName()).append('.').append(name.name).ws().append('=').ws().append("function");
            } else {
               this.append("function ").append(name.name);
            }
            break;
         case VARIABLE:
            if (name.scoped) {
               this.finishLet();
               this.append(this.naming.additionalScopeName()).append('.');
            } else if (this.letSequenceSize++ == 0) {
               this.append("let ");
            } else {
               this.append(',').softNewLine();
            }

            this.append(name.name).ws().append('=').ws();
            break;
         case VARIABLE_WITHOUT_VALUE:
            if (!name.scoped) {
               if (this.letSequenceSize++ == 0) {
                  this.append("let ");
               } else {
                  this.append(',').softNewLine();
               }

               this.append(name.name);
            }

            this.expectingDeclarationName = false;
            this.currentDeclarationType = null;
      }

      return this;
   }

   private SourceWriter appendName(ScopedName name) {
      if (name.scoped) {
         this.append(this.naming.additionalScopeName());
         this.append('.');
      }

      this.append(name.name);
      return this;
   }

   private void appendIndent() {
      if (!this.minified) {
         this.finishLetImplicitly();
         if (this.lineStart) {
            try {
               for (int i = 0; i < this.indentSize; i++) {
                  this.innerWriter.append("    ");
                  this.column += 4;
                  this.offset += 4;
               }

               this.lineStart = false;
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
      }
   }

   @Override
   public SourceWriter newLine() {
      this.finishLetImplicitly();

      try {
         this.innerWriter.append('\n');
      } catch (IOException e) {
         throw new RuntimeException(e);
      }

      this.column = 0;
      this.line++;
      this.offset++;
      this.lineStart = true;
      return this;
   }

   @Override
   public SourceWriter ws() {
      if (this.column >= this.lineWidth) {
         this.newLine();
      } else if (!this.minified) {
         this.finishLetImplicitly();

         try {
            this.innerWriter.append(' ');
         } catch (IOException e) {
            throw new RuntimeException(e);
         }

         this.column++;
         this.offset++;
      }

      return this;
   }

   @Override
   public SourceWriter sameLineWs() {
      if (!this.minified) {
         this.finishLetImplicitly();

         try {
            this.innerWriter.append(' ');
         } catch (IOException e) {
            throw new RuntimeException(e);
         }

         this.column++;
         this.offset++;
      }

      return this;
   }

   @Override
   public SourceWriter tokenBoundary() {
      if (this.column >= this.lineWidth) {
         this.newLine();
      }

      return this;
   }

   @Override
   public SourceWriter softNewLine() {
      if (!this.minified) {
         this.finishLetImplicitly();

         try {
            this.innerWriter.append('\n');
         } catch (IOException e) {
            throw new RuntimeException(e);
         }

         this.column = 0;
         this.offset++;
         this.line++;
         this.lineStart = true;
      }

      return this;
   }

   @Override
   public SourceWriter indent() {
      this.indentSize++;
      return this;
   }

   @Override
   public SourceWriter outdent() {
      this.indentSize--;
      return this;
   }

   @Override
   public SourceWriter emitLocation(String fileName, int line) {
      this.debugInformationEmitter.emitLocation(fileName, line);
      return this;
   }

   @Override
   public SourceWriter enterLocation() {
      this.debugInformationEmitter.enterLocation();
      return this;
   }

   @Override
   public SourceWriter exitLocation() {
      this.debugInformationEmitter.exitLocation();
      return this;
   }

   @Override
   public SourceWriter emitStatementStart() {
      this.debugInformationEmitter.emitStatementStart();
      return this;
   }

   @Override
   public SourceWriter emitVariables(String[] names, String jsName) {
      this.debugInformationEmitter.emitVariable(names, jsName);
      return this;
   }

   @Override
   public void emitMethod(MethodDescriptor method) {
      this.debugInformationEmitter.emitMethod(method);
   }

   @Override
   public void emitClass(String className) {
      this.debugInformationEmitter.emitClass(className);
   }

   @Override
   public int getLine() {
      return this.line;
   }

   @Override
   public int getColumn() {
      return this.column;
   }

   @Override
   public int getOffset() {
      return this.offset;
   }

   @Override
   public void markClassStart(String className) {
      this.classMarkClass = className;
      this.classMarkPos = this.offset;
   }

   @Override
   public void markClassEnd() {
      if (this.classMarkClass != null) {
         int size = this.offset - this.classMarkPos;
         if (size > 0) {
            int currentSize = this.classSizes.get(this.classMarkClass);
            this.classSizes.put(this.classMarkClass, currentSize + size);
         }

         this.classMarkClass = null;
      }
   }

   @Override
   public void markSectionStart(int id) {
      this.sectionMarkSection = id;
      this.sectionMarkPos = this.offset;
   }

   @Override
   public void markSectionEnd() {
      if (this.sectionMarkSection >= 0) {
         int size = this.offset - this.sectionMarkPos;
         if (size > 0) {
            int currentSize = this.sectionSizes.get(this.sectionMarkSection);
            this.sectionSizes.put(this.sectionMarkSection, currentSize + size);
         }

         this.sectionMarkSection = -1;
      }
   }

   public Collection<String> getClassesInStats() {
      ArrayList<String> result = new ArrayList<>();

      for (ObjectCursor<String> cursor : this.classSizes.keys()) {
         result.add((String)cursor.value);
      }

      return result;
   }

   public int getClassSize(String className) {
      return this.classSizes.get(className);
   }

   public int getSectionSize(int sectionId) {
      return this.sectionSizes.get(sectionId);
   }

   private enum DeclarationType {
      FUNCTION,
      VARIABLE,
      VARIABLE_WITHOUT_VALUE;
   }
}
