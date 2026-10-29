package org.teavm.dependency;

import java.io.Serializable;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;

class SerializableCallGraph implements Serializable {
   int[] nodeIndexes;
   int[] fieldAccessIndexes;
   SerializableCallGraph.Node[] nodes;
   SerializableCallGraph.CallSite[] callSites;
   SerializableCallGraph.FieldAccess[] fieldAccessList;

   static class CallSite implements Serializable {
      MethodReference method;
      boolean virtual;
      SerializableCallGraph.Location[] locations;
      int[] calledMethods;
      int[] callers;
   }

   static class FieldAccess implements Serializable {
      TextLocation location;
      int callee;
      FieldReference field;
   }

   static class Location implements Serializable {
      TextLocation value;
      int caller;
   }

   static class Node implements Serializable {
      MethodReference method;
      int[] callSites;
      int[] callerCallSites;
      int[] fieldAccessSites;
   }
}
