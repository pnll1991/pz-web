package org.teavm.model.optimization;

import org.teavm.model.MethodReference;

public interface InliningFilterFactory {
   InliningFilterFactory DEFAULT = m -> InliningFilter.DEFAULT;

   InliningFilter createFilter(MethodReference var1);
}
