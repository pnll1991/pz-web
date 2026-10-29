package org.teavm.runtime.reflect;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import org.teavm.interop.Intrinsified;
import org.teavm.interop.Unmanaged;
import org.teavm.runtime.StringInfo;

public final class ClassInfo extends ReflectionInfo {
   @Unmanaged
   @Intrinsified
   public native int modifiers();

   @Unmanaged
   @Intrinsified
   public native int primitiveKind();

   @Unmanaged
   @Intrinsified
   public native StringInfo name();

   @Unmanaged
   @Intrinsified
   public native StringInfo simpleName();

   @Unmanaged
   @Intrinsified
   public native ClassInfo itemType();

   @Unmanaged
   @Intrinsified
   public native ClassInfo arrayType();

   @Unmanaged
   @Intrinsified
   public native ClassInfo declaringClass();

   @Unmanaged
   @Intrinsified
   public native ClassInfo enclosingClass();

   @Unmanaged
   @Intrinsified
   public native boolean isSuperTypeOf(ClassInfo var1);

   @Unmanaged
   @Intrinsified
   public native ClassInfo parent();

   @Unmanaged
   @Intrinsified
   public native int superinterfaceCount();

   @Unmanaged
   @Intrinsified
   public native ClassInfo superinterface(int var1);

   @Unmanaged
   @Intrinsified
   public native int enumConstantCount();

   @Unmanaged
   @Intrinsified
   public native Object enumConstant(int var1);

   @Intrinsified
   public native Object newArrayInstance(int var1);

   @Unmanaged
   @Intrinsified
   public native void putItem(Object var1, int var2, Object var3);

   @Intrinsified
   public native Object getItem(Object var1, int var2);

   @Unmanaged
   @Intrinsified
   public native int arrayLength(Object var1);

   @Intrinsified
   public native Object newInstance();

   @Intrinsified
   public native Proxy newProxyInstance(InvocationHandler var1);

   @Intrinsified
   public native boolean initializeNewInstance(Object var1);

   @Intrinsified
   public native void initialize();

   @Unmanaged
   @Intrinsified
   public native ClassReflectionInfo reflection();

   @Unmanaged
   @Intrinsified
   public static native void rewind();

   @Unmanaged
   @Intrinsified
   public static native ClassInfo next();

   @Unmanaged
   @Intrinsified
   public static native boolean hasNext();

   @Intrinsified
   public native Class<?> classObject();

   public static final class PrimitiveKind {
      public static final int NOT = 0;
      public static final int BOOLEAN = 1;
      public static final int BYTE = 2;
      public static final int SHORT = 3;
      public static final int CHAR = 4;
      public static final int INT = 5;
      public static final int LONG = 6;
      public static final int FLOAT = 7;
      public static final int DOUBLE = 8;
      public static final int VOID = 9;
   }
}
