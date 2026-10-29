package org.teavm.parsing;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.teavm.common.CachedFunction;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.ReferenceCache;
import org.teavm.parsing.resource.Resource;
import org.teavm.parsing.resource.ResourceProvider;
import org.teavm.parsing.substitution.SubstituteClassNameMapping;

public class RenamingClassHolderSource implements ClassHolderSource, ClassDateProvider {
   private static final Date VOID_DATE = new Date(0L);
   private ClassHolderSource innerSource;
   private ClassRefsRenamer renamer;
   private ResourceProvider resourceProvider;
   private Map<String, Date> modificationDates = new HashMap<>();
   private SubstituteClassNameMapping classNameMapping;
   private Map<String, String> substituteToOriginalCache = new HashMap<>();
   private Map<String, RenamingClassHolderSource.CacheEntry> cache = new HashMap<>();

   public RenamingClassHolderSource(
      ResourceProvider resourceProvider, ReferenceCache referenceCache, ClassHolderSource innerSource, SubstituteClassNameMapping classNameMapping
   ) {
      this.innerSource = innerSource;
      this.classNameMapping = classNameMapping;
      this.renamer = new ClassRefsRenamer(referenceCache, new CachedFunction<>(this::substituteToOriginal));
      this.resourceProvider = resourceProvider;
   }

   @Override
   public ClassHolder get(String name) {
      return this.cache.computeIfAbsent(name, n -> new RenamingClassHolderSource.CacheEntry(this.acquire(n))).value;
   }

   private ClassHolder acquire(String name) {
      String originalName = name;
      name = this.classNameMapping.originalToSubstitute(this.innerSource, name);
      if (name == null) {
         return null;
      }

      ClassHolder cls = this.innerSource.get(name);
      if (cls != null && !cls.getName().equals(originalName)) {
         cls = this.renamer.rename(this.innerSource.get(name));
      }

      return cls;
   }

   @Override
   public Date getModificationDate(String className) {
      Date mdate = this.modificationDates.get(className);
      if (mdate == null) {
         mdate = this.getOriginalModificationDate(this.substituteToOriginal(className));
         this.modificationDates.put(className, mdate);
      }

      return mdate == VOID_DATE ? null : mdate;
   }

   private Date getOriginalModificationDate(String className) {
      if (this.resourceProvider == null) {
         return null;
      }

      Resource res = this.resourceProvider.getResource(className.replace('.', '/') + ".class");
      return res == null ? null : res.getModificationDate();
   }

   private String substituteToOriginal(String name) {
      return this.substituteToOriginalCache.computeIfAbsent(name, this.classNameMapping::substituteToOriginal);
   }

   private static class CacheEntry {
      ClassHolder value;

      CacheEntry(ClassHolder value) {
         this.value = value;
      }
   }
}
