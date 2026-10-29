package org.teavm.cache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileSymbolTable implements SymbolTable {
   private File file;
   private List<String> symbols = new ArrayList<>();
   private Map<String, Integer> symbolMap = new HashMap<>();
   private int firstUnstoredIndex;

   public FileSymbolTable(File file) {
      this.file = file;
   }

   public void update() throws IOException {
      this.symbols.clear();
      this.symbolMap.clear();
      this.firstUnstoredIndex = 0;

      try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(this.file)))) {
         while (true) {
            int length = input.read();
            if (length == -1) {
               return;
            }

            length = (length & 0xFF) << 8 | input.read() & 0xFF;
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < length; i++) {
               sb.append(input.readChar());
            }

            String symbol = sb.toString();
            this.symbolMap.put(symbol, this.symbols.size());
            this.symbols.add(symbol);
            this.firstUnstoredIndex = this.symbols.size();
         }
      }
   }

   public void flush() throws IOException {
      if (this.firstUnstoredIndex < this.symbols.size()) {
         try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(this.file, true)))) {
            while (this.firstUnstoredIndex < this.symbols.size()) {
               String symbol = this.symbols.get(this.firstUnstoredIndex);
               output.writeByte(symbol.length() >> 8 & 0xFF);
               output.writeByte(symbol.length() & 0xFF);

               for (int i = 0; i < symbol.length(); i++) {
                  output.writeChar(symbol.charAt(i));
               }

               this.firstUnstoredIndex++;
            }
         }
      }
   }

   @Override
   public String at(int index) {
      return this.symbols.get(index);
   }

   @Override
   public int lookup(String symbol) {
      Integer index = this.symbolMap.get(symbol);
      if (index == null) {
         index = this.symbols.size();
         this.symbolMap.put(symbol, index);
         this.symbols.add(symbol);
      }

      return index;
   }
}
