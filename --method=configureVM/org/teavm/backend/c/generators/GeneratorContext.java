package org.teavm.backend.c.generators;

import org.teavm.backend.c.generate.CodeWriter;
import org.teavm.backend.c.generate.FileGenerator;
import org.teavm.backend.c.generate.GenerationContext;
import org.teavm.backend.c.generate.IncludeManager;
import org.teavm.backend.c.generate.StringPool;
import org.teavm.backend.lowlevel.generate.NameProvider;
import org.teavm.dependency.DependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.lowlevel.CallSiteDescriptor;
import org.teavm.model.lowlevel.CallSiteLocation;
import org.teavm.model.lowlevel.ExceptionHandlerDescriptor;

public interface GeneratorContext {
   GenerationContext mainContext();

   CodeWriter writer();

   NameProvider names();

   Diagnostics diagnotics();

   ClassReaderSource classSource();

   ClassReaderSource initialClassSource();

   ClassHierarchy hierarchy();

   DependencyInfo dependencies();

   String parameterName(int var1);

   StringPool stringPool();

   CodeWriter writerBefore();

   CodeWriter writerAfter();

   IncludeManager includes();

   FileGenerator createSourceFile(String var1);

   FileGenerator createHeaderFile(String var1);

   String escapeFileName(String var1);

   void importMethod(MethodReference var1, boolean var2);

   CallSiteDescriptor createCallSite(CallSiteLocation[] var1, ExceptionHandlerDescriptor[] var2);
}
