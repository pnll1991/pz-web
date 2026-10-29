package org.teavm.backend.javascript.codegen;

import java.util.HashSet;
import java.util.Set;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public class DefaultAliasProvider implements AliasProvider {
   private final int maxTopLevelNames;
   private final Set<String> knownAliases = new HashSet<>(200, 0.5F);
   private final ObjectIntMap<String> knowAliasesCounter = new ObjectIntHashMap();
   private final Set<String> knownInstanceAliases = new HashSet<>(200, 0.5F);
   private final ObjectIntMap<String> knowInstanceAliasesCounter = new ObjectIntHashMap();
   private boolean additionalScopeStarted;

   public DefaultAliasProvider(int maxTopLevelNames) {
      this.maxTopLevelNames = maxTopLevelNames;
   }

   @Override
   public ScopedName getClassAlias(String cls) {
      return this.makeUnique(suggestAliasForClass(cls));
   }

   private static String suggestAliasForClass(String cls) {
      StringBuilder alias = new StringBuilder();
      int lastIndex = 0;

      while (true) {
         int index = cls.indexOf(46, lastIndex);
         if (index == -1) {
            if (lastIndex > 0) {
               alias.append("_");
            }

            alias.append(cls.substring(lastIndex));

            for (int var5 = 1; var5 < alias.length(); var5++) {
               char c = alias.charAt(var5);
               if (!Character.isJavaIdentifierPart(c)) {
                  alias.setCharAt(var5, '_');
               }
            }

            if (!Character.isJavaIdentifierStart(alias.charAt(0))) {
               alias.setCharAt(0, '_');
            }

            return alias.toString();
         }

         if (index > lastIndex) {
            alias.append(cls.charAt(lastIndex));
         }

         lastIndex = index + 1;
      }
   }

   @Override
   public String getMethodAlias(MethodDescriptor method) {
      String alias = method.getName();

      return this.makeUniqueInstance(switch (alias) {
         case "<init>" -> "$_init_";
         case "<clinit>" -> "$_clinit_";
         default -> "$" + alias;
      });
   }

   @Override
   public ScopedName getStaticMethodAlias(MethodReference method) {
      String suggested = method.getDescriptor().getName();
      switch (suggested) {
         case "<init>":
            suggested = "_init_";
            break;
         case "<clinit>":
            suggested = "_clinit_";
      }

      return this.makeUnique(suggestAliasForClass(method.getClassName()) + "_" + suggested);
   }

   @Override
   public String getFieldAlias(FieldReference field) {
      return this.makeUniqueInstance("$" + field.getFieldName());
   }

   @Override
   public ScopedName getStaticFieldAlias(FieldReference field) {
      return this.makeUnique(suggestAliasForClass(field.getClassName()) + "_" + field.getFieldName());
   }

   @Override
   public ScopedName getFunctionAlias(String name) {
      return this.makeUnique(name);
   }

   @Override
   public ScopedName getClassInitAlias(String className) {
      return this.makeUnique(suggestAliasForClass(className) + "_$callClinit");
   }

   @Override
   public String getAdditionalScopeName() {
      return this.makeUnique("$rt_java").name;
   }

   @Override
   public void reserveName(String name) {
   }

   private ScopedName makeUnique(String suggested) {
      suggested = this.sanitize(suggested);
      if (!this.additionalScopeStarted && this.knownAliases.size() >= this.maxTopLevelNames) {
         this.additionalScopeStarted = true;
         this.knownAliases.clear();
         this.knowAliasesCounter.clear();
      }

      String alias = suggested;
      int index = this.knowAliasesCounter.get(alias);
      if (index > 0) {
         alias = suggested + index++;
      }

      while (!this.knownAliases.add(alias)) {
         alias = suggested + index++;
      }

      this.knowAliasesCounter.put(alias, index);
      return new ScopedName(alias, this.additionalScopeStarted);
   }

   private String sanitize(String s) {
      if (s.isEmpty()) {
         return "_";
      }

      boolean changed = false;
      StringBuilder sb = new StringBuilder(s.length());
      char c = s.charAt(0);
      if (isIdentifierStart(c)) {
         sb.append(c);
      } else {
         sb.append('_');
         changed = true;
      }

      for (int i = 1; i < s.length(); i++) {
         c = s.charAt(i);
         if (isIdentifierPart(c)) {
            sb.append(c);
         } else {
            sb.append('_');
            changed = true;
         }
      }

      return changed ? sb.toString() : s;
   }

   private static boolean isIdentifierStart(char c) {
      return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_' || c == '$';
   }

   private static boolean isIdentifierPart(char c) {
      return isIdentifierStart(c) || c >= '0' && c <= '9';
   }

   private String makeUniqueInstance(String alias) {
      alias = this.sanitize(alias);
      String uniqueAlias = alias;
      int index = this.knowInstanceAliasesCounter.get(alias);
      if (index > 0) {
         uniqueAlias = alias + index++;
      }

      while (!this.knownInstanceAliases.add(uniqueAlias)) {
         uniqueAlias = alias + index++;
      }

      this.knowInstanceAliasesCounter.put(alias, index);
      return uniqueAlias;
   }
}
