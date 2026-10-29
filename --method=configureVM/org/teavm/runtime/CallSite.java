package org.teavm.runtime;

import org.teavm.interop.Structure;
import org.teavm.interop.c.Name;
import org.teavm.interop.c.Native;

@Native
@Name("TeaVM_CallSite")
public class CallSite extends Structure {
   public ExceptionHandler firstHandler;
   public CallSiteLocation location;
}
