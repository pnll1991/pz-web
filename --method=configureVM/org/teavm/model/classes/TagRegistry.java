package org.teavm.model.classes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.ListableClassReaderSource;

public class TagRegistry {
   private Map<String, List<TagRegistry.Range>> ranges = new HashMap<>();

   public TagRegistry(ListableClassReaderSource classSource, ClassHierarchy classHierarchy) {
      List<String> roots = new ArrayList<>();
      Map<String, Set<String>> implementedBy = new HashMap<>();
      Map<String, List<String>> hierarchy = new HashMap<>();

      for (String className : classSource.getClassNames()) {
         ClassReader cls = classSource.get(className);
         if (cls != null && !cls.hasModifier(ElementModifier.INTERFACE)) {
            for (String iface : cls.getInterfaces()) {
               this.markImplementor(classHierarchy, className, iface, implementedBy);
            }

            if (cls.getParent() != null && !cls.getParent().equals(cls.getName())) {
               hierarchy.computeIfAbsent(cls.getParent(), key -> new ArrayList<>()).add(className);
            } else {
               roots.add(className);
            }
         }
      }

      Map<String, TagRegistry.Range> simpleRanges = new HashMap<>();
      int current = 0;

      for (String root : roots) {
         current = 1 + this.assignRange(current, hierarchy, root, simpleRanges);
      }

      for (String className : classSource.getClassNames()) {
         ClassReader cls = classSource.get(className);
         if (cls != null) {
            if (cls.hasModifier(ElementModifier.INTERFACE)) {
               Set<String> implementorRoots = implementedBy.get(cls.getName());
               if (implementorRoots != null) {
                  List<TagRegistry.Range> ifaceRanges = implementorRoots.stream()
                     .map(simpleRanges::get)
                     .filter(Objects::nonNull)
                     .sorted(Comparator.comparing(range -> range.lower))
                     .collect(Collectors.toList());
                  if (!ifaceRanges.isEmpty()) {
                     this.ranges.put(className, ifaceRanges);
                  }
               }
            } else {
               TagRegistry.Range simpleRange = simpleRanges.get(className);
               if (simpleRange != null) {
                  this.ranges.put(className, new ArrayList<>(Arrays.asList(simpleRange)));
               }
            }
         }
      }
   }

   private String findTopmostImplementor(ClassHierarchy hierarchy, String className, String ifaceName) {
      ClassReader cls = hierarchy.getClassSource().get(className);
      if (cls == null) {
         return null;
      }

      if (cls.getParent() != null) {
         String candidate = this.findTopmostImplementor(hierarchy, cls.getParent(), ifaceName);
         if (candidate != null) {
            return candidate;
         }
      }

      return hierarchy.isSuperType(ifaceName, className, false) ? className : null;
   }

   private void markImplementor(ClassHierarchy hierarchy, String className, String ifaceName, Map<String, Set<String>> implementedBy) {
      className = this.findTopmostImplementor(hierarchy, className, ifaceName);
      if (implementedBy.computeIfAbsent(ifaceName, key -> new LinkedHashSet<>()).add(className)) {
         ClassReader iface = hierarchy.getClassSource().get(ifaceName);
         if (iface != null) {
            for (String superIface : iface.getInterfaces()) {
               this.markImplementor(hierarchy, className, superIface, implementedBy);
            }
         }
      }
   }

   private int assignRange(int start, Map<String, List<String>> hierarchy, String className, Map<String, TagRegistry.Range> ranges) {
      int end = start + 1;

      for (String childClass : hierarchy.getOrDefault(className, Collections.emptyList())) {
         end = this.assignRange(end, hierarchy, childClass, ranges);
      }

      ranges.put(className, new TagRegistry.Range(start, ++end));
      return end;
   }

   public List<TagRegistry.Range> getRanges(String className) {
      return new ArrayList<>(this.ranges.getOrDefault(className, Collections.emptyList()));
   }

   public static class Range {
      public int lower;
      public int upper;

      private Range(int lower, int upper) {
         this.lower = lower;
         this.upper = upper;
      }
   }
}
