package org.teavm.dependency;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.model.instructions.InvocationType;

class ClassClosureAnalyzer extends AbstractInstructionReader {
   private ClassReaderSource classSource;
   private Set<String> result = new LinkedHashSet<>();

   ClassClosureAnalyzer(ClassReaderSource classSource) {
      this.classSource = classSource;
   }

   static Set<? extends String> build(ClassReaderSource classSource, Collection<? extends String> initialClasses) {
      ClassClosureAnalyzer analyzer = new ClassClosureAnalyzer(classSource);

      for (String className : initialClasses) {
         analyzer.build(className);
      }

      return analyzer.result;
   }

   void build(String className) {
      if (this.result.add(className)) {
         ClassReader cls = this.classSource.get(className);
         if (cls != null) {
            if (cls.getParent() != null) {
               this.build(cls.getParent());
            }

            for (String itf : cls.getInterfaces()) {
               this.build(itf);
            }

            for (FieldReader field : cls.getFields()) {
               this.build(field.getType());
            }

            for (MethodReader method : cls.getMethods()) {
               this.build(method.getResultType());

               for (ValueType paramType : method.getParameterTypes()) {
                  this.build(paramType);
               }

               if (method.getProgram() != null) {
                  for (BasicBlockReader block : method.getProgram().getBasicBlocks()) {
                     block.readAllInstructions(this);
                  }
               }
            }
         }
      }
   }

   void build(ValueType type) {
      while (type instanceof ValueType.Array) {
         type = ((ValueType.Array)type).getItemType();
      }

      if (type instanceof ValueType.Object) {
         this.build(((ValueType.Object)type).getClassName());
      }
   }

   @Override
   public void classConstant(VariableReader receiver, ValueType cst) {
      this.build(cst);
   }

   @Override
   public void cast(VariableReader receiver, VariableReader value, ValueType targetType, boolean weak) {
      this.build(targetType);
   }

   @Override
   public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
      this.build(itemType);
   }

   @Override
   public void createArray(VariableReader receiver, ValueType itemType, List<? extends VariableReader> dimensions) {
      this.build(itemType);
   }

   @Override
   public void getField(VariableReader receiver, VariableReader instance, FieldReference field, ValueType fieldType) {
      this.build(fieldType);
   }

   @Override
   public void putField(VariableReader instance, FieldReference field, VariableReader value, ValueType fieldType) {
      this.build(fieldType);
   }

   @Override
   public void invoke(VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments, InvocationType type) {
      this.build(method.getReturnType());

      for (ValueType paramType : method.getParameterTypes()) {
         this.build(paramType);
      }
   }

   @Override
   public void isInstance(VariableReader receiver, VariableReader value, ValueType type) {
      this.build(type);
   }
}
