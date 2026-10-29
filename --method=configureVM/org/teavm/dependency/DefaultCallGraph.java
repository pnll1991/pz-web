package org.teavm.dependency;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.callgraph.CallGraph;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;

class DefaultCallGraph implements CallGraph, Serializable {
   public Map<MethodReference, DefaultCallGraphNode> nodes = new HashMap<>();
   Map<FieldReference, Set<DefaultFieldAccessSite>> fieldAccessSites = new HashMap<>();

   public DefaultCallGraphNode getNode(MethodReference method) {
      return this.nodes.computeIfAbsent(method, k -> new DefaultCallGraphNode(this, k));
   }

   @Override
   public Collection<DefaultFieldAccessSite> getFieldAccess(FieldReference reference) {
      Set<DefaultFieldAccessSite> resultSet = this.fieldAccessSites.get(reference);
      return resultSet != null ? Collections.unmodifiableSet(resultSet) : Collections.emptySet();
   }

   void addFieldAccess(DefaultFieldAccessSite accessSite) {
      this.fieldAccessSites.computeIfAbsent(accessSite.getField(), k -> new LinkedHashSet<>()).add(accessSite);
   }

   private void writeObject(ObjectOutputStream out) throws IOException {
      DefaultCallGraph.SerializableCallGraphBuilder builder = new DefaultCallGraph.SerializableCallGraphBuilder();
      out.writeObject(builder.build(this));
   }

