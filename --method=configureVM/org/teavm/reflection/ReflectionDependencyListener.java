package org.teavm.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.classlib.ProxyListener;
import org.teavm.classlib.ReflectionContext;
import org.teavm.classlib.ReflectionSupplier;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyConsumer;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.DependencyType;
import org.teavm.dependency.FieldDependency;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.GenericTypeParameter;
import org.teavm.model.GenericValueType;
import org.teavm.model.MemberReader;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.runtime.StringInfo;
import org.teavm.runtime.reflect.ClassInfo;
import org.teavm.runtime.reflect.MethodInfo;

public class ReflectionDependencyListener extends AbstractDependencyListener {
   private List<ReflectionSupplier> reflectionSuppliers;
   private MethodReference fieldGet = new MethodReference(Field.class, "getWithoutCheck", Object.class, Object.class);
   private MethodReference fieldGetBoolean = new MethodReference(Field.class, "getBoolean", Object.class, boolean.class);
   private MethodReference fieldGetByte = new MethodReference(Field.class, "getByte", Object.class, byte.class);
   private MethodReference fieldGetShort = new MethodReference(Field.class, "getShort", Object.class, short.class);
   private MethodReference fieldGetChar = new MethodReference(Field.class, "getChar", Object.class, char.class);
   private MethodReference fieldGetInt = new MethodReference(Field.class, "getInt", Object.class, int.class);
   private MethodReference fieldGetLong = new MethodReference(Field.class, "getLong", Object.class, long.class);
   private MethodReference fieldGetFloat = new MethodReference(Field.class, "getFloat", Object.class, float.class);
   private MethodReference fieldGetDouble = new MethodReference(Field.class, "getDouble", Object.class, double.class);
   private MethodReference fieldSet = new MethodReference(Field.class, "setWithoutCheck", Object.class, Object.class, void.class);
   private MethodReference fieldSetBoolean = new MethodReference(Field.class, "setBoolean", Object.class, boolean.class, void.class);
   private MethodReference fieldSetByte = new MethodReference(Field.class, "setByte", Object.class, byte.class, void.class);
   private MethodReference fieldSetShort = new MethodReference(Field.class, "setShort", Object.class, short.class, void.class);
   private MethodReference fieldSetChar = new MethodReference(Field.class, "setChar", Object.class, char.class, void.class);
   private MethodReference fieldSetInt = new MethodReference(Field.class, "setInt", Object.class, int.class, void.class);
   private MethodReference fieldSetLong = new MethodReference(Field.class, "setLong", Object.class, long.class, void.class);
   private MethodReference fieldSetFloat = new MethodReference(Field.class, "setFloat", Object.class, float.class, void.class);
   private MethodReference fieldSetDouble = new MethodReference(Field.class, "setDouble", Object.class, double.class, void.class);
   private MethodReference newInstance = new MethodReference(Constructor.class, "newInstance", Object[].class, Object.class);
   private MethodReference invokeMethod = new MethodReference(Method.class, "invoke", Object.class, Object[].class, Object.class);
   private MethodReference getFields = new MethodReference(Class.class, "getDeclaredFields", Field[].class);
   private MethodReference getConstructors = new MethodReference(Class.class, "getDeclaredConstructors", Constructor[].class);
   private MethodReference getMethods = new MethodReference(Class.class, "getDeclaredMethods", Method[].class);
   private MethodReference getFieldAnnotations = new MethodReference(Field.class, "getDeclaredAnnotations", Annotation[].class);
   private MethodReference getExecutableAnnotations = new MethodReference(Executable.class, "getDeclaredAnnotations", Annotation[].class);
   private MethodReference getClassAnnotations = new MethodReference(Class.class, "getDeclaredAnnotations", Annotation[].class);
   private MethodReference forName = new MethodReference(Class.class, "forName", String.class, Boolean.class, ClassLoader.class, Class.class);
   private MethodReference classNewInstance = new MethodReference(Class.class, "newInstance", Object.class);
   private MethodReference forNameShort = new MethodReference(Class.class, "forName", String.class, Class.class);
   private MethodReference fieldGetType = new MethodReference(Field.class, "getType", Class.class);
   private MethodReference fieldGetName = new MethodReference(Field.class, "getName", String.class);
   private MethodReference proxyMethod = new MethodReference(
      Proxy.class, "newProxyInstance", ClassLoader.class, Class[].class, InvocationHandler.class, Object.class
   );
   private Map<String, Set<String>> accessibleFieldCache = new LinkedHashMap<>();
   private Map<String, Set<MethodDescriptor>> accessibleMethodCache = new LinkedHashMap<>();
   private Set<String> classesWithReflectableFields = new LinkedHashSet<>();
   private Set<String> classesWithReflectableMethods = new LinkedHashSet<>();
   private DependencyAgent agent;
   private DependencyNode allClasses;
   private DependencyNode typesInReflectableSignaturesNode;
   private DependencyNode typesInThrownNode;
   private DependencyNode typesInGenericReflectableSignaturesNode;
   private Set<MethodReference> virtualMethods = new HashSet<>();
   private Set<MethodReference> virtualCallSites = new HashSet<>();
   private Set<String> classesFoundByName = new HashSet<>();
   private Set<MethodReference> calledMethods = new HashSet<>();
   private Set<FieldReference> readFields = new HashSet<>();
   private Set<FieldReference> writtenFields = new HashSet<>();
   private List<FieldReader> fieldsReadViaReflection = new ArrayList<>();
   private List<MethodReader> methodsReadViaReflection = new ArrayList<>();
   private Map<List<? extends String>, String> proxyClasses = new LinkedHashMap<>();
   private Map<? extends List<? extends String>, ? extends String> proxyClassesReadonly = Collections.unmodifiableMap(this.proxyClasses);
   private DependencyNode fieldsAnnotationsConsumer;
   private DependencyNode methodsAnnotationsConsumer;
   private DependencyNode parameterAnnotationsConsumer;
   private ReflectionDependencyListener.ReflectionContextImpl reflectionContext;
   private ProxyDependencySupport proxySupport;
   private Set<String> usedProxyNames = new HashSet<>();
   private boolean getReached;
   private boolean setReached;
   private boolean callReached;
   private boolean proxyReached;
   private AnnotationGenerationHelper annotHelper;
   private boolean withGenerics;

