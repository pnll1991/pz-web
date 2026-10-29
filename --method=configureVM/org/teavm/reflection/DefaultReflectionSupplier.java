package org.teavm.reflection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.classlib.ProxyInterfaceConsumer;
import org.teavm.classlib.ReflectionContext;
import org.teavm.classlib.ReflectionSupplier;
import org.teavm.extension.ExtensionEnvironmentImpl;
import org.teavm.extension.introspect.IntrospectClassImpl;
import org.teavm.extension.introspect.IntrospectElement;
import org.teavm.extension.introspect.IntrospectFieldImpl;
import org.teavm.extension.introspect.IntrospectMember;
import org.teavm.extension.introspect.IntrospectMethodImpl;
import org.teavm.extension.spi.reflection.ProxyListener;
import org.teavm.extension.spi.reflection.ReflectionPolicy;
import org.teavm.model.MethodDescriptor;

public class DefaultReflectionSupplier implements ReflectionSupplier {
   private ExtensionEnvironmentImpl env;
   private List<ReflectionPolicy> policies = new ArrayList<>();
   private Map<String, Set<String>> pendingAccessibleFields = new HashMap<>();
   private Map<String, Set<MethodDescriptor>> pendingAccessibleMethods = new HashMap<>();

   public DefaultReflectionSupplier(ExtensionEnvironmentImpl env) {
      this.env = env;

      for (ReflectionPolicy policy : ServiceLoader.load(ReflectionPolicy.class, env.classLoader())) {
         policy.initialize(env);
         this.policies.add(policy);
      }
   }

   @Override
   public Collection<String> getAccessibleFields(ReflectionContext context, String className) {
      Set<String> fields = this.pendingAccessibleFields.remove(className);
      if (fields != null) {
         return fields;
      }

      fields = new LinkedHashSet<>();
      LinkedHashSet<MethodDescriptor> methods = new LinkedHashSet<>();
      this.pendingAccessibleMethods.put(className, methods);
      this.fillMembers(className, fields, methods);
      return fields;
   }

   @Override
   public Collection<MethodDescriptor> getAccessibleMethods(ReflectionContext context, String className) {
      Set<MethodDescriptor> methods = this.pendingAccessibleMethods.remove(className);
      if (methods != null) {
         return methods;
      }

      methods = new LinkedHashSet<>();
      LinkedHashSet<String> fields = new LinkedHashSet<>();
      this.pendingAccessibleFields.put(className, fields);
      this.fillMembers(className, fields, methods);
      return methods;
   }

   @Override
   public boolean isClassFoundByName(ReflectionContext context, String name) {
      IntrospectClassImpl<?> introspectClass = this.env.findClass(name);

      for (ReflectionPolicy policy : this.policies) {
         if (policy.isClassFoundByName(introspectClass)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public org.teavm.classlib.ProxyListener getProxyInterfaces(ReflectionContext context, ProxyInterfaceConsumer consumer) {
      ArrayList<ProxyListener> policyListeners = new ArrayList<>();

      for (ReflectionPolicy policy : this.policies) {
         ProxyListener listener = policy.getProxyInterfaces(
            itfList -> consumer.accept(itfList.stream().map(IntrospectElement::name).collect(Collectors.toList()))
         );
         if (listener != null) {
            policyListeners.add(listener);
         }
      }

      return policyListeners.isEmpty() ? null : className -> {
         for (ProxyListener listenerx : policyListeners) {
            listenerx.onClassAdded(this.env.findClass(className));
         }
      };
   }

   private void fillMembers(String className, Collection<String> fields, Collection<MethodDescriptor> methods) {
      IntrospectClassImpl<?> introspectClass = this.env.findClass(className);

      for (ReflectionPolicy policy : this.policies) {
         for (IntrospectMember member : policy.classAccessibleMembers(introspectClass)) {
            if (member instanceof IntrospectFieldImpl) {
               fields.add(member.name());
            } else if (member instanceof IntrospectMethodImpl) {
               methods.add(((IntrospectMethodImpl)member).method.getDescriptor());
            }
         }
      }
   }
}
