package org.teavm.cache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;
import org.teavm.model.FieldReference;
import org.teavm.model.ReferenceCache;
import org.teavm.model.ValueType;

public class AnnotationIO {
   private ReferenceCache referenceCache;
   private SymbolTable symbolTable;

   public AnnotationIO(ReferenceCache referenceCache, SymbolTable symbolTable) {
      this.referenceCache = referenceCache;
      this.symbolTable = symbolTable;
   }

   public void writeAnnotations(VarDataOutput output, AnnotationContainerReader annotations) throws IOException {
      List<AnnotationReader> annotationList = new ArrayList<>();

      for (AnnotationReader annot : annotations.all()) {
         annotationList.add(annot);
      }

      output.writeUnsigned(annotationList.size());

      for (AnnotationReader annot : annotationList) {
         this.writeAnnotation(output, annot);
      }
   }

   public CachedAnnotations readAnnotations(VarDataInput input) throws IOException {
      Map<String, CachedAnnotation> annotations = new HashMap<>();
      int annotCount = input.readUnsigned();

      for (int i = 0; i < annotCount; i++) {
         CachedAnnotation annot = this.readAnnotation(input);
         annotations.put(annot.type, annot);
      }

      return new CachedAnnotations(annotations);
   }

   private void writeAnnotation(VarDataOutput output, AnnotationReader annotation) throws IOException {
      output.writeUnsigned(this.symbolTable.lookup(annotation.getType()));
      int fieldCount = 0;

      for (String field : annotation.getAvailableFields()) {
         fieldCount++;
      }

      output.writeUnsigned(fieldCount);

      for (String field : annotation.getAvailableFields()) {
         output.writeUnsigned(this.symbolTable.lookup(field));
         this.writeAnnotationValue(output, annotation.getValue(field));
      }
   }

   private CachedAnnotation readAnnotation(VarDataInput input) throws IOException {
      CachedAnnotation annotation = new CachedAnnotation();
      annotation.type = this.referenceCache.getCached(this.symbolTable.at(input.readUnsigned()));
      int valueCount = input.readUnsigned();
      Map<String, AnnotationValue> fields = new HashMap<>();

      for (int i = 0; i < valueCount; i++) {
         String name = this.referenceCache.getCached(this.symbolTable.at(input.readUnsigned()));
         AnnotationValue value = this.readAnnotationValue(input);
         fields.put(name, value);
      }

      annotation.fields = fields;
      return annotation;
   }

   public void writeAnnotationValue(VarDataOutput output, AnnotationValue value) throws IOException {
      output.writeUnsigned(value.getType());
      switch (value.getType()) {
         case 0:
            output.writeUnsigned(value.getBoolean() ? 1 : 0);
            break;
         case 1:
            output.writeSigned(value.getByte());
            break;
         case 2:
            output.writeSigned(value.getShort());
            break;
         case 3:
            output.writeSigned(value.getInt());
            break;
         case 4:
            output.writeSigned(value.getLong());
            break;
         case 5:
            output.writeFloat(value.getFloat());
            break;
         case 6:
            output.writeDouble(value.getDouble());
            break;
         case 7:
            output.write(value.getString());
            break;
         case 8:
            output.writeUnsigned(this.symbolTable.lookup(value.getJavaClass().toString()));
            break;
         case 9:
            List<AnnotationValue> list = value.getList();
            output.writeUnsigned(list.size());

            for (AnnotationValue item : list) {
               this.writeAnnotationValue(output, item);
            }
            break;
         case 10:
            output.writeUnsigned(this.symbolTable.lookup(value.getEnumValue().getClassName()));
            output.writeUnsigned(this.symbolTable.lookup(value.getEnumValue().getFieldName()));
            break;
         case 11:
            this.writeAnnotation(output, value.getAnnotation());
            break;
         case 12:
            output.writeSigned(value.getChar());
      }
   }

   public AnnotationValue readAnnotationValue(VarDataInput input) throws IOException {
      int type = input.readUnsigned();
      switch (type) {
         case 0:
            return new AnnotationValue(input.readUnsigned() != 0);
         case 1:
            return new AnnotationValue((byte)input.readSigned());
         case 2:
            return new AnnotationValue((short)input.readSigned());
         case 3:
            return new AnnotationValue(input.readSigned());
         case 4:
            return new AnnotationValue(input.readSignedLong());
         case 5:
            return new AnnotationValue(input.readFloat());
         case 6:
            return new AnnotationValue(input.readDouble());
         case 7:
            return new AnnotationValue(input.read());
         case 8:
            return new AnnotationValue(this.referenceCache.getCached(ValueType.parse(this.symbolTable.at(input.readUnsigned()))));
         case 9:
            List<AnnotationValue> list = new ArrayList<>();
            int sz = input.readUnsigned();

            for (int i = 0; i < sz; i++) {
               list.add(this.readAnnotationValue(input));
            }

            return new AnnotationValue(list);
         case 10:
            String className = this.referenceCache.getCached(this.symbolTable.at(input.readUnsigned()));
            String fieldName = this.referenceCache.getCached(this.symbolTable.at(input.readUnsigned()));
            return new AnnotationValue(this.referenceCache.getCached(new FieldReference(className, fieldName)));
         case 11:
            return new AnnotationValue(this.readAnnotation(input));
         case 12:
            return new AnnotationValue((char)input.readUnsigned());
         default:
            throw new RuntimeException("Unexpected annotation value type: " + type);
      }
   }
}