   public ReflectionDependencyListener(List<ReflectionSupplier> reflectionSuppliers, AnnotationGenerationHelper annotHelper) {
      this.reflectionSuppliers = new ArrayList<>(reflectionSuppliers);
      this.annotHelper = annotHelper;
   }

   public boolean isVirtual(MethodReference methodRef) {
      return this.virtualMethods.contains(methodRef);
   }

   public Collection<MethodReference> getVirtualCallSites() {
      return this.virtualCallSites;
   }

   public boolean isCalled(MethodReference methodRef) {
      return this.calledMethods.contains(methodRef);
   }

   public boolean isRead(FieldReference fieldRef) {
      return this.readFields.contains(fieldRef);
   }

   public boolean isWritten(FieldReference fieldRef) {
      return this.writtenFields.contains(fieldRef);
   }

   public Map<? extends List<? extends String>, ? extends String> getProxyClasses() {
      return this.proxyClassesReadonly;
   }

   @Override
   public void started(DependencyAgent agent) {
      this.allClasses = agent.createNode();
      this.typesInReflectableSignaturesNode = agent.createNode();
      this.typesInThrownNode = agent.createNode();
      this.typesInGenericReflectableSignaturesNode = agent.createNode();
      this.reflectionSuppliers.add(new DefaultReflectionSupplier(agent.extensionEnvironment()));
      agent.linkMethod(new MethodReference(ClassInfo.class, "classObject", Class.class)).getResult().propagate(agent.getType(ValueType.parse(Class.class)));
      agent.linkMethod(new MethodReference(StringInfo.class, "getStringObject", String.class))
         .getResult()
         .propagate(agent.getType(ValueType.parse(String.class)));
      agent.linkMethod(new MethodReference(Class.class, "createClass", ClassInfo.class, Class.class)).use();
      this.reflectionContext = new ReflectionDependencyListener.ReflectionContextImpl(agent);
      this.agent = agent;
      this.allClasses
         .addConsumer(
            type -> {
               if (type.getValueType() instanceof ValueType.Object) {
                  String classNamex = ((ValueType.Object)type.getValueType()).getClassName();
                  if (this.reflectionSuppliers.stream().anyMatch(s -> s.isClassFoundByName(this.reflectionContext, classNamex))
                     && this.classesFoundByName.add(classNamex)) {
                     agent.linkClass(classNamex);
                  }
               }
            }
         );
      if (!this.classesFoundByName.isEmpty()) {
         MethodDependency getName = agent.linkMethod(new MethodReference(Class.class, "getName", String.class));
         getName.getVariable(0).propagate(agent.getType(ValueType.object("java.lang.Class")));

         for (String className : this.classesFoundByName) {
            getName.getVariable(0).getClassValueNode().propagate(agent.getType(ValueType.object(className)));
         }
      }
   }

   public boolean isGetReached() {
      return this.getReached;
   }

   public boolean isSetReached() {
      return this.setReached;
   }

   public boolean isCallReached() {
      return this.callReached;
   }

   public boolean isProxyReached() {
      return this.proxyReached;
   }

   public boolean isGeneratedProxyClass(String className) {
      return this.proxySupport != null && this.proxySupport.isGeneratedClass(className);
   }

   public Collection<? extends MethodReference> getProxyReflectionMethods() {
      return this.proxySupport != null ? this.proxySupport.getUsedMethods() : List.of();
   }

   public MethodReference getProxyWorkerAcquireMethod(MethodReference method) {
      return this.proxySupport.getWorkerAcquireMethod(method);
   }

   public Set<String> getClassesWithReflectableFields() {
      return this.classesWithReflectableFields;
   }

   public Set<String> getClassesWithReflectableMethods() {
      return this.classesWithReflectableMethods;
   }

   public Set<String> getAccessibleFields(String className) {
      return this.accessibleFieldCache.get(className);
   }

   public Set<MethodDescriptor> getAccessibleMethods(String className) {
      Set<MethodDescriptor> result = this.accessibleMethodCache.get(className);
      return result != null ? result : Collections.emptySet();
   }

