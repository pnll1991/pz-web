package org.teavm.model.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.dependency.DependencyInfo;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.dependency.ValueDependencyInfo;
import org.teavm.hppc.ObjectByteHashMap;
import org.teavm.hppc.ObjectByteMap;
import org.teavm.interop.NoSideEffects;
import org.teavm.interop.StaticInit;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ProgramReader;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.optimization.Devirtualization;

public class ClassInitializerAnalysis implements ClassInitializerInfo {
   private static final MethodDescriptor CLINIT = new MethodDescriptor("<clinit>", void.class);
   private static final int METHOD_ANALYSIS_DEPTH_THRESHOLD = 250;
   private static final byte BEING_ANALYZED = 1;
   private static final byte DYNAMIC = 2;
   private static final byte STATIC = 3;
   private ObjectByteMap<String> classStatuses = new ObjectByteHashMap();
   private Map<MethodReference, ClassInitializerAnalysis.MethodInfo> methodInfoMap = new HashMap<>();
   private ListableClassReaderSource classes;
   private ClassHierarchy hierarchy;
   private String entryPoint;
   private List<String> order = new ArrayList<>();
   private List<? extends String> readonlyOrder = Collections.unmodifiableList(this.order);
   private String currentAnalyzedClass;
   private DependencyInfo dependencyInfo;
   private int methodAnalysisDepth;

   public ClassInitializerAnalysis(ListableClassReaderSource classes, ClassHierarchy hierarchy, String entryPoint) {
      this.classes = classes;
      this.hierarchy = hierarchy;
      this.entryPoint = entryPoint;
   }

   public void analyze(DependencyInfo dependencyInfo) {
      if (this.methodInfoMap != null) {
         this.dependencyInfo = dependencyInfo;

         for (String className : this.classes.getClassNames()) {
            this.analyze(className);
         }

         this.methodInfoMap = null;
         this.classes = null;
         this.hierarchy = null;
         this.dependencyInfo = null;
      }
   }

   @Override
   public boolean isDynamicInitializer(String className) {
      return this.classStatuses.get(className) != 3;
   }

   @Override
   public List<? extends String> getInitializationOrder() {
      return this.readonlyOrder;
   }

   private void analyze(String className) {
      byte classStatus = this.classStatuses.get(className);
      switch (classStatus) {
         case 1:
            if (!className.equals(this.currentAnalyzedClass)) {
               this.classStatuses.put(className, (byte)2);
            }

            return;
         case 2:
         case 3:
            return;
         default:
            if (className.equals(this.entryPoint)) {
               this.classStatuses.put(className, (byte)2);
            } else {
               ClassReader cls = this.classes.get(className);
               if (cls != null && cls.getAnnotations().get(StaticInit.class.getName()) == null) {
                  this.classStatuses.put(className, (byte)1);
                  String previousClass = this.currentAnalyzedClass;
                  this.currentAnalyzedClass = className;
                  MethodReader initializer = cls.getMethod(CLINIT);
                  boolean isStatic = true;
                  if (initializer != null) {
                     if (this.methodAnalysisDepth >= 250) {
                        isStatic = false;
                     } else {
                        ClassInitializerAnalysis.MethodInfo initializerInfo = this.analyzeMethod(initializer);
                        if (this.isDynamicInitializer(initializerInfo, className)) {
                           isStatic = false;
                        }
                     }
                  }

                  this.currentAnalyzedClass = previousClass;
                  if (this.classStatuses.get(className) == 1) {
                     this.classStatuses.put(className, (byte)(isStatic ? 3 : 2));
                     if (isStatic && initializer != null) {
                        this.order.add(className);
                     }
                  }
               } else {
                  this.classStatuses.put(className, (byte)3);
               }
            }
      }
   }

