package org.teavm.backend.wasm.intrinsics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.ServiceLoader;
import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.methods.WasmGCGenerationUtil;
import org.teavm.backend.wasm.generate.strings.WasmGCStringConstant;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmMemorySegment;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmInt32Constant;
import org.teavm.backend.wasm.model.instruction.WasmInt32Subtype;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.backend.wasm.runtime.WasmGCResources;
import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;
import org.teavm.extension.ExtensionEnvironment;
import org.teavm.extension.spi.resources.ResourcesPolicy;
import org.teavm.model.CallLocation;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.parsing.resource.Resource;

public class WasmGCResourcesIntrinsic implements WasmGCInlineIntrinsic {
   private Properties properties;
   private ByteArrayOutputStream resources = new ByteArrayOutputStream();
   private WasmGlobal baseGlobal;
   private WasmGCCodeGenContext genContext;
   private ExtensionEnvironment extensionEnv;

   public WasmGCResourcesIntrinsic(Properties properties, WasmGCCodeGenContext genContext, ExtensionEnvironment extensionEnv) {
      this.properties = properties;
      this.genContext = genContext;
      this.extensionEnv = extensionEnv;
   }

   public void writeModule(WasmModule module) {
      if (this.resources.size() == 0) {
         if (this.baseGlobal != null) {
            this.baseGlobal.getInitialValue().add(new WasmInt32Constant(0));
         }
      } else {
         WasmMemorySegment segment = new WasmMemorySegment();
         if (!module.getSegments().isEmpty()) {
            WasmMemorySegment lastSegment = module.getSegments().get(module.getSegments().size() - 1);
            segment.setOffset(lastSegment.getOffset() + lastSegment.getLength());
         }

         segment.setData(this.resources.toByteArray());
         module.getSegments().add(segment);
         this.baseGlobal.getInitialValue().clear();
         this.baseGlobal.getInitialValue().add(new WasmInt32Constant(segment.getOffset()));
      }
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      switch (invocation.getMethod().getName()) {
         case "acquireResources":
            this.acquireResources(builder);
            break;
         case "readSingleByte":
            context.generate(builder, invocation.getArguments().get(0));
            builder.loadI32(1, 0, WasmInt32Subtype.UINT8);
      }
   }

   private void acquireResources(WasmInstructionBuilder builder) {
      WasmGCResourcesIntrinsic.SupplierContextImpl supplierContext = new WasmGCResourcesIntrinsic.SupplierContextImpl(
         this.genContext.classLoader(), this.genContext.classes(), this.properties
      );
      LinkedHashSet<String> resourceSet = new LinkedHashSet<>();

      for (ResourceSupplier supplier : ServiceLoader.load(ResourceSupplier.class, this.genContext.classLoader())) {
         String[] resources = supplier.supplyResources(supplierContext);
         if (resources != null) {
            resourceSet.addAll(Arrays.asList(resources));
         }
      }

      for (ResourcesPolicy policy : ServiceLoader.load(ResourcesPolicy.class, this.genContext.classLoader())) {
         policy.initialize(this.extensionEnv);
         String[] resources = policy.supplyResources(this.genContext.classes().getClassNames());
         if (resources != null) {
            resourceSet.addAll(Arrays.asList(resources));
         }
      }

      ArrayList<WasmGCResourcesIntrinsic.ResourceDescriptor> descriptors = new ArrayList<>();
      CallLocation location = new CallLocation(new MethodReference(ClassLoader.class, "getResourceAsStream", String.class, InputStream.class));

      for (String resource : resourceSet) {
         Resource res = this.genContext.resources().getResource(resource);
         if (res == null) {
            this.genContext.diagnostics().error(location, "Resource not found: " + resource);
         } else {
            try (InputStream input = res.open()) {
               int start = this.resources.size();
               input.transferTo(this.resources);
               int end = this.resources.size();
               descriptors.add(new WasmGCResourcesIntrinsic.ResourceDescriptor(resource, start, end));
            } catch (IOException e) {
               this.genContext.diagnostics().error(location, "Error occurred reading resource '" + resource + "'");
            }
         }
      }

      this.baseGlobal = new WasmGlobal(this.genContext.names().topLevel("teavm@resourcesBaseAddress"), WasmType.INT32);
      this.genContext.module().globals.add(this.baseGlobal);
      WasmFunction constructor = this.genContext
         .functions()
         .forStaticMethod(new MethodReference(WasmGCResources.class, "create", String.class, int.class, int.class, WasmGCResources.Resource.class));
      WasmGCGenerationUtil.allocateArray(this.genContext.classInfoProvider(), ValueType.parse(WasmGCResources.Resource.class), builder, (array, b) -> {
         for (WasmGCResourcesIntrinsic.ResourceDescriptor descriptor : descriptors) {
            WasmGCStringConstant name = this.genContext.strings().getStringConstant(descriptor.name);
            b.getGlobal(name.global);
            b.getGlobal(this.baseGlobal).i32Const(descriptor.address).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.ADD);
            b.i32Const(descriptor.end - descriptor.address);
            b.call(constructor);
         }

         b.arrayNewFixed(array, descriptors.size());
      });
   }

   private static class ResourceDescriptor {
      String name;
      int address;
      int end;

      ResourceDescriptor(String name, int address, int end) {
         this.name = name;
         this.address = address;
         this.end = end;
      }
   }

   static class SupplierContextImpl implements ResourceSupplierContext {
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
