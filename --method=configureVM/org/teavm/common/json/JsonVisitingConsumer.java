package org.teavm.common.json;

import java.util.ArrayDeque;
import java.util.Deque;

public class JsonVisitingConsumer extends JsonConsumer {
   private Deque<JsonVisitor> visitorStack = new ArrayDeque<>();
   private int noVisitorLevel;

   public JsonVisitingConsumer(JsonVisitor visitor) {
      this.visitorStack.push(visitor);
   }

   @Override
   public void enterObject(JsonErrorReporter reporter) {
      if (this.noVisitorLevel == 0) {
         JsonVisitor next = this.visitorStack.peek().object(reporter);
         if (next == null) {
            this.noVisitorLevel = 1;
         } else {
            this.visitorStack.push(next);
         }
      } else {
         this.noVisitorLevel++;
      }
   }

   @Override
   public void exitObject(JsonErrorReporter reporter) {
      this.exit(reporter);
   }

   @Override
   public void enterArray(JsonErrorReporter reporter) {
      if (this.noVisitorLevel == 0) {
         JsonVisitor next = this.visitorStack.peek().array(reporter);
         if (next == null) {
            this.noVisitorLevel = 1;
         } else {
            this.visitorStack.push(next);
         }
      } else {
         this.noVisitorLevel++;
      }
   }

   @Override
   public void exitArray(JsonErrorReporter reporter) {
      this.exit(reporter);
   }

   @Override
   public void enterProperty(JsonErrorReporter reporter, String name) {
      if (this.noVisitorLevel == 0) {
         JsonVisitor next = this.visitorStack.peek().property(reporter, name);
         if (next == null) {
            this.noVisitorLevel = 1;
         } else {
            this.visitorStack.push(next);
         }
      } else {
         this.noVisitorLevel++;
      }
   }

   @Override
   public void exitProperty(JsonErrorReporter reporter, String name) {
      this.exit(reporter);
   }

   private void exit(JsonErrorReporter reporter) {
      if (this.noVisitorLevel > 0) {
         this.noVisitorLevel--;
      } else {
         this.visitorStack.pop();
      }

      if (this.noVisitorLevel == 0 && !this.visitorStack.isEmpty()) {
         this.visitorStack.peek().end(reporter);
      }
   }

   @Override
   public void stringValue(JsonErrorReporter reporter, String value) {
      if (this.noVisitorLevel == 0) {
         this.visitorStack.peek().stringValue(reporter, value);
      }
   }

   @Override
   public void intValue(JsonErrorReporter reporter, long value) {
      if (this.noVisitorLevel == 0) {
         this.visitorStack.peek().intValue(reporter, value);
      }
   }

   @Override
   public void floatValue(JsonErrorReporter reporter, double value) {
      if (this.noVisitorLevel == 0) {
         this.visitorStack.peek().floatValue(reporter, value);
      }
   }

   @Override
   public void nullValue(JsonErrorReporter reporter) {
      if (this.noVisitorLevel == 0) {
         this.visitorStack.peek().nullValue(reporter);
      }
   }

   @Override
   public void booleanValue(JsonErrorReporter reporter, boolean value) {
      if (this.noVisitorLevel == 0) {
         this.visitorStack.peek().booleanValue(reporter, value);
      }
   }
}
