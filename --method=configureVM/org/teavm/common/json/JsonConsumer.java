package org.teavm.common.json;

public abstract class JsonConsumer {
   public void enterObject(JsonErrorReporter reporter) {
   }

   public void exitObject(JsonErrorReporter reporter) {
   }

   public void enterArray(JsonErrorReporter reporter) {
   }

   public void exitArray(JsonErrorReporter reporter) {
   }

   public void enterProperty(JsonErrorReporter reporter, String name) {
   }

   public void exitProperty(JsonErrorReporter reporter, String name) {
   }

   public void stringValue(JsonErrorReporter reporter, String value) {
   }

   public void intValue(JsonErrorReporter reporter, long value) {
   }

   public void floatValue(JsonErrorReporter reporter, double value) {
   }

   public void nullValue(JsonErrorReporter reporter) {
   }

   public void booleanValue(JsonErrorReporter reporter, boolean value) {
   }
}
