package org.teavm.common.json;

public class JsonIntValue extends JsonNumericValue {
   private long number;

   public JsonIntValue(long number) {
      this.number = number;
   }

   @Override
   public double asNumber() {
      return this.number;
   }

   @Override
   public long asIntNumber() {
      return this.number;
   }
}
