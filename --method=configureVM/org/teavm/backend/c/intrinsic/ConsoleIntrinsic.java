package org.teavm.backend.c.intrinsic;

import org.teavm.ast.ConstantExpr;
import org.teavm.ast.Expr;
import org.teavm.ast.InvocationExpr;
import org.teavm.backend.c.generate.StringPoolGenerator;
import org.teavm.model.MethodReference;
import org.teavm.runtime.Console;

public class ConsoleIntrinsic implements Intrinsic {
   @Override
   public boolean canHandle(MethodReference method) {
      return !method.getClassName().equals(Console.class.getName()) ? false : method.getName().equals("printString");
   }

   @Override
   public void apply(IntrinsicContext context, InvocationExpr invocation) {
      switch (invocation.getMethod().getName()) {
         case "printString":
            context.includes().includePath("log.h");
            context.writer().print("teavm_printString(");
            Expr arg = invocation.getArguments().get(0);
            String literal = this.extractStringConstant(arg);
            if (literal != null) {
               context.writer().print("u");
               StringPoolGenerator.generateSimpleStringLiteral(context.writer(), literal);
            } else {
               context.includes().includePath("string.h");
               context.writer().print("teavm_stringToC16(");
               context.emit(arg);
               context.writer().print(")");
            }

            context.writer().print(")");
      }
   }

   private String extractStringConstant(Expr expr) {
      if (!(expr instanceof ConstantExpr)) {
         return null;
      }

      Object value = ((ConstantExpr)expr).getValue();
      return value instanceof String ? (String)value : null;
   }
}
