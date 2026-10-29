package org.teavm.backend.c.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.teavm.interop.Address;

public final class CResources {
   private static Map<String, CResources.Resource> resources = new HashMap<>();

   private CResources() {
   }

   private static native CResources.Resource[] acquireResources();

   private static native Address getBaseAddress();

   private static CResources.Resource create(String name, int start, int length) {
      return new CResources.Resource(name, getBaseAddress().add(start), length);
   }

   public static InputStream getResource(String name) {
      CResources.Resource resource = resources.get(name);
      return resource == null ? null : new CResources.ResourceInputStream(resource.start, resource.length);
   }

   static {
      for (CResources.Resource resource : acquireResources()) {
         resources.put(resource.name, resource);
      }
   }

   public static class Resource {
      public final String name;
      public final Address start;
      public final int length;

      public Resource(String name, Address start, int length) {
         this.name = name;
         this.start = start;
         this.length = length;
      }
   }

   public static final class ResourceInputStream extends InputStream {
      private Address address;
      private int remaining;

      private ResourceInputStream(Address address, int remaining) {
         this.address = address;
         this.remaining = remaining;
      }

      @Override
      public int read() throws IOException {
         if (this.remaining <= 0) {
            return -1;
         }

         int b = this.address.getByte() & 255;
         this.address = this.address.add(1);
         this.remaining--;
         return b;
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
         if (this.remaining <= 0) {
            return -1;
         }

         int bytesToRead = Math.min(len, this.remaining);
         Address.moveMemoryBlock(this.address, Address.ofData(b).add(off), bytesToRead);
         this.address = this.address.add(bytesToRead);
         this.remaining -= bytesToRead;
         return bytesToRead;
      }
   }
}
