package org.teavm.callgraph;

import java.util.Collection;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;

public interface CallGraph {
   CallGraphNode getNode(MethodReference var1);

   Collection<? extends FieldAccessSite> getFieldAccess(FieldReference var1);
}
