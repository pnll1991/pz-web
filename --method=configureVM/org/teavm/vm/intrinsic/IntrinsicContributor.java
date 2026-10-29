package org.teavm.vm.intrinsic;

public interface IntrinsicContributor<X extends IntrinsicContributorContext, R> {
   void contribute(X var1, R var2);
}
