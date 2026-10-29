package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;
import org.teavm.interop.Unmanaged;

public class GenericTypeInfo extends ReflectionInfo {
   @Unmanaged
   @Intrinsified
   public final native int kind();

   @Unmanaged
   @Intrinsified
   public final native ParameterizedTypeInfo asParameterizedType();

   @Unmanaged
   @Intrinsified
   public final native TypeVariableReference asTypeVariable();

   @Unmanaged
   @Intrinsified
   public final native GenericArrayInfo asGenericArray();

   @Unmanaged
   @Intrinsified
   public final native WildcardTypeInfo asWildcard();

   @Unmanaged
   @Intrinsified
   public final native RawTypeInfo asRawType();

   public static class Kind {
      public static final int PARAMETERIZED_TYPE = 0;
      public static final int TYPE_VARIABLE = 1;
      public static final int GENERIC_ARRAY = 2;
      public static final int UPPER_BOUND_WILDCARD = 3;
      public static final int LOWER_BOUND_WILDCARD = 4;
      public static final int UNBOUNDED_WILDCARD = 5;
      public static final int RAW_TYPE = 6;
   }
}
