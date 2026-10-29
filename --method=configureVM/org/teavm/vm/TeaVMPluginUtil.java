package org.teavm.vm;

import java.lang.reflect.Modifier;
import org.teavm.backend.javascript.TeaVMJavaScriptHost;
import org.teavm.backend.javascript.spi.GeneratedBy;
import org.teavm.backend.javascript.spi.Generator;
import org.teavm.backend.javascript.spi.InjectedBy;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.dependency.DependencyPlugin;
import org.teavm.dependency.PluggableDependency;
import org.teavm.interop.PlatformMarker;
import org.teavm.metaprogramming.CompileTime;
import org.teavm.metaprogramming.Meta;
import org.teavm.metaprogramming.Metaprogramming;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.Value;
import org.teavm.metaprogramming.reflect.ReflectMethod;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.vm.spi.TeaVMHost;

@CompileTime
public final class TeaVMPluginUtil {
   private TeaVMPluginUtil() {
   }

   public static void handleNatives(TeaVMHost host, Class<?> cls) {
      if (isBootstrap()) {
         handleNativesImpl(host, host.getExtension(TeaVMJavaScriptHost.class), cls);
      }
   }

   @PlatformMarker
   private static boolean isBootstrap() {
      return false;
   }

   @Meta
   private static native void handleNativesImpl(TeaVMHost var0, TeaVMJavaScriptHost var1, Class<?> var2);

   private static void handleNativesImpl(Value<TeaVMHost> host, Value<TeaVMJavaScriptHost> jsHost, ReflectClass<?> cls) {
      for (ReflectMethod method : cls.getDeclaredMethods()) {
         if (Modifier.isNative(method.getModifiers())) {
            GeneratedBy generatedBy = (GeneratedBy)method.getAnnotation(GeneratedBy.class);
            if (generatedBy != null) {
               ReflectClass<?> generatorClass = Metaprogramming.findClass(generatedBy.value().getName());
               ReflectMethod generatorConstructor = generatorClass.getMethod("<init>", new ReflectClass[0]);
               Value<MethodReference> methodRef = methodToReference(method);
               Metaprogramming.emit(
                  () -> ((TeaVMJavaScriptHost)jsHost.get()).add((MethodReference)methodRef.get(), (Generator)generatorConstructor.construct(new Object[0]))
               );
            }

            InjectedBy injectedBy = (InjectedBy)method.getAnnotation(InjectedBy.class);
            if (injectedBy != null) {
               ReflectClass<?> generatorClass = Metaprogramming.findClass(injectedBy.value().getName());
               ReflectMethod generatorConstructor = generatorClass.getMethod("<init>", new ReflectClass[0]);
               Value<MethodReference> methodRef = methodToReference(method);
               Metaprogramming.emit(
                  () -> ((TeaVMJavaScriptHost)jsHost.get()).add((MethodReference)methodRef.get(), (Injector)generatorConstructor.construct(new Object[0]))
               );
            }

            PluggableDependency dependency = (PluggableDependency)method.getAnnotation(PluggableDependency.class);
            if (dependency != null) {
               ReflectClass<?> generatorClass = Metaprogramming.findClass(dependency.value().getName());
               ReflectMethod generatorConstructor = generatorClass.getMethod("<init>", new ReflectClass[0]);
               Value<MethodReference> methodRef = methodToReference(method);
               Metaprogramming.emit(
                  () -> ((TeaVMHost)host.get()).add((MethodReference)methodRef.get(), (DependencyPlugin)generatorConstructor.construct(new Object[0]))
               );
            }
         }
      }
   }

   private static Value<MethodReference> methodToReference(ReflectMethod method) {
      int signatureSize = method.getParameterCount() + 1;
      Value<ValueType[]> signature = Metaprogramming.emit(() -> new ValueType[signatureSize]);

      for (int i = 0; i < method.getParameterCount(); i++) {
         Value<ValueType> paramType = classToValueType(method.getParameterType(i));
         int index = i;
         Metaprogramming.emit(() -> ((ValueType[])signature.get())[index] = (ValueType)paramType.get());
      }

      Value<ValueType> returnType = classToValueType(method.getReturnType());
      Metaprogramming.emit(() -> ((ValueType[])signature.get())[signatureSize - 1] = (ValueType)returnType.get());
      String className = method.getDeclaringClass().getName();
      String name = method.getName();
      return Metaprogramming.emit(() -> new MethodReference(className, new MethodDescriptor(name, (ValueType[])signature.get())));
   }

   private static Value<ValueType> classToValueType(ReflectClass<?> cls) {
      if (cls.isArray()) {
         Value<ValueType> itemType = classToValueType(cls.getComponentType());
         return Metaprogramming.emit(() -> ValueType.arrayOf((ValueType)itemType.get()));
      }

      if (cls.isPrimitive()) {
         switch (cls.getName()) {
            case "boolean":
               return Metaprogramming.emit(() -> ValueType.BOOLEAN);
            case "byte":
               return Metaprogramming.emit(() -> ValueType.BYTE);
            case "short":
               return Metaprogramming.emit(() -> ValueType.SHORT);
            case "char":
               return Metaprogramming.emit(() -> ValueType.CHARACTER);
            case "int":
               return Metaprogramming.emit(() -> ValueType.INTEGER);
            case "long":
               return Metaprogramming.emit(() -> ValueType.LONG);
            case "float":
               return Metaprogramming.emit(() -> ValueType.FLOAT);
            case "double":
               return Metaprogramming.emit(() -> ValueType.DOUBLE);
            case "void":
               return Metaprogramming.emit(() -> ValueType.VOID);
            default:
               throw new IllegalArgumentException("Unexpected primitive type: " + cls.getName());
         }
      } else {
         String name = cls.getName();
         return Metaprogramming.emit(() -> ValueType.object(name));
      }
   }
}
