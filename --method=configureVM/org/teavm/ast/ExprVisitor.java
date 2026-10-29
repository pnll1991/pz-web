package org.teavm.ast;

public interface ExprVisitor {
   void visit(BinaryExpr var1);

   void visit(UnaryExpr var1);

   void visit(ConditionalExpr var1);

   void visit(ConstantExpr var1);

   void visit(VariableExpr var1);

   void visit(SubscriptExpr var1);

   void visit(UnwrapArrayExpr var1);

   void visit(InvocationExpr var1);

   void visit(QualificationExpr var1);

   void visit(NewExpr var1);

   void visit(NewArrayExpr var1);

   void visit(NewMultiArrayExpr var1);

   void visit(ArrayFromDataExpr var1);

   void visit(InstanceOfExpr var1);

   void visit(CastExpr var1);

   void visit(PrimitiveCastExpr var1);

   void visit(BoundCheckExpr var1);
}
