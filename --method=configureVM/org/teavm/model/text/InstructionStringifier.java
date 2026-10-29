package org.teavm.model.text;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHandle;
import org.teavm.model.MethodReference;
import org.teavm.model.ProgramReader;
import org.teavm.model.RuntimeConstant;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.CastIntegerDirection;
import org.teavm.model.instructions.InstructionReader;
import org.teavm.model.instructions.IntegerSubtype;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.SwitchTableEntryReader;

class InstructionStringifier implements InstructionReader {
   private TextLocation location;
   private StringBuilder sb;
   private String[] variableLabels;

   InstructionStringifier(StringBuilder sb, ProgramReader program) {
      this.sb = sb;
      this.variableLabels = new String[program.variableCount()];
      Set<String> occupiedLabels = new HashSet<>();

      for (int i = 0; i < program.variableCount(); i++) {
         VariableReader var = program.variableAt(i);
         if (var != null) {
            String suggestedName = var.getLabel() != null ? var.getLabel() : Integer.toString(i);
            if (!occupiedLabels.add(suggestedName)) {
               int suffix = 1;
               String base = suggestedName + "_";

               do {
                  suggestedName = base + suffix++;
               } while (!occupiedLabels.add(suggestedName));
            }

            this.variableLabels[i] = suggestedName;
         }
      }
   }

   public String getVariableLabel(int index) {
      return this.variableLabels[index];
   }

   public TextLocation getLocation() {
      return this.location;
   }

   @Override
   public void location(TextLocation location) {
      this.location = location;
   }

   @Override
   public void nop() {
      this.sb.append("nop");
   }

   InstructionStringifier append(String str) {
      this.sb.append(str);
      return this;
   }

   InstructionStringifier append(int value) {
      this.sb.append(value);
      return this;
   }

   InstructionStringifier append(char value) {
      this.sb.append(value);
      return this;
   }

   InstructionStringifier appendLocalVar(VariableReader var) {
      return this.append("@").append(this.variableLabels[var.getIndex()]);
   }

   @Override
   public void classConstant(VariableReader receiver, ValueType cst) {
      this.appendLocalVar(receiver).append(" := classOf ").escapeIdentifierIfNeeded(cst.toString());
   }

   @Override
   public void nullConstant(VariableReader receiver) {
      this.appendLocalVar(receiver).append(" := null");
   }

   @Override
   public void integerConstant(VariableReader receiver, int cst) {
      this.appendLocalVar(receiver).append(" := " + cst);
   }

   @Override
   public void longConstant(VariableReader receiver, long cst) {
      this.appendLocalVar(receiver).append(" := " + cst + "L");
   }

   @Override
   public void floatConstant(VariableReader receiver, float cst) {
      this.appendLocalVar(receiver).append(" := " + cst + "F");
   }

   @Override
   public void doubleConstant(VariableReader receiver, double cst) {
      this.appendLocalVar(receiver).append(" := " + cst);
   }

   @Override
   public void stringConstant(VariableReader receiver, String cst) {
      this.appendLocalVar(receiver).append(" := '");
      escapeStringLiteral(cst, this.sb);
      this.sb.append("'");
   }

