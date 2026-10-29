package org.teavm.diagnostics;

import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;

public class DefaultProblemTextConsumer implements ProblemTextConsumer {
   private StringBuilder sb = new StringBuilder();

   public void clear() {
      this.sb.setLength(0);
   }

   public String getText() {
      return this.sb.toString();
   }

   @Override
   public void append(String text) {
      this.sb.append(text);
   }

   @Override
   public void appendClass(String className) {
      this.sb.append(className);
   }

   @Override
   public void appendMethod(MethodReference method) {
      this.sb.append(method);
   }

   @Override
   public void appendField(FieldReference field) {
      this.sb.append(field);
   }

   @Override
   public void appendLocation(TextLocation location) {
      this.sb.append(location);
   }

   @Override
   public void appendType(ValueType type) {
      this.sb.append(type);
   }
}
