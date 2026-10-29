package org.teavm.backend.javascript.codegen;

import java.util.ArrayList;
import java.util.List;
import org.teavm.hppc.ByteArrayList;
import org.teavm.hppc.IntArrayList;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public class RememberingSourceWriter extends SourceWriter {
   static final byte CLASS = 0;
   static final byte FIELD = 1;
   static final byte STATIC_FIELD = 2;
   static final byte METHOD = 3;
   static final byte METHOD_BODY = 4;
   static final byte FUNCTION = 5;
   static final byte GLOBAL = 6;
   static final byte INIT = 7;
   static final byte CLASS_INIT = 8;
   static final byte NEW_LINE = 9;
   static final byte WS = 10;
   static final byte SAME_LINE_WS = 21;
   static final byte TOKEN_BOUNDARY = 11;
   static final byte SOFT_NEW_LINE = 12;
   static final byte INDENT = 13;
   static final byte OUTDENT = 14;
   static final byte START_FUNCTION = 27;
   static final byte START_VARIABLE = 28;
   static final byte END_DECLARATION = 29;
   static final byte DECLARE_VARIABLE = 30;
   static final byte EMIT_LOCATION = 15;
   static final byte ENTER_LOCATION = 16;
   static final byte EXIT_LOCATION = 17;
   static final byte EMIT_STATEMENT_START = 18;
   static final byte EMIT_VARIABLES = 26;
   static final byte EMIT_METHOD = 19;
   static final byte EMIT_CLASS = 20;
   static final byte MARK_CLASS_START = 22;
   static final byte MARK_CLASS_END = 23;
   static final byte MARK_SECTION_START = 24;
   static final byte MARK_SECTION_END = 25;
   private boolean debug;
   private StringBuilder sb = new StringBuilder();
   private int lastWrittenChar;
   private IntArrayList intArgs = new IntArrayList();
   private ByteArrayList commands = new ByteArrayList();
   private List<String> strings = new ArrayList<>();
   private ObjectIntMap<String> stringIndexes = new ObjectIntHashMap();
   private List<FieldReference> fields = new ArrayList<>();
   private ObjectIntMap<FieldReference> fieldIndexes = new ObjectIntHashMap();
   private List<MethodDescriptor> methodDescriptors = new ArrayList<>();
   private ObjectIntMap<MethodDescriptor> methodDescriptorIndexes = new ObjectIntHashMap();
   private List<MethodReference> methods = new ArrayList<>();
   private ObjectIntMap<MethodReference> methodIndexes = new ObjectIntHashMap();
   private boolean isInDeclaration;

   public RememberingSourceWriter(boolean debug) {
      this.debug = debug;
   }

   public void clear() {
      this.sb.setLength(0);
      this.lastWrittenChar = 0;
      this.intArgs.clear();
      this.commands.clear();
      this.strings.clear();
      this.stringIndexes.clear();
      this.fields.clear();
      this.fieldIndexes.clear();
      this.methodDescriptors.clear();
      this.methodDescriptorIndexes.clear();
      this.methods.clear();
      this.methodIndexes.clear();
   }

   @Override
   public SourceWriter append(char value) {
      this.sb.append(value);
      return this;
   }

   @Override
   public SourceWriter append(CharSequence csq, int start, int end) {
      this.sb.append(csq, start, end);
      return this;
   }

   @Override
   public SourceWriter appendClass(String cls) {
      this.flush();
      this.commands.add((byte)0);
      this.appendStringArg(cls);
      return this;
   }

   @Override
   public SourceWriter appendField(FieldReference field) {
      this.flush();
      this.commands.add((byte)1);
      this.appendFieldArg(field);
      return this;
   }

   @Override
   public SourceWriter appendStaticField(FieldReference field) {
      this.flush();
      this.commands.add((byte)2);
      this.appendFieldArg(field);
      return this;
   }

   @Override
   public SourceWriter appendVirtualMethod(MethodDescriptor method) {
      this.flush();
      this.commands.add((byte)3);
      this.appendMethodDescriptorArg(method);
      return this;
   }

   @Override
   public SourceWriter appendMethod(MethodReference method) {
      this.flush();
      this.commands.add((byte)4);
      this.appendMethodArg(method);
      return this;
   }

   @Override
   public SourceWriter appendFunction(String name) {
      this.flush();
      this.commands.add((byte)5);
      this.appendStringArg(name);
      return this;
   }

   @Override
   public SourceWriter appendGlobal(String name) {
      this.flush();
      this.commands.add((byte)6);
      this.appendStringArg(name);
      return this;
   }

   @Override
   public SourceWriter appendInit(MethodReference method) {
      this.flush();
      this.commands.add((byte)7);
      this.appendMethodArg(method);
      return this;
   }

   @Override
   public SourceWriter appendClassInit(String className) {
      this.flush();
      this.commands.add((byte)8);
      this.appendStringArg(className);
      return this;
   }

   @Override
   public SourceWriter newLine() {
      this.flush();
      this.commands.add((byte)9);
      return this;
   }

   @Override
   public SourceWriter ws() {
      this.flush();
      this.commands.add((byte)10);
      return this;
   }

   @Override
   public SourceWriter sameLineWs() {
      this.flush();
      this.commands.add((byte)21);
      return this;
   }

   @Override
   public SourceWriter tokenBoundary() {
      this.flush();
      this.commands.add((byte)11);
      return this;
   }

   @Override
   public SourceWriter softNewLine() {
      this.flush();
      this.commands.add((byte)12);
      return this;
   }

   @Override
   public SourceWriter indent() {
      this.flush();
      this.commands.add((byte)13);
      return this;
   }

   @Override
   public SourceWriter outdent() {
      this.flush();
      this.commands.add((byte)14);
      return this;
   }

   @Override
   public SourceWriter startFunctionDeclaration() {
      this.checkNotInDeclaration();
      this.isInDeclaration = true;
      this.flush();
      this.commands.add((byte)27);
      return this;
   }

   @Override
   public SourceWriter startVariableDeclaration() {
      this.checkNotInDeclaration();
      this.isInDeclaration = true;
      this.flush();
      this.commands.add((byte)28);
      return this;
   }

   private void checkNotInDeclaration() {
      if (this.isInDeclaration) {
         throw new IllegalStateException();
      }
   }

   @Override
   public SourceWriter endDeclaration() {
      if (!this.isInDeclaration) {
         throw new IllegalStateException();
      }

      this.isInDeclaration = false;
      this.flush();
      this.commands.add((byte)29);
      return this;
   }

   @Override
   public SourceWriter declareVariable() {
      this.checkNotInDeclaration();
      this.flush();
      this.commands.add((byte)30);
      return this;
   }

   @Override
   public SourceWriter emitLocation(String fileName, int line) {
      if (this.debug) {
         this.flush();
         this.commands.add((byte)15);
         if (fileName == null) {
            this.intArgs.add(-1);
         } else {
            this.appendStringArg(fileName);
         }

         this.intArgs.add(line);
      }

      return this;
   }

   @Override
   public SourceWriter enterLocation() {
      if (this.debug) {
         this.flush();
         this.commands.add((byte)16);
      }

      return this;
   }

   @Override
   public SourceWriter exitLocation() {
      if (this.debug) {
         this.flush();
         this.commands.add((byte)17);
      }

      return this;
   }

   @Override
   public SourceWriter emitStatementStart() {
      if (this.debug) {
         this.flush();
         this.commands.add((byte)18);
      }

      return this;
   }

   @Override
   public SourceWriter emitVariables(String[] names, String jsName) {
      if (this.debug) {
         this.flush();
         this.commands.add((byte)26);
         this.intArgs.add(names.length);

         for (String name : names) {
            this.appendStringArg(name);
         }

         this.appendStringArg(jsName);
      }

      return this;
   }

   @Override
   public void emitMethod(MethodDescriptor method) {
      if (this.debug) {
         this.flush();
         this.commands.add((byte)19);
         if (method == null) {
            this.intArgs.add(-1);
         } else {
            this.appendMethodDescriptorArg(method);
         }
      }
   }

   @Override
   public void emitClass(String className) {
      if (this.debug) {
         this.flush();
         this.commands.add((byte)20);
         if (className == null) {
            this.intArgs.add(-1);
         } else {
            this.appendStringArg(className);
         }
      }
   }

   @Override
   public void markClassStart(String className) {
      this.flush();
      this.commands.add((byte)22);
      this.appendStringArg(className);
   }

   @Override
   public void markClassEnd() {
      this.flush();
      this.commands.add((byte)23);
   }

   @Override
   public void markSectionStart(int id) {
      this.flush();
      this.commands.add((byte)24);
      this.intArgs.add(id);
   }

   @Override
   public void markSectionEnd() {
      this.flush();
      this.commands.add((byte)25);
   }

   public void flush() {
      if (this.lastWrittenChar != this.sb.length()) {
         for (int i = this.lastWrittenChar; i < this.sb.length(); i += 128) {
            int j = Math.min(this.sb.length(), i + 128);
            int n = j - i - 1;
            this.commands.add((byte)(128 | n));
         }

         this.lastWrittenChar = this.sb.length();
      }
   }

   public RememberedSource save() {
      this.flush();
      return new RememberedSource(
         this.commands.toArray(),
         this.sb.toString(),
         this.intArgs.toArray(),
         !this.strings.isEmpty() ? this.strings.toArray(new String[0]) : null,
         !this.fields.isEmpty() ? this.fields.toArray(new FieldReference[0]) : null,
         !this.methodDescriptors.isEmpty() ? this.methodDescriptors.toArray(new MethodDescriptor[0]) : null,
         !this.methods.isEmpty() ? this.methods.toArray(new MethodReference[0]) : null
      );
   }

   private void appendStringArg(String arg) {
      int index = this.stringIndexes.getOrDefault(arg, -1);
      if (index < 0) {
         index = this.strings.size();
         this.stringIndexes.put(arg, index);
         this.strings.add(arg);
      }

      this.intArgs.add(index);
   }

   private void appendFieldArg(FieldReference arg) {
      int index = this.fieldIndexes.getOrDefault(arg, -1);
      if (index < 0) {
         index = this.fields.size();
         this.fieldIndexes.put(arg, index);
         this.fields.add(arg);
      }

      this.intArgs.add(index);
   }

   private void appendMethodDescriptorArg(MethodDescriptor arg) {
      int index = this.methodDescriptorIndexes.getOrDefault(arg, -1);
      if (index < 0) {
         index = this.methodDescriptors.size();
         this.methodDescriptorIndexes.put(arg, index);
         this.methodDescriptors.add(arg);
      }

      this.intArgs.add(index);
   }

   private void appendMethodArg(MethodReference arg) {
      int index = this.methodIndexes.getOrDefault(arg, -1);
      if (index < 0) {
         index = this.methods.size();
         this.methodIndexes.put(arg, index);
         this.methods.add(arg);
      }

      this.intArgs.add(index);
   }
}
