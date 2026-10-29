package org.teavm.model;

import java.util.List;

public class DynamicConstant {
   public final String name;
   public final ValueType type;
   public final MethodHandle bootstrapMethod;
   public final List<? extends RuntimeConstant> bootstrapMethodArguments;

   public DynamicConstant(String name, ValueType type, MethodHandle bootstrapMethod, List<? extends RuntimeConstant> bootstrapMethodArguments) {
      this.name = name;
      this.type = type;
      this.bootstrapMethod = bootstrapMethod;
      this.bootstrapMethodArguments = List.copyOf(bootstrapMethodArguments);
   }
}
