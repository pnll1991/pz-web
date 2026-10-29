package org.teavm.backend.javascript.spi;

import java.util.Properties;
import org.teavm.ast.Expr;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.rendering.Precedence;
import org.teavm.common.ServiceRepository;
import org.teavm.dependency.DependencyInfo;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.ValueType;
import org.teavm.parsing.resource.ResourceProvider;

public interface InjectorContext extends ServiceRepository {
   Expr getArgument(int var1);

   String importModule(String var1);

   int argumentCount();

   boolean isMinifying();

   SourceWriter getWriter();

   SourceWriter getMetadataWriter();

   Properties getProperties();

   void writeEscaped(String var1);

   void writeType(ValueType var1);

   void writeExpr(Expr var1);

   void writeExpr(Expr var1, Precedence var2);

   Precedence getPrecedence();

   ClassLoader getClassLoader();

   DependencyInfo getDependencies();

   ListableClassReaderSource getClassSource();

   ResourceProvider getResources();
}
