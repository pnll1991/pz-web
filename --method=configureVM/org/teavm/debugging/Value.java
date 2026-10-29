package org.teavm.debugging;

import java.util.Map;
import org.teavm.common.Promise;
import org.teavm.debugging.javascript.JavaScriptValue;

public abstract class Value {
   Debugger debugger;
   private Promise<Map<String, Variable>> properties;
   private Promise<String> type;

   Value(Debugger debugger) {
      this.debugger = debugger;
   }

   static boolean isNumeric(String str) {
      for (int i = 0; i < str.length(); i++) {
         char c = str.charAt(i);
         if (c < '0' || c > '9') {
            return false;
         }
      }

      return true;
   }

   public abstract Promise<String> getRepresentation();

   public Promise<String> getType() {
      if (this.type == null) {
         this.type = this.prepareType();
      }

      return this.type;
   }

   abstract Promise<String> prepareType();

   public Promise<Map<String, Variable>> getProperties() {
      if (this.properties == null) {
         this.properties = this.prepareProperties();
      }

      return this.properties;
   }

   abstract Promise<Map<String, Variable>> prepareProperties();

   public abstract Promise<Boolean> hasInnerStructure();

   public abstract Promise<String> getInstanceId();

   public abstract JavaScriptValue getOriginalValue();
}
