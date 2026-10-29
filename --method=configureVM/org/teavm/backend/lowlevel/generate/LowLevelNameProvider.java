package org.teavm.backend.lowlevel.generate;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public abstract class LowLevelNameProvider implements NameProvider {
   protected Set<String> occupiedTopLevelNames = new HashSet<>();
   protected Set<String> occupiedVtableNames = new HashSet<>();
   protected Map<String, Set<String>> occupiedClassNames = new HashMap<>();
   protected Map<MethodReference, String> methodNames = new HashMap<>();
   protected Map<MethodDescriptor, String> virtualMethodNames = new HashMap<>();
   protected Map<FieldReference, String> staticFieldNames = new HashMap<>();
   protected Map<FieldReference, String> memberFieldNames = new HashMap<>();
   protected Map<String, String> classNames = new HashMap<>();
   protected Map<String, String> classInitializerNames = new HashMap<>();
   protected Map<String, String> classClassNames = new HashMap<>();
   protected Map<ValueType, String> classSystemInitializerNames = new HashMap<>();
   protected Map<ValueType, String> classInstanceNames = new HashMap<>();
   protected Map<ValueType, String> supertypeNames = new HashMap<>();

   @Override
   public String forMethod(MethodReference method) {
      return this.methodNames.computeIfAbsent(method, k -> this.pickUnoccupied("meth_" + this.suggestForMethod(k)));
   }

   @Override
   public String forVirtualMethod(MethodDescriptor method) {
      return this.virtualMethodNames.computeIfAbsent(method, k -> this.pickUnoccupied("virt_" + this.sanitize(k.getName()), this.occupiedVtableNames));
   }

   @Override
   public String forStaticField(FieldReference field) {
      return this.staticFieldNames.computeIfAbsent(field, k -> this.pickUnoccupied("sfld_" + this.suggestForStaticField(k)));
   }

   @Override
   public String forMemberField(FieldReference field) {
      return this.memberFieldNames.computeIfAbsent(field, k -> {
         Set<String> occupied = this.occupiedClassNames.computeIfAbsent(k.getClassName(), c -> new HashSet<>(Arrays.asList("parent")));
         return this.pickUnoccupied("fld_" + this.sanitize(field.getFieldName()), occupied);
      });
   }

   @Override
   public String forClass(String className) {
      return this.classNames.computeIfAbsent(className, k -> this.pickUnoccupied("cls_" + this.suggestForClass(k)));
   }

   @Override
   public String forClassInitializer(String className) {
      return this.classInitializerNames.computeIfAbsent(className, k -> this.pickUnoccupied("initclass_" + this.suggestForClass(k)));
   }

   @Override
   public String forClassSystemInitializer(ValueType type) {
      return this.classSystemInitializerNames.computeIfAbsent(type, k -> this.pickUnoccupied("sysinitclass_" + this.suggestForType(k)));
   }

   @Override
   public String forClassClass(String className) {
      return this.classClassNames.computeIfAbsent(className, k -> this.pickUnoccupied(this.suggestForClass(k) + "_VT"));
   }

   @Override
   public String forClassInstance(ValueType type) {
      return this.classInstanceNames.computeIfAbsent(type, k -> this.pickUnoccupied(this.suggestForType(k) + "_Cls"));
   }

   @Override
   public String forSupertypeFunction(ValueType type) {
      return this.supertypeNames.computeIfAbsent(type, k -> this.pickUnoccupied("supertypeof_" + this.suggestForType(k)));
   }

   private String suggestForMethod(MethodReference method) {
      StringBuilder sb = new StringBuilder();
      this.suggestForClass(method.getClassName(), sb);
      sb.append('_');
      sb.append(this.sanitize(method.getName()));
      return sb.toString();
   }

   private String suggestForStaticField(FieldReference field) {
      StringBuilder sb = new StringBuilder();
      this.suggestForClass(field.getClassName(), sb);
      sb.append('_');
      sb.append(this.sanitize(field.getFieldName()));
      return sb.toString();
   }

   private String suggestForClass(String className) {
      StringBuilder sb = new StringBuilder();
      this.suggestForClass(className, sb);
      return sb.toString();
   }

   private void suggestForClass(String className, StringBuilder sb) {
      int index = 0;

      while (true) {
         int next = className.indexOf(46, index);
         if (next < 0) {
            if (index > 0) {
               sb.append('_');
               sb.append(this.sanitize(className.substring(index)));
            } else {
               sb.append(this.sanitize(className));
            }

            return;
         }

         sb.append(this.sanitize(String.valueOf(className.charAt(index))));
         index = next + 1;
      }
   }

   private String suggestForType(ValueType type) {
      StringBuilder sb = new StringBuilder();
      this.suggestForType(type, sb);
      return sb.toString();
   }

   private void suggestForType(ValueType type, StringBuilder sb) {
      if (type instanceof ValueType.Object) {
         this.suggestForClass(((ValueType.Object)type).getClassName(), sb);
      } else if (type instanceof ValueType.Array) {
         sb.append("Arr_");
         this.suggestForType(((ValueType.Array)type).getItemType(), sb);
      } else {
         sb.append(type.toString());
      }
   }

   private String sanitize(String name) {
      StringBuilder sb = new StringBuilder();
      char c = name.charAt(0);
      sb.append(isIdentifierStart(c) ? c : '_');

      for (int i = 1; i < name.length(); i++) {
         c = name.charAt(i);
         sb.append(isIdentifierPart(c) ? c : '_');
      }

      return sb.toString();
   }

   private static boolean isIdentifierStart(char c) {
      return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '_';
   }

   private static boolean isIdentifierPart(char c) {
      return isIdentifierStart(c) || c >= '0' && c <= '9';
   }

   protected String pickUnoccupied(String name) {
      return this.pickUnoccupied(name, this.occupiedTopLevelNames);
   }

   private String pickUnoccupied(String name, Set<String> occupied) {
      String result = name;
      int index = 0;
      Set<? extends String> keywords = this.getKeywords();

      while (keywords.contains(result) || !occupied.add(result)) {
         result = name + "_" + index++;
      }

      return result;
   }

   protected Set<? extends String> getKeywords() {
      return Collections.emptySet();
   }

   @Override
   public String createTopLevelName(String base) {
      return this.pickUnoccupied(base);
   }
}
