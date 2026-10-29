package org.teavm.backend.javascript.intrinsics.reflection;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.teavm.backend.javascript.ProviderContext;
import org.teavm.backend.javascript.rendering.Precedence;
import org.teavm.backend.javascript.spi.Generator;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.dependency.DependencyInfo;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.reflection.ReflectionDependencyListener;
import org.teavm.runtime.StringInfo;
import org.teavm.runtime.reflect.AnnotationConstructor;
import org.teavm.runtime.reflect.AnnotationData;
import org.teavm.runtime.reflect.AnnotationInfo;
import org.teavm.runtime.reflect.AnnotationValueArray;
import org.teavm.runtime.reflect.ClassInfo;
import org.teavm.runtime.reflect.ClassReflectionInfo;
import org.teavm.runtime.reflect.DerivedClassInfo;
import org.teavm.runtime.reflect.FieldInfo;
import org.teavm.runtime.reflect.FieldReflectionInfo;
import org.teavm.runtime.reflect.GenericArrayInfo;
import org.teavm.runtime.reflect.GenericTypeInfo;
import org.teavm.runtime.reflect.MethodInfo;
import org.teavm.runtime.reflect.MethodReflectionInfo;
import org.teavm.runtime.reflect.ParameterInfo;
import org.teavm.runtime.reflect.ParameterizedTypeInfo;
import org.teavm.runtime.reflect.RawTypeInfo;
import org.teavm.runtime.reflect.TypeVariableInfo;
import org.teavm.runtime.reflect.TypeVariableReference;
import org.teavm.runtime.reflect.WildcardTypeInfo;

public class ReflectionIntrinsics {
   private Map<MethodReference, Injector> injectors;
   private Map<MethodReference, Generator> generators;
   private List<Function<ProviderContext, Injector>> injectorProviders;
   private ListableClassReaderSource classes;
   private ReflectionDependencyListener reflection;
   private DependencyInfo dependencies;

   public ReflectionIntrinsics(
      Map<MethodReference, Injector> injectors,
      Map<MethodReference, Generator> generators,
      List<Function<ProviderContext, Injector>> injectorProviders,
      ListableClassReaderSource classes,
      ReflectionDependencyListener reflection,
      DependencyInfo dependencies
   ) {
      this.injectors = injectors;
      this.generators = generators;
      this.injectorProviders = injectorProviders;
      this.classes = classes;
      this.reflection = reflection;
      this.dependencies = dependencies;
   }

   public void apply() {
      ClassInfoGenerator classGen = new ClassInfoGenerator();
      this.applyIntrinsics(ClassInfo.class, classGen, "newArrayInstance");
      this.generators.put(new MethodReference(ClassInfo.class, "newArrayInstance", int.class, Object.class), classGen);
      this.applyIntrinsics(ClassReflectionInfo.class, new ClassReflectionInfoGenerator(this.reflection, this.dependencies));
      this.applyIntrinsics(StringInfo.class, new StringInfoGenerator());
      this.applyIntrinsics(AnnotationInfo.class, new AnnotationInfoGenerator());
      this.applyIntrinsics(AnnotationConstructor.class, new AnnotationConstructorGenerator());
      this.applyIntrinsics(AnnotationValueArray.class, new AnnotationValueArrayGenerator());
      this.applyIntrinsics(DerivedClassInfo.class, new DerivedClassInfoGenerator());
      this.applyIntrinsics(FieldInfo.class, new FieldInfoGenerator());
      this.applyIntrinsics(FieldReflectionInfo.class, new FieldReflectionInfoGenerator());
      this.applyIntrinsics(MethodInfo.class, new MethodInfoGenerator());
      this.applyIntrinsics(MethodReflectionInfo.class, new MethodReflectionInfoGenerator());
      this.applyIntrinsics(ParameterInfo.class, new ParameterInfoGenerator());
      this.applyIntrinsics(TypeVariableInfo.class, new TypeVariableInfoGenerator());
      GenericTypeInfoGenerator genericTypeGen = new GenericTypeInfoGenerator();
      this.applyIntrinsics(GenericTypeInfo.class, genericTypeGen);
      this.applyIntrinsics(ParameterizedTypeInfo.class, genericTypeGen);
      this.applyIntrinsics(TypeVariableReference.class, genericTypeGen);
      this.applyIntrinsics(GenericArrayInfo.class, genericTypeGen);
      this.applyIntrinsics(WildcardTypeInfo.class, genericTypeGen);
      this.applyIntrinsics(RawTypeInfo.class, genericTypeGen);
      this.generators.put(new MethodReference(Object.class, "getObjectClassInfo", Object.class, ClassInfo.class), new ObjectGenerator());
      ProxyGeneratorContext proxyContext = new ProxyGeneratorContext();
      ProxyGenerator proxyGen = new ProxyGenerator(this.reflection, proxyContext);
      this.injectors.put(new MethodReference(Proxy.class, "wrapDependency", Object.class, Object.class), proxyGen);
      this.injectors.put(new MethodReference(Proxy.class, "registerProxyClasses", void.class), proxyGen);
      this.injectorProviders.add(new ProxyMethodInjectorProvider(this.reflection, proxyContext));
      this.applyToAnnotationData();
   }

   private void applyToAnnotationData() {
      for (String className : this.classes.getClassNames()) {
         ClassReader cls = this.classes.get(className);
         if (Objects.equals(cls.getParent(), AnnotationData.class.getName())) {
            this.applyToAnnotationData(cls);
         }
      }
   }

   private void applyToAnnotationData(ClassReader cls) {
      int index = 0;

      for (MethodReader method : cls.getMethods()) {
         if (!method.getName().equals("<init>")) {
            if (method.getName().equals("constructor") && method.getResultType().isObject(AnnotationConstructor.class)) {
               this.injectors.put(method.getReference(), (ctx, m) -> {
                  String annotItfName = cls.getName().substring(0, cls.getName().length() - "$$_nativeData".length());
                  String annotImplName = annotItfName + "$$_impl";
                  MethodReference factory = new MethodReference(annotImplName, "create", ValueType.object(cls.getName()), ValueType.object(annotImplName));
                  ctx.getWriter().appendMethod(factory);
               });
            } else {
               int currentIndex = index;
               this.injectors.put(method.getReference(), (ctx, m) -> {
                  ctx.writeExpr(ctx.getArgument(0), Precedence.MEMBER_ACCESS);
                  ctx.getWriter().append("[").append(currentIndex).append("]");
               });
               index++;
            }
         }
      }
   }

   private void applyIntrinsics(Class<?> cls, Injector generator, String... skip) {
      ClassReader clsReader = this.classes.get(cls.getName());
      if (clsReader != null) {
         Set<String> skipSet = Set.of(skip);

         for (MethodReader method : clsReader.getMethods()) {
            if (method.hasModifier(ElementModifier.NATIVE) && !skipSet.contains(method.getName())) {
               this.injectors.put(method.getReference(), generator);
            }
         }
      }
   }
}