   private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      SerializableCallGraph scg = (SerializableCallGraph)in.readObject();
      this.nodes = new LinkedHashMap<>();
      this.fieldAccessSites = new LinkedHashMap<>();
      new DefaultCallGraph.CallGraphBuilder().build(scg, this);
   }

   static class CallGraphBuilder {
      List<DefaultCallGraphNode> nodes = new ArrayList<>();
      List<DefaultCallSite> callSites = new ArrayList<>();
      List<DefaultFieldAccessSite> fieldAccessList = new ArrayList<>();

      void build(SerializableCallGraph scg, DefaultCallGraph cg) {
         for (SerializableCallGraph.Node serializableNode : scg.nodes) {
            this.nodes.add(new DefaultCallGraphNode(cg, serializableNode.method));
         }

         for (SerializableCallGraph.CallSite scs : scg.callSites) {
            DefaultCallSite callSite;
            if (scs.virtual) {
               callSite = new DefaultCallSite(scs.method, this.mapNodes(scs.callers));
               callSite.calledMethods.addAll(this.mapNodes(scs.calledMethods));
            } else {
               callSite = new DefaultCallSite(this.nodes.get(scs.calledMethods[0]), this.nodes.get(scs.callers[0]));
            }

            for (SerializableCallGraph.Location location : scs.locations) {
               callSite.addLocation(this.nodes.get(location.caller), location.value);
            }

            this.callSites.add(callSite);
         }

         for (SerializableCallGraph.FieldAccess sfa : scg.fieldAccessList) {
            this.fieldAccessList.add(new DefaultFieldAccessSite(sfa.location, this.nodes.get(sfa.callee), sfa.field));
         }

         for (int i = 0; i < scg.nodes.length; i++) {
            SerializableCallGraph.Node node = scg.nodes[i];
            DefaultCallGraphNode resultNode = this.nodes.get(i);
            if (node.callSites != null) {
               for (int callSiteIndex : node.callSites) {
                  resultNode.addCallSite(this.callSites.get(callSiteIndex));
               }
            }

            if (node.callerCallSites != null) {
               for (int callerCallSiteIndex : node.callerCallSites) {
                  resultNode.addCaller(this.callSites.get(callerCallSiteIndex));
               }
            }
         }

         for (int index : scg.nodeIndexes) {
            DefaultCallGraphNode node = this.nodes.get(index);
            cg.nodes.put(node.getMethod(), node);
         }

         for (int index : scg.fieldAccessIndexes) {
            cg.addFieldAccess(this.fieldAccessList.get(index));
         }
      }

      private Set<DefaultCallGraphNode> mapNodes(int[] nodes) {
         Set<DefaultCallGraphNode> result = new LinkedHashSet<>();

         for (int i = 0; i < nodes.length; i++) {
            result.add(this.nodes.get(nodes[i]));
         }

         return result;
      }
   }

   static class SerializableCallGraphBuilder {
      List<SerializableCallGraph.Node> nodes = new ArrayList<>();
      ObjectIntMap<DefaultCallGraphNode> nodeToIndex = new ObjectIntHashMap();
      List<SerializableCallGraph.CallSite> callSites = new ArrayList<>();
      ObjectIntMap<DefaultCallSite> callSiteToIndex = new ObjectIntHashMap();
      List<SerializableCallGraph.FieldAccess> fieldAccessList = new ArrayList<>();
      ObjectIntMap<DefaultFieldAccessSite> fieldAccessToIndex = new ObjectIntHashMap();
      List<DefaultCallGraphNode> nodesToProcess = new ArrayList<>();
      List<DefaultCallSite> callSitesToProcess = new ArrayList<>();
      List<DefaultFieldAccessSite> fieldAccessToProcess = new ArrayList<>();

      SerializableCallGraph build(DefaultCallGraph cg) {
         SerializableCallGraph scg = new SerializableCallGraph();
         scg.nodeIndexes = cg.nodes.values().stream().mapToInt(this::getNode).toArray();
         scg.fieldAccessIndexes = cg.fieldAccessSites
            .values()
            .stream()
            .flatMapToInt(accessSites -> accessSites.stream().mapToInt(this::getFieldAccess))
            .toArray();

         while (this.step()) {
         }

         scg.nodes = this.nodes.toArray(new SerializableCallGraph.Node[0]);
         scg.callSites = this.callSites.toArray(new SerializableCallGraph.CallSite[0]);
         scg.fieldAccessList = this.fieldAccessList.toArray(new SerializableCallGraph.FieldAccess[0]);
         return scg;
      }

      boolean step() {
         return this.processNodes() | this.processCallSites() | this.processFieldAccess();
      }

      boolean processNodes() {
         boolean hasAny = false;

         for (DefaultCallGraphNode node : this.nodesToProcess.toArray(new DefaultCallGraphNode[0])) {
            int index = this.nodeToIndex.get(node);
            SerializableCallGraph.Node serializableNode = this.nodes.get(index);
            serializableNode.method = node.getMethod();
            serializableNode.callSites = node.getCallSites().stream().mapToInt(this::getCallSite).toArray();
            serializableNode.callerCallSites = node.getCallerCallSites().stream().mapToInt(this::getCallSite).toArray();
            serializableNode.fieldAccessSites = node.getFieldAccessSites().stream().mapToInt(this::getFieldAccess).toArray();
            hasAny = true;
         }

         this.nodesToProcess.clear();
         return hasAny;
      }

      boolean processCallSites() {
         boolean hasAny = false;

         for (DefaultCallSite callSite : this.callSitesToProcess.toArray(new DefaultCallSite[0])) {
            int index = this.callSiteToIndex.get(callSite);
            SerializableCallGraph.CallSite scs = this.callSites.get(index);
            scs.virtual = callSite.callers != null;
            List<SerializableCallGraph.Location> locations = new ArrayList<>();

            for (DefaultCallGraphNode caller : callSite.getCallers()) {
               for (TextLocation textLocation : callSite.getLocations(caller)) {
                  SerializableCallGraph.Location location = new SerializableCallGraph.Location();
                  location.caller = this.getNode(caller);
                  location.value = textLocation;
                  locations.add(location);
               }
            }

            scs.locations = locations.toArray(new SerializableCallGraph.Location[0]);
            scs.callers = this.getNodes(callSite.getCallers());
            scs.calledMethods = this.getNodes(callSite.getCalledMethods());
            hasAny = true;
         }

         this.callSitesToProcess.clear();
         return hasAny;
      }

      boolean processFieldAccess() {
         boolean hasAny = false;

         for (DefaultFieldAccessSite accessSite : this.fieldAccessToProcess.toArray(new DefaultFieldAccessSite[0])) {
            int index = this.fieldAccessToIndex.get(accessSite);
            SerializableCallGraph.FieldAccess sfa = this.fieldAccessList.get(index);
            sfa.location = accessSite.getLocation();
            sfa.field = accessSite.getField();
            sfa.callee = this.getNode(accessSite.getCallee());
            hasAny = true;
         }

         this.fieldAccessToProcess.clear();
         return hasAny;
      }

      private int getNode(DefaultCallGraphNode node) {
         int index = this.nodeToIndex.getOrDefault(node, -1);
         if (index < 0) {
            index = this.nodeToIndex.size();
            this.nodeToIndex.put(node, index);
            this.nodes.add(new SerializableCallGraph.Node());
            this.nodesToProcess.add(node);
         }

         return index;
      }

      private int[] getNodes(Collection<? extends DefaultCallGraphNode> nodes) {
         int[] result = new int[nodes.size()];
         int index = 0;

         for (DefaultCallGraphNode node : nodes) {
            result[index++] = this.getNode(node);
         }

         return result;
      }

      private int getCallSite(DefaultCallSite callSite) {
         int index = this.callSiteToIndex.getOrDefault(callSite, -1);
         if (index < 0) {
            index = this.callSiteToIndex.size();
            this.callSiteToIndex.put(callSite, index);
            this.callSites.add(new SerializableCallGraph.CallSite());
            this.callSitesToProcess.add(callSite);
         }

         return index;
      }

      private int getFieldAccess(DefaultFieldAccessSite fieldAccessSite) {
         int index = this.fieldAccessToIndex.getOrDefault(fieldAccessSite, -1);
         if (index < 0) {
            index = this.fieldAccessToIndex.size();
            this.fieldAccessToIndex.put(fieldAccessSite, index);
            this.fieldAccessList.add(new SerializableCallGraph.FieldAccess());
            this.fieldAccessToProcess.add(fieldAccessSite);
         }

         return index;
      }
   }
}
