package org.teavm.backend.c.generate;

public enum CVariableType {
   INT("int32_t"),
   LONG("int64_t"),
   FLOAT("float"),
   DOUBLE("double"),
   PTR("void*");

   public final String text;

   CVariableType(String text) {
      this.text = text;
   }
}
