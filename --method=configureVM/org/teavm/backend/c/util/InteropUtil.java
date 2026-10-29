package org.teavm.backend.c.util;

import org.teavm.backend.c.generate.CodeWriter;
import org.teavm.backend.c.generate.IncludeManager;
import org.teavm.backend.c.intrinsic.RuntimeInclude;
import org.teavm.interop.c.Include;
import org.teavm.interop.c.Name;
import org.teavm.interop.c.Native;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;
import org.teavm.model.ClassReader;
import org.teavm.model.FieldReader;

public final class InteropUtil {
   private InteropUtil() {
   }

   public static boolean isNative(ClassReader cls) {
      return cls.getAnnotations().get(Native.class.getName()) != null;
   }

   public static void printNativeReference(CodeWriter writer, ClassReader cls) {
      AnnotationReader annot = cls.getAnnotations().get(Native.class.getName());
      if (annot != null) {
         AnnotationValue fieldValue = annot.getValue("structKeyword");
         if (fieldValue != null && fieldValue.getBoolean()) {
            writer.print("struct ");
         }
      }

      writer.print(getNativeName(cls));
   }

   private static String getNativeName(ClassReader cls) {
      AnnotationReader nameAnnot = cls.getAnnotations().get(Name.class.getName());
      if (nameAnnot != null) {
         return nameAnnot.getValue("value").getString();
      }

      int index = Math.max(cls.getName().lastIndexOf(46), cls.getName().lastIndexOf(36));
      return cls.getName().substring(index + 1);
   }

   public static String getNativeName(ClassReader cls, String fieldName) {
      FieldReader field = cls.getField(fieldName);
      if (field != null) {
         AnnotationReader nameAnnot = field.getAnnotations().get(Name.class.getName());
         if (nameAnnot != null) {
            return nameAnnot.getValue("value").getString();
         }
      }

      return fieldName;
   }

   public static void processInclude(AnnotationContainerReader container, IncludeManager includes) {
      AnnotationReader annot = container.get(RuntimeInclude.class.getName());
      if (annot != null) {
         includes.includePath(annot.getValue("value").getString());
      } else {
         annot = container.get(Include.class.getName());
         if (annot != null) {
            String includeString = annot.getValue("value").getString();
            AnnotationValue systemValue = annot.getValue("isSystem");
            if (systemValue != null && !systemValue.getBoolean()) {
               includeString = "\"" + includeString + "\"";
            } else {
               includeString = "<" + includeString + ">";
            }

            includes.addInclude(includeString);
         }
      }
   }
}
