package org.teavm.vm;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.hppc.ObjectByteHashMap;
import org.teavm.hppc.ObjectByteMap;

public class IncrementalDirectoryBuildTarget implements BuildTarget {
   private File directory;
   private Set<String> writtenFiles = new HashSet<>();
   private Set<String> formerWrittenFiles = new HashSet<>();
   private ObjectByteMap<String> knownExistingFiles = new ObjectByteHashMap();
   private Map<String, IncrementalDirectoryBuildTarget.FileDescriptor> knownDescriptors = new HashMap<>();

   public IncrementalDirectoryBuildTarget(File directory) {
      this.directory = directory;
   }

   public void reset() {
      for (String fileName : this.formerWrittenFiles) {
         if (!this.writtenFiles.contains(fileName)) {
            new File(this.directory, fileName).delete();
            this.knownExistingFiles.put(fileName, (byte)0);
            this.knownDescriptors.remove(fileName);
         }
      }

      this.formerWrittenFiles.clear();
      this.formerWrittenFiles.addAll(this.writtenFiles);
      this.writtenFiles.clear();
   }

   @Override
   public OutputStream createResource(String fileName) {
      this.writtenFiles.add(fileName);
      return new IncrementalDirectoryBuildTarget.OutputStreamImpl(new File(this.directory, fileName), fileName);
   }

   private static long hash(byte[] data) {
      long hash = -3750763034362895579L;

      for (byte b : data) {
         hash *= 1099511628211L;
         hash ^= b & 0xFF;
      }

      return hash;
   }

   static class FileDescriptor {
      long hash;
      int length;
   }

   class OutputStreamImpl extends OutputStream {
      private File file;
      private String name;
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      OutputStreamImpl(File file, String name) {
         this.file = file;
         this.name = name;
      }

      @Override
      public void write(int b) throws IOException {
         this.checkNotClosed();
         this.bytes.write(b);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
         this.checkNotClosed();
         this.bytes.write(b, off, len);
      }

      @Override
      public void close() throws IOException {
         this.checkNotClosed();
         byte[] data = this.bytes.toByteArray();
         this.bytes = null;
         byte cachedExisting = IncrementalDirectoryBuildTarget.this.knownExistingFiles.getOrDefault(this.name, (byte)-1);
         if (cachedExisting < 0) {
            cachedExisting = (byte)(this.file.exists() ? 1 : 0);
            IncrementalDirectoryBuildTarget.this.knownExistingFiles.put(this.name, cachedExisting);
         }

         boolean exists = cachedExisting != 0;
         if (!exists || this.isChanged(this.file, data)) {
            this.file.getParentFile().mkdirs();

            try (OutputStream output = new BufferedOutputStream(new FileOutputStream(this.file))) {
               output.write(data);
            }
         }
      }

      private boolean isChanged(File file, byte[] data) throws IOException {
         IncrementalDirectoryBuildTarget.FileDescriptor descriptor = IncrementalDirectoryBuildTarget.this.knownDescriptors.get(this.name);
         long hash = IncrementalDirectoryBuildTarget.hash(data);
         if (descriptor != null) {
            if (descriptor.hash == hash && descriptor.length == data.length) {
               return false;
            }

            descriptor.hash = hash;
            descriptor.length = data.length;
            return true;
         } else {
            descriptor = new IncrementalDirectoryBuildTarget.FileDescriptor();
            IncrementalDirectoryBuildTarget.this.knownDescriptors.put(this.name, descriptor);
            descriptor.hash = hash;
            descriptor.length = data.length;
            InputStream input = new BufferedInputStream(new FileInputStream(file));
            byte[] buffer = new byte[4096];
            int index = 0;

            while (true) {
               int bytesRead = input.read(buffer);
               if (bytesRead < 0) {
                  return index < data.length;
               }

               if (bytesRead + index > data.length) {
                  return true;
               }

               for (int i = 0; i < bytesRead; i++) {
                  if (buffer[i] != data[index++]) {
                     return true;
                  }
               }
            }
         }
      }

      private void checkNotClosed() throws IOException {
         if (this.bytes == null) {
            throw new IOException("Already closed");
         }
      }
   }
}
