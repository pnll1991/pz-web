package org.teavm.vm;

import java.util.ArrayList;
import java.util.List;
import org.teavm.metaprogramming.CompileTime;
import org.teavm.metaprogramming.Meta;
import org.teavm.metaprogramming.Metaprogramming;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.Value;
import org.teavm.metaprogramming.reflect.ReflectMethod;
import org.teavm.vm.spi.TeaVMPlugin;

@CompileTime
public final class TeaVMBootstrapPluginLoader {
   private TeaVMBootstrapPluginLoader() {
   }

   @Meta
   public static native List<TeaVMPlugin> loadPlugins(boolean var0);

   private static void loadPlugins(Value<Boolean> ignore) {
      Value<List<TeaVMPlugin>> plugins = Metaprogramming.emit(() -> new ArrayList());
      List<String> pluginClassNames = new ArrayList<>();
      TeaVMPluginReader.load(Metaprogramming.getClassLoader(), pluginClassNames::add);

      for (String pluginClassName : pluginClassNames) {
         ReflectClass<?> cls = Metaprogramming.findClass(pluginClassName);
         ReflectMethod constructor = cls.getMethod("<init>", new ReflectClass[0]);
         Metaprogramming.emit(() -> ((List)plugins.get()).add((TeaVMPlugin)constructor.construct(new Object[0])));
      }

      Metaprogramming.exit(() -> plugins.get());
   }
}
