package org.teavm.model.optimization;

import java.util.Objects;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.BaseTypeInference;
import org.teavm.model.instructions.InvokeInstruction;

public class SystemArrayCopyOptimization implements MethodOptimization {
   private static final MethodReference ARRAY_COPY_METHOD = new MethodReference(
      System.class, "arraycopy", Object.class, int.class, Object.class, int.class, int.class, void.class
   );
   private static final MethodReference FAST_ARRAY_COPY_METHOD = new MethodReference(
      System.class, "fastArraycopy", Object.class, int.class, Object.class, int.class, int.class, void.class
   );
   private static final ValueType DEFAULT_TYPE = ValueType.object("java.lang.Object");

   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      SystemArrayCopyOptimization.TypeInference typeInference = new SystemArrayCopyOptimization.TypeInference(program, context.getMethod().getReference());
      boolean somethingChanged = false;

      for (BasicBlock block : program.getBasicBlocks()) {
         for (Instruction instruction : block) {
            if (instruction instanceof InvokeInstruction invoke) {
               MethodReference method = invoke.getMethod();
               if (method.equals(ARRAY_COPY_METHOD)) {
                  ValueType sourceType = typeInference.typeOf(invoke.getArguments().get(0));
                  ValueType destType = typeInference.typeOf(invoke.getArguments().get(2));
                  if (sourceType instanceof ValueType.Array
                     && destType instanceof ValueType.Array
                     && (sourceType.equals(destType) || context.getHierarchy().isSuperType(destType, sourceType, false))) {
                     invoke.setMethod(FAST_ARRAY_COPY_METHOD);
                     somethingChanged = true;
                  }
               }
            }
         }
      }

      return somethingChanged;
   }

   private static class TypeInference extends BaseTypeInference<ValueType> {
      TypeInference(Program program, MethodReference reference) {
         super(program, reference);
      }

      public ValueType merge(ValueType a, ValueType b) {
         return !Objects.equals(a, b) ? SystemArrayCopyOptimization.DEFAULT_TYPE : a;
      }

      public ValueType elementType(ValueType valueType) {
         return valueType instanceof ValueType.Array ? ((ValueType.Array)valueType).getItemType() : SystemArrayCopyOptimization.DEFAULT_TYPE;
      }

      public ValueType nullType() {
         return SystemArrayCopyOptimization.DEFAULT_TYPE;
      }

      public ValueType mapType(ValueType type) {
         return type instanceof ValueType.Array ? type : SystemArrayCopyOptimization.DEFAULT_TYPE;
      }
   }
}
