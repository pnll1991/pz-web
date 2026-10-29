package org.teavm.model.util;

import org.teavm.model.MethodReference;
import org.teavm.model.Program;

public interface VariableCategoryProvider {
   Object[] getCategories(Program var1, MethodReference var2);
}
