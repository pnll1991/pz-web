package org.teavm.backend.javascript.rendering;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;
import org.teavm.backend.javascript.codegen.NamingStrategy;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.spi.InjectedBy;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.common.ServiceRepository;
import org.teavm.debugging.information.DebugInformationEmitter;
import org.teavm.dependency.DependencyInfo;
import org.teavm.interop.PlatformMarker;
import org.teavm.model.AnnotationReader;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.parsing.resource.ResourceProvider;

public abstract class RenderingContext {
   private final DebugInformationEmitter debugEmitter;
   private ClassReaderSource initialClassSource;
   private ListableClassReaderSource classSource;
   private ResourceProvider resourceProvider;
   private ClassLoader classLoader;
   private ServiceRepository services;
   private Properties properties;
   private NamingStrategy naming;
   private DependencyInfo dependencyInfo;
   private Predicate<MethodReference> virtualPredicate;
   private Predicate<MethodReference> forcedFunctionPredicate;
   private final Map<String, Integer> stringPoolMap = new HashMap<>();
   private final List<String> stringPool = new ArrayList<>();
   private final List<String> readonlyStringPool = Collections.unmodifiableList(this.stringPool);
   private final Map<MethodReference, RenderingContext.InjectorHolder> injectorMap = new HashMap<>();
   private boolean minifying;
   private ClassInitializerInfo classInitializerInfo;
   private boolean strict;

   public RenderingContext(
      DebugInformationEmitter debugEmitter,
      ClassReaderSource initialClassSource,
      ListableClassReaderSource classSource,
      ResourceProvider resourceProvider,
      ClassLoader classLoader,
      ServiceRepository services,
      Properties properties,
      NamingStrategy naming,
      DependencyInfo dependencyInfo,
      Predicate<MethodReference> virtualPredicate,
      Predicate<MethodReference> forcedFunctionPredicate,
      ClassInitializerInfo classInitializerInfo,
      boolean strict
   ) {
      this.debugEmitter = debugEmitter;
      this.initialClassSource = initialClassSource;
      this.classSource = classSource;
      this.resourceProvider = resourceProvider;
      this.classLoader = classLoader;
      this.services = services;
      this.properties = properties;
      this.naming = naming;
      this.dependencyInfo = dependencyInfo;
      this.virtualPredicate = virtualPredicate;
      this.forcedFunctionPredicate = forcedFunctionPredicate;
      this.classInitializerInfo = classInitializerInfo;
      this.strict = strict;
   }

   public ClassReaderSource getInitialClassSource() {
      return this.initialClassSource;
   }

   public ListableClassReaderSource getClassSource() {
      return this.classSource;
   }

   public ResourceProvider getResourceProvider() {
      return this.resourceProvider;
   }

   public ClassLoader getClassLoader() {
      return this.classLoader;
   }

   public ServiceRepository getServices() {
      return this.services;
   }

   public Properties getProperties() {
      return this.properties;
   }

   public NamingStrategy getNaming() {
      return this.naming;
   }

   public DependencyInfo getDependencyInfo() {
      return this.dependencyInfo;
   }

   public void setMinifying(boolean minifying) {
      this.minifying = minifying;
   }

   public DebugInformationEmitter getDebugEmitter() {
      return this.debugEmitter;
   }

   public boolean isVirtual(MethodReference method) {
      return this.virtualPredicate.test(method);
   }

   public boolean isForcedFunction(MethodReference method) {
      return this.forcedFunctionPredicate.test(method);
   }

   public boolean isDynamicInitializer(String className) {
      return this.classInitializerInfo.isDynamicInitializer(className);
   }

   public boolean isMinifying() {
      return this.minifying;
   }

   public int lookupString(String string) {
      return this.stringPoolMap.computeIfAbsent(string, key -> {
         this.stringPool.add(key);
         return this.stringPool.size() - 1;
      });
   }

   public List<String> getStringPool() {
      return this.readonlyStringPool;
   }

