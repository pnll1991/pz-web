package org.teavm.runtime;

import org.teavm.interop.Structure;
import org.teavm.interop.c.Name;
import org.teavm.interop.c.Native;

@Native
@Name("TeaVM_StringPtr")
public class StringPtr extends Structure {
   public String value;
}
