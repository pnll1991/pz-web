package org.teavm.backend.javascript.codegen;

import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public interface SourceWriterSink {
   default SourceWriterSink append(CharSequence csq, int start, int end) {
      return this;
   }

   default SourceWriterSink appendClass(String cls) {
      return this;
   }

   default SourceWriterSink appendField(FieldReference field) {
      return this;
   }

   default SourceWriterSink appendStaticField(FieldReference field) {
      return this;
   }

   default SourceWriterSink appendVirtualMethod(MethodDescriptor method) {
      return this;
   }

   default SourceWriterSink appendMethod(MethodReference method) {
      return this;
   }

   default SourceWriterSink startVariableDeclaration() {
      return this;
   }

   default SourceWriterSink startFunctionDeclaration() {
      return this;
   }

   default SourceWriterSink endDeclaration() {
      return this;
   }

   default SourceWriterSink declareVariable() {
      return this;
   }

   default SourceWriterSink appendFunction(String name) {
      return this;
   }

   default SourceWriterSink appendGlobal(String name) {
      return this;
   }

   default SourceWriterSink appendInit(MethodReference method) {
      return this;
   }

   default SourceWriterSink appendClassInit(String className) {
      return this;
   }

   default SourceWriterSink newLine() {
      return this;
   }

   default SourceWriterSink ws() {
      return this;
   }

   default SourceWriterSink sameLineWs() {
      return this;
   }

   default SourceWriterSink tokenBoundary() {
      return this;
   }

   default SourceWriterSink softNewLine() {
      return this;
   }

   default SourceWriterSink indent() {
      return this;
   }

   default SourceWriterSink outdent() {
      return this;
   }

   default SourceWriterSink emitLocation(String fileName, int line) {
      return this;
   }

   default SourceWriterSink enterLocation() {
      return this;
   }

   default SourceWriterSink exitLocation() {
      return this;
   }

   default SourceWriterSink emitStatementStart() {
      return this;
   }

   default SourceWriterSink emitVariables(String[] names, String jsName) {
      return this;
   }

   default void emitMethod(MethodDescriptor method) {
   }

   default void emitClass(String className) {
   }

   default void markClassStart(String className) {
   }

   default void markClassEnd() {
   }

   default void markSectionStart(int id) {
   }

   default void markSectionEnd() {
   }
}
