package org.teavm.runtime;

import org.teavm.interop.Address;
import org.teavm.interop.Structure;

class RelocationBlock extends Structure {
   int count;
   Address start;
   Address end;
}
