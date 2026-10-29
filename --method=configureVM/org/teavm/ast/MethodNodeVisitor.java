package org.teavm.ast;

public interface MethodNodeVisitor {
   void visit(RegularMethodNode var1);

   void visit(AsyncMethodNode var1);
}
