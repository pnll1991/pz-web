package org.teavm.runtime;

import org.teavm.interop.Structure;
import org.teavm.interop.c.Name;
import org.teavm.interop.c.Native;

@Native
@Name("TeaVM_CallSiteLocation")
public class CallSiteLocation extends Structure {
   public MethodLocation method;
   public int lineNumber;
   CallSiteLocation next;
}
