package org.teavm.backend.c.generators;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.ServiceLoader;
import org.teavm.backend.c.generate.CodeGeneratorUtil;
import org.teavm.backend.c.generate.CodeWriter;
import org.teavm.backend.c.generate.FileGenerator;
import org.teavm.backend.c.runtime.CResources;
import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;
import org.teavm.extension.ExtensionEnvironment;
import org.teavm.extension.spi.resources.ResourcesPolicy;
import org.teavm.interop.Address;
import org.teavm.model.CallLocation;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.parsing.resource.Resource;
import org.teavm.parsing.resource.ResourceProvider;
import org.teavm.runtime.Allocator;
import org.teavm.runtime.RuntimeClass;

public class CResourcesGenerator implements Generator {
   private static final MethodReference ALLOC_ARRAY_METHOD = new MethodReference(Allocator.class, "allocateArray", RuntimeClass.class, int.class, Address.class);
   private static final MethodReference CREATE_METHOD = new MethodReference(
      CResources.class, "create", String.class, int.class, int.class, CResources.Resource.class
   );
   private Properties properties;
   private ResourceProvider resourceProvider;
   private ClassLoader classLoader;
   private ExtensionEnvironment extensionEnv;
   private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

   public CResourcesGenerator(Properties properties, ResourceProvider resourceProvider, ClassLoader classLoader, ExtensionEnvironment extensionEnv) {
      this.properties = properties;
      this.resourceProvider = resourceProvider;
      this.classLoader = classLoader;
      this.extensionEnv = extensionEnv;
   }

   @Override
   public boolean canHandle(MethodReference method) {
      return method.getClassName().equals(CResources.class.getName()) && method.getName().equals("acquireResources");
   }

   @Override
   public void generate(GeneratorContext context, MethodReference method) {
      ListableClassReaderSource classSource = (ListableClassReaderSource)context.classSource();
      CResourcesGenerator.SupplierContextImpl supplierContext = new CResourcesGenerator.SupplierContextImpl(this.classLoader, classSource, this.properties);
      LinkedHashSet<String> resourceSet = new LinkedHashSet<>();

      for (ResourceSupplier supplier : ServiceLoader.load(ResourceSupplier.class, this.classLoader)) {
         String[] resources = supplier.supplyResources(supplierContext);
         if (resources != null) {
            resourceSet.addAll(Arrays.asList(resources));
         }
      }

      for (ResourcesPolicy policy : ServiceLoader.load(ResourcesPolicy.class, this.classLoader)) {
         policy.initialize(this.extensionEnv);
         String[] resources = policy.supplyResources(classSource.getClassNames());
         if (resources != null) {
            resourceSet.addAll(Arrays.asList(resources));
         }
      }

      ArrayList<CResourcesGenerator.ResourceDescriptor> descriptors = new ArrayList<>();
      ByteArrayOutputStream data = new ByteArrayOutputStream();
      CallLocation location = new CallLocation(new MethodReference(ClassLoader.class, "getResourceAsStream", String.class, InputStream.class));

      for (String resourceName : resourceSet) {
         Resource res = this.resourceProvider.getResource(resourceName);
         if (res == null) {
            context.diagnotics().error(location, "Resource not found: " + resourceName);
         } else {
            try (InputStream input = res.open()) {
               int start = data.size();
               input.transferTo(data);
               int end = data.size();
               descriptors.add(new CResourcesGenerator.ResourceDescriptor(resourceName, start, end));
            } catch (IOException e) {
               context.diagnotics().error(location, "Error occurred reading resource '" + resourceName + "'");
            }
         }
      }

      this.writeHeaderFile(context);
      this.writeDataArray(context, data.toByteArray());
      this.writeFunctionBody(context, descriptors);
   }

   private void writeHeaderFile(GeneratorContext context) {
      FileGenerator header = context.createHeaderFile("resources_gen.h");
      header.writer().println("extern const char teavm_resourceData[];");
   }

