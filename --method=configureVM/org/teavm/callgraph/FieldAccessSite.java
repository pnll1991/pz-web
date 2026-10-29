package org.teavm.callgraph;

import org.teavm.model.FieldReference;
import org.teavm.model.TextLocation;

public interface FieldAccessSite {
   TextLocation getLocation();

   CallGraphNode getCallee();

   FieldReference getField();
}
