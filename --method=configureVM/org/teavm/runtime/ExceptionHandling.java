package org.teavm.runtime;

import org.teavm.interop.Address;
import org.teavm.interop.Export;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Unmanaged;

@StaticInit
public final class ExceptionHandling {
   private static Throwable thrownException;

   private ExceptionHandling() {
   }

   @Unmanaged
   public static native CallSite findCallSiteById(int var0, Address var1);

   @Unmanaged
   public static native boolean isJumpSupported();

   @Unmanaged
   public static native void jumpToFrame(Address var0, int var1);

   @Unmanaged
   public static native void abort();

   @Unmanaged
   private static native boolean isObfuscated();

   @Unmanaged
   public static void printStack() {
      for (Address stackFrame = ShadowStack.getStackTop(); stackFrame != null; stackFrame = ShadowStack.getNextStackFrame(stackFrame)) {
         int callSiteId = ShadowStack.getCallSiteId(stackFrame);
         if (isObfuscated()) {
            Console.printString("\tat Obfuscated.obfuscated(Obfuscated.java:");
            Console.printInt(callSiteId);
            Console.printString(")\n");
         } else {
            CallSite callSite = findCallSiteById(callSiteId, stackFrame);

            for (CallSiteLocation location = callSite.location; location != null; location = location.next) {
               MethodLocation methodLocation = location.method;
               if (methodLocation != null) {
                  Console.printString("\tat ");
                  if (methodLocation.className != null && methodLocation.methodName != null) {
                     Console.printString(methodLocation.className.value);
                     Console.printString(".");
                     Console.printString(methodLocation.methodName.value);
                  } else {
                     Console.printString("(Unknown method)");
                  }

                  Console.printString("(");
                  if (methodLocation.fileName != null && location.lineNumber >= 0) {
                     Console.printString(methodLocation.fileName.value);
                     Console.printString(":");
                     Console.printInt(location.lineNumber);
                  }

                  Console.printString(")\n");
               }
            }
         }
      }
   }

   @Export(name = "teavm_catchException")
   @Unmanaged
   public static Throwable catchException() {
      Throwable exception = thrownException;
      thrownException = null;
      return exception;
   }

   @Unmanaged
   public static Throwable peekException() {
      return thrownException;
   }

   @Unmanaged
   public static void throwException(Throwable exception) {
      thrownException = exception;
      RuntimeObject exceptionPtr = (RuntimeObject)Address.ofObject(exception).toStructure();
      RuntimeClass exceptionClass = RuntimeClass.getClass(exceptionPtr);
      Address stackFrame = ShadowStack.getStackTop();
      int handlerId = 0;

      label58:
      while (stackFrame != null) {
         int callSiteId = ShadowStack.getCallSiteId(stackFrame);
         if (callSiteId >= 0) {
            CallSite callSite = findCallSiteById(callSiteId, stackFrame);

            for (ExceptionHandler handler = callSite.firstHandler; handler != null; handler = handler.next) {
               if (handler.exceptionClass == null || handler.exceptionClass.isSupertypeOf.apply(handler.exceptionClass, exceptionClass)) {
                  handlerId = handler.id;
                  if (!isJumpSupported()) {
                     ShadowStack.setExceptionHandlerId(stackFrame, handlerId);
                  }
                  break label58;
               }
            }

            if (!isJumpSupported()) {
               ShadowStack.setExceptionHandlerSkip(stackFrame);
            }
         }

         stackFrame = ShadowStack.getNextStackFrame(stackFrame);
      }

      if (stackFrame == null) {
         if (!isJumpSupported()) {
            for (Address var8 = ShadowStack.getStackTop(); var8 != null; var8 = ShadowStack.getNextStackFrame(var8)) {
               int callSiteId = ShadowStack.getCallSiteId(var8);
               if (callSiteId >= 0) {
                  ShadowStack.setExceptionHandlerRestore(var8);
               }
            }
         }

         printStack();
         abort();
      } else {
         jumpToFrame(stackFrame, handlerId);
      }
   }

   @Unmanaged
   public static void throwClassCastException() {
      throwException(new ClassCastException());
   }

   @Unmanaged
   @Export(name = "teavm_throwNullPointerException")
   public static void throwNullPointerException() {
      throwException(new NullPointerException());
   }

   @Unmanaged
   @Export(name = "teavm_throwArrayIndexOutOfBoundsException")
   public static void throwArrayIndexOutOfBoundsException() {
      throwException(new ArrayIndexOutOfBoundsException());
   }

   @Unmanaged
   private static int callStackSize() {
      Address stackFrame = ShadowStack.getStackTop();
      int size = 0;

      while (stackFrame != null) {
         int callSiteId = ShadowStack.getCallSiteId(stackFrame);
         if (callSiteId >= 0) {
            CallSite callSite = findCallSiteById(callSiteId, stackFrame);
            CallSiteLocation location = callSite.location;
            if (!isObfuscated() && location != null) {
               while (location != null) {
                  size++;
                  location = location.next;
               }
            } else {
               size++;
            }
         }

         stackFrame = ShadowStack.getNextStackFrame(stackFrame);
      }

      return size;
   }

   @Unmanaged
   public static StackTraceElement[] fillStackTrace() {
      Address stackFrame = ShadowStack.getStackTop();
      int size = callStackSize();
      ShadowStack.allocStack(1);
      ShadowStack.removeGCRoot(0);
      StackTraceElement[] target = new StackTraceElement[size];
      ShadowStack.registerGCRoot(0, target);
      int index = 0;

      while (stackFrame != null) {
         int callSiteId = ShadowStack.getCallSiteId(stackFrame);
         if (callSiteId >= 0) {
            CallSite callSite = findCallSiteById(callSiteId, stackFrame);
            CallSiteLocation location = callSite.location;
            if (isObfuscated()) {
               target[index++] = new StackTraceElement("Obfuscated", "obfuscated", "Obfuscated.java", callSiteId);
            } else if (location == null) {
               target[index++] = new StackTraceElement("", "", null, -1);
            } else {
               while (location != null) {
                  MethodLocation methodLocation = location.method;
                  StackTraceElement element;
                  if (methodLocation != null) {
                     element = new StackTraceElement(
                        methodLocation.className != null ? methodLocation.className.value : "",
                        methodLocation.methodName != null ? methodLocation.methodName.value : "",
                        methodLocation.fileName != null ? methodLocation.fileName.value : null,
                        location.lineNumber
                     );
                  } else {
                     element = new StackTraceElement("", "", null, location.lineNumber);
                  }

                  target[index++] = element;
                  location = location.next;
               }
            }
         }

         stackFrame = ShadowStack.getNextStackFrame(stackFrame);
      }

      ShadowStack.releaseStack(1);
      return target;
   }
}