   private void writeDataArray(GeneratorContext context, byte[] data) {
      CodeWriter writer = context.writerBefore();
      if (data.length == 0) {
         writer.println("const char teavm_resourceData[] = \"\";");
      } else {
         writer.println("const char teavm_resourceData[] =");
         StringBuilder line = new StringBuilder("    \"");
         boolean lastHex = false;

         for (byte b : data) {
            int v = b & 255;
            boolean useHex = v < 32 || v > 126 || v == 34 || lastHex && isHexDigit(v);
            int len = useHex ? 4 : (v == 92 ? 2 : 1);
            if (line.length() + len > 80) {
               line.append('"');
               writer.println(line.toString());
               line.setLength(0);
               line.append("    \"");
               lastHex = false;
               useHex = v < 32 || v > 126 || v == 34;
            }

            if (useHex) {
               line.append('\\').append('x').append(HEX_CHARS[v >> 4]).append(HEX_CHARS[v & 15]);
               lastHex = true;
            } else if (v == 92) {
               line.append("\\\\");
               lastHex = false;
            } else {
               line.append((char)v);
               lastHex = false;
            }
         }

         line.append("\";");
         writer.println(line.toString());
      }
   }

   private static boolean isHexDigit(int c) {
      return c >= 48 && c <= 57 || c >= 97 && c <= 102 || c >= 65 && c <= 70;
   }

   private void writeFunctionBody(GeneratorContext context, ArrayList<CResourcesGenerator.ResourceDescriptor> descriptors) {
      CodeWriter writer = context.writer();
      ValueType.Object resourceType = ValueType.object(CResources.Resource.class.getName());
      ValueType.Array resourceArrayType = ValueType.arrayOf(resourceType);
      context.importMethod(ALLOC_ARRAY_METHOD, true);
      context.importMethod(CREATE_METHOD, true);
      context.includes().includePath("strings.h");
      context.includes().includePath("arrayclass.h");
      context.includes().includeClass(CResources.Resource.class.getName());
      String allocArrayName = context.names().forMethod(ALLOC_ARRAY_METHOD);
      String createName = context.names().forMethod(CREATE_METHOD);
      writer.print("TeaVM_Array* result = (TeaVM_Array*) " + allocArrayName + "(");
      CodeGeneratorUtil.writeTypeReference(writer, context.mainContext(), context.includes(), resourceArrayType);
      writer.println(", " + descriptors.size() + ");");
      writer.println("void** arrayData = TEAVM_ARRAY_DATA(result, void*);");

      for (int i = 0; i < descriptors.size(); i++) {
         CResourcesGenerator.ResourceDescriptor descriptor = descriptors.get(i);
         int nameIndex = context.stringPool().getStringIndex(descriptor.name);
         writer.print("arrayData[" + i + "] = " + createName + "(TEAVM_GET_STRING(" + nameIndex + "), ");
         CodeGeneratorUtil.writeIntValue(writer, descriptor.start);
         writer.print(", ");
         CodeGeneratorUtil.writeIntValue(writer, descriptor.end - descriptor.start);
         writer.println(");");
      }

      writer.println("return result;");
   }

   private static class ResourceDescriptor {
      String name;
      int start;
      int end;

      ResourceDescriptor(String name, int start, int end) {
         this.name = name;
         this.start = start;
         this.end = end;
      }
   }

   private static class SupplierContextImpl implements ResourceSupplierContext {
      private ClassLoader classLoader;
      private ListableClassReaderSource classSource;
      private Properties properties;

      SupplierContextImpl(ClassLoader classLoader, ListableClassReaderSource classSource, Properties properties) {
         this.classLoader = classLoader;
         this.classSource = classSource;
         this.properties = properties;
      }

      @Override
      public ClassLoader getClassLoader() {
         return this.classLoader;
      }

      @Override
      public ListableClassReaderSource getClassSource() {
         return this.classSource;
      }

      @Override
      public Properties getProperties() {
         return this.properties;
      }
   }
}
