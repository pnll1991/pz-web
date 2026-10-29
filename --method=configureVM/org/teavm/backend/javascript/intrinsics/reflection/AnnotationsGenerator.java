package org.teavm.backend.javascript.intrinsics.reflection;

import java.util.List;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.rendering.RenderingUtil;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class AnnotationsGenerator {
   private AnnotationsGenerator() {
   }

   public static void generate(SourceWriter writer, ClassReaderSource classes, AnnotationReader annot) {
      String implName = annot.getType() + "$$_impl";
      String dataName = annot.getType() + "$$_nativeData";
      MethodReference ctor = new MethodReference(implName, "create", ValueType.object(dataName), ValueType.object(implName));
      writer.append('[').appendMethod(ctor);
      generateValues(writer, classes, annot, true);
      writer.append(']');
   }

   private static void generateValues(SourceWriter writer, ClassReaderSource classes, AnnotationReader annot, boolean needsComma) {
      ClassReader cls = classes.get(annot.getType());
      if (cls != null) {
         for (MethodReader method : cls.getMethods()) {
            if (needsComma) {
               writer.append(',').ws();
            }

            needsComma = true;
            AnnotationValue value = annot.getValue(method.getName());
            if (value == null) {
               value = method.getAnnotationDefault();
            }

            generateValue(writer, classes, method.getResultType(), value);
         }
      }
   }

   private static void generateValue(SourceWriter writer, ClassReaderSource classes, ValueType type, AnnotationValue value) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
               writer.append(value.getBoolean() ? "1" : "0");
               break;
            case BYTE:
               writer.append(String.valueOf(value.getByte()));
               break;
            case SHORT:
               writer.append(String.valueOf(value.getShort()));
               break;
            case CHARACTER:
               writer.append(String.valueOf(value.getChar()));
               break;
            case INTEGER:
               writer.append(String.valueOf(value.getInt()));
               break;
            case LONG:
               RenderingUtil.appendLongConstant(writer, value.getLong());
               break;
            case FLOAT:
               writer.append(String.valueOf(value.getFloat()));
               break;
            case DOUBLE:
               writer.append(String.valueOf(value.getDouble()));
         }
      } else if (type instanceof ValueType.Object) {
         String className = ((ValueType.Object)type).getClassName();
         switch (className) {
            case "java.lang.String":
               writer.append("\"").append(RenderingUtil.escapeString(value.getString())).append("\"");
               break;
            case "java.lang.Class":
               RenderingUtil.typeToClsString(writer, value.getJavaClass());
               break;
            default:
               ClassReader cls = classes.get(className);
               if (cls == null) {
                  writer.append("null");
               } else if (cls.hasModifier(ElementModifier.ENUM)) {
                  int index = 0;

                  for (FieldReader field : cls.getFields()) {
                     if (field.hasModifier(ElementModifier.STATIC) && field.hasModifier(ElementModifier.ENUM)) {
                        if (field.getName().equals(value.getEnumValue().getFieldName())) {
                           break;
                        }

                        index++;
                     }
                  }

                  writer.append(String.valueOf(index));
               } else {
                  writer.append('[');
                  generateValues(writer, classes, value.getAnnotation(), false);
                  writer.append(']');
               }
         }
      } else if (type instanceof ValueType.Array) {
         writer.append('[');
         ValueType itemType = ((ValueType.Array)type).getItemType();
         List<AnnotationValue> arrayValue = value.getList();
         boolean first = true;

         for (AnnotationValue item : arrayValue) {
            if (!first) {
               writer.append(",").ws();
            }

            first = false;
            generateValue(writer, classes, itemType, item);
         }

         writer.append(']');
      }
   }
}
