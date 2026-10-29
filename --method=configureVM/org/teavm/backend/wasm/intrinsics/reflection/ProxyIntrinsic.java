package org.teavm.backend.wasm.intrinsics.reflection;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.methods.WasmGCGenerationUtil;
import org.teavm.backend.wasm.intrinsics.WasmGCCodeGenContext;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.reflection.ReflectionDependencyListener;
import org.teavm.runtime.reflect.ClassInfo;

public class ProxyIntrinsic implements WasmGCInlineIntrinsic {
   private ReflectionDependencyListener reflection;
   private WasmGCCodeGenContext codeGenContext;
   private ProxyIntrinsicContext intrinsicContext;

   public ProxyIntrinsic(ReflectionDependencyListener reflection, WasmGCCodeGenContext codeGenContext, ProxyIntrinsicContext intrinsicContext) {
      this.reflection = reflection;
      this.codeGenContext = codeGenContext;
      this.intrinsicContext = intrinsicContext;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      switch (invocation.getMethod().getName()) {
         case "registerProxyClasses":
            this.registerProxyClasses(builder);
            break;
         case "wrapDependency":
            context.generate(builder, invocation.getArguments().get(0));
      }
   }

   private void registerProxyClasses(WasmInstructionBuilder builder) {
      HashSet<MethodReference> initializedMethods = new HashSet<>();

      for (MethodReference method : this.reflection.getProxyReflectionMethods()) {
         MethodDependencyInfo methodDep = this.codeGenContext.dependency().getMethod(method);
         if (methodDep != null && methodDep.isUsed()) {
            MethodReference methodToAcquire = this.reflection.getProxyWorkerAcquireMethod(method);
            if (initializedMethods.add(methodToAcquire)) {
               WasmGCGenerationUtil.emitClassLiteral(this.codeGenContext.classInfoProvider(), builder, ValueType.object(methodToAcquire.getClassName()));
               builder.getGlobal(this.codeGenContext.strings().getStringConstant(methodToAcquire.getName()).global);
               WasmGCGenerationUtil.allocateArray(this.codeGenContext.classInfoProvider(), ValueType.parse(Class.class), builder, (array, b) -> {
                  for (ValueType param : methodToAcquire.getParameterTypes()) {
                     WasmGCGenerationUtil.emitClassLiteral(this.codeGenContext.classInfoProvider(), b, param);
                  }

                  b.arrayNewFixed(array, methodToAcquire.parameterCount());
               });
               WasmFunction getDeclaredMethod = this.codeGenContext
                  .functions()
                  .forInstanceMethod(new MethodReference(Class.class, "getDeclaredMethod", String.class, Class[].class, Method.class));
               builder.call(getDeclaredMethod);
               builder.setGlobal(this.intrinsicContext.getMethodGlobal(methodToAcquire));
            }
         }
      }

      for (Entry<? extends List<? extends String>, ? extends String> entry : this.reflection.getProxyClasses().entrySet()) {
         List<? extends String> itfList = (List<? extends String>)entry.getKey();
         String cls = entry.getValue();
         WasmFunction appendItf = this.codeGenContext
            .functions()
            .forStaticMethod(new MethodReference(Proxy.class, "appendInterface", ClassInfo.class, void.class));

         for (String itf : itfList) {
            builder.getGlobal(this.codeGenContext.classInfoProvider().getClassInfo(itf).getPointer());
            builder.call(appendItf);
         }

         WasmFunction registerCls = this.codeGenContext
            .functions()
            .forStaticMethod(new MethodReference(Proxy.class, "registerClass", ClassInfo.class, void.class));
         builder.getGlobal(this.codeGenContext.classInfoProvider().getClassInfo(cls).getPointer());
         builder.call(registerCls);
      }
   }
}
