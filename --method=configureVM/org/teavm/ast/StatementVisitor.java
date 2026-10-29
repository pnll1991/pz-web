package org.teavm.ast;

public interface StatementVisitor {
   void visit(AssignmentStatement var1);

   void visit(SequentialStatement var1);

   void visit(ConditionalStatement var1);

   void visit(SwitchStatement var1);

   void visit(WhileStatement var1);

   void visit(BlockStatement var1);

   void visit(BreakStatement var1);

   void visit(ContinueStatement var1);

   void visit(ReturnStatement var1);

   void visit(ThrowStatement var1);

   void visit(InitClassStatement var1);

   void visit(TryCatchStatement var1);

   void visit(GotoPartStatement var1);

   void visit(MonitorEnterStatement var1);

   void visit(MonitorExitStatement var1);
}
