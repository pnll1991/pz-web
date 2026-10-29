package org.teavm.debugging.information;

import org.teavm.backend.javascript.codegen.LocationProvider;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public class DummyDebugInformationEmitter implements DebugInformationEmitter {
   @Override
   public void emitLocation(String fileName, int line) {
   }

   @Override
   public void enterLocation() {
   }

   @Override
   public void exitLocation() {
   }

   @Override
   public void emitMethod(MethodDescriptor method) {
   }

   @Override
   public void emitClass(String className) {
   }

   @Override
   public void emitVariable(String[] sourceName, String generatedName) {
   }

   @Override
   public void emitStatementStart() {
   }

   @Override
   public DeferredCallSite emitCallSite() {
      return new DeferredCallSite() {
         @Override
         public void setVirtualMethod(MethodReference method) {
         }

         @Override
         public void setStaticMethod(MethodReference method) {
         }

         @Override
         public void clean() {
         }
      };
   }

   @Override
   public void setLocationProvider(LocationProvider locationProvider) {
   }

   @Override
   public void addClass(String jsName, String className, String parentName) {
   }

   @Override
   public void addField(String fieldName, String jsName) {
   }

   @Override
   public void addSuccessors(SourceLocation location, SourceLocation[] successors) {
   }
}
