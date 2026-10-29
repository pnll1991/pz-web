package org.teavm.extension.introspect;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.GenericTypeParameter;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.ValueType;

public class IntrospectClassImpl<T> extends IntrospectAnnotatedElementImpl implements IntrospectClass<T> {
   private static final int FLAG_RESOLVED = 1;
   private static final int FLAG_SUPERCLASS_RESOLVED = 2;
   public final ValueType type;
   public ClassReader classReader;
   private List<? extends IntrospectClass<T>> interfaces;
   private IntrospectClass<T> superclass;
   private List<? extends IntrospectField> declaredFields;
   private Map<String, IntrospectFieldImpl> declaredFieldByName;
   private List<? extends IntrospectField> fieldsCache;
   private Map<MethodDescriptor, IntrospectMethodImpl> methods;
   private List<? extends IntrospectMethod> declaredMethods;
   private Map<String, IntrospectMethodImpl> declaredMethodsBySignature;
   private List<? extends IntrospectMethod> methodsCache;
   private List<? extends IntrospectField> enumConstants;
   private List<? extends IntrospectTypeVariableImpl> typeParameters;
   private int flags;

   IntrospectClassImpl(ValueType type, Introspection introspection) {
      super(introspection);
      this.type = type;
   }

   public Introspection introspection() {
      return this.introspection;
   }

   public boolean isPrimitive() {
      return this.type instanceof ValueType.Primitive || this.type == ValueType.VOID;
   }

   public boolean isInterface() {
      this.resolve();
      return this.classReader != null && this.classReader.readModifiers().contains(ElementModifier.INTERFACE);
   }

   public boolean isArray() {
      return this.type instanceof ValueType.Array;
   }

   public boolean isAnnotation() {
      this.resolve();
      return this.classReader != null && this.classReader.readModifiers().contains(ElementModifier.ANNOTATION);
   }

   public boolean isEnum() {
      this.resolve();
      return this.classReader != null && this.classReader.readModifiers().contains(ElementModifier.ENUM);
   }

   public boolean isRecord() {
      this.resolve();
      return this.classReader != null && this.classReader.readModifiers().contains(ElementModifier.RECORD);
   }

   public List<? extends IntrospectField> enumConstants() {
      List<? extends IntrospectField> result = this.enumConstants;
      if (result == null) {
         this.resolve();
         if (this.classReader != null && this.classReader.hasModifier(ElementModifier.ENUM)) {
            ArrayList<IntrospectField> constants = new ArrayList<>();

            for (IntrospectField field : this.declaredFields()) {
               if (field.isEnumConstant()) {
                  constants.add(field);
               }
            }

            result = Collections.unmodifiableList(constants);
         } else {
            result = Collections.emptyList();
         }

         this.enumConstants = result;
      }

      return result;
   }

   public int modifiers() {
      this.resolve();
      return this.classReader == null ? 0 : Introspection.getModifiers(this.classReader);
   }

   public IntrospectClass<?> componentType() {
      if (!(this.type instanceof ValueType.Array)) {
         return null;
      }

      ValueType componentType = ((ValueType.Array)this.type).getItemType();
      return this.introspection.getClass(componentType);
   }

   public IntrospectClass<T[]> arrayType() {
      return (IntrospectClass<T[]>)this.introspection.getClass(ValueType.arrayOf(this.type));
   }

