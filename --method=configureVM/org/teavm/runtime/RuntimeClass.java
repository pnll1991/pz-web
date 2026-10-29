package org.teavm.runtime;

import org.teavm.interop.Address;
import org.teavm.interop.Structure;
import org.teavm.interop.Unmanaged;
import org.teavm.interop.c.Name;
import org.teavm.interop.c.Native;

@Native
@Name("TeaVM_Class")
public class RuntimeClass extends Structure {
   public static final int INITIALIZED = 1;
   public static final int VM_TYPE_SHIFT = 1;
   public static final int VM_TYPE_MASK = 7;
   public static final int PRIMITIVE_TYPE_SHIFT = 4;
   public static final int PRIMITIVE_TYPE_MASK = 15;
   public static final int VM_TYPE_REGULAR = 0;
   public static final int VM_TYPE_WEAKREFERENCE = 1;
   public static final int VM_TYPE_REFERENCEQUEUE = 2;
   public static final int VM_TYPE_BUFFER = 3;
   public RuntimeObject classObject;
   public RuntimeClass next;
   public int size;
   public int flags;
   public int tag;
   public int modifiers;
   public RuntimeObjectPtr name;
   public RuntimeObjectPtr simpleName;
   public RuntimeClass itemType;
   public RuntimeClass arrayType;
   public RuntimeClass declaringClass;
   public RuntimeClass enclosingClass;
   public IsSupertypeFunction isSupertypeOf;
   public InitFunction init;
   public RuntimeClass superclass;
   public int superinterfaceCount;
   public RuntimeClassPointer superinterfaces;
   public Address enumValues;
   public Address layout;

   @Unmanaged
   public static RuntimeClass getClass(RuntimeObject object) {
      return unpack(object.classReference);
   }

   @Unmanaged
   public static native RuntimeClass unpack(int var0);

   @Unmanaged
   public final native int pack();

   @Unmanaged
   public static boolean isPrimitive(RuntimeClass cls) {
      return (cls.flags >> 4 & 15) != 0;
   }

   @Unmanaged
   public static native RuntimeClass first();
}