   @Override
   public void classReached(DependencyAgent agent, String className) {
      this.allClasses.propagate(agent.getType(ValueType.object(className)));
   }

   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
      if (method.getReference().equals(this.fieldGet)) {
         this.handleFieldGet(agent, method, false);
      } else if (method.getReference().equals(this.fieldGetBoolean)
         || method.getReference().equals(this.fieldGetByte)
         || method.getReference().equals(this.fieldGetShort)
         || method.getReference().equals(this.fieldGetChar)
         || method.getReference().equals(this.fieldGetInt)
         || method.getReference().equals(this.fieldGetLong)
         || method.getReference().equals(this.fieldGetFloat)
         || method.getReference().equals(this.fieldGetDouble)) {
         this.handleFieldGet(agent, method, true);
      } else if (method.getReference().equals(this.fieldSet)) {
         this.handleFieldSet(agent, method, false);
      } else if (method.getReference().equals(this.fieldSetBoolean)
         || method.getReference().equals(this.fieldSetByte)
         || method.getReference().equals(this.fieldSetShort)
         || method.getReference().equals(this.fieldSetChar)
         || method.getReference().equals(this.fieldSetInt)
         || method.getReference().equals(this.fieldSetLong)
         || method.getReference().equals(this.fieldSetFloat)
         || method.getReference().equals(this.fieldSetDouble)) {
         this.handleFieldSet(agent, method, true);
      } else if (method.getReference().equals(this.newInstance)) {
         this.handleNewInstance(agent, method);
      } else if (method.getReference().equals(this.classNewInstance)) {
         this.handleClassNewInstance(agent, method);
      } else if (method.getReference().equals(this.invokeMethod)) {
         this.handleInvoke(agent, method);
      } else if (method.getReference().equals(this.getFields)) {
         method.getVariable(0)
            .getClassValueNode()
            .addConsumer(
               type -> {
                  if (type.getValueType() instanceof ValueType.Object) {
                     String classNamex = ((ValueType.Object)type.getValueType()).getClassName();
                     this.classesWithReflectableFields.add(classNamex);
                     ClassReader cls = agent.getClassSource().get(classNamex);
                     if (cls != null) {
                        boolean skipPrivates = shouldSkipPrivates(cls);
                        Set<String> reflectable = this.getAccessibleFields(agent, classNamex);

                        for (FieldReader field : cls.getFields()) {
                           if (reflectable.contains(field.getName())
                              && (!skipPrivates || field.getLevel() != AccessLevel.PRIVATE && field.getLevel() != AccessLevel.PACKAGE_PRIVATE)) {
                              this.linkType(agent, field.getType());
                              agent.linkField(field.getReference());
                              if (field.getGenericType() != null) {
                                 this.linkGenericType(agent, field.getGenericType());
                              }

                              this.fieldsReadViaReflection.add(field);
                              if (this.fieldsAnnotationsConsumer != null) {
                                 this.annotHelper.propagateAnnotationImplementations(agent, field.getAnnotations().all(), this.fieldsAnnotationsConsumer);
                              }
                           }
                        }
                     }
                  }
               }
            );
      } else if (method.getReference().equals(this.getFieldAnnotations)) {
         this.fieldsAnnotationsConsumer = method.getResult().getArrayItem();
         method.getResult().propagate(agent.getType(ValueType.parse(Annotation[].class)));

         for (FieldReader field : this.fieldsReadViaReflection) {
            this.annotHelper.propagateAnnotationImplementations(agent, field.getAnnotations().all(), this.fieldsAnnotationsConsumer);
         }
      } else if (method.getReference().equals(this.getConstructors) || method.getReference().equals(this.getMethods)) {
         method.getVariable(0)
            .getClassValueNode()
            .addConsumer(
               type -> {
                  if (type.getValueType() instanceof ValueType.Object) {
                     String classNamex = ((ValueType.Object)type.getValueType()).getClassName();
                     this.classesWithReflectableMethods.add(classNamex);
                     ClassReader cls = agent.getClassSource().get(classNamex);
                     if (cls != null) {
                        boolean skipPrivates = shouldSkipPrivates(cls);
                        Set<MethodDescriptor> reflectable = this.collectAccessibleMethods(classNamex);

                        for (MethodReader reflectableMethod : cls.getMethods()) {
                           if (reflectable.contains(reflectableMethod.getDescriptor())
                              && (
                                 !skipPrivates
                                    || reflectableMethod.getLevel() != AccessLevel.PRIVATE && reflectableMethod.getLevel() != AccessLevel.PACKAGE_PRIVATE
                              )) {
                              this.addReflectableMethod(agent, reflectableMethod);
                           }
                        }
                     }
                  }
               }
            );
      } else if (method.getReference().equals(this.getExecutableAnnotations)) {
         this.methodsAnnotationsConsumer = method.getResult().getArrayItem();
         method.getResult().propagate(agent.getType(ValueType.parse(Annotation[].class)));

         for (MethodReader reflectableMethod : this.methodsReadViaReflection) {
            this.annotHelper.propagateAnnotationImplementations(agent, reflectableMethod.getAnnotations().all(), this.methodsAnnotationsConsumer);
         }
      } else if (method.getReference().equals(ReflectionMethods.EXECUTABLE_GET_PARAMETER_ANNOTATIONS)) {
         this.parameterAnnotationsConsumer = method.getResult().getArrayItem().getArrayItem();
         method.getResult().propagate(agent.getType(ValueType.parse(Annotation[][].class)));
         method.getResult().getArrayItem().propagate(agent.getType(ValueType.parse(Annotation[].class)));

         for (MethodReader reflectableMethod : this.methodsReadViaReflection) {
            this.propagateParamAnnotationImplementations(agent, reflectableMethod);
         }
      } else if (method.getReference().equals(this.getClassAnnotations)) {
         method.getVariable(0).getClassValueNode().addConsumer(type -> {
            if (type.getValueType() instanceof ValueType.Object) {
               ClassReader cls = agent.getClassSource().get(((ValueType.Object)type.getValueType()).getClassName());
               if (cls != null) {
                  this.annotHelper.propagateAnnotationImplementations(agent, cls.getAnnotations().all(), method.getResult().getArrayItem());
               }
            }
         });
      } else if (method.getReference().equals(this.forName) || method.getReference().equals(this.forNameShort)) {
         method.getResult().propagate(agent.getType(ValueType.object("java.lang.Class")));

         for (String className : this.classesFoundByName) {
            method.getResult().getClassValueNode().propagate(agent.getType(ValueType.object(className)));
         }
      } else if (method.getReference().equals(this.fieldGetType) || method.getReference().equals(ReflectionMethods.METHOD_GET_RETURN_TYPE)) {
         method.getResult().propagate(agent.getType(ValueType.object("java.lang.Class")));
         this.typesInReflectableSignaturesNode.connect(method.getResult().getClassValueNode());
      } else if (method.getReference().equals(ReflectionMethods.FIELD_GET_GENERIC_TYPE)
         || method.getReference().equals(ReflectionMethods.METHOD_GET_GENERIC_RETURN_TYPE)) {
         this.linkGenerics(agent);
         this.propagateGenerics(agent, method.getResult());
      } else if (method.getReference().equals(this.fieldGetName)) {
         method.getResult().propagate(agent.getType(ValueType.object("java.lang.String")));
      } else if (method.getReference().equals(ReflectionMethods.EXECUTABLE_GET_PARAMETER_TYPES)) {
         method.getResult().propagate(agent.getType(ValueType.arrayOf(ValueType.object("java.lang.Class"))));
         method.getResult().getArrayItem().propagate(agent.getType(ValueType.object("java.lang.Class")));
         this.typesInReflectableSignaturesNode.connect(method.getResult().getArrayItem().getClassValueNode());
      } else if (method.getReference().equals(ReflectionMethods.EXECUTABLE_GET_EXCEPTION_TYPES)) {
         method.getResult().propagate(agent.getType(ValueType.arrayOf(ValueType.object("java.lang.Class"))));
         method.getResult().getArrayItem().propagate(agent.getType(ValueType.object("java.lang.Class")));
         this.typesInThrownNode.connect(method.getResult().getArrayItem().getClassValueNode());
      } else if (method.getReference().equals(ReflectionMethods.EXECUTABLE_PARAMETER_TYPE)) {
         method.getResult().propagate(agent.getType(ValueType.object("java.lang.Class")));
         this.typesInReflectableSignaturesNode.connect(method.getResult().getClassValueNode());
      } else if (method.getReference().equals(ReflectionMethods.EXECUTABLE_GET_GENERIC_PARAMETER_TYPES)) {
         this.linkGenerics(agent);
         this.propagateGenerics(agent, method.getResult().getArrayItem());
      } else if (method.getReference().equals(ReflectionMethods.CLASS_GET_TYPE_PARAMS)
         || method.getReference().equals(ReflectionMethods.EXECUTABLE_GET_TYPE_PARAMS)) {
         this.linkGenerics(agent);
         method.getResult().getArrayItem().propagate(agent.getType(ReflectionMethods.TYPE_VAR_IMPL_TYPE));
      } else if (method.getReference().equals(ReflectionMethods.TYPE_VAR_GET_BOUNDS)) {
         this.linkGenerics(agent);
         method.getResult().propagate(agent.getType(ValueType.parse(Type[].class)));
         this.propagateGenerics(agent, method.getResult().getArrayItem());
      } else if (method.getReference().equals(this.proxyMethod)) {
         this.installProxy();
      }

