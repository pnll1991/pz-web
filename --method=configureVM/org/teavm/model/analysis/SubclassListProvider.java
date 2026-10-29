package org.teavm.model.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;

class SubclassListProvider {
   private Map<String, SubclassListProvider.ClassInfo> classes = new HashMap<>();
   private Map<MethodDescriptor, List<MethodReference>> methodImplementations = new HashMap<>();
   private int limit;

   SubclassListProvider(ClassReaderSource classSource, Iterable<? extends String> classNames, int limit) {
      this.limit = limit;

      for (String className : classNames) {
         this.registerClass(classSource, className);
      }
   }

   private SubclassListProvider.ClassInfo registerClass(ClassReaderSource classSource, String className) {
      SubclassListProvider.ClassInfo classInfo = this.classes.get(className);
      if (classInfo == null) {
         classInfo = new SubclassListProvider.ClassInfo();
         this.classes.put(className, classInfo);
         ClassReader cls = classSource.get(className);
         if (cls != null) {
            if (!cls.hasModifier(ElementModifier.INTERFACE) && !cls.hasModifier(ElementModifier.ABSTRACT)) {
               classInfo.concrete = true;
            }

            this.increaseClassCount(classSource, className, new HashSet<>(), classInfo.concrete);
            if (cls.getParent() != null) {
               SubclassListProvider.ClassInfo parentInfo = this.registerClass(classSource, cls.getParent());
               if (parentInfo.directSubclasses == null) {
                  parentInfo.directSubclasses = new ArrayList<>();
               }

               parentInfo.directSubclasses.add(className);
            }

            for (String itf : cls.getInterfaces()) {
               SubclassListProvider.ClassInfo parentInfo = this.registerClass(classSource, itf);
               if (parentInfo.directSubclasses == null) {
                  parentInfo.directSubclasses = new ArrayList<>();
               }

               parentInfo.directSubclasses.add(className);
            }

            for (MethodReader method : cls.getMethods()) {
               if (!method.hasModifier(ElementModifier.STATIC) && !method.hasModifier(ElementModifier.ABSTRACT) && method.getLevel() != AccessLevel.PRIVATE) {
                  List<MethodReference> implementations = this.methodImplementations.get(method.getDescriptor());
                  if (implementations == null) {
                     implementations = new ArrayList<>();
                     this.methodImplementations.put(method.getDescriptor(), implementations);
                  }

                  implementations.add(method.getReference());
               }
            }
         }
      }

      return classInfo;
   }

   private void increaseClassCount(ClassReaderSource classSource, String className, Set<String> visited, boolean concrete) {
      if (visited.add(className)) {
         SubclassListProvider.ClassInfo classInfo = this.registerClass(classSource, className);
         if (concrete && classInfo.concreteCount <= this.limit || classInfo.count <= this.limit) {
            classInfo.count++;
            if (concrete) {
               classInfo.concreteCount++;
            }

            ClassReader cls = classSource.get(className);
            if (cls != null) {
               if (cls.getParent() != null) {
                  this.increaseClassCount(classSource, cls.getParent(), visited, concrete);
               }

               for (String itf : cls.getInterfaces()) {
                  this.increaseClassCount(classSource, itf, visited, concrete);
               }
            }
         }
      }
   }

   List<? extends String> getSubclasses(String className, boolean includeAbstract) {
      SubclassListProvider.ClassInfo classInfo = this.classes.get(className);
      if (classInfo == null) {
         return null;
      }

      if (includeAbstract) {
         if (classInfo.count > this.limit) {
            return null;
         }
      } else if (classInfo.concreteCount > this.limit) {
         return null;
      }

      String[] result = new String[includeAbstract ? classInfo.count : classInfo.concreteCount];
      this.collectSubclasses(className, result, 0, new HashSet<>(), includeAbstract);
      return Arrays.asList(result);
   }

   List<? extends MethodReference> getMethods(MethodDescriptor descriptor) {
      return this.methodImplementations.get(descriptor);
   }

   private int collectSubclasses(String className, String[] consumer, int index, Set<String> visited, boolean includeAbstract) {
      if (!visited.add(className)) {
         return index;
      }

      SubclassListProvider.ClassInfo classInfo = this.classes.get(className);
      if (classInfo == null) {
         return index;
      }

      if (includeAbstract || classInfo.concrete) {
         consumer[index++] = className;
      }

      if (classInfo.directSubclasses != null) {
         for (String subclassName : classInfo.directSubclasses) {
            index = this.collectSubclasses(subclassName, consumer, index, visited, includeAbstract);
         }
      }

      return index;
   }

   static class ClassInfo {
      int count;
      int concreteCount;
      boolean concrete;
      List<String> directSubclasses;
   }
}
