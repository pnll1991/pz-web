package org.teavm.model.emit;

import org.teavm.model.ValueType;
import org.teavm.model.instructions.IntegerSubtype;

public class StringBuilderEmitter {
   private ProgramEmitter pe;
   private ValueEmitter sb;

   StringBuilderEmitter(ProgramEmitter pe) {
      this.pe = pe;
      this.sb = pe.construct(StringBuilder.class);
   }

   public StringBuilderEmitter append(ValueEmitter value) {
      if (value.getType() instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)value.getType()).getKind()) {
            case BYTE:
               value = value.castToInteger(IntegerSubtype.BYTE);
               break;
            case SHORT:
               value = value.castToInteger(IntegerSubtype.SHORT);
         }
      } else if (!value.getType().isObject("java.lang.String")) {
         value = value.cast(Object.class);
      }

      this.sb = this.sb.invokeVirtual("append", StringBuilder.class, value);
      return this;
   }

   public StringBuilderEmitter append(String value) {
      this.sb = this.sb.invokeVirtual("append", StringBuilder.class, this.pe.constant(value));
      return this;
   }

   public StringBuilderEmitter append(int value) {
      this.sb = this.sb.invokeVirtual("append", StringBuilder.class, this.pe.constant(value));
      return this;
   }

   public StringBuilderEmitter append(long value) {
      this.sb = this.sb.invokeVirtual("append", StringBuilder.class, this.pe.constant(value));
      return this;
   }

   public StringBuilderEmitter append(float value) {
      this.sb = this.sb.invokeVirtual("append", StringBuilder.class, this.pe.constant(value));
      return this;
   }

   public StringBuilderEmitter append(double value) {
      this.sb = this.sb.invokeVirtual("append", StringBuilder.class, this.pe.constant(value));
      return this;
   }

   public ValueEmitter build() {
      return this.sb.invokeVirtual("toString", String.class);
   }
}
