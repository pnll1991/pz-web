package org.teavm.backend.wasm.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.teavm.interop.Intrinsified;

public final class WasmGCResources {
   private static Map<String, WasmGCResources.Resource> resources = new HashMap<>();

   private WasmGCResources() {
   }

   @Intrinsified
   private static native WasmGCResources.Resource[] acquireResources();

   private static WasmGCResources.Resource create(String name, int start, int length) {
      return new WasmGCResources.Resource(name, start, start + length);
   }

   public static InputStream getResource(String name) {
      WasmGCResources.Resource resource = resources.get(name);
      return resource == null ? null : new WasmGCResources.ResourceInputStream(resource.start, resource.end);
   }

   private static void readBytes(byte[] bytes, int off, int len, int fromAddress) {
      for (int i = 0; i < len; i++) {
         bytes[i + off] = (byte)readSingleByte(fromAddress++);
      }
   }

   @Intrinsified
   private static native int readSingleByte(int var0);

   static {
      for (WasmGCResources.Resource resource : acquireResources()) {
         resources.put(resource.name, resource);
      }
   }

   public static class Resource {
      public final String name;
      public final int start;
      public final int end;

      public Resource(String name, int start, int end) {
         this.name = name;
         this.start = start;
         this.end = end;
      }
   }

   public static final class ResourceInputStream extends InputStream {
      private int address;
      private int end;

      private ResourceInputStream(int address, int end) {
         this.address = address;
         this.end = end;
      }

      @Override
      public int read() throws IOException {
         return this.address < this.end ? WasmGCResources.readSingleByte(this.address++) : -1;
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
         if (this.address >= this.end) {
            return -1;
         }

         int bytesToRead = Math.min(len, this.end - this.address);
         WasmGCResources.readBytes(b, off, bytesToRead, this.address);
         this.address += bytesToRead;
         return bytesToRead;
      }
   }
}
