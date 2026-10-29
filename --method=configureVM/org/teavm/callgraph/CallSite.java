package org.teavm.callgraph;

import java.util.Collection;
import org.teavm.model.TextLocation;

public interface CallSite {
   Collection<? extends TextLocation> getLocations(CallGraphNode var1);

   Collection<? extends CallGraphNode> getCalledMethods();

   Collection<? extends CallGraphNode> getCallers();
}
