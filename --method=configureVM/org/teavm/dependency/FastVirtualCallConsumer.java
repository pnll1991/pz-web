package org.teavm.dependency;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.model.CallLocation;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

class FastVirtualCallConsumer implements DependencyConsumer {
   private final DependencyNode node;
   private final MethodReference methodRef;
   private final DependencyAnalyzer analyzer;
   private final Map<MethodReference, CallLocation> callLocations = new LinkedHashMap<>();
   private final Set<MethodDependency> methods = new LinkedHashSet<>(100, 0.5F);
   final DefaultCallSite callSite;

   FastVirtualCallConsumer(DependencyNode node, MethodReference methodRef, DependencyAnalyzer analyzer) {
      this.node = node;
      this.methodRef = methodRef;
      this.analyzer = analyzer;
      this.callSite = analyzer.callGraph.getNode(methodRef).getVirtualCallSite();
   }

   @Override
   public void consume(DependencyType type) {
      ValueType valueType = type.getValueType();
      if (DependencyAnalyzer.shouldLog) {
         System.out.println("Virtual call of " + this.methodRef + " detected on " + this.node.getTag() + ". Target class is " + valueType);
      }

      String className;
      if (valueType instanceof ValueType.Object) {
         className = ((ValueType.Object)valueType).getClassName();
      } else {
         if (!(valueType instanceof ValueType.Array)) {
            return;
         }

         className = "java.lang.Object";
      }

      MethodDependency methodDep = this.analyzer.linkMethod(className, this.methodRef.getDescriptor());
      if (this.methods.add(methodDep)) {
         DefaultCallGraphNode calledMethodNode = this.analyzer.callGraph.getNode(methodDep.getReference());
         this.callSite.calledMethods.add(calledMethodNode);
         calledMethodNode.addCaller(this.callSite);

         for (CallLocation location : this.callLocations.values()) {
            methodDep.addLocation(location, false);
         }

         if (!methodDep.isMissing()) {
            methodDep.use();
         }
      }
   }

   void addLocation(CallLocation location) {
      if (this.callLocations.putIfAbsent(location.getMethod(), location) == null) {
         DefaultCallGraphNode caller = this.analyzer.callGraph.getNode(location.getMethod());
         caller.addVirtualCallSite(this.callSite);
         if (location.getSourceLocation() != null) {
            this.callSite.addLocation(caller, location.getSourceLocation());
         }

         for (MethodDependency method : this.methods) {
            method.addLocation(location, false);
         }
      }
   }
}
