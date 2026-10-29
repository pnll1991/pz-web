package org.teavm.backend.javascript.codegen;

import java.util.HashSet;
import java.util.Set;
import org.teavm.backend.javascript.rendering.RenderingUtil;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public class MinifyingAliasProvider implements AliasProvider {
   private static final String startLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
   private static final String startVirtualLetters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
   private final int maxTopLevelNames;
   private int lastSuffix;
   private int lastInstanceSuffix;
   private int topLevelNames;
   private boolean additionalScopeStarted;
   private final Set<String> usedAliases = new HashSet<>();

   public MinifyingAliasProvider(int maxTopLevelNames) {
      this.maxTopLevelNames = maxTopLevelNames;
   }

   @Override
   public String getFieldAlias(FieldReference field) {
      return this.createInstanceName();
   }

   @Override
   public ScopedName getStaticFieldAlias(FieldReference field) {
      return this.createTopLevelName();
   }

   @Override
   public ScopedName getStaticMethodAlias(MethodReference method) {
      return this.createTopLevelName();
   }

   @Override
   public String getMethodAlias(MethodDescriptor method) {
      return this.createInstanceName();
   }

   @Override
   public ScopedName getClassAlias(String className) {
      return this.createTopLevelName();
   }

   @Override
   public ScopedName getFunctionAlias(String className) {
      return this.createTopLevelName();
   }

   @Override
   public ScopedName getClassInitAlias(String className) {
      return this.createTopLevelName();
   }

   @Override
   public String getAdditionalScopeName() {
      return this.createTopLevelName().name;
   }

   @Override
   public void reserveName(String name) {
      this.usedAliases.add(name);
   }

   private ScopedName createTopLevelName() {
      if (!this.additionalScopeStarted && this.topLevelNames >= this.maxTopLevelNames) {
         this.additionalScopeStarted = true;
         this.lastSuffix = 0;
      }

      String result;
      do {
         result = RenderingUtil.indexToId(this.lastSuffix++, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
      } while (!this.additionalScopeStarted && this.usedAliases.contains(result) || RenderingUtil.KEYWORDS.contains(result));

      this.topLevelNames++;
      return new ScopedName(result, this.additionalScopeStarted);
   }

   private String createInstanceName() {
      String result;
      do {
         result = RenderingUtil.indexToId(this.lastInstanceSuffix++, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
      } while (RenderingUtil.KEYWORDS.contains(result));

      return result;
   }
}
