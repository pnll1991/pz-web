package org.teavm.vm;

public interface TeaVMProgressListener {
   TeaVMProgressFeedback phaseStarted(TeaVMPhase var1, int var2);

   TeaVMProgressFeedback progressReached(int var1);
}
