package org.teavm.dependency;

import org.teavm.model.MethodReference;

public interface DependencyGraphCreatorProvider {
   DependencyGraphCreator get(MethodReference var1);
}
