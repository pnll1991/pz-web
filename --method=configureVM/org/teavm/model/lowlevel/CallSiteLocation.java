package org.teavm.model.lowlevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.teavm.model.InliningInfo;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;

public class CallSiteLocation {
   private String fileName;
   private String className;
   private String methodName;
   private int lineNumber;

   public CallSiteLocation(String fileName, String className, String methodName, int lineNumber) {
      this.fileName = fileName;
      this.className = className;
      this.methodName = methodName;
      this.lineNumber = lineNumber;
   }

   public static CallSiteLocation[] fromTextLocation(TextLocation location, MethodReference callingMethod) {
      if (location == null) {
         return new CallSiteLocation[]{new CallSiteLocation("", callingMethod.getClassName(), callingMethod.getName(), -1)};
      }

      if (location.getInlining() == null) {
         return new CallSiteLocation[]{
            new CallSiteLocation(convertFileName(location.getFileName()), callingMethod.getClassName(), callingMethod.getName(), location.getLine())
         };
      }

      List<CallSiteLocation> result = new ArrayList<>();
      InliningInfo inlining = location.getInlining();
      result.add(
         new CallSiteLocation(convertFileName(location.getFileName()), inlining.getMethod().getClassName(), inlining.getMethod().getName(), location.getLine())
      );

      while (inlining != null) {
         MethodReference method = inlining.getParent() != null ? inlining.getParent().getMethod() : callingMethod;
         result.add(new CallSiteLocation(convertFileName(inlining.getFileName()), method.getClassName(), method.getName(), inlining.getLine()));
         inlining = inlining.getParent();
      }

      return result.toArray(new CallSiteLocation[0]);
   }

   private static String convertFileName(String fileName) {
      if (fileName != null) {
         fileName = fileName.substring(fileName.lastIndexOf(47) + 1);
      }

      return fileName;
   }

   public String getFileName() {
      return this.fileName;
   }

   public String getClassName() {
      return this.className;
   }

   public String getMethodName() {
      return this.methodName;
   }

   public int getLineNumber() {
      return this.lineNumber;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof CallSiteLocation other)
            ? false
            : Objects.equals(this.fileName, other.fileName)
               && Objects.equals(this.className, other.className)
               && Objects.equals(this.methodName, other.methodName)
               && this.lineNumber == other.lineNumber;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.fileName, this.className, this.methodName, this.lineNumber);
   }
}
