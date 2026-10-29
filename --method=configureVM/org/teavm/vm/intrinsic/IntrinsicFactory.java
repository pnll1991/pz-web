package org.teavm.vm.intrinsic;

import org.teavm.model.MethodReference;

public interface IntrinsicFactory<I> {
   I createIntrinsic(MethodReference var1);
}