   public void constantToString(SourceWriter writer, Object cst) {
      if (cst == null) {
         writer.append("null");
      }

      if (cst instanceof ValueType type) {
         writer.appendFunction("$rt_cls").append("(");
         RenderingUtil.typeToClsString(writer, type);
         writer.append(")");
      } else if (cst instanceof String string) {
         int index = this.lookupString(string);
         writer.appendFunction("$rt_s").append("(" + index + ")");
      } else if (cst instanceof Long) {
         long value = (Long)cst;
         RenderingUtil.appendLongConstant(writer, value);
      } else if (cst instanceof Character) {
         writer.append(Integer.toString((Character)cst));
      } else if (cst instanceof Boolean) {
         writer.append((Boolean)cst ? "1" : "0");
      } else if (cst instanceof Integer) {
         int value = (Integer)cst;
         if (value < 0) {
            writer.append("(");
            writer.append(Integer.toString(value));
            writer.append(")");
         } else {
            writer.append(Integer.toString(value));
         }
      } else if (cst instanceof Byte) {
         int value = (Byte)cst;
         if (value < 0) {
            writer.append("(");
            writer.append(Integer.toString(value));
            writer.append(")");
         } else {
            writer.append(Integer.toString(value));
         }
      } else if (cst instanceof Short) {
         int value = (Short)cst;
         if (value < 0) {
            writer.append("(");
            writer.append(Integer.toString(value));
            writer.append(")");
         } else {
            writer.append(Integer.toString(value));
         }
      } else if (cst instanceof Double) {
         double value = (Double)cst;
         if (value < 0.0) {
            writer.append("(");
            writer.append(Double.toString(value));
            writer.append(")");
         } else {
            writer.append(Double.toString(value));
         }
      } else if (cst instanceof Float) {
         float value = (Float)cst;
         if (value < 0.0F) {
            writer.append("(");
            writer.append(Double.toString(value));
            writer.append(")");
         } else {
            writer.append(Double.toString(value));
         }
      }
   }

   public String pointerName() {
      return this.minifying ? "$p" : "$ptr";
   }

   public String mainLoopName() {
      return this.minifying ? "_" : "main";
   }

   public String tempVarName() {
      return this.minifying ? "$z" : "$tmp";
   }

   public String threadName() {
      return this.minifying ? "$T" : "$thread";
   }

   public void addInjector(MethodReference method, Injector injector) {
      this.injectorMap.put(method, new RenderingContext.InjectorHolder(injector));
   }

   public Injector getInjector(MethodReference ref) {
      RenderingContext.InjectorHolder holder = this.injectorMap.get(ref);
      if (holder == null) {
         holder = new RenderingContext.InjectorHolder(null);
         if (!isBootstrap()) {
            ClassReader cls = this.classSource.get(ref.getClassName());
            if (cls == null) {
               cls = this.initialClassSource.get(ref.getClassName());
            }

            if (cls != null) {
               MethodReader method = cls.getMethod(ref.getDescriptor());
               if (method != null) {
                  AnnotationReader injectedByAnnot = method.getAnnotations().get(InjectedBy.class.getName());
                  if (injectedByAnnot != null) {
                     ValueType type = injectedByAnnot.getValue("value").getJavaClass();
                     holder = new RenderingContext.InjectorHolder(this.instantiateInjector(((ValueType.Object)type).getClassName()));
                  }
               }
            }
         }

         this.injectorMap.put(ref, holder);
      }

      return holder.injector;
   }

   public boolean isStrict() {
      return this.strict;
   }

   public abstract String importModule(String var1);

   @PlatformMarker
   private static boolean isBootstrap() {
      return false;
   }

   private Injector instantiateInjector(String type) {
      try {
         Class<? extends Injector> cls = Class.forName(type, true, this.classLoader).asSubclass(Injector.class);
         Constructor<? extends Injector> cons = cls.getConstructor();
         return cons.newInstance();
      } catch (ClassNotFoundException e) {
         throw new RuntimeException("Illegal injector: " + type, e);
      } catch (NoSuchMethodException e) {
         throw new RuntimeException("Default constructor was not found in the " + type + " injector", e);
      } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
         throw new RuntimeException("Error instantiating injector " + type, e);
      }
   }

   private static class InjectorHolder {
      public final Injector injector;

      private InjectorHolder(Injector injector) {
         this.injector = injector;
      }
   }
}
