package org.teavm.backend.wasm.debug.info;

public enum VariableType {
   INT,
   LONG,
   FLOAT,
   DOUBLE,
   OBJECT,
   ADDRESS,
   UNDEFINED;

   public FieldType asFieldType() {
      switch (this) {
         case INT:
            return FieldType.INT;
         case LONG:
            return FieldType.LONG;
         case FLOAT:
            return FieldType.FLOAT;
         case DOUBLE:
            return FieldType.DOUBLE;
         case OBJECT:
            return FieldType.OBJECT;
         case ADDRESS:
            return FieldType.ADDRESS;
         default:
            return FieldType.UNDEFINED;
      }
   }
}
