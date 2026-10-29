package org.teavm.vm.spi;

import org.teavm.model.ValueType;

public interface ClassFilter {
   boolean accept(ClassFilterContext var1, ValueType var2);
}
