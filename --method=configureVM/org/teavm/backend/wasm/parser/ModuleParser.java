package org.teavm.backend.wasm.parser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.teavm.common.AsyncInputStream;
import org.teavm.common.Promise;

public class ModuleParser {
   private static final int WASM_HEADER_SIZE = 8;
   private AsyncInputStream reader;
   private int pos;
   private int posBefore;
   private byte[] buffer = new byte[256];
   private int posInBuffer;
   private int bufferLimit;
   private int currentLEB;
   private int currentLEBShift;
   private boolean eof;
   private int bytesInLEB;
   private int sectionCode;
   private List<byte[]> chunks = new ArrayList<>();

   public ModuleParser(AsyncInputStream inputStream) {
      this.reader = inputStream;
   }

   public Promise<Void> parse() {
      return this.parseHeader().thenAsync(v -> this.parseSections());
   }

   private Promise<Void> parseHeader() {
      return this.fillAtLeast(16).thenVoid(v -> {
         if (this.remaining() < 8) {
            this.error("Invalid WebAssembly header");
         }

         if (this.readInt32() != 1836278016) {
            this.error("Invalid WebAssembly magic number");
         }

         if (this.readInt32() != 1) {
            this.error("Unsupported WebAssembly version");
         }
      });
   }

   private Promise<Void> parseSections() {
      return this.readLEB().thenAsync(n -> {
         if (n == null) {
            return Promise.VOID;
         }

         this.sectionCode = n;
         return this.continueParsingSection().thenAsync(v -> this.parseSections());
      });
   }

   private Promise<Void> continueParsingSection() {
      return this.requireLEB("Unexpected end of file reading section length")
         .thenAsync(
            sectionSize -> {
               if (this.sectionCode == 0) {
                  return this.parseSection(this.pos + sectionSize);
               }

               Consumer<byte[]> consumer = this.getSectionConsumer(this.sectionCode, this.pos, null);
               return consumer == null
                  ? this.skip(sectionSize, "Error skipping section " + this.sectionCode + " of size " + sectionSize)
                  : this.readBytes(sectionSize, "Error reading section " + this.sectionCode + " content").thenVoid(consumer);
            }
         );
   }

   private Promise<Void> parseSection(int limit) {
      return this.readString("Error reading custom section name")
         .thenAsync(
            name -> {
               Consumer<byte[]> consumer = this.getSectionConsumer(0, this.pos, name);
               return consumer != null
                  ? this.readBytes(limit - this.pos, "Error reading section '" + name + "' content").thenVoid(consumer)
                  : this.skip(limit - this.pos, "Error skipping section '" + name + "'");
            }
         );
   }

   protected Consumer<byte[]> getSectionConsumer(int code, int pos, String name) {
      return null;
   }

   private Promise<String> readString(String error) {
      return this.readBytes(error).then(b -> new String(b, StandardCharsets.UTF_8));
   }

   private Promise<byte[]> readBytes(String error) {
      return this.requireLEB(error + ": error parsing size").thenAsync(size -> this.readBytes(size, error + ": error reading content"));
   }

   private Promise<byte[]> readBytes(int count, String error) {
      return this.readBytesImpl(count, error).then(v -> {
         byte[] result = new byte[count];
         int i = 0;

         for (byte[] chunk : this.chunks) {
            System.arraycopy(chunk, 0, result, i, chunk.length);
            i += chunk.length;
         }

         this.chunks.clear();
         return result;
      });
   }

   private Promise<Void> readBytesImpl(int count, String error) {
      this.posBefore = this.pos;
      int min = Math.min(count, this.remaining());
      byte[] chunk = new byte[min];
      System.arraycopy(this.buffer, this.posInBuffer, chunk, 0, min);
      this.chunks.add(chunk);
      this.pos += min;
      this.posInBuffer += min;
      if (count > min) {
         if (this.eof) {
            this.error(error);
         }

         return this.fill().thenAsync(x -> this.readBytesImpl(count - min, error));
      } else {
         return Promise.VOID;
      }
   }

