package org.teavm.runtime;

import org.teavm.interop.Structure;
import org.teavm.interop.c.Name;
import org.teavm.interop.c.Native;

@Native
@Name("TeaVM_MethodLocation")
public class MethodLocation extends Structure {
   public StringPtr fileName;
   public StringPtr className;
   public StringPtr methodName;
}
