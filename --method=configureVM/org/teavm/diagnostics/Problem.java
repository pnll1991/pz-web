package org.teavm.diagnostics;

import java.io.Serializable;
import java.util.Arrays;
import org.teavm.model.CallLocation;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;

public class Problem implements Serializable {
   private ProblemSeverity severity;
   private CallLocation location;
   private String text;
   private Object[] params;

   public Problem(ProblemSeverity severity, CallLocation location, String text, Object[] params) {
      this.severity = severity;
      this.location = location;
      this.text = text;
      this.params = Arrays.copyOf(params, params.length);
   }

   public ProblemSeverity getSeverity() {
      return this.severity;
   }

   public CallLocation getLocation() {
      return this.location;
   }

   public String getText() {
      return this.text;
   }

   public Object[] getParams() {
      return this.params;
   }

   public void render(ProblemTextConsumer consumer) {
      int index = 0;

      while (index < this.text.length()) {
         int next = this.text.indexOf("{{", index);
         if (next < 0) {
            break;
         }

         consumer.append(this.text.substring(index, next));
         index = this.parseParameter(consumer, next);
         if (index == next) {
            consumer.append("{{");
            index += 2;
         }
      }

      consumer.append(this.text.substring(index));
   }

   private int parseParameter(ProblemTextConsumer consumer, int index) {
      int next = index + 2;
      if (next >= this.text.length()) {
         return index;
      }

      Problem.ParamType type;
      switch (Character.toLowerCase(this.text.charAt(next++))) {
         case 'c':
            type = Problem.ParamType.CLASS;
            break;
         case 'f':
            type = Problem.ParamType.FIELD;
            break;
         case 'l':
            type = Problem.ParamType.LOCATION;
            break;
         case 'm':
            type = Problem.ParamType.METHOD;
            break;
         case 't':
            type = Problem.ParamType.TYPE;
            break;
         default:
            return index;
      }

      int digitsEnd = this.skipDigits(next);
      if (digitsEnd == next) {
         return index;
      }

      int paramIndex = Integer.parseInt(this.text.substring(next, digitsEnd));
      if (paramIndex >= this.params.length) {
         return index;
      }

      next = digitsEnd;
      if (next + 1 < this.text.length() && this.text.substring(next, next + 2).equals("}}")) {
         Object param = this.params[paramIndex];
         switch (type) {
            case CLASS:
               if (!(param instanceof String)) {
                  return index;
               }

               consumer.appendClass((String)param);
               break;
            case TYPE:
               if (!(param instanceof ValueType)) {
                  return index;
               }

               consumer.appendType((ValueType)param);
               break;
            case METHOD:
               if (!(param instanceof MethodReference)) {
                  return index;
               }

               consumer.appendMethod((MethodReference)param);
               break;
            case FIELD:
               if (!(param instanceof FieldReference)) {
                  return index;
               }

               consumer.appendField((FieldReference)param);
               break;
            case LOCATION:
               if (!(param instanceof TextLocation)) {
                  return index;
               }

               consumer.appendLocation((TextLocation)param);
         }

         return next + 2;
      } else {
         return index;
      }
   }

   private int skipDigits(int index) {
      while (index < this.text.length() && Character.isDigit(this.text.charAt(index))) {
         index++;
      }

      return index;
   }

   enum ParamType {
      CLASS,
      TYPE,
      METHOD,
      FIELD,
      LOCATION;
   }
}
