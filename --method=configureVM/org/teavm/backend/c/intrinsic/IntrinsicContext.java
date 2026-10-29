package org.teavm.backend.c.intrinsic;

import org.teavm.ast.Expr;
import org.teavm.backend.c.generate.CodeWriter;
import org.teavm.backend.c.generate.IncludeManager;
import org.teavm.backend.c.generate.StringPool;
import org.teavm.backend.lowlevel.generate.NameProvider;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReference;

public interface IntrinsicContext {
   CodeWriter writer();

   NameProvider names();

   void emit(Expr var1);

   Diagnostics diagnotics();

   MethodReference callingMethod();

   StringPool stringPool();

   IncludeManager includes();

   String escapeFileName(String var1);

   ClassReaderSource classes();

   void importMethod(MethodReference var1, boolean var2);

   boolean isIncremental();

   void emitCallSite();
}
