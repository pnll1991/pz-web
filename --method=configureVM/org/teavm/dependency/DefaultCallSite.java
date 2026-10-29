package org.teavm.dependency;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.callgraph.CallGraphNode;
import org.teavm.callgraph.CallSite;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;

class DefaultCallSite implements CallSite, Serializable {
   private Map<CallGraphNode, Set<TextLocation>> locations;
   private TextLocation singleLocation;
   MethodReference method;
   Set<DefaultCallGraphNode> callers;
   private DefaultCallGraphNode singleCaller;
   Set<DefaultCallGraphNode> calledMethods;
   DefaultCallGraphNode singleCalledMethod;
   Collection<? extends DefaultCallGraphNode> readonlyCalledMethods;

   DefaultCallSite(MethodReference method, Set<DefaultCallGraphNode> callers) {
      this.method = method;
      this.callers = callers;
      this.locations = new HashMap<>();
      this.calledMethods = new LinkedHashSet<>();
   }

   DefaultCallSite(DefaultCallGraphNode callee, DefaultCallGraphNode caller) {
      this.singleCalledMethod = callee;
      this.singleCaller = caller;
   }

   @Override
   public Collection<? extends TextLocation> getLocations(CallGraphNode caller) {
      if (this.singleLocation != null) {
         return caller == this.singleCaller ? Collections.singleton(this.singleLocation) : Collections.emptySet();
      }

      if (this.locations == null) {
         return Collections.emptyList();
      }

      Set<TextLocation> result = this.locations.get(caller);
      return result != null ? Collections.unmodifiableSet(result) : Collections.emptySet();
   }

   public void addLocation(DefaultCallGraphNode caller, TextLocation location) {
      if (this.locations == null) {
         if (this.singleLocation == null && this.callers == null) {
            this.singleLocation = location;
            return;
         }

         this.locations = new LinkedHashMap<>();
         if (this.singleLocation != null) {
            Set<TextLocation> singleLocations = new LinkedHashSet<>();
            singleLocations.add(this.singleLocation);
            this.locations.put(this.singleCaller, singleLocations);
         }
      }

      this.locations.computeIfAbsent(caller, k -> new LinkedHashSet<>()).add(location);
   }

   @Override
   public Collection<? extends DefaultCallGraphNode> getCalledMethods() {
      if (this.singleCalledMethod != null) {
         return Collections.singletonList(this.singleCalledMethod);
      }

      if (this.readonlyCalledMethods == null) {
         this.readonlyCalledMethods = Collections.unmodifiableCollection(this.calledMethods);
      }

      return this.readonlyCalledMethods;
   }

   @Override
   public Collection<? extends DefaultCallGraphNode> getCallers() {
      return this.callers != null ? this.callers : Collections.singletonList(this.singleCaller);
   }
}
