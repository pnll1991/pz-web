package org.teavm.reflection;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.FieldDependency;
import org.teavm.interop.Unmanaged;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.runtime.StringInfo;
import org.teavm.runtime.reflect.AnnotationConstructor;
import org.teavm.runtime.reflect.AnnotationData;
import org.teavm.runtime.reflect.AnnotationInfoUtil;
import org.teavm.runtime.reflect.AnnotationValueArray;
import org.teavm.runtime.reflect.ClassInfo;
import org.teavm.runtime.reflect.ClassInfoUtil;
import org.teavm.runtime.reflect.DerivedClassInfo;

public class AnnotationGenerationHelper {
   public static final String ANNOTATION_IMPLEMENTOR_SUFFIX = "$$_impl";
   public static final String ANNOTATION_DATA_SUFFIX = "$$_nativeData";
   private Set<String> annotationImplementors = new HashSet<>();

   public final String getAnnotationImplementor(DependencyAgent agent, String annotationType) {
      String implementorName = annotationType + "$$_impl";
      if (this.annotationImplementors.add(implementorName)) {
         this.createImplementor(agent, annotationType, implementorName);
      }

      return implementorName;
   }

   private void createImplementor(DependencyAgent agent, String annotationType, String implementorName) {
      ClassHierarchy hierarchy = agent.getClassHierarchy();
      this.annotationImplementors.add(implementorName);
      ClassHolder implementor = new ClassHolder(implementorName);
      implementor.setParent("java.lang.Object");
      implementor.getInterfaces().add(annotationType);
      implementor.getModifiers().add(ElementModifier.FINAL);
      implementor.setLevel(AccessLevel.PUBLIC);
      ClassReader annotation = hierarchy.getClassSource().get(annotationType);
      if (annotation != null) {
         ClassHolder dataCls = new ClassHolder(annotationType + "$$_nativeData");
         dataCls.setParent(AnnotationData.class.getName());
         dataCls.getModifiers().add(ElementModifier.FINAL);
         dataCls.setLevel(AccessLevel.PUBLIC);
         dataCls.getAnnotations().add(new AnnotationHolder(Unmanaged.class.getName()));
         FieldHolder infoField = new FieldHolder("info");
         infoField.setType(ValueType.object(dataCls.getName()));
         infoField.setLevel(AccessLevel.PRIVATE);
         implementor.addField(infoField);
         MethodHolder dataCtorMethod = new MethodHolder("constructor", ValueType.object(AnnotationConstructor.class.getName()));
         dataCtorMethod.setLevel(AccessLevel.PUBLIC);
         dataCtorMethod.getModifiers().addAll(List.of(ElementModifier.STATIC, ElementModifier.NATIVE));
         dataCls.addMethod(dataCtorMethod);
         MethodHolder ctor = new MethodHolder("<init>", infoField.getType(), ValueType.VOID);
         ctor.setLevel(AccessLevel.PUBLIC);
         ProgramEmitter pe = ProgramEmitter.create(ctor, hierarchy);
         ValueEmitter thisVar = pe.var(0, implementor);
         ValueEmitter infoVar = pe.var(1, infoField.getType());
         thisVar.setField(infoField.getName(), infoVar);
         pe.exit();
         implementor.addMethod(ctor);
         MethodHolder factory = new MethodHolder("create", infoField.getType(), ValueType.object(implementorName));
         factory.setLevel(AccessLevel.PUBLIC);
         factory.getModifiers().add(ElementModifier.STATIC);
         ProgramEmitter factoryPe = ProgramEmitter.create(factory, hierarchy);
         factoryPe.construct(implementorName, factoryPe.var(1, infoField.getType())).returnValue();
         implementor.addMethod(factory);
         MethodHolder clinit = new MethodHolder("<clinit>", ValueType.VOID);
         clinit.setLevel(AccessLevel.PUBLIC);
         clinit.getModifiers().add(ElementModifier.STATIC);
         ProgramEmitter clinitPe = ProgramEmitter.create(clinit, hierarchy);
         boolean needsClinit = false;
         ArrayList<Runnable> postSubmitActions = new ArrayList<>();

         for (MethodReader methodDecl : annotation.getMethods()) {
            if (!methodDecl.hasModifier(ElementModifier.STATIC)) {
               ValueType type = methodDecl.getResultType();
               MethodHolder accessor = new MethodHolder(methodDecl.getDescriptor());
               accessor.setLevel(AccessLevel.PUBLIC);
               pe = ProgramEmitter.create(accessor, hierarchy);
               ValueEmitter thisVal = pe.var(0, implementor);
               ValueEmitter info = thisVal.getField(infoField.getName(), infoField.getType());
               implementor.addMethod(accessor);
               String className = null;
               ValueType implementorType = methodDecl.getResultType();
               if (type instanceof ValueType.Array) {
                  implementorType = ValueType.parse(AnnotationValueArray.class);
                  ValueType itemType = ((ValueType.Array)type).getItemType();
                  if (itemType instanceof ValueType.Object) {
                     className = ((ValueType.Object)itemType).getClassName();
                  }
               } else if (type.isObject(String.class)) {
                  implementorType = ValueType.parse(StringInfo.class);
               } else if (methodDecl.getResultType().isObject(Class.class)) {
                  implementorType = ValueType.parse(DerivedClassInfo.class);
               } else if (methodDecl.getResultType() instanceof ValueType.Object) {
                  className = ((ValueType.Object)methodDecl.getResultType()).getClassName();
               }

               FieldHolder enumCacheField = null;
               boolean isEnum = false;
               boolean isAnnotation = false;
               if (className != null) {
                  ClassReader implementorCls = hierarchy.getClassSource().get(className);
                  if (implementorCls != null) {
                     if (implementorCls.hasModifier(ElementModifier.ENUM)) {
                        isEnum = true;
                        needsClinit = true;
                        enumCacheField = new FieldHolder(methodDecl.getName() + "_$enumCache");
                        enumCacheField.setLevel(AccessLevel.PRIVATE);
                        enumCacheField.getModifiers().add(ElementModifier.STATIC);
                        enumCacheField.setType(ValueType.arrayOf(ValueType.object(className)));
                        implementor.addField(enumCacheField);
                        clinitPe.setField(enumCacheField.getReference(), clinitPe.invoke(className, "values", enumCacheField.getType()));
                     } else if (implementorCls.hasModifier(ElementModifier.ANNOTATION)) {
                        isAnnotation = true;
                     }
                  }
               }

               if (!(type instanceof ValueType.Array)) {
                  if (isAnnotation) {
                     implementorType = ValueType.object(className + "$$_nativeData");
                  } else if (isEnum) {
                     implementorType = ValueType.SHORT;
                  }
               }

               MethodHolder dataMethod = new MethodHolder(methodDecl.getName(), implementorType);
               dataMethod.setLevel(AccessLevel.PUBLIC);
               dataMethod.getModifiers().add(ElementModifier.NATIVE);
               dataCls.addMethod(dataMethod);
               ValueEmitter result = info.invokeSpecial(dataMethod.getReference());
               if (type instanceof ValueType.Array) {
                  ValueType itemType = ((ValueType.Array)type).getItemType();
                  if (itemType instanceof ValueType.Primitive) {
                     switch (((ValueType.Primitive)itemType).getKind()) {
                        case BOOLEAN:
                           result = pe.invoke(AnnotationInfoUtil.class, "asBooleanArray", boolean[].class, result);
                           break;
                        case BYTE:
                           result = pe.invoke(AnnotationInfoUtil.class, "asByteArray", byte[].class, result);
                           break;
                        case SHORT:
                           result = pe.invoke(AnnotationInfoUtil.class, "asShortArray", short[].class, result);
                           break;
                        case CHARACTER:
                           result = pe.invoke(AnnotationInfoUtil.class, "asCharArray", char[].class, result);
                           break;
                        case INTEGER:
                           result = pe.invoke(AnnotationInfoUtil.class, "asIntArray", int[].class, result);
                           break;
                        case LONG:
                           result = pe.invoke(AnnotationInfoUtil.class, "asLongArray", long[].class, result);
                           break;
                        case FLOAT:
                           result = pe.invoke(AnnotationInfoUtil.class, "asFloatArray", float[].class, result);
                           break;
                        case DOUBLE:
                           result = pe.invoke(AnnotationInfoUtil.class, "asDoubleArray", double[].class, result);
                     }
                  } else if (itemType.isObject(String.class)) {
                     result = pe.invoke(AnnotationInfoUtil.class, "asStringArray", String[].class, result);
                  } else if (itemType.isObject(Class.class)) {
                     result = pe.invoke(AnnotationInfoUtil.class, "asClassArray", Class[].class, result);
                  } else if (isEnum) {
                     ValueEmitter newArray = pe.constructArray(itemType, result.invokeSpecial("size", int.class));
                     ValueEmitter fields = pe.getField(enumCacheField.getReference(), enumCacheField.getType());
                     pe.invoke(AnnotationInfoUtil.class, "fillEnumArray", result, fields.cast(Enum[].class), newArray.cast(Enum[].class));
                     result = newArray;
                  } else if (isAnnotation) {
                     ValueEmitter newArray = pe.constructArray(itemType, result.invokeSpecial("size", int.class));
                     ValueEmitter annotCtor = pe.invoke(
                        new MethodReference(className + "$$_nativeData", "constructor", ValueType.parse(AnnotationConstructor.class))
                     );
                     pe.invoke(AnnotationInfoUtil.class, "fillAnnotationArray", result, annotCtor, newArray.cast(Annotation[].class));
                     result = newArray;
                     String annotImplName = this.getAnnotationImplementor(agent, className);
                     postSubmitActions.add(
                        () -> agent.linkMethod(accessor.getReference()).getResult().getArrayItem().propagate(agent.getType(ValueType.object(annotImplName)))
                     );
                  }
               } else if (type.isObject(String.class)) {
                  result = result.invokeSpecial("getStringObject", String.class);
               } else if (type.isObject(Class.class)) {
                  result = pe.invoke(ClassInfoUtil.class, "resolve", ClassInfo.class, result);
                  result = result.invokeSpecial("classObject", Class.class);
               } else if (isAnnotation) {
                  String annotImplName = this.getAnnotationImplementor(agent, className);
                  result = pe.invoke(annotImplName, "create", ValueType.object(annotImplName), result);
               } else if (isEnum) {
                  result = pe.getField(enumCacheField.getReference(), enumCacheField.getType()).getElement(result);
               }

               result.returnValue();
            }
         }

         MethodHolder annotTypeMethod = new MethodHolder("annotationType", ValueType.parse(Class.class));
         pe = ProgramEmitter.create(annotTypeMethod, hierarchy);
         pe.constant(ValueType.object(annotationType)).returnValue();
         implementor.addMethod(annotTypeMethod);
         if (needsClinit) {
            clinitPe.exit();
            implementor.addMethod(clinit);
         }

         agent.submitClass(implementor);
         agent.submitClass(dataCls);

         for (Runnable action : postSubmitActions) {
            action.run();
         }

         agent.linkMethod(factory.getReference()).use();
      }
   }

