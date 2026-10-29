package org.teavm.common;

public final class HashUtils {
   private HashUtils() {
   }

   public static String[] createHashTable(String[] values) {
      int tableSize = values.length * 2;
      int maxTableSize = Math.min(values.length * 5 / 2, tableSize + 10);
      String[] bestTable = null;
      int bestCollisionRatio = 0;

      while (tableSize <= maxTableSize) {
         String[] table = new String[tableSize];
         int maxCollisionRatio = 0;

         for (String key : values) {
            int hashCode = key.hashCode();
            int collisionRatio = 0;

            while (true) {
               int index = Integer.remainderUnsigned(hashCode++, table.length);
               if (table[index] == null) {
                  table[index] = key;
                  maxCollisionRatio = Math.max(maxCollisionRatio, collisionRatio);
                  break;
               }

               collisionRatio++;
            }
         }

         if (bestTable == null || bestCollisionRatio > maxCollisionRatio) {
            bestCollisionRatio = maxCollisionRatio;
            bestTable = table;
         }

         tableSize++;
      }

      return bestTable;
   }
}
