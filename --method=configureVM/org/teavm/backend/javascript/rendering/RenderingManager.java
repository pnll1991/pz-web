package org.teavm.backend.javascript.rendering;

import java.util.Properties;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.common.ServiceRepository;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReference;

public interface RenderingManager extends ServiceRepository {
   SourceWriter getWriter();

   void exportMethod(MethodReference var1, String var2);

   void exportClass(String var1, String var2);

   void exportFunction(String var1, String var2);

   ListableClassReaderSource getClassSource();

   ClassReaderSource getOriginalClassSource();

   ClassLoader getClassLoader();

   Properties getProperties();

   String getEntryPoint();
}