   public void propagateAnnotationImplementations(DependencyAgent agent, Iterable<? extends AnnotationReader> inputAnnotations, DependencyNode outputNode) {
      for (AnnotationReader annotation : inputAnnotations) {
         agent.linkClass(annotation.getType());
      }

      for (AnnotationReader annotation : collectRuntimeAnnotations(agent.getClassSource(), inputAnnotations)) {
         this.propagateAnnotationInstance(agent, annotation, outputNode);
      }
   }

   public static List<AnnotationReader> collectRuntimeAnnotations(ClassReaderSource classes, Iterable<? extends AnnotationReader> inputAnnotations) {
      ArrayList<AnnotationReader> annotations = new ArrayList<>();

      for (AnnotationReader annot : inputAnnotations) {
         ClassReader annotType = classes.get(annot.getType());
         if (annotType != null) {
            AnnotationReader retention = annotType.getAnnotations().get(Retention.class.getName());
            if (retention != null) {
               String retentionPolicy = retention.getValue("value").getEnumValue().getFieldName();
               if (retentionPolicy.equals("RUNTIME")) {
                  annotations.add(annot);
               }
            }
         }
      }

      return annotations;
   }

   private void propagateAnnotationInstance(DependencyAgent agent, AnnotationReader annotation, DependencyNode outputNode) {
      ClassReader annotationClass = agent.getClassSource().get(annotation.getType());
      if (annotationClass != null) {
         String implementor = this.getAnnotationImplementor(agent, annotation.getType());
         if (implementor != null) {
            agent.linkClass(implementor).initClass(null);
            agent.linkClass(annotation.getType() + "$$_nativeData");
            outputNode.propagate(agent.getType(ValueType.object(implementor)));

            for (MethodReader methodDecl : annotationClass.getMethods()) {
               if (!methodDecl.hasModifier(ElementModifier.STATIC)) {
                  AnnotationValue value = annotation.getValue(methodDecl.getName());
                  if (value == null) {
                     value = methodDecl.getAnnotationDefault();
                  }

                  FieldDependency field = agent.linkField(new FieldReference(implementor, "$" + methodDecl.getName()));
                  this.propagateAnnotationValue(agent, value, methodDecl.getResultType(), field.getValue());
               }
            }

            ValueType.Object initParamType = ValueType.object(annotation.getType() + "$$_nativeData");
            agent.linkMethod(new MethodReference(implementor, "<init>", initParamType, ValueType.VOID)).use();
         }
      }
   }

   private void propagateAnnotationValue(DependencyAgent agent, AnnotationValue value, ValueType type, DependencyNode outputNode) {
      switch (value.getType()) {
         case 8:
            ValueType cls = value.getJavaClass();

            while (cls instanceof ValueType.Array) {
               cls = ((ValueType.Array)cls).getItemType();
            }

            if (cls instanceof ValueType.Object) {
               String className = ((ValueType.Object)cls).getClassName();
               agent.linkClass(className).initClass(null);
            }

            outputNode.getClassValueNode().propagate(agent.getType(value.getJavaClass()));
            outputNode.propagate(agent.getType(ValueType.object("java.lang.Class")));
            break;
         case 9:
            outputNode.propagate(agent.getType(type));
            ValueType itemType = ((ValueType.Array)type).getItemType();

            for (AnnotationValue annotationValue : value.getList()) {
               this.propagateAnnotationValue(agent, annotationValue, itemType, outputNode.getArrayItem());
            }
         case 10:
         default:
            break;
         case 11:
            this.propagateAnnotationInstance(agent, value.getAnnotation(), outputNode);
      }
   }
}
