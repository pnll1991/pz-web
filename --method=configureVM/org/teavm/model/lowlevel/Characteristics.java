package org.teavm.model.lowlevel;

import org.teavm.hppc.ObjectByteHashMap;
import org.teavm.hppc.ObjectByteMap;
import org.teavm.interop.Address;
import org.teavm.interop.Function;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Structure;
import org.teavm.interop.Unmanaged;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.ReflectionInfo;

public class Characteristics {
   private ClassReaderSource classSource;
   private ObjectByteMap<String> isStructure = new ObjectByteHashMap();
   private ObjectByteMap<String> isReflectionSpecial = new ObjectByteHashMap();
   private ObjectByteMap<String> isStaticInit = new ObjectByteHashMap();
   private ObjectByteMap<String> isFunction = new ObjectByteHashMap();
   private ObjectByteMap<String> isResource = new ObjectByteHashMap();
   private ObjectByteMap<MethodReference> isManaged = new ObjectByteHashMap();

   public Characteristics(ClassReaderSource classSource) {
      this.classSource = classSource;
   }

   public boolean isStructure(String className) {
      byte result = this.isStructure.getOrDefault(className, (byte)-1);
      if (result < 0) {
         if (className.equals(Structure.class.getName())) {
            result = 1;
         } else {
            ClassReader cls = this.classSource.get(className);
            if (cls != null && cls.getParent() != null) {
               result = (byte)(this.isStructure(cls.getParent()) ? 1 : 0);
            } else {
               result = 0;
            }
         }

         this.isStructure.put(className, result);
      }

      return result != 0;
   }

   public boolean isReflectionSpecial(String className) {
      byte result = this.isReflectionSpecial.getOrDefault(className, (byte)-1);
      if (result < 0) {
         if (className.equals(ReflectionInfo.class.getName())) {
            result = 1;
         } else {
            ClassReader cls = this.classSource.get(className);
            if (cls != null && cls.getParent() != null) {
               result = (byte)(this.isReflectionSpecial(cls.getParent()) ? 1 : 0);
            } else {
               result = 0;
            }
         }

         this.isReflectionSpecial.put(className, result);
      }

      return result != 0;
   }

   public boolean isResource(String className) {
      byte result = this.isResource.getOrDefault(className, (byte)-1);
      if (result < 0) {
         if (className.equals("org.teavm.platform.metadata.Resource")) {
            result = 1;
         } else {
            ClassReader cls = this.classSource.get(className);
            result = 0;
            if (cls != null) {
               if (cls.getParent() != null) {
                  result = (byte)(this.isResource(cls.getParent()) ? 1 : 0);
               }

               if (result == 0) {
                  for (String itf : cls.getInterfaces()) {
                     if (this.isResource(itf)) {
                        result = 1;
                        break;
                     }
                  }
               }
            } else {
               result = 0;
            }
         }

         this.isResource.put(className, result);
      }

      return result != 0;
   }

   public boolean isStaticInit(String className) {
      byte result = this.isStaticInit.getOrDefault(className, (byte)-1);
      if (result < 0) {
         ClassReader cls = this.classSource.get(className);
         result = (byte)(cls != null && cls.getAnnotations().get(StaticInit.class.getName()) != null ? 1 : 0);
         this.isStaticInit.put(className, result);
      }

      return result != 0;
   }

   public boolean isFunction(String className) {
      byte result = this.isFunction.getOrDefault(className, (byte)-1);
      if (result < 0) {
         if (className.equals(Function.class.getName())) {
            result = 1;
         } else {
            ClassReader cls = this.classSource.get(className);
            if (cls != null && cls.getParent() != null) {
               result = (byte)(this.isFunction(cls.getParent()) ? 1 : 0);
            } else {
               result = 0;
            }
         }

         this.isFunction.put(className, result);
      }

      return result != 0;
   }

   public boolean isManaged(String className) {
      return !this.isStructure(className) && !this.isFunction(className) && !this.isReflectionSpecial(className) && !className.equals(Address.class.getName());
   }

   public boolean isManaged(MethodReference methodReference) {
      byte result = this.isManaged.getOrDefault(methodReference, (byte)-1);
      if (result < 0) {
         result = (byte)(this.computeIsManaged(methodReference) ? 1 : 0);
         this.isManaged.put(methodReference, result);
      }

      return result != 0;
   }

   private boolean computeIsManaged(MethodReference methodReference) {
      MethodReader method = this.classSource.resolve(methodReference);
      if (method == null) {
         return true;
      }

      ClassReader cls = this.classSource.get(method.getOwnerName());
      return cls.getAnnotations().get(Unmanaged.class.getName()) != null ? false : method.getAnnotations().get(Unmanaged.class.getName()) == null;
   }
}
