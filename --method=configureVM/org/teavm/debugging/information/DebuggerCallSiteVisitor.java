package org.teavm.debugging.information;

public interface DebuggerCallSiteVisitor {
   void visit(DebuggerVirtualCallSite var1);

   void visit(DebuggerStaticCallSite var1);
}
