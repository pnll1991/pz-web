package org.teavm.vm;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.teavm.asm.AnnotationVisitor;
import org.teavm.asm.ClassReader;
import org.teavm.asm.ClassVisitor;
import org.teavm.asm.Type;
import org.teavm.metaprogramming.CompileTime;
import org.teavm.vm.spi.After;
import org.teavm.vm.spi.Before;
import org.teavm.vm.spi.Requires;

@CompileTime
final class TeaVMPluginReader {
   static final String DESCRIPTOR_LOCATION = "META-INF/services/org.teavm.vm.spi.TeaVMPlugin";
   static final String REQUIRES_DESC = Type.getDescriptor(Requires.class);
   static final String BEFORE_DESC = Type.getDescriptor(Before.class);
   static final String AFTER_DESC = Type.getDescriptor(After.class);

   private TeaVMPluginReader() {
   }

   static void load(ClassLoader classLoader, Consumer<String> consumer) {
      Set<String> unorderedPlugins = new HashSet<>();

      try {
         Enumeration<URL> resourceFiles = classLoader.getResources("META-INF/services/org.teavm.vm.spi.TeaVMPlugin");

         while (resourceFiles.hasMoreElements()) {
            URL resourceFile = resourceFiles.nextElement();

            try (BufferedReader input = new BufferedReader(new InputStreamReader(resourceFile.openStream(), StandardCharsets.UTF_8))) {
               readPlugins(input, unorderedPlugins);
            }
         }
      } catch (IOException e) {
         throw new IllegalStateException("Error loading plugins", e);
      }

      orderPlugins(classLoader, unorderedPlugins).forEach(consumer);
   }

   static List<String> orderPlugins(ClassLoader classLoader, Set<String> classNames) {
      Map<String, TeaVMPluginReader.PluginDescriptor> descriptors = new HashMap<>();

      try {
         for (String className : classNames) {
            TeaVMPluginReader.PluginDescriptor descriptor = new TeaVMPluginReader.PluginDescriptor();
            descriptor.name = className;
            if (readDescriptor(classLoader, className, descriptor)) {
               descriptors.put(className, descriptor);
            }
         }
      } catch (IOException e) {
         throw new IllegalStateException("Error ordering plugins", e);
      }

      findReachableDescriptors(descriptors);
      processDescriptors(descriptors);
      List<String> plugins = new ArrayList<>();
      Set<String> visited = new HashSet<>();
      Set<String> emmited = new HashSet<>();

      for (TeaVMPluginReader.PluginDescriptor descriptor : descriptors.values()) {
         orderDescriptors(descriptor, descriptors, plugins, visited, emmited, new ArrayList<>());
      }

      return plugins;
   }

   static void readPlugins(BufferedReader input, Set<String> plugins) throws IOException {
      while (true) {
         String line = input.readLine();
         if (line == null) {
            return;
         }

         line = line.trim();
         if (!line.isEmpty() && !line.startsWith("#")) {
            plugins.add(line);
         }
      }
   }

   private static boolean readDescriptor(ClassLoader classLoader, String className, TeaVMPluginReader.PluginDescriptor descriptor) throws IOException {
      try (InputStream input = classLoader.getResourceAsStream(className.replace('.', '/') + ".class")) {
         if (input == null) {
            return false;
         }

         ClassReader reader = new ClassReader(new BufferedInputStream(input));
         TeaVMPluginReader.PluginDescriptorFiller filler = new TeaVMPluginReader.PluginDescriptorFiller(descriptor);
         reader.accept(filler, 0);
         return true;
      }
   }

   private static void findReachableDescriptors(Map<String, TeaVMPluginReader.PluginDescriptor> descriptors) {
      Set<String> visited = new HashSet<>();

      for (String plugin : descriptors.keySet()) {
         isReachable(plugin, visited, descriptors);
      }
   }

