package org.teavm.backend.javascript.spi;

import org.teavm.model.MethodReference;

public interface MethodContributor {
   boolean isContributing(MethodContributorContext var1, MethodReference var2);
}
