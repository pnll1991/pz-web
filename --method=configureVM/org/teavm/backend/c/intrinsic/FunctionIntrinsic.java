package org.teavm.backend.c.intrinsic;

import java.util.Map;
import org.teavm.ast.ConstantExpr;
import org.teavm.ast.InvocationExpr;
import org.teavm.interop.Function;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.lowlevel.Characteristics;
import org.teavm.model.lowlevel.ExportedMethodKey;

public class FunctionIntrinsic implements Intrinsic {
   private Characteristics characteristics;
   private Map<? extends ExportedMethodKey, ? extends MethodReference> resolvedMethods;

   public FunctionIntrinsic(Characteristics characteristics, Map<? extends ExportedMethodKey, ? extends MethodReference> resolvedMethods) {
      this.characteristics = characteristics;
      this.resolvedMethods = resolvedMethods;
   }

   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(Function.class.getName()) ? method.getName().equals("get") : this.characteristics.isFunction(method.getClassName());
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      MethodReference method = invocation.getMethod();
      if (method.getClassName().equals(Function.class.getName())) {
         this.generateGetFunction(context, invocation);
      } else {
         context.writer().print("(((").printType(method.getReturnType()).print(" (*)(");
         if (method.parameterCount() > 0) {
            context.writer().printType(method.parameterType(0));

            for (int i = 1; i < method.parameterCount(); i++) {
               context.writer().print(", ").printType(method.parameterType(i));
            }
         }

         context.writer().print(")) ");
         context.emit(invocation.getArguments().get(0));
         context.writer().print(")(");
         if (invocation.getArguments().size() > 1) {
            context.emit(invocation.getArguments().get(1));

            for (int i = 2; i < invocation.getArguments().size(); i++) {
               context.writer().print(", ");
               context.emit(invocation.getArguments().get(i));
            }
         }

         context.writer().print("))");
      }
   }

   private void generateGetFunction(IntrinsicContext context, InvocationExpr invocation) {
      if (invocation.getArguments().get(0) instanceof ConstantExpr
         && invocation.getArguments().get(1) instanceof ConstantExpr
         && invocation.getArguments().get(2) instanceof ConstantExpr) {
         Object functionClassValue = ((ConstantExpr)invocation.getArguments().get(0)).getValue();
         Object classValue = ((ConstantExpr)invocation.getArguments().get(1)).getValue();
         Object methodValue = ((ConstantExpr)invocation.getArguments().get(2)).getValue();
         if (functionClassValue instanceof ValueType.Object && classValue instanceof ValueType.Object && methodValue instanceof String) {
            String functionClassName = ((ValueType.Object)functionClassValue).getClassName();
            String className = ((ValueType.Object)classValue).getClassName();
            String methodName = (String)methodValue;
            ExportedMethodKey key = new ExportedMethodKey(functionClassName, className, methodName);
            MethodReference method = this.resolvedMethods.get(key);
            if (method != null) {
               context.importMethod(method, true);
               context.writer().print("&").print(context.names().forMethod(method));
            }
         }
      }
   }
}
