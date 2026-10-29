package org.teavm.backend.wasm.debug.info;

import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import org.teavm.hppc.IntObjectHashMap;
import org.teavm.hppc.IntObjectMap;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;

public abstract class ClassLayoutInfo {
   private IntObjectMap<TypeLayout> layoutByAddress;

   public abstract List<? extends TypeLayout> types();

   public TypeLayout find(int address) {
      if (this.layoutByAddress == null) {
         this.layoutByAddress = new IntObjectHashMap();

         for (TypeLayout typeLayout : this.types()) {
            this.layoutByAddress.put(typeLayout.address(), typeLayout);
         }
      }

      return (TypeLayout)this.layoutByAddress.get(address);
   }

   public void dump(PrintStream out) {
      ObjectIntHashMap<TypeLayout> indexes = new ObjectIntHashMap();

      for (int i = 0; i < this.types().size(); i++) {
         indexes.put(this.types().get(i), i);
      }

      for (int i = 0; i < this.types().size(); i++) {
         out.print("#" + i + ": ");
         TypeLayout type = this.types().get(i);
         out.println(type.kind().name().toLowerCase());
         out.println("  address: " + Integer.toHexString(type.address()));
         switch (type.kind()) {
            case CLASS:
               dumpClass(out, indexes, (ClassLayout)type);
               break;
            case INTERFACE:
               dumpInterface(out, (InterfaceLayout)type);
               break;
            case ARRAY:
               dumpArray(out, indexes, (ArrayLayout)type);
               break;
            case PRIMITIVE:
               dumpPrimitive(out, (PrimitiveLayout)type);
         }
      }
   }

   private static void dumpClass(PrintStream out, ObjectIntMap<TypeLayout> indexes, ClassLayout cls) {
      out.println("  name: " + cls.classRef().fullName());
      out.println("  size: " + cls.size());
      if (cls.superclass() != null) {
         out.println("  superclass: #" + indexes.get(cls.superclass()));
      }

      if (!cls.staticFields().isEmpty()) {
         out.println("  static fields:");
         dumpFields(out, cls.staticFields());
      }

      if (!cls.instanceFields().isEmpty()) {
         out.println("  instance fields:");
         dumpFields(out, cls.instanceFields());
      }
   }

   private static void dumpFields(PrintStream out, Collection<? extends FieldInfo> fields) {
      for (FieldInfo field : fields) {
         out.println("    " + field.name() + ": ");
         out.println("      offset: " + Integer.toHexString(field.address()));
         out.println("      type: " + field.type().name().toLowerCase());
      }
   }

   private static void dumpInterface(PrintStream out, InterfaceLayout cls) {
      out.println("  name: " + cls.classRef().fullName());
   }

   private static void dumpArray(PrintStream out, ObjectIntMap<TypeLayout> indexes, ArrayLayout array) {
      out.println("  element: #" + indexes.get(array.elementType()));
   }

   private static void dumpPrimitive(PrintStream out, PrimitiveLayout primitive) {
      out.println("  primitive type: " + primitive.primitiveType().name().toLowerCase());
   }
}
