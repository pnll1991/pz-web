package org.teavm.dependency;

import java.util.List;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHandle;
import org.teavm.model.MethodReference;
import org.teavm.model.RuntimeConstant;
import org.teavm.model.TextLocation;
import org.teavm.model.emit.ValueEmitter;

public class DynamicCallSite {
   private MethodReference caller;
   private MethodDescriptor calledMethod;
   private ValueEmitter instance;
   private List<ValueEmitter> arguments;
   private MethodHandle bootstrapMethod;
   private List<RuntimeConstant> bootstrapArguments;
   private DependencyAgent agent;
   private TextLocation location;

   DynamicCallSite(
      MethodReference caller,
      MethodDescriptor calledMethod,
      ValueEmitter instance,
      List<ValueEmitter> arguments,
      MethodHandle bootstrapMethod,
      List<RuntimeConstant> bootstrapArguments,
      DependencyAgent agent,
      TextLocation location
   ) {
      this.caller = caller;
      this.calledMethod = calledMethod;
      this.instance = instance;
      this.arguments = List.copyOf(arguments);
      this.bootstrapMethod = bootstrapMethod;
      this.bootstrapArguments = List.copyOf(bootstrapArguments);
      this.agent = agent;
      this.location = location;
   }

   public MethodReference getCaller() {
      return this.caller;
   }

   public MethodDescriptor getCalledMethod() {
      return this.calledMethod;
   }

   public MethodHandle getBootstrapMethod() {
      return this.bootstrapMethod;
   }

   public List<ValueEmitter> getArguments() {
      return this.arguments;
   }

   public List<RuntimeConstant> getBootstrapArguments() {
      return this.bootstrapArguments;
   }

   public DependencyAgent getAgent() {
      return this.agent;
   }

   public ValueEmitter getInstance() {
      return this.instance;
   }

   public TextLocation getLocation() {
      return this.location;
   }
}
