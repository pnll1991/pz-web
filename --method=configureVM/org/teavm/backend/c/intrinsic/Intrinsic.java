package org.teavm.backend.c.intrinsic;

import org.teavm.ast.InvocationExpr;
import org.teavm.model.MethodReference;

public interface Intrinsic {
   boolean canHandle(MethodReference var1);

   void apply(IntrinsicContext var1, InvocationExpr var2);
}
