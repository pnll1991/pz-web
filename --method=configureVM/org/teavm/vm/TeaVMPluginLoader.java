package org.teavm.vm;

import java.util.ArrayList;
import java.util.List;
import org.teavm.interop.PlatformMarker;
import org.teavm.vm.spi.TeaVMPlugin;

public final class TeaVMPluginLoader {
   private TeaVMPluginLoader() {
   }

   public static List<TeaVMPlugin> load(ClassLoader classLoader) {
      if (isBootstrap()) {
         return TeaVMBootstrapPluginLoader.loadPlugins(false);
      }

      List<TeaVMPlugin> plugins = new ArrayList<>();
      TeaVMPluginReader.load(classLoader, className -> plugins.add(instantiate(classLoader, className)));
      return plugins;
   }

   @PlatformMarker
   private static boolean isBootstrap() {
      return false;
   }

   private static TeaVMPlugin instantiate(ClassLoader classLoader, String className) {
      try {
         return (TeaVMPlugin)Class.forName(className, true, classLoader).newInstance();
      } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
         throw new IllegalStateException("Can't instantiate plugin " + className, e);
      }
   }
}
