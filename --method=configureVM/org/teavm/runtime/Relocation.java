package org.teavm.runtime;

import org.teavm.interop.Address;
import org.teavm.interop.Structure;

class Relocation extends Structure {
   int classBackup;
   int sizeBackup;
   Address newAddress;
}
