package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;
import org.teavm.interop.Unmanaged;
import org.teavm.runtime.StringInfo;

public final class FieldInfo extends ReflectionInfo {
   @Unmanaged
   @Intrinsified
   public native StringInfo name();

   @Unmanaged
   @Intrinsified
   public native int modifiers();

   @Unmanaged
   @Intrinsified
   public native DerivedClassInfo type();

   @Unmanaged
   @Intrinsified
   public native Object read(Object var1);

   @Unmanaged
   @Intrinsified
   public native boolean readAsBoolean(Object var1);

   @Unmanaged
   @Intrinsified
   public native byte readAsByte(Object var1);

   @Unmanaged
   @Intrinsified
   public native short readAsShort(Object var1);

   @Unmanaged
   @Intrinsified
   public native char readAsChar(Object var1);

   @Unmanaged
   @Intrinsified
   public native int readAsInt(Object var1);

   @Unmanaged
   @Intrinsified
   public native long readAsLong(Object var1);

   @Unmanaged
   @Intrinsified
   public native float readAsFloat(Object var1);

   @Unmanaged
   @Intrinsified
   public native double readAsDouble(Object var1);

   @Unmanaged
   @Intrinsified
   public native void write(Object var1, Object var2);

   @Unmanaged
   @Intrinsified
   public native void write(Object var1, boolean var2);

   @Unmanaged
   @Intrinsified
   public native void write(Object var1, byte var2);

   @Unmanaged
   @Intrinsified
   public native void write(Object var1, short var2);

   @Unmanaged
   @Intrinsified
   public native void write(Object var1, char var2);

   @Unmanaged
   @Intrinsified
   public native void write(Object var1, int var2);

   @Unmanaged
   @Intrinsified
   public native void write(Object var1, long var2);

   @Unmanaged
   @Intrinsified
   public native void write(Object var1, float var2);

   @Unmanaged
   @Intrinsified
   public native void write(Object var1, double var2);

   @Unmanaged
   @Intrinsified
   public native FieldReflectionInfo reflection();
}
