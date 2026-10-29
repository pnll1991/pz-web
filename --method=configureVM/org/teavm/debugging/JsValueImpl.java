package org.teavm.debugging;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.teavm.common.Promise;
import org.teavm.debugging.information.DebugInformation;
import org.teavm.debugging.javascript.JavaScriptValue;
import org.teavm.debugging.javascript.JavaScriptVariable;

class JsValueImpl extends Value {
   private DebugInformation debugInformation;
   private JavaScriptValue jsValue;

   JsValueImpl(Debugger debugger, DebugInformation debugInformation, JavaScriptValue jsValue) {
      super(debugger);
      this.debugInformation = debugInformation;
      this.jsValue = jsValue;
   }

   @Override
   public Promise<String> getRepresentation() {
      return this.jsValue.getRepresentation();
   }

   @Override
   Promise<String> prepareType() {
      return this.jsValue.getClassName().then(className -> {
         if (className.startsWith("a/")) {
            className = className.substring(2);
            String origClassName = className;

            int degree;
            for (degree = 0; className.endsWith("[]"); degree++) {
               className = className.substring(0, className.length() - 2);
            }

            String javaClassName = this.debugInformation.getClassNameByJsName(className);
            if (javaClassName != null) {
               if (degree > 0) {
                  StringBuilder sb = new StringBuilder(javaClassName);

                  for (int i = 0; i < degree; i++) {
                     sb.append("[]");
                  }

                  javaClassName = sb.toString();
               }

               className = javaClassName;
            } else {
               className = origClassName;
            }
         }

         return className;
      });
   }

   @Override
   Promise<Map<String, Variable>> prepareProperties() {
      return this.jsValue.getProperties().thenAsync(jsVariables -> this.getType().thenAsync(className -> {
         if (!className.startsWith("@") && className.endsWith("[]") && jsVariables.containsKey("data")) {
            return jsVariables.get("data").getValue().getProperties().then(arrayData -> this.fillArray((Map<String, ? extends JavaScriptVariable>)arrayData));
         }

         HashMap<String, Variable> vars = new HashMap<>();

         for (Entry<String, ? extends JavaScriptVariable> entry : jsVariables.entrySet()) {
            JavaScriptVariable jsVar = entry.getValue();
            String name = this.debugger.mapField(className, entry.getKey());
            if (name != null) {
               JsValueImpl value = new JsValueImpl(this.debugger, this.debugInformation, jsVar.getValue());
               vars.put(name, new Variable(name, value));
            }
         }

         return Promise.of(vars);
      }));
   }

   private Map<String, Variable> fillArray(Map<String, ? extends JavaScriptVariable> jsVariables) {
      HashMap<String, Variable> vars = new HashMap<>();

      for (Entry<String, ? extends JavaScriptVariable> entry : jsVariables.entrySet()) {
         JavaScriptVariable jsVar = entry.getValue();
         if (isNumeric(entry.getKey())) {
            Value value = new JsValueImpl(this.debugger, this.debugInformation, jsVar.getValue());
            vars.put(entry.getKey(), new Variable(entry.getKey(), value));
         }
      }

      return vars;
   }

   @Override
   public Promise<Boolean> hasInnerStructure() {
      return this.getType().then(value -> !value.equals("long") && this.jsValue.hasInnerStructure());
   }

   @Override
   public Promise<String> getInstanceId() {
      return this.getType().then(value -> value.equals("long") ? null : this.jsValue.getInstanceId());
   }

   @Override
   public JavaScriptValue getOriginalValue() {
      return this.jsValue;
   }
}