   private static boolean isReachable(String plugin, Set<String> visited, Map<String, TeaVMPluginReader.PluginDescriptor> descriptors) {
      TeaVMPluginReader.PluginDescriptor descriptor = descriptors.get(plugin);
      if (descriptor == null) {
         return false;
      }

      if (!visited.add(plugin)) {
         return descriptor.reachable;
      }

      boolean reachable = true;

      for (String required : descriptor.requires) {
         if (!isReachable(required, visited, descriptors)) {
            reachable = false;
            break;
         }
      }

      descriptor.reachable = reachable;
      return reachable;
   }

   private static void processDescriptors(Map<String, TeaVMPluginReader.PluginDescriptor> descriptors) {
      for (TeaVMPluginReader.PluginDescriptor descriptor : descriptors.values()) {
         if (descriptor.after.length > 0 && descriptor.before.length > 0) {
            throw new IllegalStateException("Plugin " + descriptor.name + " has both before and after annotations");
         }

         descriptor.afterList.addAll(Arrays.asList(descriptor.after));

         for (String before : descriptor.before) {
            TeaVMPluginReader.PluginDescriptor beforeDescriptor = descriptors.get(before);
            if (beforeDescriptor != null && beforeDescriptor.reachable) {
               beforeDescriptor.afterList.add(descriptor.name);
            }
         }
      }
   }

   private static void orderDescriptors(
      TeaVMPluginReader.PluginDescriptor descriptor,
      Map<String, TeaVMPluginReader.PluginDescriptor> descriptors,
      List<String> list,
      Set<String> visited,
      Set<String> emmited,
      List<String> path
   ) {
      if (descriptor.reachable) {
         if (visited.add(descriptor.name)) {
            path.add(descriptor.name);

            for (String after : descriptor.afterList) {
               TeaVMPluginReader.PluginDescriptor afterDescriptor = descriptors.get(after);
               if (afterDescriptor != null && afterDescriptor.reachable) {
                  if (visited.contains(after) && !emmited.contains(after)) {
                     List<String> loop = new ArrayList<>();
                     Collections.reverse(path);

                     for (String pathElem : path) {
                        loop.add(pathElem);
                        if (pathElem.equals(afterDescriptor.name)) {
                           break;
                        }
                     }

                     Collections.reverse(loop);
                     throw new IllegalStateException("Circular dependency found: " + loop.stream().collect(Collectors.joining(" -> ")));
                  }

                  orderDescriptors(afterDescriptor, descriptors, list, visited, emmited, path);
               }
            }

            emmited.add(descriptor.name);
            path.remove(descriptor.name);
            list.add(descriptor.name);
         }
      }
   }

   static class PluginDescriptor {
      String name;
      String[] requires = new String[0];
      String[] before = new String[0];
      String[] after = new String[0];
      List<String> afterList = new ArrayList<>();
      boolean reachable;
   }

   static class PluginDescriptorFiller extends ClassVisitor {
      TeaVMPluginReader.PluginDescriptor descriptor;

      PluginDescriptorFiller(TeaVMPluginReader.PluginDescriptor descriptor) {
         super(589824);
         this.descriptor = descriptor;
      }

      public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
         if (desc.equals(TeaVMPluginReader.REQUIRES_DESC)) {
            return this.readClassArray(arr -> this.descriptor.requires = arr);
         } else if (desc.equals(TeaVMPluginReader.BEFORE_DESC)) {
            return this.readClassArray(arr -> this.descriptor.before = arr);
         } else {
            return desc.equals(TeaVMPluginReader.AFTER_DESC) ? this.readClassArray(arr -> this.descriptor.after = arr) : null;
         }
      }

      private AnnotationVisitor readClassArray(final Consumer<String[]> resultConsumer) {
         return new AnnotationVisitor(589824) {
            public AnnotationVisitor visitArray(String name) {
               final List<String> values = new ArrayList<>();
               return name.equals("value") ? new AnnotationVisitor(589824) {
                  public void visit(String name, Object value) {
                     values.add(((Type)value).getClassName());
                  }

                  public void visitEnd() {
                     resultConsumer.accept(values.toArray(new String[0]));
                  }
               } : null;
            }
         };
      }
   }
}