   public String name() {
      if (this.type instanceof ValueType.Object) {
         return ((ValueType.Object)this.type).getClassName();
      }

      if (this.type instanceof ValueType.Void) {
         return "void";
      }

      if (this.type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)this.type).getKind()) {
            case BOOLEAN:
               return "boolean";
            case BYTE:
               return "byte";
            case SHORT:
               return "short";
            case CHARACTER:
               return "char";
            case INTEGER:
               return "int";
            case LONG:
               return "long";
            case FLOAT:
               return "float";
            case DOUBLE:
               return "double";
            default:
               return "";
         }
      } else {
         return this.type instanceof ValueType.Array ? this.type.toString().replace('/', '.') : "";
      }
   }

   public String simpleName() {
      if (this.type instanceof ValueType.Object) {
         this.resolve();
         if (this.classReader != null && this.classReader.getSimpleName() != null) {
            return this.classReader.getSimpleName();
         }

         String name = ((ValueType.Object)this.type).getClassName();
         int lastIndex = name.lastIndexOf(36);
         if (lastIndex < 0) {
            lastIndex = name.lastIndexOf(46);
         }

         return name.substring(lastIndex + 1);
      } else {
         if (this.type instanceof ValueType.Void) {
            return "void";
         }

         if (this.type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive)this.type).getKind()) {
               case BOOLEAN:
                  return "boolean";
               case BYTE:
                  return "byte";
               case SHORT:
                  return "short";
               case CHARACTER:
                  return "char";
               case INTEGER:
                  return "int";
               case LONG:
                  return "long";
               case FLOAT:
                  return "float";
               case DOUBLE:
                  return "double";
               default:
                  return "";
            }
         } else {
            return this.type instanceof ValueType.Array ? this.introspection.getClass(((ValueType.Array)this.type).getItemType()).simpleName() + "[]" : "";
         }
      }
   }

   public IntrospectClass<? super T> superclass() {
      this.resolve();
      IntrospectClass<T> result = this.superclass;
      if ((this.flags & 2) == 0) {
         if (this.classReader != null && this.classReader.getParent() != null && !this.classReader.getName().equals(this.classReader.getParent())) {
            result = (IntrospectClass<T>)this.introspection.getClass(new ValueType.Object(this.classReader.getParent()));
         } else {
            result = null;
         }

         this.superclass = result;
         this.flags |= 2;
      }

      return result;
   }

   public List<? extends IntrospectClass<? super T>> interfaces() {
      this.resolve();
      List<? extends IntrospectClass<T>> result = this.interfaces;
      if (result == null) {
         if (this.classReader == null) {
            result = Collections.emptyList();
         } else {
            result = this.classReader
               .getInterfaces()
               .stream()
               .map(iface -> this.introspection.getClass(new ValueType.Object(iface)))
               .collect(Collectors.toUnmodifiableList());
         }

         this.interfaces = result;
      }

      return result;
   }

   public <U> IntrospectClass<U> asSubclass(IntrospectClass<U> cls) {
      if (!cls.isAssignableFrom(this)) {
         throw new IllegalArgumentException(cls.name() + " is not subclass of " + this.name());
      } else {
         return (IntrospectClass<U>)this;
      }
   }

   public <U> IntrospectClass<U> asSubclass(Class<U> cls) {
      IntrospectClassImpl<U> reflectClass = this.introspection.findClass(cls);
      if (!reflectClass.isAssignableFrom(this)) {
         throw new IllegalArgumentException(cls.getName() + " is not subclass of " + this.name());
      } else {
         return (IntrospectClass<U>)this;
      }
   }

   public boolean isAssignableFrom(IntrospectClass<?> cls) {
      return cls == this || cls.superclass() != null && this.isAssignableFrom(cls.superclass()) || cls.interfaces().stream().anyMatch(this::isAssignableFrom);
   }

   public boolean isAssignableFrom(Class<?> cls) {
      return this.isAssignableFrom(this.introspection.findClass(cls));
   }

   public List<? extends IntrospectMethod> declaredMethods() {
      this.resolve();
      List<? extends IntrospectMethod> result = this.declaredMethods;
      if (result == null) {
         if (this.classReader == null) {
            result = Collections.emptyList();
         } else {
            result = this.classReader
               .getMethods()
               .stream()
               .filter(method -> !method.getName().equals("<clinit>"))
               .map(method -> this.declaredMethod(method.getDescriptor()))
               .collect(Collectors.toUnmodifiableList());
         }

         this.declaredMethods = result;
      }

      return result;
   }

   public List<? extends IntrospectMethod> methods() {
      this.resolve();
      List<? extends IntrospectMethod> result = this.methodsCache;
      if (result == null) {
         if (this.classReader == null) {
            result = Collections.emptyList();
         } else {
            Set<String> visited = new HashSet<>();
            result = this.introspection
               .getClassSource()
               .getAncestors(this.classReader.getName())
               .flatMap(cls -> cls.getMethods().stream())
               .filter(method -> !method.getName().equals("<clinit>"))
               .filter(method -> visited.add(method.getDescriptor().toString()))
               .map(method -> this.introspection.getClass(ValueType.object(method.getOwnerName())).declaredMethod(method.getDescriptor()))
               .filter(Objects::nonNull)
               .collect(Collectors.toUnmodifiableList());
         }

         this.methodsCache = result;
      }

      return result;
   }

   public IntrospectMethod declaredMethod(String name, IntrospectClass<?>... parameterTypes) {
      this.resolve();
      if (this.classReader == null) {
         return null;
      }

      ValueType[] internalParameterTypes = Arrays.stream(parameterTypes).map(type -> ((IntrospectClassImpl)type).type).toArray(ValueType[]::new);
      String key = name + "(" + ValueType.manyToString(internalParameterTypes) + ")";
      if (this.declaredMethods == null) {
         this.declaredMethodsBySignature = new LinkedHashMap<>();
      }

      return this.declaredMethodsBySignature.computeIfAbsent(key, k -> {
         MethodReader candidate = null;

         for (MethodReader method : this.classReader.getMethods()) {
            if (method.getName().equals(name) && Arrays.equals(method.getParameterTypes(), internalParameterTypes)) {
               if (candidate == null) {
                  candidate = method;
               } else {
                  boolean moreSpecial = this.introspection.getHierarchy().isSuperType(candidate.getResultType(), method.getResultType(), false);
                  if (moreSpecial) {
                     candidate = method;
                  }
               }
            }
         }

         return candidate != null ? this.declaredMethod(candidate.getDescriptor()) : null;
      });
   }

   public IntrospectMethodImpl declaredMethod(MethodDescriptor method) {
      this.resolve();
      if (this.methods == null) {
         this.methods = new HashMap<>();
      }

      return this.methods.computeIfAbsent(method, m -> {
         MethodReader methodReader = this.classReader.getMethod(m);
         return methodReader != null ? new IntrospectMethodImpl(this, methodReader) : null;
      });
   }

   public IntrospectMethod declaredJavaMethod(String name, Class<?>... parameterTypes) {
      IntrospectClass[] mappedParamTypes = Arrays.stream(parameterTypes).map(this.introspection::findClass).toArray(IntrospectClass[]::new);
      return this.declaredMethod(name, mappedParamTypes);
   }

   public IntrospectMethod javaMethod(String name, Class<?>... parameterTypes) {
      IntrospectClass[] mappedParamTypes = Arrays.stream(parameterTypes).map(this.introspection::findClass).toArray(IntrospectClass[]::new);
      return this.method(name, mappedParamTypes);
   }

   public IntrospectMethod method(String name, IntrospectClass<?>... parameterTypes) {
      this.resolve();
      if (this.classReader == null) {
         return null;
      }

      for (ClassReader cls : () -> this.introspection.getClassSource().getAncestors(this.classReader.getName()).iterator()) {
         IntrospectClassImpl<?> reflectClass = this.introspection.getClass(ValueType.object(cls.getName()));
         IntrospectMethod method = reflectClass.declaredMethod(name, parameterTypes);
         if (method != null && Modifier.isPublic(method.modifiers())) {
            return method;
         }
      }

      return null;
   }

   public List<? extends IntrospectField> declaredFields() {
      this.resolve();
      List<? extends IntrospectField> result = this.declaredFields;
      if (result == null) {
         if (this.classReader == null) {
            result = Collections.emptyList();
         } else {
            result = this.classReader.getFields().stream().map(fld -> this.declaredField(fld.getName())).collect(Collectors.toUnmodifiableList());
         }

         this.declaredFields = result;
      }

      return result;
   }

   public List<? extends IntrospectField> fields() {
      this.resolve();
      List<? extends IntrospectField> result = this.fieldsCache;
      if (this.fieldsCache == null) {
         if (this.classReader == null) {
            result = Collections.emptyList();
         } else {
            Set<String> visited = new HashSet<>();
            result = this.introspection
               .getClassSource()
               .getAncestors(this.classReader.getName())
               .flatMap(cls -> cls.getFields().stream().filter(fld -> fld.getLevel() == AccessLevel.PUBLIC))
               .filter(fld -> visited.add(fld.getName()))
               .map(fld -> this.introspection.getClass(ValueType.object(fld.getOwnerName())).declaredField(fld.getName()))
               .collect(Collectors.toUnmodifiableList());
         }

         this.fieldsCache = result;
      }

      return result;
   }

   public IntrospectField declaredField(String name) {
      this.resolve();
      if (this.declaredFieldByName == null) {
         this.declaredFieldByName = new HashMap<>();
      }

      return this.declaredFieldByName.computeIfAbsent(name, n -> {
         FieldReader fld = this.classReader.getField(n);
         return fld != null ? new IntrospectFieldImpl(this, fld) : null;
      });
   }

   public IntrospectField field(String name) {
      this.resolve();
      if (this.classReader == null) {
         return null;
      }

      for (ClassReader cls = this.classReader; cls != null; cls = cls.getParent() != null ? this.introspection.getClassSource().get(cls.getParent()) : null) {
         FieldReader fieldReader = this.classReader.getField(name);
         if (fieldReader != null && fieldReader.getLevel() == AccessLevel.PUBLIC) {
            return this.declaredField(name);
         }
      }

      return null;
   }

   @Override
   protected AnnotationContainerReader annotationContainer() {
      this.resolve();
      return this.classReader != null ? this.classReader.getAnnotations() : null;
   }

   public void resolve() {
      if ((this.flags & 1) == 0) {
         this.flags |= 1;
         if (this.type instanceof ValueType.Object) {
            String className = ((ValueType.Object)this.type).getClassName();
            this.classReader = this.introspection.getClassSource().get(className);
         }
      }
   }

   @Override
   public String toString() {
      if (this.isArray()) {
         return this.componentType().toString() + "[]";
      }

      this.typeParameters();
      if (this.typeParameters.isEmpty()) {
         return this.name();
      }

      StringBuilder sb = new StringBuilder(this.name());
      sb.append('<');

      for (int i = 0; i < this.typeParameters.size(); i++) {
         if (i > 0) {
            sb.append(", ");
         }

         sb.append(this.typeParameters.get(i).toTypeParameterString());
      }

      sb.append('>');
      return sb.toString();
   }

   public List<? extends IntrospectTypeVariable> typeParameters() {
      if (this.typeParameters == null) {
         this.resolve();
         if (this.classReader == null) {
            this.typeParameters = Collections.emptyList();
         } else {
            GenericTypeParameter[] params = this.classReader.getGenericParameters();
            if (params != null && params.length != 0) {
               ArrayList<IntrospectTypeVariableImpl> result = new ArrayList<>(params.length);

               for (GenericTypeParameter param : params) {
                  result.add(new IntrospectTypeVariableImpl(param, this, null, this.introspection));
               }

               this.typeParameters = Collections.unmodifiableList(result);
            } else {
               this.typeParameters = Collections.emptyList();
            }
         }
      }

      return this.typeParameters;
   }

   IntrospectTypeVariableImpl typeParameter(String name) {
      for (IntrospectTypeVariable tp : this.typeParameters()) {
         if (tp.name().equals(name)) {
            return (IntrospectTypeVariableImpl)tp;
         }
      }

      return null;
   }

   public Class<T> asJavaClass() {
      throw new IllegalStateException("Don't call this method from compile domain");
   }
}