   private boolean isDynamicInitializer(ClassInitializerAnalysis.MethodInfo methodInfo, String className) {
      if (!methodInfo.anyFieldModified && !methodInfo.complete) {
         if (methodInfo.classesWithModifiedFields != null) {
            for (String affectedClass : methodInfo.classesWithModifiedFields) {
               if (!affectedClass.equals(className)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private ClassInitializerAnalysis.MethodInfo analyzeMethod(MethodReader method) {
      this.methodAnalysisDepth++;
      ClassInitializerAnalysis.MethodInfo methodInfo = this.methodInfoMap.get(method.getReference());
      if (methodInfo == null) {
         methodInfo = new ClassInitializerAnalysis.MethodInfo(method.getReference());
         this.methodInfoMap.put(method.getReference(), methodInfo);
         String currentClass = method.getDescriptor().equals(CLINIT) ? method.getOwnerName() : null;
         ClassInitializerAnalysis.InstructionAnalyzer reader = new ClassInitializerAnalysis.InstructionAnalyzer(currentClass, methodInfo);
         ProgramReader program = method.getProgram();
         if (program == null) {
            methodInfo.anyFieldModified = this.hasSideEffects(method);
         } else {
            for (BasicBlockReader block : program.getBasicBlocks()) {
               block.readAllInstructions(reader);
            }
         }

         if (method.hasModifier(ElementModifier.SYNCHRONIZED)) {
            reader.initClass("java.lang.Thread");
         }

         methodInfo.complete = true;
      }

      this.methodAnalysisDepth--;
      return methodInfo;
   }

   private boolean hasSideEffects(MethodReader method) {
      if (method.hasModifier(ElementModifier.ABSTRACT)) {
         return false;
      }

      if (method.getAnnotations().get(NoSideEffects.class.getName()) != null) {
         return false;
      }

      ClassReader containingClass = this.classes.get(method.getOwnerName());
      return containingClass.getAnnotations().get(NoSideEffects.class.getName()) == null;
   }

   class InstructionAnalyzer extends AbstractInstructionReader {
      String currentClass;
      ClassInitializerAnalysis.MethodInfo methodInfo;
      MethodDependencyInfo methodDep;

      InstructionAnalyzer(String currentClass, ClassInitializerAnalysis.MethodInfo methodInfo) {
         this.currentClass = currentClass;
         this.methodInfo = methodInfo;
         this.methodDep = ClassInitializerAnalysis.this.dependencyInfo.getMethod(methodInfo.method);
      }

      @Override
      public void stringConstant(VariableReader receiver, String cst) {
         this.analyzeInitializer("java.lang.String");
      }

      @Override
      public void create(VariableReader receiver, String type) {
         this.analyzeInitializer(type);
      }

      @Override
      public void getField(VariableReader receiver, VariableReader instance, FieldReference field, ValueType fieldType) {
         if (instance == null) {
            this.touchField(field);
         }
      }

      @Override
      public void putField(VariableReader instance, FieldReference field, VariableReader value, ValueType fieldType) {
         if (instance == null) {
            ClassReader cls = ClassInitializerAnalysis.this.classes.get(field.getClassName());
            if (cls != null) {
               FieldReader fieldReader = cls.getField(field.getFieldName());
               if (fieldReader != null && fieldReader.hasModifier(ElementModifier.FINAL)) {
                  return;
               }
            }

            this.touchField(field);
         }
      }

      private void touchField(FieldReference field) {
         this.analyzeInitializer(field.getClassName());
         if (!this.methodInfo.anyFieldModified && !field.getClassName().equals(this.currentClass)) {
            if (this.methodInfo.classesWithModifiedFields == null) {
               this.methodInfo.classesWithModifiedFields = new HashSet<>();
            }

            this.methodInfo.classesWithModifiedFields.add(field.getClassName());
         }
      }

      @Override
      public void invoke(
         VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments, InvocationType type
      ) {
         if (type == InvocationType.VIRTUAL) {
            ValueDependencyInfo instanceDep = this.methodDep.getVariable(instance.getIndex());
            ValueType[] types = instanceDep.getTypes();

            for (MethodReference implementation : Devirtualization.implementations(
               ClassInitializerAnalysis.this.hierarchy, ClassInitializerAnalysis.this.dependencyInfo, types, method
            )) {
               this.invokeMethod(implementation);
            }
         } else {
            this.analyzeInitializer(method.getClassName());
            this.invokeMethod(method);
         }
      }

      private void invokeMethod(MethodReference method) {
         ClassReader cls = ClassInitializerAnalysis.this.classes.get(method.getClassName());
         if (cls != null) {
            if (ClassInitializerAnalysis.this.methodAnalysisDepth >= 250) {
               this.methodInfo.anyFieldModified = true;
               this.methodInfo.classesWithModifiedFields = null;
            } else {
               MethodReader methodReader = cls.getMethod(method.getDescriptor());
               if (methodReader != null) {
                  this.analyzeCalledMethod(ClassInitializerAnalysis.this.analyzeMethod(methodReader));
               }
            }
         }
      }

      @Override
      public void initClass(String className) {
         this.analyzeInitializer(className);
      }

      @Override
      public void monitorEnter(VariableReader objectRef) {
         this.initClass("java.lang.Thread");
      }

      @Override
      public void monitorExit(VariableReader objectRef) {
         this.initClass("java.lang.Thread");
      }

      void analyzeInitializer(String className) {
         if (!className.equals(this.currentClass) && !className.equals(this.methodInfo.method.getClassName())) {
            ClassInitializerAnalysis.this.analyze(className);
            if (ClassInitializerAnalysis.this.isDynamicInitializer(className)) {
               this.methodInfo.anyFieldModified = true;
            }
         }
      }

      private void analyzeCalledMethod(ClassInitializerAnalysis.MethodInfo calledMethod) {
         if (!this.methodInfo.anyFieldModified) {
            if (calledMethod.anyFieldModified || calledMethod.complete) {
               this.methodInfo.anyFieldModified = true;
               this.methodInfo.classesWithModifiedFields = null;
            } else if (calledMethod.classesWithModifiedFields != null) {
               for (String className : calledMethod.classesWithModifiedFields) {
                  if (!className.equals(this.currentClass)) {
                     if (this.methodInfo.classesWithModifiedFields == null) {
                        this.methodInfo.classesWithModifiedFields = new HashSet<>();
                     }

                     this.methodInfo.classesWithModifiedFields.add(className);
                  }
               }
            }
         }
      }
   }

   static class MethodInfo {
      MethodReference method;
      boolean complete;
      Set<String> classesWithModifiedFields;
      boolean anyFieldModified;

      MethodInfo(MethodReference method) {
         this.method = method;
      }
   }
}
