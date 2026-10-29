package org.teavm.callgraph;

import java.util.Collection;
import org.teavm.model.MethodReference;

public interface CallGraphNode {
   CallGraph getGraph();

   MethodReference getMethod();

   Collection<? extends CallSite> getCallSites();

   Collection<? extends CallSite> getCallerCallSites();

   Collection<? extends FieldAccessSite> getFieldAccessSites();
}
