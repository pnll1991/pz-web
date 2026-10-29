package org.teavm.common.json;

public class JsonObjectVisitor extends JsonVisitor {
   private JsonVisitor propertyVisitor;

   public JsonObjectVisitor(JsonVisitor propertyVisitor) {
      this.propertyVisitor = propertyVisitor;
   }

   @Override
   public JsonVisitor object(JsonErrorReporter reporter) {
      return this.propertyVisitor;
   }
}
