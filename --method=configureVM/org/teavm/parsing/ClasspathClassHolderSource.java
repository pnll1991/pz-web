package org.teavm.parsing;

import java.util.Date;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.ReferenceCache;
import org.teavm.parsing.resource.ResourceBytecodeClassHolderSource;
import org.teavm.parsing.resource.ResourceProvider;
import org.teavm.parsing.substitution.DefaultSubstituteClassNameMapping;
import org.teavm.parsing.substitution.SubstituteClassNameMapping;

public class ClasspathClassHolderSource implements ClassHolderSource, ClassDateProvider {
   private RenamingClassHolderSource classPathMapper;

   public ClasspathClassHolderSource(ResourceProvider resourceProvider, ReferenceCache referenceCache, ClassLoader classLoader) {
      this(resourceProvider, referenceCache, DefaultSubstituteClassNameMapping.createWithSPI(classLoader));
   }

   public ClasspathClassHolderSource(ResourceProvider resourceProvider, ReferenceCache referenceCache, SubstituteClassNameMapping nameMapping) {
      ResourceBytecodeClassHolderSource rawMapper = new ResourceBytecodeClassHolderSource(resourceProvider, referenceCache);
      this.classPathMapper = new RenamingClassHolderSource(resourceProvider, referenceCache, rawMapper, nameMapping);
   }

   public ClasspathClassHolderSource(ReferenceCache referenceCache) {
      this(new ClasspathResourceProvider(ClasspathClassHolderSource.class.getClassLoader()), referenceCache, ClasspathClassHolderSource.class.getClassLoader());
   }

   @Override
   public ClassHolder get(String name) {
      return this.classPathMapper.get(name);
   }

   @Override
   public Date getModificationDate(String className) {
      return this.classPathMapper.getModificationDate(className);
   }
}
