package org.teavm.backend.wasm.types;

import org.teavm.backend.wasm.WasmGCUtil;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReader;
import org.teavm.model.MethodReference;
import org.teavm.model.PrimitiveType;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.BaseTypeInference;

public class PreciseTypeInference extends BaseTypeInference<PreciseValueType> {
   public static final PreciseValueType OBJECT_TYPE = new PreciseValueType(ValueType.object("java.lang.Object"), false);
   private ClassHierarchy hierarchy;

   public PreciseTypeInference(Program program, MethodReference reference, ClassHierarchy hierarchy) {
      super(program, reference);
      this.hierarchy = hierarchy;
   }

   protected PreciseValueType mapType(ValueType type) {
      return new PreciseValueType(type, false);
   }

   protected PreciseValueType nullType() {
      return null;
   }

   protected PreciseValueType merge(PreciseValueType a, PreciseValueType b) {
      if (a == null) {
         return b;
      }

      if (b == null) {
         return a;
      }

      if (a.valueType instanceof ValueType.Primitive && b.valueType instanceof ValueType.Primitive) {
         if (a.valueType != b.valueType) {
            PrimitiveType firstKind = ((ValueType.Primitive)a.valueType).getKind();
            PrimitiveType secondKind = ((ValueType.Primitive)b.valueType).getKind();
            return this.isIntegerType(firstKind) && this.isIntegerType(secondKind) ? new PreciseValueType(ValueType.INTEGER, false) : OBJECT_TYPE;
         } else {
            return a;
         }
      } else if (a.valueType instanceof ValueType.Array) {
         if (b.valueType instanceof ValueType.Array) {
            ValueType p = ((ValueType.Array)a.valueType).getItemType();
            ValueType q = ((ValueType.Array)b.valueType).getItemType();
            if (p.equals(q)) {
               return a;
            } else if (!(p instanceof ValueType.Primitive) && !(q instanceof ValueType.Primitive)) {
               PreciseValueType itemType = this.merge(new PreciseValueType(p, false), new PreciseValueType(q, false));
               return new PreciseValueType(ValueType.arrayOf(itemType.valueType), a.isArrayUnwrap);
            } else {
               return OBJECT_TYPE;
            }
         } else {
            return OBJECT_TYPE;
         }
      } else {
         if (b.valueType instanceof ValueType.Array) {
            return OBJECT_TYPE;
         }

         if (a.valueType instanceof ValueType.Object) {
            if (b.valueType instanceof ValueType.Object) {
               String p = ((ValueType.Object)a.valueType).getClassName();
               String q = ((ValueType.Object)b.valueType).getClassName();
               if (p.equals(q)) {
                  return a;
               }

               ClassReader first = this.hierarchy.getClassSource().get(p);
               if (first == null) {
                  p = "java.lang.Object";
               }

               ClassReader second = this.hierarchy.getClassSource().get(q);
               if (second == null) {
                  q = "java.lang.Object";
               }

               if (this.hierarchy.isSuperType(p, q, false)) {
                  return a;
               }

               if (this.hierarchy.isSuperType(q, p, false)) {
                  return b;
               }

               ValueType.Object result = ValueType.object(WasmGCUtil.findCommonSuperclass(this.hierarchy, first, second));
               return new PreciseValueType(result, false);
            } else {
               return OBJECT_TYPE;
            }
         } else {
            return OBJECT_TYPE;
         }
      }
   }

   private boolean isIntegerType(PrimitiveType type) {
      switch (type) {
         case BOOLEAN:
         case BYTE:
         case SHORT:
         case CHARACTER:
         case INTEGER:
            return true;
         default:
            return false;
      }
   }

   protected PreciseValueType elementType(PreciseValueType valueType) {
      return new PreciseValueType(((ValueType.Array)valueType.valueType).getItemType(), false);
   }

   protected PreciseValueType arrayType(PreciseValueType preciseValueType) {
      return new PreciseValueType(ValueType.arrayOf(preciseValueType.valueType), false);
   }

   protected PreciseValueType arrayUnwrapType(PreciseValueType type) {
      return new PreciseValueType(type.valueType, true);
   }

   protected PreciseValueType arrayWrapType(PreciseValueType type) {
      return new PreciseValueType(type.valueType, false);
   }
}
