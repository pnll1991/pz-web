package org.teavm.common.json;

public class JsonAllErrorVisitor extends JsonVisitor {
   @Override
   public JsonVisitor object(JsonErrorReporter reporter) {
      reporter.error("Unexpected object");
      return null;
   }

   @Override
   public JsonVisitor array(JsonErrorReporter reporter) {
      reporter.error("Unexpected array");
      return null;
   }

   @Override
   public JsonVisitor property(JsonErrorReporter reporter, String name) {
      reporter.error("Unexpected property");
      return null;
   }

   @Override
   public void stringValue(JsonErrorReporter reporter, String value) {
      reporter.error("Unexpected string");
   }

   @Override
   public void intValue(JsonErrorReporter reporter, long value) {
      reporter.error("Unexpected number");
   }

   @Override
   public void floatValue(JsonErrorReporter reporter, double value) {
      reporter.error("Unexpected number");
   }

   @Override
   public void nullValue(JsonErrorReporter reporter) {
      reporter.error("Unexpected null");
   }

   @Override
   public void booleanValue(JsonErrorReporter reporter, boolean value) {
      reporter.error("Unexpected boolean");
   }
}
