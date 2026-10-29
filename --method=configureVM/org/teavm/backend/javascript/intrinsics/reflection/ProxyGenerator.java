package org.teavm.backend.javascript.intrinsics.reflection;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.rendering.RenderingUtil;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.reflection.ReflectionDependencyListener;
import org.teavm.runtime.reflect.ClassInfo;

public class ProxyGenerator implements Injector {
   private ReflectionDependencyListener reflection;
   private ProxyGeneratorContext proxyContext;

   public ProxyGenerator(ReflectionDependencyListener reflection, ProxyGeneratorContext proxyContext) {
      this.reflection = reflection;
      this.proxyContext = proxyContext;
   }

   @Override
   public void generate(InjectorContext context, MethodReference methodRef) {
      switch (methodRef.getName()) {
         case "registerProxyClasses":
            this.registerProxyClasses(context);
            break;
         case "wrapDependency":
            context.writeExpr(context.getArgument(0));
      }
   }

   private void registerProxyClasses(InjectorContext context) {
      SourceWriter writer = context.getWriter();
      writer.append("Object.assign(").appendFunction("$rt_proxyMethods").append(",").ws().append("{").softNewLine().indent();
      boolean first = true;
      HashSet<MethodReference> initializedMethods = new HashSet<>();

      for (MethodReference method : this.reflection.getProxyReflectionMethods()) {
         MethodDependencyInfo methodDep = context.getDependencies().getMethod(method);
         if (methodDep != null && methodDep.isUsed()) {
            MethodReference methodToAcquire = this.reflection.getProxyWorkerAcquireMethod(method);
            if (initializedMethods.add(methodToAcquire)) {
               if (!first) {
                  writer.append(",").softNewLine();
               }

               first = false;
               writer.append(this.proxyContext.getPropertyName(methodToAcquire)).append(":").ws();
               writer.appendMethod(Class.class, "getDeclaredMethod", String.class, Class[].class, Method.class);
               writer.append("(").appendFunction("$rt_cls").append("(");
               RenderingUtil.typeToClsString(writer, ValueType.object(methodToAcquire.getClassName()));
               writer.append("),").ws().appendFunction("$rt_str").append("(");
               RenderingUtil.writeString(writer, methodToAcquire.getName());
               writer.append("),").ws();
               writer.appendFunction("$rt_wrapArray").append("(").appendClass("java.lang.Class").append(",").ws().append("[");
               boolean firstParam = true;

               for (ValueType param : methodToAcquire.getParameterTypes()) {
                  if (!firstParam) {
                     writer.append(",").ws();
                  }

                  firstParam = false;
                  writer.appendFunction("$rt_cls").append("(");
                  RenderingUtil.typeToClsString(writer, param);
                  writer.append(")");
               }

               writer.append("]))");
            }
         }
      }

      writer.softNewLine().outdent().append("});").softNewLine();

      for (Entry<? extends List<? extends String>, ? extends String> entry : this.reflection.getProxyClasses().entrySet()) {
         List<? extends String> itfList = (List<? extends String>)entry.getKey();
         String cls = entry.getValue();

         for (String itf : itfList) {
            writer.appendMethod(Proxy.class, "appendInterface", ClassInfo.class, void.class).append("(");
            RenderingUtil.typeToClsString(writer, ValueType.object(itf));
            writer.append(");").softNewLine();
         }

         writer.appendMethod(Proxy.class, "registerClass", ClassInfo.class, void.class).append("(");
         RenderingUtil.typeToClsString(writer, ValueType.object(cls));
         writer.append(");").softNewLine();
      }
   }
}
