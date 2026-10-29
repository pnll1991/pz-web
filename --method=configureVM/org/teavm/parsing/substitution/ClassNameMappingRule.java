package org.teavm.parsing.substitution;

import java.util.function.Predicate;

class ClassNameMappingRule {
   Predicate<String> predicate;
   String packagePrefix;
   String packageSuffix;
   String packageRemove;
   String simpleNamePrefix;
   String simpleNameSuffix;
   boolean dontFallback;

   ClassNameMappingRule(Predicate<String> predicate) {
      this.predicate = predicate;
   }
}
