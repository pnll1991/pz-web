package org.teavm.model.lowlevel;

public class ExceptionHandlerDescriptor {
   private int id;
   private String className;

   public ExceptionHandlerDescriptor(int id, String className) {
      this.id = id;
      this.className = className;
   }

   public int getId() {
      return this.id;
   }

   public String getClassName() {
      return this.className;
   }
}
