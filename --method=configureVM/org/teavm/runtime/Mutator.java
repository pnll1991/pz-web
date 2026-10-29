package org.teavm.runtime;

import org.teavm.backend.c.intrinsic.RuntimeInclude;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Unmanaged;

@Unmanaged
@StaticInit
public final class Mutator {
   private Mutator() {
   }

   public static native Address getStaticGCRoots();

   @Import(name = "teavm_stringHashtableRewind")
   @RuntimeInclude("stringhash.h")
   public static native void stringHashtableRewind();

   @Import(name = "teavm_stringHashtableCurrent")
   @RuntimeInclude("stringhash.h")
   public static native RuntimeObject stringHashtableCurrent();

   @Import(name = "teavm_stringHashtableUpdateRef")
   @RuntimeInclude("stringhash.h")
   public static native void stringHashtableUpdateRef(RuntimeObject var0);

   @Import(name = "teavm_stringHashtableDelete")
   @RuntimeInclude("stringhash.h")
   public static native void stringHashtableDelete();

   @Import(name = "teavm_stringHashtableNext")
   @RuntimeInclude("stringhash.h")
   public static native void stringHashtableNext();
}
