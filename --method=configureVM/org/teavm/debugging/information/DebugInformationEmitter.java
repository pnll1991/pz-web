package org.teavm.debugging.information;

import org.teavm.backend.javascript.codegen.LocationProvider;
import org.teavm.model.MethodDescriptor;

public interface DebugInformationEmitter {
   void setLocationProvider(LocationProvider var1);

   void emitLocation(String var1, int var2);

   void enterLocation();

   void exitLocation();

   void emitStatementStart();

   void emitMethod(MethodDescriptor var1);

   void emitClass(String var1);

   void emitVariable(String[] var1, String var2);

   DeferredCallSite emitCallSite();

   void addClass(String var1, String var2, String var3);

   void addField(String var1, String var2);

   void addSuccessors(SourceLocation var1, SourceLocation[] var2);
}
