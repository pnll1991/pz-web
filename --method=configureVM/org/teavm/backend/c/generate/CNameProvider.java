package org.teavm.backend.c.generate;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.teavm.backend.lowlevel.generate.LowLevelNameProvider;
import org.teavm.model.FieldReference;
import org.teavm.runtime.RuntimeArray;
import org.teavm.runtime.RuntimeClass;
import org.teavm.runtime.RuntimeObject;
import org.teavm.runtime.RuntimeReference;
import org.teavm.runtime.RuntimeReferenceQueue;

public class CNameProvider extends LowLevelNameProvider {
   private static final Set<? extends String> keywords = Collections.unmodifiableSet(
      new HashSet<>(
         Arrays.asList(
            "auto",
            "break",
            "case",
            "char",
            "const",
            "continue",
            "default",
            "do",
            "double",
            "else",
            "enum",
            "extern",
            "float",
            "for",
            "goto",
            "if",
            "inline",
            "int",
            "long",
            "register",
            "restrict",
            "return",
            "short",
            "signed",
            "sizeof",
            "static",
            "struct",
            "switch",
            "typedef",
            "union",
            "unsigned",
            "void",
            "volatile",
            "while"
         )
      )
   );

   public CNameProvider() {
      this.occupiedTopLevelNames.add("TeaVM_Object");
      this.occupiedTopLevelNames.add("TeaVM_Array");
      this.occupiedTopLevelNames.add("TeaVM_String");
      this.occupiedTopLevelNames.add("TeaVM_Class");
      this.occupiedTopLevelNames.add("TeaVM_Reference");
      this.occupiedTopLevelNames.add("TeaVM_ReferenceQueue");
      this.classNames.put(RuntimeObject.class.getName(), "TeaVM_Object");
      this.classNames.put(Object.class.getName(), "TeaVM_Object");
      this.classNames.put(String.class.getName(), "TeaVM_String");
      this.classNames.put(RuntimeClass.class.getName(), "TeaVM_Class");
      this.classNames.put(RuntimeArray.class.getName(), "TeaVM_Array");
      this.classNames.put(WeakReference.class.getName(), "TeaVM_Reference");
      this.classNames.put(ReferenceQueue.class.getName(), "TeaVM_ReferenceQueue");
      this.classNames.put(RuntimeReference.class.getName(), "TeaVM_Reference");
      this.classNames.put(RuntimeReferenceQueue.class.getName(), "TeaVM_ReferenceQueue");
      this.memberFieldNames.put(new FieldReference(RuntimeObject.class.getName(), "classReference"), "header");
      this.memberFieldNames.put(new FieldReference(RuntimeObject.class.getName(), "hashCode"), "hash");
      this.memberFieldNames.put(new FieldReference(RuntimeArray.class.getName(), "size"), "size");
      this.memberFieldNames.put(new FieldReference(String.class.getName(), "characters"), "characters");
      this.memberFieldNames.put(new FieldReference(String.class.getName(), "hashCode"), "hashCode");
      this.preserveFieldNames(
         RuntimeClass.class.getName(),
         "size",
         "flags",
         "tag",
         "modifiers",
         "name",
         "itemType",
         "arrayType",
         "isSupertypeOf",
         "init",
         "enumValues",
         "layout",
         "simpleName",
         "superinterfaceCount",
         "superinterfaces",
         "declaringClass",
         "enclosingClass"
      );
      this.memberFieldNames.put(new FieldReference(RuntimeClass.class.getName(), "parent"), "superclass");
      this.preserveFieldNames(RuntimeReference.class.getName(), "queue", "object", "next");
      this.preserveFieldNames(RuntimeReferenceQueue.class.getName(), "first", "last");
      this.occupiedClassNames.put(RuntimeObject.class.getName(), new HashSet<>(Arrays.asList("header")));
      this.occupiedClassNames.put(RuntimeArray.class.getName(), new HashSet<>(Arrays.asList("length")));
   }

   private void preserveFieldNames(String className, String... fieldNames) {
      for (String name : fieldNames) {
         this.memberFieldNames.put(new FieldReference(className, name), name);
      }
   }

   @Override
   protected Set<? extends String> getKeywords() {
      return keywords;
   }
}