      if (this.proxySupport != null) {
         this.proxySupport.onMethodAdded(method);
      }
   }

   private void installProxy() {
      this.proxySupport = new ProxyDependencySupport(this, this.agent);
      this.proxyReached = true;

      for (ReflectionSupplier reflectionSupplier : this.reflectionSuppliers) {
         ProxyListener listener = reflectionSupplier.getProxyInterfaces(this.reflectionContext, this::registerProxy);
         if (listener != null) {
            for (ValueType type : this.allClasses.getTypes()) {
               if (type instanceof ValueType.Object) {
                  String className = ((ValueType.Object)type).getClassName();
                  listener.onClassAdded(className);
               }
            }

            this.allClasses.addConsumer(typex -> {
               if (typex.getValueType() instanceof ValueType.Object) {
                  String classNamex = ((ValueType.Object)typex.getValueType()).getClassName();
                  listener.onClassAdded(classNamex);
               }
            });
         }
      }

      this.agent.linkMethod(new MethodReference(Proxy.class, "appendInterface", ClassInfo.class, void.class)).use();
      this.agent.linkMethod(new MethodReference(Proxy.class, "registerClass", ClassInfo.class, void.class)).use();
   }

   private void registerProxy(List<? extends String> interfaces) {
      if (!this.proxyClasses.containsKey(interfaces)) {
         if (interfaces.isEmpty()) {
            throw new IllegalArgumentException("Proxy interfaces cannot be empty");
         }

         for (String className : interfaces) {
            ClassReader cls = this.agent.getClassSource().get(className);
            if (cls == null) {
               throw new IllegalArgumentException("Proxy interface '" + className + "' provided by user's ReflectionSupplier was not found");
            }

            if (!cls.hasModifier(ElementModifier.INTERFACE)) {
               throw new IllegalArgumentException("Proxy interface '" + className + "' provided by user's ReflectionSupplier is not an interface");
            }

            this.agent.linkClass(className);
         }

         String proxyClassName = interfaces.get(0) + "$_Proxy_";
         if (!this.usedProxyNames.add(proxyClassName)) {
            String prefix = proxyClassName;
            int i = 1;

            do {
               proxyClassName = prefix + i;
               i++;
            } while (!this.usedProxyNames.add(proxyClassName));
         }

         this.proxyClasses.put(interfaces, proxyClassName);
         this.proxySupport.generateProxyClass(interfaces, proxyClassName);
      }
   }

   void addAccessibleMethod(MethodReference method) {
      this.accessibleMethodCache.computeIfAbsent(method.getClassName(), k -> new HashSet<>()).add(method.getDescriptor());
   }

   void addReflectableMethod(DependencyAgent agent, MethodReader reflectableMethod) {
      if (this.methodsReadViaReflection.add(reflectableMethod)) {
         this.classesWithReflectableMethods.add(reflectableMethod.getOwnerName());
         this.linkType(agent, reflectableMethod.getResultType());

         for (ValueType param : reflectableMethod.getParameterTypes()) {
            this.linkType(agent, param);
         }

         if (reflectableMethod.getGenericResultType() != null) {
            this.linkGenericType(agent, reflectableMethod.getGenericResultType());
         }

         GenericValueType[] genericParamTypes = reflectableMethod.getGenericParameterTypes();
         if (genericParamTypes != null) {
            for (GenericValueType param : genericParamTypes) {
               this.linkGenericType(agent, param);
            }
         }

         for (String thrownType : reflectableMethod.getThrownTypes()) {
            this.typesInThrownNode.propagate(agent.getType(ValueType.object(thrownType)));
            agent.linkClass(thrownType);
         }

         if (this.methodsAnnotationsConsumer != null) {
            this.annotHelper.propagateAnnotationImplementations(agent, reflectableMethod.getAnnotations().all(), this.methodsAnnotationsConsumer);
         }

         if (this.parameterAnnotationsConsumer != null) {
            this.propagateParamAnnotationImplementations(agent, reflectableMethod);
         }
      }
   }

   private void propagateParamAnnotationImplementations(DependencyAgent agent, MethodReader method) {
      AnnotationContainerReader[] paramAnnotations = method.getParameterAnnotations();
      if (paramAnnotations != null) {
         for (AnnotationContainerReader paramAnnots : paramAnnotations) {
            this.annotHelper.propagateAnnotationImplementations(agent, paramAnnots.all(), this.parameterAnnotationsConsumer);
         }
      }
   }

   private void linkGenerics(DependencyAgent agent) {
      if (!this.withGenerics) {
         this.withGenerics = true;

         for (String className : agent.getReachableClasses()) {
            this.linkTypeParameterBounds(agent, className);
         }

         this.allClasses.addConsumer(type -> {
            ValueType valueType = type.getValueType();
            if (valueType instanceof ValueType.Object) {
               this.linkTypeParameterBounds(agent, ((ValueType.Object)valueType).getClassName());
            }
         });
         this.typesInGenericReflectableSignaturesNode.addConsumer(refType -> this.linkClass(agent, refType.getValueType()));
      }
   }

   private void linkTypeParameterBounds(DependencyAgent agent, String className) {
      ClassReader cls = agent.getClassSource().get(className);
      if (cls != null) {
         GenericTypeParameter[] params = cls.getGenericParameters();
         if (params != null) {
            for (GenericTypeParameter param : params) {
               if (param.getClassBound() != null) {
                  this.linkGenericType(agent, param.getClassBound());
               }

               for (GenericValueType.Reference bound : param.getInterfaceBounds()) {
                  this.linkGenericType(agent, bound);
               }
            }
         }
      }
   }

   private void propagateGenerics(DependencyAgent agent, DependencyNode target) {
      target.propagate(agent.getType(ValueType.object("java.lang.Class")));
      target.propagate(agent.getType(ReflectionMethods.TYPE_VAR_IMPL_TYPE));
      target.propagate(agent.getType(ValueType.object("java.lang.reflect.ParameterizedTypeImpl")));
      target.propagate(agent.getType(ValueType.object("java.lang.reflect.WildcardTypeImpl")));
      target.propagate(agent.getType(ValueType.object("java.lang.reflect.GenericArrayTypeImpl")));
      this.typesInReflectableSignaturesNode.connect(target.getClassValueNode());
      this.typesInGenericReflectableSignaturesNode.connect(target.getClassValueNode());
   }

   public static boolean shouldSkipPrivates(ClassReader cls) {
      return cls.getName().equals("java.lang.Object") || cls.getName().equals("java.lang.Class");
   }

   private void handleFieldGet(DependencyAgent agent, MethodDependency method, boolean primitive) {
      CallLocation location = new CallLocation(method.getReference());
      DependencyNode classValueNode = agent.linkMethod(this.getFields).addLocation(location).getVariable(0).getClassValueNode();
      this.getReached = true;
      classValueNode.addConsumer(
         reflectedType -> {
            if (reflectedType.getValueType() instanceof ValueType.Object) {
               String className = ((ValueType.Object)reflectedType.getValueType()).getClassName();
               Set<String> accessibleFields = this.getAccessibleFields(agent, className);
               ClassReader cls = agent.getClassSource().get(className);

               for (String fieldName : accessibleFields) {
                  FieldReader field = cls.getField(fieldName);
                  FieldDependency fieldDep = agent.linkField(field.getReference()).addLocation(location);
                  if (field.hasModifier(ElementModifier.STATIC)) {
                     this.readFields.add(field.getReference());
                     if (!primitive) {
                        this.propagateGet(agent, field.getType(), fieldDep.getValue(), method.getResult(), location);
                     }
                  } else {
                     method.getVariable(1)
                        .addConsumer(new ReflectionDependencyListener.InstanceGetConsumer(agent, field.getReference(), method, location, primitive));
                  }

                  this.linkClassIfNecessary(agent, field, location);
               }
            }
         }
      );
   }

   private void handleFieldSet(DependencyAgent agent, MethodDependency method, boolean primitive) {
      CallLocation location = new CallLocation(method.getReference());
      DependencyNode classValueNode = agent.linkMethod(this.getFields).addLocation(location).getVariable(0).getClassValueNode();
      this.setReached = true;
      classValueNode.addConsumer(
         reflectedType -> {
            if (reflectedType.getValueType() instanceof ValueType.Object) {
               String className = ((ValueType.Object)reflectedType.getValueType()).getClassName();
               Set<String> accessibleFields = this.getAccessibleFields(agent, className);
               ClassReader cls = agent.getClassSource().get(className);

               for (String fieldName : accessibleFields) {
                  FieldReader field = cls.getField(fieldName);
                  FieldDependency fieldDep = agent.linkField(field.getReference()).addLocation(location);
                  if (field.hasModifier(ElementModifier.STATIC)) {
                     this.writtenFields.add(field.getReference());
                     if (!primitive) {
                        this.propagateSet(agent, field.getType(), method.getVariable(2), fieldDep.getValue(), location);
                     }
                  } else {
                     method.getVariable(1)
                        .addConsumer(new ReflectionDependencyListener.InstanceSetConsumer(agent, field.getReference(), method, location, primitive));
                  }

                  this.linkClassIfNecessary(agent, field, location);
               }
            }
         }
      );
   }

   private void handleNewInstance(DependencyAgent agent, MethodDependency method) {
      CallLocation location = new CallLocation(method.getReference());
      DependencyNode classValueNode = agent.linkMethod(this.getConstructors).addLocation(location).getVariable(0).getClassValueNode();
      this.callReached = true;
      CallLocation infoLocation = new CallLocation(new MethodReference(MethodInfo.class, "call", Object.class, Object[].class, Object.class));
      agent.linkMethod(infoLocation.getMethod()).addLocation(location).use();
      classValueNode.addConsumer(
         reflectedType -> {
            if (reflectedType.getValueType() instanceof ValueType.Object) {
               String className = ((ValueType.Object)reflectedType.getValueType()).getClassName();
               ClassReader cls = agent.getClassSource().get(className);
               if (cls != null && !cls.hasModifier(ElementModifier.ABSTRACT) && !cls.hasModifier(ElementModifier.INTERFACE)) {
                  Set<MethodDescriptor> accessibleMethods = this.collectAccessibleMethods(className);
                  boolean hasConstructors = false;

                  for (MethodDescriptor methodDescriptor : accessibleMethods) {
                     if (methodDescriptor.getName().equals("<init>")) {
                        MethodReader calledMethod = cls.getMethod(methodDescriptor);
                        MethodDependency calledMethodDep = agent.linkMethod(calledMethod.getReference()).addLocation(infoLocation);
                        calledMethodDep.use();

                        for (int i = 0; i < calledMethod.parameterCount(); i++) {
                           this.propagateSet(
                              agent, methodDescriptor.parameterType(i), method.getVariable(1).getArrayItem(), calledMethodDep.getVariable(i + 1), infoLocation
                           );
                        }

                        calledMethodDep.getVariable(0).propagate(reflectedType);
                        this.calledMethods.add(calledMethod.getReference());
                        this.virtualMethods.add(calledMethod.getReference());
                        hasConstructors = true;
                     }
                  }

                  if (hasConstructors) {
                     method.getResult().propagate(reflectedType);
                  }
               }
            }
         }
      );
   }

   private void handleClassNewInstance(DependencyAgent agent, MethodDependency method) {
      CallLocation location = new CallLocation(method.getReference());
      DependencyNode classValueNode = method.getVariable(0).getClassValueNode();
      classValueNode.addConsumer(reflectedType -> {
         if (reflectedType.getValueType() instanceof ValueType.Object) {
            String className = ((ValueType.Object)reflectedType.getValueType()).getClassName();
            ClassReader cls = agent.getClassSource().get(className);
            if (cls != null && !cls.hasModifier(ElementModifier.ABSTRACT) && !cls.hasModifier(ElementModifier.INTERFACE)) {
               MethodReader constructor = cls.getMethod(new MethodDescriptor("<init>", void.class));
               if (constructor != null && constructor.getProgram() != null) {
                  MethodDependency constructorDep = agent.linkMethod(constructor.getReference()).addLocation(location);
                  constructorDep.getVariable(0).propagate(reflectedType);
                  constructorDep.use();
                  method.getResult().propagate(reflectedType);
               }
            }
         }
      });
   }

   private void handleInvoke(DependencyAgent agent, MethodDependency method) {
      CallLocation location = new CallLocation(method.getReference());
      DependencyNode classValueNode = agent.linkMethod(this.getMethods).addLocation(location).getVariable(0).getClassValueNode();
      this.callReached = true;
      CallLocation infoLocation = new CallLocation(new MethodReference(MethodInfo.class, "call", Object.class, Object[].class, Object.class));
      agent.linkMethod(infoLocation.getMethod()).addLocation(location).use();
      classValueNode.addConsumer(
         reflectedType -> {
            if (reflectedType.getValueType() instanceof ValueType.Object) {
               String className = ((ValueType.Object)reflectedType.getValueType()).getClassName();
               Set<MethodDescriptor> accessibleMethods = this.collectAccessibleMethods(className);
               ClassReader cls = agent.getClassSource().get(className);

               for (MethodDescriptor methodDescriptor : accessibleMethods) {
                  if (!methodDescriptor.getName().equals("<init>")) {
                     MethodReader calledMethod = cls.getMethod(methodDescriptor);
                     if (calledMethod.hasModifier(ElementModifier.STATIC)) {
                        MethodDependency calledMethodDep = agent.linkMethod(calledMethod.getReference()).addLocation(infoLocation);
                        calledMethodDep.use();

                        for (int i = 0; i < calledMethod.parameterCount(); i++) {
                           this.propagateSet(
                              agent, methodDescriptor.parameterType(i), method.getVariable(2).getArrayItem(), calledMethodDep.getVariable(i + 1), infoLocation
                           );
                        }

                        this.propagateSet(agent, ValueType.object(className), method.getVariable(1), calledMethodDep.getVariable(0), infoLocation);
                        this.propagateGet(agent, calledMethod.getResultType(), calledMethodDep.getResult(), method.getResult(), infoLocation);
                        this.linkClassIfNecessary(agent, calledMethod, infoLocation);
                        this.calledMethods.add(calledMethod.getReference());
                     } else {
                        boolean virtual = !calledMethod.hasModifier(ElementModifier.FINAL) && calledMethod.getLevel() != AccessLevel.PRIVATE;
                        method.getVariable(1)
                           .addConsumer(
                              new ReflectionDependencyListener.InstanceCallConsumer(agent, calledMethod.getReference(), method, infoLocation, virtual)
                           );
                     }
                  }
               }
            }
         }
      );
   }

   private void linkType(DependencyAgent agent, ValueType type) {
      this.typesInReflectableSignaturesNode.propagate(agent.getType(type));
      this.linkClass(agent, type);
   }

   private void linkClass(DependencyAgent agent, ValueType type) {
      if (type instanceof ValueType.Object) {
         agent.linkClass(((ValueType.Object)type).getClassName());
      } else if (type instanceof ValueType.Array) {
         this.linkClass(agent, ((ValueType.Array)type).getItemType());
      }
   }

   private void linkGenericType(DependencyAgent agent, GenericValueType type) {
      ValueType simpleType = type.asValueType();
      if (simpleType != null) {
         this.typesInGenericReflectableSignaturesNode.propagate(agent.getType(simpleType));
      } else {
         if (type instanceof GenericValueType.Array) {
            this.linkGenericType(agent, ((GenericValueType.Array)type).getItemType());
         } else if (type instanceof GenericValueType.Object objType) {
            this.typesInGenericReflectableSignaturesNode.propagate(agent.getType(ValueType.object(objType.getFullClassName())));

            for (GenericValueType.Argument arg : objType.getArguments()) {
               if (arg.getValue() != null) {
                  this.linkGenericType(agent, arg.getValue());
               }
            }

            if (objType.getParent() != null) {
               this.linkGenericType(agent, objType.getParent());
            }
         }
      }
   }

   private void linkClassIfNecessary(DependencyAgent agent, MemberReader member, CallLocation location) {
      if (member.hasModifier(ElementModifier.STATIC)) {
         agent.linkClass(member.getOwnerName()).initClass(location);
      }
   }

   private Set<String> getAccessibleFields(DependencyAgent agent, String className) {
      return this.accessibleFieldCache.computeIfAbsent(className, key -> this.gatherAccessibleFields(agent, key));
   }

   private Set<MethodDescriptor> collectAccessibleMethods(String className) {
      return this.accessibleMethodCache.computeIfAbsent(className, this::gatherAccessibleMethods);
   }

   private Set<String> gatherAccessibleFields(DependencyAgent agent, String className) {
      ReflectionDependencyListener.ReflectionContextImpl context = new ReflectionDependencyListener.ReflectionContextImpl(agent);
      Set<String> fields = new LinkedHashSet<>();

      for (ReflectionSupplier supplier : this.reflectionSuppliers) {
         fields.addAll(supplier.getAccessibleFields(context, className));
      }

      return fields;
   }

   private Set<MethodDescriptor> gatherAccessibleMethods(String className) {
      Set<MethodDescriptor> methods = new LinkedHashSet<>();

      for (ReflectionSupplier supplier : this.reflectionSuppliers) {
         methods.addAll(supplier.getAccessibleMethods(this.reflectionContext, className));
      }

      return methods;
   }

   private void propagateGet(DependencyAgent agent, ValueType type, DependencyNode sourceNode, DependencyNode targetNode, CallLocation location) {
      if (type instanceof ValueType.Primitive) {
         MethodDependency boxMethodDep = agent.linkMethod(switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN -> new MethodReference(Boolean.class, "valueOf", boolean.class, Boolean.class);
            case BYTE -> new MethodReference(Byte.class, "valueOf", byte.class, Byte.class);
            case SHORT -> new MethodReference(Short.class, "valueOf", short.class, Short.class);
            case CHARACTER -> new MethodReference(Character.class, "valueOf", char.class, Character.class);
            case INTEGER -> new MethodReference(Integer.class, "valueOf", int.class, Integer.class);
            case LONG -> new MethodReference(Long.class, "valueOf", long.class, Long.class);
            case FLOAT -> new MethodReference(Float.class, "valueOf", float.class, Float.class);
            case DOUBLE -> new MethodReference(Double.class, "valueOf", double.class, Double.class);
            default -> throw new AssertionError(type.toString());
         }).addLocation(location);
         boxMethodDep.use();
         boxMethodDep.getResult().connect(targetNode);
      } else if (type instanceof ValueType.Array || type instanceof ValueType.Object) {
         sourceNode.connect(targetNode);
      }
   }

   private void propagateSet(DependencyAgent agent, ValueType type, DependencyNode sourceNode, DependencyNode targetNode, CallLocation location) {
      if (type instanceof ValueType.Primitive) {
         MethodReference unboxMethod = switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN -> new MethodReference(Boolean.class, "booleanValue", boolean.class);
            case BYTE -> new MethodReference(Byte.class, "byteValue", byte.class);
            case SHORT -> new MethodReference(Short.class, "shortValue", short.class);
            case CHARACTER -> new MethodReference(Character.class, "charValue", char.class);
            case INTEGER -> new MethodReference(Integer.class, "intValue", int.class);
            case LONG -> new MethodReference(Long.class, "longValue", long.class);
            case FLOAT -> new MethodReference(Float.class, "floatValue", float.class);
            case DOUBLE -> new MethodReference(Double.class, "doubleValue", double.class);
            default -> throw new AssertionError(type.toString());
         };
         MethodDependency unboxMethodDep = agent.linkMethod(unboxMethod).addLocation(location);
         unboxMethodDep.propagateClass(0, unboxMethod.getClassName());
         unboxMethodDep.use();
         sourceNode.connect(unboxMethodDep.getResult());
      } else if (type instanceof ValueType.Array || type instanceof ValueType.Object) {
         sourceNode.connect(targetNode);
      }
   }

   private class InstanceCallConsumer implements DependencyConsumer {
      private final DependencyAgent agent;
      private final MethodReference methodRef;
      private final MethodDependency invokeDep;
      private final CallLocation location;
      private final boolean virtual;
      private Set<DependencyType> knownTypes = new HashSet<>();
      private Set<MethodReference> knownMethods = new HashSet<>();

      InstanceCallConsumer(DependencyAgent agent, MethodReference methodRef, MethodDependency invokeDep, CallLocation location, boolean virtual) {
         this.agent = agent;
         this.methodRef = methodRef;
         this.invokeDep = invokeDep;
         this.location = location;
         this.virtual = virtual;
      }

      @Override
      public void consume(DependencyType type) {
         if (this.knownTypes.add(type)) {
            ValueType valueType = type.getValueType();
            String className;
            if (valueType instanceof ValueType.Object) {
               className = ((ValueType.Object)valueType).getClassName();
            } else {
               if (!(valueType instanceof ValueType.Array)) {
                  return;
               }

               className = "java.lang.Object";
            }

            if (this.agent.getClassHierarchy().isSuperType(this.methodRef.getClassName(), className, false)) {
               MethodDependency methodDep = this.virtual
                  ? this.agent.linkMethod(className, this.methodRef.getDescriptor())
                  : this.agent.linkMethod(this.methodRef);
               methodDep.addLocation(this.location);
               if (this.knownMethods.add(methodDep.getReference())) {
                  if (!methodDep.isMissing()) {
                     ReflectionDependencyListener.this.virtualCallSites.add(this.methodRef);
                     ReflectionDependencyListener.this.calledMethods.add(this.methodRef);
                     methodDep.use();
                     ReflectionDependencyListener.this.virtualMethods.add(methodDep.getReference());
                     this.invokeDep.getVariable(1).connect(methodDep.getVariable(0));

                     for (int i = 0; i < this.methodRef.parameterCount(); i++) {
                        ReflectionDependencyListener.this.propagateSet(
                           this.agent,
                           this.methodRef.parameterType(i),
                           this.invokeDep.getVariable(2).getArrayItem(),
                           methodDep.getVariable(i + 1),
                           this.location
                        );
                     }

                     if (methodDep.getResult() != null) {
                        ReflectionDependencyListener.this.propagateGet(
                           this.agent, this.methodRef.getReturnType(), methodDep.getResult(), this.invokeDep.getResult(), this.location
                        );
                     }
                  }
               }
            }
         }
      }
   }

   private class InstanceGetConsumer implements DependencyConsumer {
      private final DependencyAgent agent;
      private final FieldReference fieldRef;
      private final MethodDependency readDep;
      private final CallLocation location;
      private final boolean primitive;
      private Set<DependencyType> knownTypes = new HashSet<>();
      private boolean done;

      InstanceGetConsumer(DependencyAgent agent, FieldReference fieldRef, MethodDependency readDep, CallLocation location, boolean primitive) {
         this.agent = agent;
         this.fieldRef = fieldRef;
         this.readDep = readDep;
         this.location = location;
         this.primitive = primitive;
      }

      @Override
      public void consume(DependencyType type) {
         if (!this.done && this.knownTypes.add(type)) {
            ValueType valueType = type.getValueType();
            String className;
            if (valueType instanceof ValueType.Object) {
               className = ((ValueType.Object)valueType).getClassName();
            } else {
               if (!(valueType instanceof ValueType.Array)) {
                  return;
               }

               className = "java.lang.Object";
            }

            if (this.agent.getClassHierarchy().isSuperType(this.fieldRef.getClassName(), className, false)) {
               FieldDependency fieldDep = this.agent.linkField(this.fieldRef);
               this.done = true;
               if (!fieldDep.isMissing()) {
                  ReflectionDependencyListener.this.readFields.add(this.fieldRef);
                  if (!this.primitive) {
                     ReflectionDependencyListener.this.propagateGet(
                        this.agent, fieldDep.getField().getType(), fieldDep.getValue(), this.readDep.getResult(), this.location
                     );
                  }
               }
            }
         }
      }
   }

   private class InstanceSetConsumer implements DependencyConsumer {
      private final DependencyAgent agent;
      private final FieldReference fieldRef;
      private final MethodDependency readDep;
      private final CallLocation location;
      private final boolean primitive;
      private Set<DependencyType> knownTypes = new HashSet<>();
      private boolean done;

      InstanceSetConsumer(DependencyAgent agent, FieldReference fieldRef, MethodDependency readDep, CallLocation location, boolean primitive) {
         this.agent = agent;
         this.fieldRef = fieldRef;
         this.readDep = readDep;
         this.location = location;
         this.primitive = primitive;
      }

      @Override
      public void consume(DependencyType type) {
         if (!this.done && this.knownTypes.add(type)) {
            ValueType valueType = type.getValueType();
            String className;
            if (valueType instanceof ValueType.Object) {
               className = ((ValueType.Object)valueType).getClassName();
            } else {
               if (!(valueType instanceof ValueType.Array)) {
                  return;
               }

               className = "java.lang.Object";
            }

            if (this.agent.getClassHierarchy().isSuperType(this.fieldRef.getClassName(), className, false)) {
               FieldDependency fieldDep = this.agent.linkField(this.fieldRef);
               this.done = true;
               if (!fieldDep.isMissing()) {
                  ReflectionDependencyListener.this.writtenFields.add(this.fieldRef);
                  if (!this.primitive) {
                     ReflectionDependencyListener.this.propagateSet(
                        this.agent, fieldDep.getField().getType(), this.readDep.getVariable(2), fieldDep.getValue(), this.location
                     );
                  }
               }
            }
         }
      }
   }

   private static class ReflectionContextImpl implements ReflectionContext {
      private DependencyAgent agent;

      ReflectionContextImpl(DependencyAgent agent) {
         this.agent = agent;
      }

      @Override
      public ClassLoader getClassLoader() {
         return this.agent.getClassLoader();
      }

      @Override
      public ClassReaderSource getClassSource() {
         return this.agent.getClassSource();
      }
   }
}
