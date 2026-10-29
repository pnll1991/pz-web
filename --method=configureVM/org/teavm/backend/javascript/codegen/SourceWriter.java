package org.teavm.backend.javascript.codegen;

import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public abstract class SourceWriter implements Appendable, SourceWriterSink {
   public SourceWriter append(String value) {
      this.append((CharSequence)value);
      return this;
   }

   public SourceWriter appendBlockStart() {
      return this.ws().append("{").indent().softNewLine();
   }

   public SourceWriter appendBlockEnd() {
      return this.outdent().append("}").softNewLine();
   }

   public SourceWriter appendIf() {
      return this.append("if").ws().append("(");
   }

   public SourceWriter appendElseIf() {
      return this.outdent().append("}").ws().append("else ").appendIf();
   }

   public SourceWriter appendElse() {
      return this.outdent().append("}").ws().append("else").appendBlockStart();
   }

   public SourceWriter append(int value) {
      return this.append(String.valueOf(value));
   }

   public abstract SourceWriter append(char var1);

   public SourceWriter append(CharSequence csq) {
      this.append(csq, 0, csq.length());
      return this;
   }

   public abstract SourceWriter append(CharSequence var1, int var2, int var3);

   public abstract SourceWriter appendClass(String var1);

   public SourceWriter appendClass(Class<?> cls) {
      return this.appendClass(cls.getName());
   }

   public abstract SourceWriter appendField(FieldReference var1);

   public abstract SourceWriter appendStaticField(FieldReference var1);

   public abstract SourceWriter appendVirtualMethod(MethodDescriptor var1);

   public SourceWriter appendVirtualMethod(String name, Class<?>... params) {
      return this.appendVirtualMethod(new MethodDescriptor(name, params));
   }

   public abstract SourceWriter appendMethod(MethodReference var1);

   public SourceWriter appendMethod(String className, String name, ValueType... params) {
      return this.appendMethod(new MethodReference(className, new MethodDescriptor(name, params)));
   }

   public SourceWriter appendMethod(Class<?> cls, String name, Class<?>... params) {
      return this.appendMethod(new MethodReference(cls, name, params));
   }

   public abstract SourceWriter appendFunction(String var1);

   public abstract SourceWriter startFunctionDeclaration();

   public abstract SourceWriter startVariableDeclaration();

   public abstract SourceWriter endDeclaration();

   public abstract SourceWriter declareVariable();

   public abstract SourceWriter appendGlobal(String var1);

   public abstract SourceWriter appendInit(MethodReference var1);

   public abstract SourceWriter appendClassInit(String var1);

   public abstract SourceWriter newLine();

   public abstract SourceWriter ws();

   public abstract SourceWriter sameLineWs();

   public abstract SourceWriter tokenBoundary();

   public abstract SourceWriter softNewLine();

   public abstract SourceWriter indent();

   public abstract SourceWriter outdent();

   public abstract SourceWriter emitLocation(String var1, int var2);

   public abstract SourceWriter enterLocation();

   public abstract SourceWriter exitLocation();

   public abstract SourceWriter emitStatementStart();

   public abstract SourceWriter emitVariables(String[] var1, String var2);

   @Override
   public abstract void emitMethod(MethodDescriptor var1);

   @Override
   public abstract void emitClass(String var1);

   @Override
   public abstract void markClassStart(String var1);

   @Override
   public abstract void markClassEnd();

   @Override
   public abstract void markSectionStart(int var1);

   @Override
   public abstract void markSectionEnd();
}
