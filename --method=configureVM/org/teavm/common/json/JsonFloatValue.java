package org.teavm.common.json;

public class JsonFloatValue extends JsonNumericValue {
   private double number;

   public JsonFloatValue(double number) {
      this.number = number;
   }

   @Override
   public double asNumber() {
      return this.number;
   }
}
