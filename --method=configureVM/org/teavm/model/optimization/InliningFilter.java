package org.teavm.model.optimization;

import org.teavm.model.MethodReference;

public interface InliningFilter {
   InliningFilter DEFAULT = m -> true;

   boolean apply(MethodReference var1);
}
