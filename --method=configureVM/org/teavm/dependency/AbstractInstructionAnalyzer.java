package org.teavm.dependency;

import java.util.List;
import java.util.Objects;
import org.teavm.model.CallLocation;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHandle;
import org.teavm.model.MethodReference;
import org.teavm.model.RuntimeConstant;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.model.instructions.InvocationType;

abstract class AbstractInstructionAnalyzer extends AbstractInstructionReader {
   private static final MethodReference STRING_INIT_FROM_CHARS_METHOD = new MethodReference(String.class, "<init>", char[].class, void.class);
   static final MethodReference CLONE_METHOD = new MethodReference(Object.class, "clone", Object.class);
   private static final MethodReference NPE_INIT_METHOD = new MethodReference(NullPointerException.class, "<init>", void.class);
   private static final MethodReference AIOOB_INIT_METHOD = new MethodReference(ArrayIndexOutOfBoundsException.class, "<init>", void.class);
   static final MethodReference MONITOR_ENTER_METHOD = new MethodReference(Object.class, "monitorEnter", Object.class, void.class);
   static final MethodReference MONITOR_ENTER_SYNC_METHOD = new MethodReference(Object.class, "monitorEnterSync", Object.class, void.class);
   static final MethodReference MONITOR_EXIT_METHOD = new MethodReference(Object.class, "monitorExit", Object.class, void.class);
   static final MethodReference MONITOR_EXIT_SYNC_METHOD = new MethodReference(Object.class, "monitorExitSync", Object.class, void.class);
   protected TextLocation location;
   protected MethodReference caller;
   protected CallLocation callLocation;

   public void setCaller(MethodReference caller) {
      this.caller = caller;
      this.callLocation = null;
   }

   @Override
   public void location(TextLocation location) {
      if (!Objects.equals(this.location, location)) {
         this.location = location;
         this.callLocation = null;
      }
   }

   @Override
   public void classConstant(VariableReader receiver, ValueType cst) {
      DependencyNode node = this.getNode(receiver);
      if (node != null) {
         node.propagate(this.getAnalyzer().getClassType("java.lang.Class"));
         node.getClassValueNode().propagate(this.getAnalyzer().getType(cst));
      }

      while (cst instanceof ValueType.Array) {
         cst = ((ValueType.Array)cst).getItemType();
      }

      if (cst instanceof ValueType.Object) {
         String className = ((ValueType.Object)cst).getClassName();
         this.getAnalyzer().linkClass(className);
      }
   }

   @Override
   public void stringConstant(VariableReader receiver, String cst) {
      DependencyNode node = this.getNode(receiver);
      if (node != null) {
         node.propagate(this.getAnalyzer().getClassType("java.lang.String"));
      }

      MethodDependency method = this.getAnalyzer().linkMethod(STRING_INIT_FROM_CHARS_METHOD);
      method.addLocation(this.getCallLocation());
      method.use();
   }

   @Override
   public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
      DependencyNode node = this.getNode(receiver);
      if (node != null) {
         node.propagate(this.getAnalyzer().getType(ValueType.arrayOf(itemType)));
      }

