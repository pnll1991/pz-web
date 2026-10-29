package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;
import org.teavm.runtime.StringInfo;

public final class AnnotationValueArray extends ReflectionInfo {
   @Intrinsified
   public native int size();

   @Intrinsified
   public native boolean getBoolean(int var1);

   @Intrinsified
   public native byte getByte(int var1);

   @Intrinsified
   public native short getShort(int var1);

   @Intrinsified
   public native char getChar(int var1);

   @Intrinsified
   public native int getInt(int var1);

   @Intrinsified
   public native long getLong(int var1);

   @Intrinsified
   public native float getFloat(int var1);

   @Intrinsified
   public native double getDouble(int var1);

   @Intrinsified
   public native DerivedClassInfo getClass(int var1);

   @Intrinsified
   public native short getEnum(int var1);

   @Intrinsified
   public native StringInfo getString(int var1);

   @Intrinsified
   public native AnnotationData getAnnotation(int var1);
}
