package org.teavm.backend.c.runtime;

import org.teavm.interop.Structure;

public class StringList extends Structure {
   public CharPtr data;
   public int length;
   public StringList next;
}
