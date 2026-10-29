package org.teavm.backend.wasm.runtime;

import org.teavm.interop.Intrinsified;

public final class StringInternPool {
   private static StringInternPool.Entry[] table = new StringInternPool.Entry[16];
   private static int occupiedCells;
   private static int occupiedCellsThreshold = table.length * 3 / 4;

   private StringInternPool() {
   }

   public static String query(String s) {
      int hash = s.hashCode();
      int index = Integer.remainderUnsigned(hash, table.length);

      for (StringInternPool.Entry entry = table[index]; entry != null; entry = entry.next) {
         if (entry.hash == hash) {
            String value = entry.getValue();
            if (value.equals(s)) {
               return value;
            }
         }
      }

      if (table[index] == null) {
         if (++occupiedCells > occupiedCellsThreshold) {
            rehash();
         }

         index = Integer.remainderUnsigned(hash, table.length);
         if (table[index] == null) {
            occupiedCells++;
         }
      }

      table[index] = new StringInternPool.Entry(index, hash, table[index], s);
      return s;
   }

   private static void rehash() {
      StringInternPool.Entry[] oldTable = table;
      table = new StringInternPool.Entry[oldTable.length * 2];
      occupiedCells = 0;
      occupiedCellsThreshold = table.length * 3 / 4;

      for (StringInternPool.Entry value : oldTable) {
         StringInternPool.Entry entry = value;

         while (entry != null) {
            StringInternPool.Entry next = entry.next;
            int index = Integer.remainderUnsigned(entry.hash, table.length);
            entry.index = index;
            entry.next = table[index];
            if (entry.next == null) {
               occupiedCells++;
            }

            table[index] = entry;
            entry = next;
         }
      }
   }

   static void remove(StringInternPool.Entry entry) {
      int index = entry.index;
      StringInternPool.Entry previous = null;

      for (StringInternPool.Entry e = table[index]; e != null; e = e.next) {
         if (e == entry) {
            if (previous == null) {
               table[index] = e.next;
               if (e.next == null) {
                  occupiedCells--;
               }
            } else {
               previous.next = e.next;
            }
            break;
         }

         previous = e;
      }
   }

   static class Entry {
      int index;
      final int hash;
      StringInternPool.Entry next;

      Entry(int index, int hash, StringInternPool.Entry next, String str) {
         this.index = index;
         this.hash = hash;
         this.next = next;
         this.setValue(str);
      }

      @Intrinsified
      final native String getValue();

      @Intrinsified
      final native void setValue(String var1);
   }
}
