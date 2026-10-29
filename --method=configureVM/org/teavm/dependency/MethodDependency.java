package org.teavm.dependency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.teavm.model.CallLocation;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;

public class MethodDependency implements MethodDependencyInfo {
   private DependencyAnalyzer dependencyAnalyzer;
   DependencyNode[] variableNodes;
   private int parameterCount;
   DependencyNode resultNode;
   DependencyNode thrown;
   MethodHolder method;
   boolean present;
   private MethodReference reference;
   boolean used;
   boolean external;
   DependencyPlugin dependencyPlugin;
   boolean dependencyPluginAttached;
   List<LocationListener> locationListeners;
   Set<CallLocation> locations;
   boolean activated;

   MethodDependency(
      DependencyAnalyzer dependencyAnalyzer,
      DependencyNode[] variableNodes,
      int parameterCount,
      DependencyNode resultNode,
      DependencyNode thrown,
      MethodHolder method,
      MethodReference reference
   ) {
      this.dependencyAnalyzer = dependencyAnalyzer;
      this.variableNodes = Arrays.copyOf(variableNodes, variableNodes.length);
      this.parameterCount = parameterCount;
      this.thrown = thrown;
      this.resultNode = resultNode;
      this.method = method;
      this.reference = reference;
   }

   public DependencyAgent getDependencyAgent() {
      return this.dependencyAnalyzer.getAgent();
   }

   public DependencyNode[] getVariables() {
      return Arrays.copyOf(this.variableNodes, this.variableNodes.length);
   }

   void setVariables(DependencyNode[] variables) {
      this.variableNodes = variables;
   }

   @Override
   public int getVariableCount() {
      return this.variableNodes.length;
   }

   public DependencyNode getVariable(int index) {
      return this.variableNodes[index];
   }

   @Override
   public int getParameterCount() {
      return this.parameterCount;
   }

   public DependencyNode getResult() {
      return this.resultNode;
   }

   public DependencyNode getThrown() {
      return this.thrown;
   }

   @Override
   public MethodReference getReference() {
      return this.reference;
   }

   public MethodReader getMethod() {
      return this.method;
   }

   @Override
   public boolean isMissing() {
      return this.method == null && !this.present;
   }

   @Override
   public boolean isUsed() {
      return this.used;
   }

   public MethodDependency addLocation(CallLocation location) {
      return this.addLocation(location, true);
   }

   MethodDependency addLocation(CallLocation location, boolean addCallSite) {
      DefaultCallGraphNode node = this.dependencyAnalyzer.callGraph.getNode(location.getMethod());
      if (this.locations == null) {
         this.locations = new LinkedHashSet<>();
      }

      if (this.locations.add(location)) {
         if (addCallSite) {
            DefaultCallSite callSite = node.addCallSite(this.reference);
            if (location.getSourceLocation() != null) {
               callSite.addLocation(node, location.getSourceLocation());
            }
         }

         if (this.locationListeners != null) {
            for (LocationListener listener : this.locationListeners.toArray(new LocationListener[0])) {
               listener.locationAdded(location);
            }
         }
      }

      return this;
   }

   public void addLocationListener(LocationListener listener) {
      if (this.locationListeners == null) {
         this.locationListeners = new ArrayList<>();
         this.locationListeners.add(listener);
         if (this.locations != null) {
            for (CallLocation location : this.locations.toArray(new CallLocation[0])) {
               listener.locationAdded(location);
            }
         }
      }
   }

   public MethodDependency propagate(int parameterIndex, Class<?> type) {
      return this.propagate(parameterIndex, this.dependencyAnalyzer.getClassType(type.getName()));
   }

   public MethodDependency propagateClass(int parameterIndex, String type) {
      return this.propagate(parameterIndex, this.dependencyAnalyzer.getClassType(type));
   }

   public MethodDependency propagate(int parameterIndex, DependencyType type) {
      this.getVariable(parameterIndex).propagate(type);
      return this;
   }

   public void use() {
      this.use(true);
   }

   void use(boolean external) {
      if (!this.used) {
         this.used = true;
         if (!this.isMissing()) {
            this.dependencyAnalyzer.scheduleMethodAnalysis(this);
         }
      }

      if (external) {
         this.external = true;
      }
   }

   @Override
   public boolean isCalled() {
      return this.external;
   }

   void cleanup() {
      if (this.method != null) {
         this.present = true;
         this.method = null;
      }
   }
}