   static void escapeStringLiteral(String s, StringBuilder sb) {
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         switch (c) {
            case '\t':
               sb.append("\\t");
               break;
            case '\n':
               sb.append("\\n");
               break;
            case '\'':
               sb.append("\\'");
               break;
            case '\\':
               sb.append("\\\\");
               break;
            default:
               if (c < ' ') {
                  sb.append("\\u");
                  int pos = 12;

                  for (int j = 0; j < 4; j++) {
                     sb.append(Character.forDigit(c >> pos & 15, 16));
                     pos -= 4;
                  }
               } else {
                  sb.append(c);
               }
         }
      }
   }

   private InstructionStringifier escapeIdentifierIfNeeded(String s) {
      escapeIdentifierIfNeeded(this.sb, s);
      return this;
   }

   static void escapeIdentifierIfNeeded(StringBuilder sb, String s) {
      boolean needsEscaping = false;
      if (s.isEmpty()) {
         needsEscaping = true;
      } else if (!ListingLexer.isIdentifierStart(s.charAt(0))) {
         needsEscaping = true;
      } else {
         for (int i = 1; i < s.length(); i++) {
            if (!ListingLexer.isIdentifierPart(s.charAt(i))) {
               needsEscaping = true;
               break;
            }
         }
      }

      if (needsEscaping) {
         sb.append('`').append(s).append('`');
      } else {
         sb.append(s);
      }
   }

   @Override
   public void binary(BinaryOperation op, VariableReader receiver, VariableReader first, VariableReader second, NumericOperandType type) {
      this.appendLocalVar(receiver).append(" := ").appendLocalVar(first).append(" ");
      switch (op) {
         case ADD:
            this.append("+");
            break;
         case AND:
            this.append("&");
            break;
         case COMPARE_GREATER:
         case COMPARE_LESS:
            this.append("compareTo");
            break;
         case DIVIDE:
            this.append("/");
            break;
         case MODULO:
            this.append("%");
            break;
         case MULTIPLY:
            this.append("*");
            break;
         case OR:
            this.append("|");
            break;
         case SHIFT_LEFT:
            this.append("<<");
            break;
         case SHIFT_RIGHT:
            this.append(">>");
            break;
         case SHIFT_RIGHT_UNSIGNED:
            this.append(">>>");
            break;
         case SUBTRACT:
            this.append("-");
            break;
         case XOR:
            this.append("^");
      }

      this.append(" ").appendLocalVar(second);
      this.append(" as ").append(type.name().toLowerCase());
   }

   @Override
   public void negate(VariableReader receiver, VariableReader operand, NumericOperandType type) {
      this.appendLocalVar(receiver).append(" := -").append(" ").appendLocalVar(operand);
   }

   @Override
   public void assign(VariableReader receiver, VariableReader assignee) {
      this.appendLocalVar(receiver).append(" := ").appendLocalVar(assignee);
   }

   @Override
   public void cast(VariableReader receiver, VariableReader value, ValueType targetType, boolean weak) {
      this.appendLocalVar(receiver).append(" := ");
      if (weak) {
         this.append("weak ");
      }

      this.append("cast ").appendLocalVar(value).append(" to ").escapeIdentifierIfNeeded(targetType.toString());
   }

   @Override
   public void cast(VariableReader receiver, VariableReader value, NumericOperandType sourceType, NumericOperandType targetType) {
      this.appendLocalVar(receiver)
         .append(" := cast ")
         .appendLocalVar(value)
         .append(" from ")
         .append(sourceType.toString().toLowerCase(Locale.ROOT))
         .append(" to ")
         .append(targetType.toString().toLowerCase(Locale.ROOT));
   }

   @Override
   public void cast(VariableReader receiver, VariableReader value, IntegerSubtype type, CastIntegerDirection direction) {
      this.appendLocalVar(receiver).append(" := cast ").appendLocalVar(value);
      switch (direction) {
         case FROM_INTEGER:
            this.append(" from int to ").append(type.name().toLowerCase(Locale.ROOT));
            break;
         case TO_INTEGER:
            this.append(" from ").append(type.name().toLowerCase(Locale.ROOT)).append(" to int");
      }
   }

   @Override
   public void jumpIf(BranchingCondition cond, VariableReader operand, BasicBlockReader consequent, BasicBlockReader alternative) {
      this.append("if ").appendLocalVar(operand).append(" ");
      switch (cond) {
         case EQUAL:
            this.sb.append("== 0");
            break;
         case NOT_EQUAL:
            this.sb.append("!= 0");
            break;
         case GREATER:
            this.sb.append("> 0");
            break;
         case GREATER_OR_EQUAL:
            this.sb.append(">= 0");
            break;
         case LESS:
            this.sb.append("<= 0");
            break;
         case LESS_OR_EQUAL:
            this.sb.append("< 0");
            break;
         case NOT_NULL:
            this.sb.append("!== null");
            break;
         case NULL:
            this.sb.append("=== null");
      }

      this.append(" then goto $").append(consequent.getIndex()).append(" else goto $").append(alternative.getIndex());
   }

   @Override
   public void jumpIf(BinaryBranchingCondition cond, VariableReader first, VariableReader second, BasicBlockReader consequent, BasicBlockReader alternative) {
      this.append("if ").appendLocalVar(first).append(" ");
      switch (cond) {
         case EQUAL:
            this.append("==");
            break;
         case REFERENCE_EQUAL:
            this.append("===");
            break;
         case NOT_EQUAL:
            this.append("!=");
            break;
         case REFERENCE_NOT_EQUAL:
            this.append("!==");
      }

      this.appendLocalVar(second).append(" then goto $").append(consequent.getIndex()).append(" else goto $").append(alternative.getIndex());
   }

   @Override
   public void jump(BasicBlockReader target) {
      this.append("goto $").append(target.getIndex());
   }

   @Override
   public void choose(VariableReader condition, List<? extends SwitchTableEntryReader> table, BasicBlockReader defaultTarget) {
      this.append("switch ").appendLocalVar(condition).append(" ");

      for (int i = 0; i < table.size(); i++) {
         if (i > 0) {
            this.append(" ");
         }

         SwitchTableEntryReader entry = table.get(i);
         this.append("case ").append(entry.getCondition()).append(" goto $").append(entry.getTarget().getIndex());
      }

      this.sb.append(" else goto $").append(defaultTarget.getIndex());
   }

   @Override
   public void exit(VariableReader valueToReturn) {
      this.append("return");
      if (valueToReturn != null) {
         this.append(" ").appendLocalVar(valueToReturn);
      }
   }

   @Override
   public void raise(VariableReader exception) {
      this.append("throw ").appendLocalVar(exception);
   }

   @Override
   public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
      this.appendLocalVar(receiver).append(" := newArray ").escapeIdentifierIfNeeded(itemType.toString()).append("[").appendLocalVar(size).append(']');
   }

   @Override
   public void createArray(VariableReader receiver, ValueType itemType, List<? extends VariableReader> dimensions) {
      this.appendLocalVar(receiver).append(" := newArray ").escapeIdentifierIfNeeded(itemType.toString());
      this.append("[");

      for (int i = 0; i < dimensions.size(); i++) {
         if (i > 0) {
            this.append(", ");
         }

         this.appendLocalVar(dimensions.get(i));
      }

      this.append("]");
   }

   @Override
   public void create(VariableReader receiver, String type) {
      this.appendLocalVar(receiver).append(" := new ").escapeIdentifierIfNeeded(type);
   }

   @Override
   public void getField(VariableReader receiver, VariableReader instance, FieldReference field, ValueType fieldType) {
      this.appendLocalVar(receiver).append(" := field ").escapeIdentifierIfNeeded(field.toString());
      if (instance != null) {
         this.append(" ").appendLocalVar(instance);
      }

      this.append(" as ").escapeIdentifierIfNeeded(fieldType.toString());
   }

   @Override
   public void putField(VariableReader instance, FieldReference field, VariableReader value, ValueType fieldType) {
      this.append("field ").escapeIdentifierIfNeeded(field.toString());
      if (instance != null) {
         this.append(" ").appendLocalVar(instance);
      }

      this.append(" := ").appendLocalVar(value).append(" as ").escapeIdentifierIfNeeded(fieldType.toString());
   }

   @Override
   public void arrayLength(VariableReader receiver, VariableReader array) {
      this.appendLocalVar(receiver).append(" := lengthOf ").appendLocalVar(array);
   }

   @Override
   public void cloneArray(VariableReader receiver, VariableReader array) {
      this.appendLocalVar(receiver).append(" := clone ").appendLocalVar(array);
   }

   @Override
   public void unwrapArray(VariableReader receiver, VariableReader array, ArrayElementType elementType) {
      this.appendLocalVar(receiver).append(" := data ").appendLocalVar(array).append(" as ").append(elementType.name().toLowerCase(Locale.ROOT));
   }

   @Override
   public void getElement(VariableReader receiver, VariableReader array, VariableReader index, ArrayElementType type) {
      this.appendLocalVar(receiver)
         .append(" := ")
         .appendLocalVar(array)
         .append("[")
         .appendLocalVar(index)
         .append("]")
         .append(" as " + type.name().toLowerCase(Locale.ROOT));
   }

   @Override
   public void putElement(VariableReader array, VariableReader index, VariableReader value, ArrayElementType type) {
      this.appendLocalVar(array).append("[").appendLocalVar(index).append("] := ").appendLocalVar(value).append(" as " + type.name().toLowerCase(Locale.ROOT));
   }

   @Override
   public void invoke(VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments, InvocationType type) {
      if (receiver != null) {
         this.appendLocalVar(receiver).append(" := ");
      }

      if (instance == null) {
         this.append("invokeStatic ");
      } else {
         switch (type) {
            case SPECIAL:
               this.append("invoke ");
               break;
            case VIRTUAL:
               this.append("invokeVirtual ");
         }
      }

      this.escapeIdentifierIfNeeded(method.toString());
      if (instance != null) {
         this.append(' ').appendLocalVar(instance);
      }

      for (int i = 0; i < arguments.size(); i++) {
         if (instance != null || i > 0) {
            this.append(",");
         }

         this.append(' ').appendLocalVar(arguments.get(i));
      }
   }

   @Override
   public void invokeDynamic(
      VariableReader receiver,
      VariableReader instance,
      MethodDescriptor method,
      List<? extends VariableReader> arguments,
      MethodHandle bootstrapMethod,
      List<RuntimeConstant> bootstrapArguments
   ) {
      if (receiver != null) {
         this.appendLocalVar(receiver).append(" := ");
      }

      if (instance != null) {
         this.appendLocalVar(instance).append(".");
      }

      this.append(method.getName()).append("(");
      this.append(arguments.stream().map(arg -> "@" + arg.getIndex()).collect(Collectors.joining(", ")));
      this.append(") ");
      this.append("[").append(this.convert(bootstrapMethod)).append('(');
      this.append(bootstrapArguments.stream().map(this::convert).collect(Collectors.joining(", ")));
      this.append(")");
   }

   private String convert(MethodHandle handle) {
      switch (handle.getKind()) {
         case INVOKE_VIRTUAL:
         case INVOKE_SPECIAL:
         case INVOKE_INTERFACE:
            return new MethodDescriptor(handle.getName(), handle.signature()).toString();
         case INVOKE_CONSTRUCTOR:
            return "new" + handle.getClassName() + "." + new MethodDescriptor(handle.getName(), handle.signature()).toString();
         case INVOKE_STATIC:
            return handle.getClassName() + "." + new MethodDescriptor(handle.getName(), handle.signature()).toString();
         case GET_FIELD:
            return "GET " + handle.getName();
         case GET_STATIC_FIELD:
            return "GET " + handle.getClassName() + "." + handle.getName();
         case PUT_FIELD:
            return "PUT " + handle.getName();
         case PUT_STATIC_FIELD:
            return "PUT " + handle.getClassName() + "." + handle.getName();
         default:
            throw new IllegalArgumentException("Unexpected handle type: " + handle.getKind());
      }
   }

   private String convert(RuntimeConstant cst) {
      switch (cst.getKind()) {
         case 0:
            return String.valueOf(cst.getInt());
         case 1:
            return String.valueOf(cst.getLong());
         case 2:
            return String.valueOf(cst.getFloat());
         case 3:
            return String.valueOf(cst.getDouble());
         case 4:
            return String.valueOf(cst.getString());
         case 5:
            return String.valueOf(cst.getValueType());
         case 6:
            ValueType[] methodType = cst.getMethodType();
            return "("
               + Arrays.stream(methodType, 0, methodType.length - 1).map(Object::toString).collect(Collectors.joining())
               + ")"
               + methodType[methodType.length - 1];
         case 7:
            return this.convert(cst.getMethodHandle());
         default:
            throw new IllegalArgumentException("Unexpected runtime constant type: " + cst.getKind());
      }
   }

   @Override
   public void isInstance(VariableReader receiver, VariableReader value, ValueType type) {
      this.appendLocalVar(receiver).append(" := ").appendLocalVar(value).append(" instanceOf ").escapeIdentifierIfNeeded(type.toString());
   }

   @Override
   public void initClass(String className) {
      this.append("initClass ").append(className);
   }

   @Override
   public void nullCheck(VariableReader receiver, VariableReader value) {
      this.appendLocalVar(receiver).append(" := nullCheck ").appendLocalVar(value);
   }

   @Override
   public void monitorEnter(VariableReader objectRef) {
      this.append("monitorEnter ").appendLocalVar(objectRef);
   }

   @Override
   public void monitorExit(VariableReader objectRef) {
      this.append("monitorExit ").appendLocalVar(objectRef);
   }

   @Override
   public void boundCheck(VariableReader receiver, VariableReader index, VariableReader array, boolean lower) {
      this.appendLocalVar(receiver).append(" = boundCheck ").appendLocalVar(index);
      if (array != null) {
         this.append(" upper ").appendLocalVar(array);
      }

      if (lower) {
         this.append(" lower");
      }
   }
}
