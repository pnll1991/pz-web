package org.teavm.diagnostics;

import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;

public interface ProblemTextConsumer {
   void append(String var1);

   void appendClass(String var1);

   void appendType(ValueType var1);

   void appendMethod(MethodReference var1);

   void appendField(FieldReference var1);

   void appendLocation(TextLocation var1);
}
