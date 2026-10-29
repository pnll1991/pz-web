package org.teavm.runtime;

import org.teavm.interop.Structure;
import org.teavm.interop.c.Name;
import org.teavm.interop.c.Native;

@Native
@Name("TeaVM_ExceptionHandler")
public class ExceptionHandler extends Structure {
   public int id;
   public RuntimeClass exceptionClass;
   public ExceptionHandler next;
}
