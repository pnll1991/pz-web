package org.teavm.dependency;

import org.teavm.model.FieldReference;

public interface FieldDependencyInfo {
   ValueDependencyInfo getValue();

   FieldReference getReference();

   boolean isMissing();
}
