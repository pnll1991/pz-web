package org.teavm.vm.intrinsic;

public interface IntrinsicProviders<C, B> {
   IntrinsicProvider<C> callSiteIntrinsics();

   IntrinsicProvider<B> bodyIntrinsics();
}
