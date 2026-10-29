package org.teavm.common;

public interface Loop {
   int getHead();

   Loop getParent();

   boolean isChildOf(Loop var1);
}
