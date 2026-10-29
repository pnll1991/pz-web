package org.teavm.common.json;

public class JsonArrayVisitor extends JsonAllErrorVisitor {
   private JsonVisitor itemVisitor;

   public JsonArrayVisitor(JsonVisitor itemVisitor) {
      this.itemVisitor = itemVisitor;
   }

   @Override
   public JsonVisitor array(JsonErrorReporter reporter) {
      return this.itemVisitor;
   }
}
