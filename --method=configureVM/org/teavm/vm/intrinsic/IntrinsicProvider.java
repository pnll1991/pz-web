package org.teavm.vm.intrinsic;

import org.teavm.model.MethodReference;

public interface IntrinsicProvider<I> {
   I getIntrinsic(MethodReference var1);
}
