package org.teavm.runtime;

import org.teavm.interop.StaticInit;
import org.teavm.interop.Structure;

@StaticInit
class FreeChunk extends Structure {
   int classReference;
   int size;
}
