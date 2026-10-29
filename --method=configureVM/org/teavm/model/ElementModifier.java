package org.teavm.model;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.util.Set;
import org.teavm.runtime.reflect.ProxyImplementor;

public enum ElementModifier {
   ABSTRACT,
   INTERFACE,
   FINAL,
   ENUM,
   ANNOTATION,
   SYNTHETIC,
   BRIDGE,
   DEPRECATED,
   NATIVE,
   STATIC,
   STRICT,
   SYNCHRONIZED,
   TRANSIENT,
   VARARGS,
   VOLATILE,
   RECORD;

   public static int pack(Set<ElementModifier> elementModifiers) {
      ElementModifier[] knownModifiers = values();
      int value = 0;
      int bit = 1;

      for (int i = 0; i < knownModifiers.length; i++) {
         ElementModifier modifier = knownModifiers[i];
         if (elementModifiers.contains(modifier)) {
            value |= bit;
         }

         bit <<= 1;
      }

      return value;
   }

   public static int encodeModifiers(ClassReader cls) {
      int modifiers = asModifiersInfo(cls.readModifiers(), cls.getLevel());
      if (cls.hasModifier(ANNOTATION)) {
         AnnotationReader retention = cls.getAnnotations().get(Retention.class.getName());
         if (retention != null
            && retention.getValue("value").getEnumValue().getFieldName().equals("RUNTIME")
            && cls.getAnnotations().get(Inherited.class.getName()) != null) {
            modifiers |= 16384;
         }
      }

      if (cls.getAnnotations().get(ProxyImplementor.class.getName()) != null) {
         modifiers |= 262144;
      }

      return modifiers;
   }

   public static int asModifiersInfo(Set<ElementModifier> elementModifiers, AccessLevel level) {
      int modifiers = 0;

      modifiers = switch (level) {
         case PRIVATE -> 2;
         case PROTECTED -> 4;
         case PUBLIC -> 1;
      };
      if (elementModifiers.contains(STATIC)) {
         modifiers |= 8;
      }

      if (elementModifiers.contains(BRIDGE)) {
         modifiers |= 131072;
      }

      if (elementModifiers.contains(FINAL)) {
         modifiers |= 16;
      }

      if (elementModifiers.contains(SYNCHRONIZED)) {
         modifiers |= 32;
      }

      if (elementModifiers.contains(VOLATILE)) {
         modifiers |= 64;
      }

      if (elementModifiers.contains(TRANSIENT)) {
         modifiers |= 128;
      }

      if (elementModifiers.contains(NATIVE)) {
         modifiers |= 256;
      }

      if (elementModifiers.contains(INTERFACE)) {
         modifiers |= 512;
      }

      if (elementModifiers.contains(ABSTRACT)) {
         modifiers |= 1024;
      }

      if (elementModifiers.contains(STRICT)) {
         modifiers |= 2048;
      }

      if (elementModifiers.contains(VARARGS)) {
         modifiers |= 4096;
      }

      if (elementModifiers.contains(ANNOTATION)) {
         modifiers |= 8192;
      }

      if (elementModifiers.contains(SYNTHETIC)) {
         modifiers |= 32768;
      }

      if (elementModifiers.contains(ENUM)) {
         modifiers |= 65536;
      }

      return modifiers;
   }
}
