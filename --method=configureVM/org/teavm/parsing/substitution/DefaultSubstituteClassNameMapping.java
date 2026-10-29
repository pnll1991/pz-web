package org.teavm.parsing.substitution;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.function.Predicate;
import org.teavm.extension.spi.substitution.ClassSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;

public class DefaultSubstituteClassNameMapping implements SubstituteClassNameMapping {
   private List<? extends ClassNameMappingRule> rules;

   private DefaultSubstituteClassNameMapping(List<? extends ClassNameMappingRule> rules) {
      this.rules = List.copyOf(rules);
   }

   @Override
   public String originalToSubstitute(ClassReaderSource classes, String original) {
      boolean dontFallback = false;

      for (ClassNameMappingRule rule : this.rules) {
         if (rule.predicate.test(original)) {
            dontFallback |= rule.dontFallback;
         }
      }

      for (ClassNameMappingRule rule : this.rules) {
         if (rule.predicate.test(original)) {
            int lastDot = original.lastIndexOf(46);
            String packageName = lastDot >= 0 ? original.substring(0, lastDot) : "";
            String simpleName = original.substring(lastDot + 1);
            String newName = this.constructName(packageName, simpleName, rule);
            if (!newName.equals(original)) {
               ClassReader newCls = classes.get(newName);
               if (newCls != null) {
                  return newName;
               }
            }
         }
      }

      return dontFallback && classes.get(original) == null ? null : original;
   }

   private String constructName(String packageName, String simpleName, ClassNameMappingRule rule) {
      StringBuilder sb = new StringBuilder();
      if (rule.packagePrefix != null) {
         sb.append(rule.packagePrefix);
      }

      String newPackageName = packageName;
      if (rule.packageRemove != null) {
         if (newPackageName.equals(rule.packageRemove)) {
            newPackageName = "";
         } else if (newPackageName.startsWith(rule.packageRemove) && newPackageName.charAt(rule.packageRemove.length()) == '.') {
            newPackageName = newPackageName.substring(rule.packageRemove.length() + 1);
         }
      }

      if (!newPackageName.isEmpty()) {
         if (!sb.isEmpty()) {
            sb.append('.');
         }

         sb.append(newPackageName);
      }

      if (rule.packageSuffix != null) {
         if (!sb.isEmpty()) {
            sb.append('.');
         }

         sb.append(rule.packageSuffix);
      }

      if (!sb.isEmpty()) {
         sb.append('.');
      }

      if (rule.simpleNamePrefix != null) {
         sb.append(rule.simpleNamePrefix);
      }

      sb.append(simpleName);
      if (rule.simpleNameSuffix != null) {
         sb.append(rule.simpleNameSuffix);
      }

      return sb.toString();
   }

   @Override
   public String substituteToOriginal(String substitute) {
      int lastDot = substitute.lastIndexOf(46);
      String packageName = lastDot >= 0 ? substitute.substring(0, lastDot) : "";
      String simpleName = substitute.substring(lastDot + 1);

      for (ClassNameMappingRule rule : this.rules) {
         String newSimpleName = simpleName;
         String newPackageName = packageName;
         if (rule.simpleNamePrefix != null) {
            if (!newSimpleName.startsWith(rule.simpleNamePrefix)) {
               continue;
            }

            newSimpleName = newSimpleName.substring(rule.simpleNamePrefix.length());
         }

         if (rule.simpleNameSuffix != null) {
            if (!newSimpleName.endsWith(rule.simpleNameSuffix)) {
               continue;
            }

            newSimpleName = newSimpleName.substring(0, newSimpleName.length() - rule.simpleNameSuffix.length());
         }

         if (rule.packagePrefix != null) {
            newPackageName = this.removePackagePrefix(newPackageName, rule.packagePrefix);
            if (newPackageName == null) {
               continue;
            }
         }

         if (rule.packageSuffix != null) {
            newPackageName = this.removePackageSuffix(newPackageName, rule.packageSuffix);
            if (newPackageName == null) {
               continue;
            }
         }

         if (rule.packageRemove != null) {
            if (newPackageName.isEmpty()) {
               newPackageName = rule.packageRemove;
            } else {
               newPackageName = rule.packageRemove + "." + newPackageName;
            }
         }

         String newName = newPackageName.isEmpty() ? newSimpleName : newPackageName + "." + newSimpleName;
         if (rule.predicate.test(newName)) {
            return newName;
         }
      }

      return substitute;
   }

   private String removePackagePrefix(String packageName, String packagePrefix) {
      if (packageName.equals(packagePrefix)) {
         return "";
      } else {
         return packageName.startsWith(packagePrefix) && packageName.charAt(packagePrefix.length()) == '.'
            ? packageName.substring(packagePrefix.length() + 1)
            : null;
      }
   }

   private String removePackageSuffix(String packageName, String packageSuffix) {
      if (packageName.equals(packageSuffix)) {
         return "";
      } else {
         return packageName.endsWith(packageSuffix) && packageName.charAt(packageName.length() - packageSuffix.length() - 1) == '.'
            ? packageName.substring(0, packageName.length() - packageSuffix.length() - 1)
            : null;
      }
   }

   public static DefaultSubstituteClassNameMapping createWithPolicies(Iterable<? extends SubstitutionPolicy> policies) {
      final ArrayList<ClassNameMappingRule> rules = new ArrayList<>();
      var sink = new SubstitutionSink() {
         public ClassSubstitutionPolicy selectClasses(Predicate<String> predicate) {
            final ClassNameMappingRule rule = new ClassNameMappingRule(predicate);
            rules.add(rule);
            return new ClassSubstitutionPolicy() {
               public ClassSubstitutionPolicy simpleNamePrefix(String name) {
                  if (name != null && !name.isEmpty()) {
                     rule.simpleNamePrefix = name;
                  } else {
                     rule.simpleNamePrefix = null;
                  }

                  return this;
               }

               public ClassSubstitutionPolicy simpleNameSuffix(String name) {
                  if (name != null && !name.isEmpty()) {
                     rule.simpleNameSuffix = name;
                  } else {
                     rule.simpleNameSuffix = null;
                  }

                  return this;
               }

               public ClassSubstitutionPolicy packagePrefix(String name) {
                  if (name != null && !name.isEmpty()) {
                     if (name.endsWith(".")) {
                        name = name.substring(0, name.length() - 1);
                     }

                     rule.packagePrefix = name;
                  } else {
                     rule.packagePrefix = null;
                  }

                  return this;
               }

               public ClassSubstitutionPolicy packageSuffix(String name) {
                  if (name != null && !name.isEmpty()) {
                     if (name.startsWith(".")) {
                        name = name.substring(1);
                     }

                     rule.packageSuffix = name;
                  } else {
                     rule.packageSuffix = null;
                  }

                  return this;
               }

               public ClassSubstitutionPolicy replacePackage(String from, String to) {
                  if (from.endsWith(".")) {
                     from = from.substring(0, from.length() - 1);
                  }

                  rule.packageRemove = from;
                  return this.packagePrefix(to);
               }

               public ClassSubstitutionPolicy dontFallbackWhenNoSubstitution() {
                  rule.dontFallback = true;
                  return null;
               }
            };
         }
      };

      for (SubstitutionPolicy policy : policies) {
         policy.contribute(sink);
      }

      return new DefaultSubstituteClassNameMapping(rules);
   }

   public static DefaultSubstituteClassNameMapping createWithSPI(ClassLoader classLoader) {
      List<SubstitutionPolicy> policies = ServiceLoader.load(SubstitutionPolicy.class, classLoader).stream().map(Provider::get).toList();
      return createWithPolicies(policies);
   }
}
