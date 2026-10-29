package org.teavm.dependency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.callgraph.CallGraphNode;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;

class DefaultCallGraphNode implements CallGraphNode {
   private DefaultCallGraph graph;
   private MethodReference method;
   private Map<MethodReference, DefaultCallSite> callSiteMap;
   private List<DefaultCallSite> callSites;
   private DefaultCallSite singleCallSite;
   private Collection<DefaultCallSite> safeCallSites;
   private DefaultCallSite singleCaller;
   private List<DefaultCallSite> callerCallSites;
   private List<DefaultCallSite> safeCallersCallSites;
   private Set<DefaultFieldAccessSite> fieldAccessSites = new LinkedHashSet<>();
   private Set<DefaultFieldAccessSite> safeFieldAccessSites;
   private DefaultCallSite virtualCallSite;

   DefaultCallGraphNode(DefaultCallGraph graph, MethodReference method) {
      this.graph = graph;
      this.method = method;
   }

   public DefaultCallGraph getGraph() {
      return this.graph;
   }

   @Override
   public MethodReference getMethod() {
      return this.method;
   }

   @Override
   public Collection<DefaultCallSite> getCallSites() {
      if (this.callSites == null) {
         return this.singleCallSite != null ? Collections.singletonList(this.singleCallSite) : Collections.emptyList();
      }

      if (this.safeCallSites == null) {
         this.safeCallSites = Collections.unmodifiableCollection(this.callSites);
      }

      return this.safeCallSites;
   }

   @Override
   public Collection<DefaultCallSite> getCallerCallSites() {
      if (this.callerCallSites == null) {
         return this.singleCaller != null ? Collections.singletonList(this.singleCaller) : Collections.emptyList();
      }

      if (this.safeCallersCallSites == null) {
         this.safeCallersCallSites = Collections.unmodifiableList(this.callerCallSites);
      }

      return this.safeCallersCallSites;
   }

   void addCallSite(DefaultCallSite callSite) {
      if (this.callSiteMap == null) {
         if (this.singleCallSite == null) {
            this.singleCallSite = callSite;
            return;
         }

         this.callSiteMap = new LinkedHashMap<>();
      }

      this.callSiteMap.put(callSite.method, callSite);
   }

   DefaultCallSite addCallSite(MethodReference method) {
      DefaultCallGraphNode callee = this.graph.getNode(method);
      if (this.callSites == null) {
         if (this.singleCallSite == null) {
            this.singleCallSite = new DefaultCallSite(callee, this);
            callee.addCaller(this.singleCallSite);
            return this.singleCallSite;
         }

         if (this.singleCallSite.singleCalledMethod.getMethod().equals(method)) {
            return this.singleCallSite;
         }

         this.callSiteMap = new LinkedHashMap<>();
         this.callSites = new ArrayList<>();
         this.callSiteMap.put(this.singleCallSite.singleCalledMethod.getMethod(), this.singleCallSite);
         this.callSites.add(this.singleCallSite);
         this.singleCallSite = null;
      }

      DefaultCallSite callSite = this.callSiteMap.get(method);
      if (callSite == null) {
         callSite = new DefaultCallSite(callee, this);
         callee.addCaller(callSite);
         this.callSiteMap.put(method, callSite);
         this.callSites.add(callSite);
      }

      return callSite;
   }

   DefaultCallSite getVirtualCallSite() {
      if (this.virtualCallSite == null) {
         this.virtualCallSite = new DefaultCallSite(this.method, new LinkedHashSet<>());
      }

      return this.virtualCallSite;
   }

   void addVirtualCallSite(DefaultCallSite callSite) {
      if (callSite.callers == null) {
         throw new IllegalArgumentException("Call site is not virtual");
      }

      if (callSite.callers.add(this)) {
         if (this.callSites == null) {
            this.callSites = new ArrayList<>();
            this.callSiteMap = new LinkedHashMap<>();
            if (this.singleCallSite != null) {
               this.callSites.add(this.singleCallSite);
               this.callSiteMap.put(this.singleCallSite.method, this.singleCallSite);
               this.singleCallSite = null;
            }
         }

         this.callSites.add(callSite);
      }
   }

   void addCaller(DefaultCallSite caller) {
      if (this.callerCallSites == null) {
         if (this.singleCaller == null) {
            this.singleCaller = caller;
            return;
         }

         this.callerCallSites = new ArrayList<>();
         this.callerCallSites.add(this.singleCaller);
         this.singleCaller = null;
      }

      this.callerCallSites.add(caller);
   }

   @Override
   public Collection<DefaultFieldAccessSite> getFieldAccessSites() {
      if (this.safeFieldAccessSites == null) {
         this.safeFieldAccessSites = Collections.unmodifiableSet(this.fieldAccessSites);
      }

      return this.safeFieldAccessSites;
   }

   boolean addFieldAccess(FieldReference field, TextLocation location) {
      DefaultFieldAccessSite site = new DefaultFieldAccessSite(location, this, field);
      if (this.fieldAccessSites.add(site)) {
         this.graph.addFieldAccess(site);
         return true;
      } else {
         return false;
      }
   }
}
