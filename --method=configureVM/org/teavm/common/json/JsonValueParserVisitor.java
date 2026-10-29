package org.teavm.common.json;

import java.util.function.Consumer;

public abstract class JsonValueParserVisitor extends JsonAllErrorVisitor {
   private JsonValue deferred;

   public abstract void consume(JsonValue var1);

   public static JsonValueParserVisitor create(final Consumer<JsonValue> consumer) {
      return new JsonValueParserVisitor() {
         @Override
         public void consume(JsonValue value) {
            consumer.accept(value);
         }
      };
   }

   @Override
   public JsonVisitor object(JsonErrorReporter reporter) {
      final JsonObjectValue jsonObject = new JsonObjectValue();
      this.deferred = jsonObject;
      return new JsonAllErrorVisitor() {
         @Override
         public JsonVisitor property(JsonErrorReporter reporter, final String name) {
            return new JsonValueParserVisitor() {
               @Override
               public void consume(JsonValue value) {
                  jsonObject.put(name, value);
               }
            };
         }
      };
   }

   @Override
   public void end(JsonErrorReporter reporter) {
      super.end(reporter);
      JsonValue value = this.deferred;
      this.deferred = null;
      this.consume(value);
   }

   @Override
   public JsonVisitor array(JsonErrorReporter reporter) {
      final JsonArrayValue jsonArray = new JsonArrayValue();
      this.deferred = jsonArray;
      return new JsonValueParserVisitor() {
         @Override
         public void consume(JsonValue value) {
            jsonArray.add(value);
         }
      };
   }

   @Override
   public void stringValue(JsonErrorReporter reporter, String value) {
      this.consume(new JsonStringValue(value));
   }

   @Override
   public void intValue(JsonErrorReporter reporter, long value) {
      this.consume(new JsonIntValue(value));
   }

   @Override
   public void floatValue(JsonErrorReporter reporter, double value) {
      this.consume(new JsonFloatValue(value));
   }

   @Override
   public void nullValue(JsonErrorReporter reporter) {
      this.consume(new JsonNullValue());
   }

   @Override
   public void booleanValue(JsonErrorReporter reporter, boolean value) {
      this.consume(new JsonBooleanValue(value));
   }
}