      String className = this.extractClassName(itemType);
      if (className != null) {
         this.getAnalyzer().linkClass(className);
      }
   }

   @Override
   public void createArray(VariableReader receiver, ValueType itemType, List<? extends VariableReader> dimensions) {
      DependencyNode node = this.getNode(receiver);

      for (int i = 0; i < dimensions.size() && node != null; i++) {
         node.propagate(this.getAnalyzer().getType(itemType));
         node = node.getArrayItem();
         itemType = ((ValueType.Array)itemType).getItemType();
      }

      String className = this.extractClassName(itemType);
      if (className != null) {
         this.getAnalyzer().linkClass(className);
      }
   }

   protected final String extractClassName(ValueType itemType) {
      while (itemType instanceof ValueType.Array) {
         itemType = ((ValueType.Array)itemType).getItemType();
      }

      return itemType instanceof ValueType.Object ? ((ValueType.Object)itemType).getClassName() : null;
   }

   @Override
   public void create(VariableReader receiver, String type) {
      this.getAnalyzer().linkClass(type);
      DependencyNode node = this.getNode(receiver);
      if (node != null) {
         node.propagate(this.getAnalyzer().getClassType(type));
      }
   }

   @Override
   public void getField(VariableReader receiver, VariableReader instance, FieldReference field, ValueType fieldType) {
      FieldDependency fieldDep = this.getAnalyzer().linkField(field);
      fieldDep.addLocation(this.getCallLocation());
      if (!(fieldType instanceof ValueType.Primitive)) {
         DependencyNode receiverNode = this.getNode(receiver);
         if (receiverNode != null) {
            fieldDep.getValue().connect(receiverNode);
         }
      }

      this.touchField(instance, fieldDep, field);
   }

   @Override
   public void putField(VariableReader instance, FieldReference field, VariableReader value, ValueType fieldType) {
      FieldDependency fieldDep = this.getAnalyzer().linkField(field);
      fieldDep.addLocation(this.getCallLocation());
      if (!(fieldType instanceof ValueType.Primitive)) {
         DependencyNode valueNode = this.getNode(value);
         if (valueNode != null) {
            valueNode.connect(fieldDep.getValue());
         }
      }

      this.touchField(instance, fieldDep, field);
   }

   private void touchField(VariableReader instance, FieldDependency fieldDep, FieldReference field) {
      if (instance == null) {
         if (fieldDep.getField() != null) {
            this.initClass(fieldDep.getField().getOwnerName());
         }
      } else {
         this.getAnalyzer().linkClass(field.getClassName());
      }
   }

   @Override
   public void invoke(VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments, InvocationType type) {
      if (instance == null) {
         this.invokeSpecial(receiver, null, method, arguments);
      } else {
         switch (type) {
            case SPECIAL:
               this.invokeSpecial(receiver, instance, method, arguments);
               break;
            case VIRTUAL:
               this.invokeVirtual(receiver, instance, method, arguments);
         }
      }
   }

   protected abstract void invokeSpecial(VariableReader var1, VariableReader var2, MethodReference var3, List<? extends VariableReader> var4);

   protected abstract void invokeVirtual(VariableReader var1, VariableReader var2, MethodReference var3, List<? extends VariableReader> var4);

   @Override
   public void invokeDynamic(
      VariableReader receiver,
      VariableReader instance,
      MethodDescriptor method,
      List<? extends VariableReader> arguments,
      MethodHandle bootstrapMethod,
      List<RuntimeConstant> bootstrapArguments
   ) {
   }

   @Override
   public void isInstance(VariableReader receiver, VariableReader value, ValueType type) {
      String className = this.extractClassName(type);
      if (className != null) {
         this.getAnalyzer().linkClass(className);
      }
   }

   @Override
   public void cast(VariableReader receiver, VariableReader value, ValueType targetType, boolean weak) {
      if (!weak) {
         String className = this.extractClassName(targetType);
         if (className != null) {
            this.getAnalyzer().linkClass(className);
         }

         this.getAnalyzer().linkClass("java.lang.ClassCastException");
      }
   }

   @Override
   public void initClass(String className) {
      this.getAnalyzer().linkClass(className).initClass(this.getCallLocation());
   }

   @Override
   public void nullCheck(VariableReader receiver, VariableReader value) {
      DependencyNode valueNode = this.getNode(value);
      DependencyNode receiverNode = this.getNode(receiver);
      if (valueNode != null) {
         valueNode.connect(receiverNode);
      }

      MethodDependency npeMethod = this.getAnalyzer().linkMethod(NPE_INIT_METHOD);
      npeMethod.addLocation(this.getCallLocation());
      npeMethod.use();
   }

   @Override
   public void monitorEnter(VariableReader objectRef) {
      if (this.getAnalyzer().asyncSupported) {
         MethodDependency methodDep = this.getAnalyzer().linkMethod(MONITOR_ENTER_METHOD);
         methodDep.addLocation(this.getCallLocation());
         this.getNode(objectRef).connect(methodDep.getVariable(1));
         methodDep.use();
      }

      MethodDependency methodDep = this.getAnalyzer().linkMethod(MONITOR_ENTER_SYNC_METHOD);
      methodDep.addLocation(this.getCallLocation());
      this.getNode(objectRef).connect(methodDep.getVariable(1));
      methodDep.use();
   }

   @Override
   public void monitorExit(VariableReader objectRef) {
      if (this.getAnalyzer().asyncSupported) {
         MethodDependency methodDep = this.getAnalyzer().linkMethod(MONITOR_EXIT_METHOD);
         methodDep.addLocation(this.getCallLocation());
         this.getNode(objectRef).connect(methodDep.getVariable(1));
         methodDep.use();
      }

      MethodDependency methodDep = this.getAnalyzer().linkMethod(MONITOR_EXIT_SYNC_METHOD);
      methodDep.addLocation(this.getCallLocation());
      this.getNode(objectRef).connect(methodDep.getVariable(1));
      methodDep.use();
   }

   @Override
   public void boundCheck(VariableReader receiver, VariableReader index, VariableReader array, boolean lower) {
      MethodDependency methodDep = this.getAnalyzer().linkMethod(AIOOB_INIT_METHOD);
      methodDep.addLocation(this.getCallLocation());
      methodDep.getVariable(0).propagate(this.getAnalyzer().getClassType(ArrayIndexOutOfBoundsException.class.getName()));
      methodDep.use();
   }

   protected abstract DependencyNode getNode(VariableReader var1);

   protected abstract DependencyAnalyzer getAnalyzer();

   protected CallLocation getCallLocation() {
      if (this.callLocation == null) {
         this.callLocation = new CallLocation(this.caller, this.location);
      }

      return this.callLocation;
   }
}
