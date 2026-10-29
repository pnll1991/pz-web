package org.teavm.classlib;

import org.teavm.interop.PlatformMarker;

public final class PlatformDetector {
   private PlatformDetector() {
   }

   @PlatformMarker
   public static boolean isTeaVM() {
      return false;
   }

   @PlatformMarker("webassembly-gc")
   public static boolean isWebAssemblyGC() {
      return false;
   }

   @PlatformMarker("javascript")
   public static boolean isJavaScript() {
      return false;
   }

   @PlatformMarker("c")
   public static boolean isC() {
      return false;
   }

   @PlatformMarker("low_level")
   public static boolean isLowLevel() {
      return false;
   }

   @PlatformMarker("own_gc")
   public static boolean requiresOwnGC() {
      return false;
   }
}
