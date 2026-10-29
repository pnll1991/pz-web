package org.teavm.debugging.information;

public abstract class DebuggerCallSite {
   static final int NONE = 0;
   static final int STATIC = 1;
   static final int VIRTUAL = 2;

   public abstract void acceptVisitor(DebuggerCallSiteVisitor var1);
}