   private Promise<Integer> requireLEB(String errorMessage) {
      return this.readLEB().then(n -> {
         if (n == null) {
            this.error(errorMessage);
         }

         return n;
      });
   }

   private Promise<Integer> readLEB() {
      this.posBefore = this.pos;
      this.currentLEB = 0;
      this.bytesInLEB = 0;
      this.currentLEBShift = 0;
      return this.continueLEB();
   }

   private Promise<Integer> continueLEB() {
      while (this.posInBuffer < this.bufferLimit) {
         byte b = this.buffer[this.posInBuffer++];
         this.pos++;
         this.bytesInLEB++;
         int digit = b & 127;
         if (digit << this.currentLEBShift >> this.currentLEBShift != digit) {
            this.error("LEB represents too big number");
         }

         this.currentLEB = this.currentLEB | digit << this.currentLEBShift;
         if ((b & 128) == 0) {
            return Promise.of(this.currentLEB);
         }

         this.currentLEBShift += 7;
      }

      return this.fill().thenAsync(bytesRead -> {
         if (this.eof) {
            if (this.bytesInLEB > 0) {
               this.error("Unexpected end of file reached reading LEB");
            }

            return Promise.of(null);
         } else {
            return this.continueLEB();
         }
      });
   }

   private int readInt32() {
      this.posBefore = this.pos;
      int result = this.buffer[this.posInBuffer] & 255
         | (this.buffer[this.posInBuffer + 1] & 255) << 8
         | (this.buffer[this.posInBuffer + 2] & 255) << 16
         | (this.buffer[this.posInBuffer + 3] & 255) << 24;
      this.posInBuffer += 4;
      this.pos += 4;
      return result;
   }

   private int remaining() {
      return this.bufferLimit - this.posInBuffer;
   }

   private Promise<Void> skip(int bytes, String error) {
      if (bytes <= this.remaining()) {
         this.posInBuffer += bytes;
         this.pos += bytes;
         return Promise.VOID;
      } else {
         this.posBefore = this.pos;
         this.pos = this.pos + this.remaining();
         bytes -= this.remaining();
         this.posInBuffer = 0;
         this.bufferLimit = 0;
         return this.skipImpl(bytes, error);
      }
   }

   private Promise<Void> skipImpl(int bytes, String error) {
      return this.reader.skip(bytes).thenAsync(bytesSkipped -> {
         if (bytesSkipped < 0) {
            this.error(error);
         }

         this.pos = this.pos + bytesSkipped;
         return bytes > bytesSkipped ? this.skipImpl(bytes - bytesSkipped, error) : Promise.VOID;
      });
   }

   private Promise<Void> fillAtLeast(int least) {
      return this.fill().thenAsync(x -> this.fillAtLeastImpl(least));
   }

   private Promise<Void> fillAtLeastImpl(int least) {
      return !this.eof && least > this.remaining()
         ? this.reader.read(this.buffer, this.bufferLimit, Math.min(least, this.buffer.length - this.bufferLimit)).thenAsync(bytesRead -> {
            if (bytesRead < 0) {
               this.eof = true;
            } else {
               this.bufferLimit = this.bufferLimit + bytesRead;
            }

            return this.fillAtLeastImpl(least);
         })
         : Promise.VOID;
   }

   private Promise<Void> fill() {
      return this.readNext(this.buffer.length - this.remaining());
   }

   private Promise<Void> readNext(int bytes) {
      if (this.eof) {
         return Promise.VOID;
      }

      if (this.posInBuffer > 0 && this.posInBuffer < this.bufferLimit) {
         System.arraycopy(this.buffer, this.posInBuffer, this.buffer, 0, this.bufferLimit - this.posInBuffer);
      }

      this.bufferLimit = this.bufferLimit - this.posInBuffer;
      this.posInBuffer = 0;
      return this.reader.read(this.buffer, this.bufferLimit, bytes).thenVoid(bytesRead -> {
         if (bytesRead < 0) {
            this.eof = true;
         } else {
            this.bufferLimit = this.bufferLimit + bytesRead;
         }
      });
   }

   private void error(String message) {
      throw new ParseException(message, this.posBefore);
   }
}
