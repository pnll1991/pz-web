package org.teavm.common;

import java.util.Arrays;

public class RecordArray {
   private int recordSize;
   private int arraysPerRecord;
   private int[] data;
   private int[] substart;
   private int[] subdata;
   private int size;

   RecordArray(int recordSize, int arraysPerRecord, int size, int[] data, int[] substart, int[] subdata) {
      this.recordSize = recordSize;
      this.arraysPerRecord = arraysPerRecord;
      this.size = size;
      this.data = data;
      this.substart = substart;
      this.subdata = subdata;
   }

   public RecordArray.Record get(int index) {
      if (index >= 0 && index < this.size) {
         return new RecordArray.Record(index * this.recordSize, index * this.arraysPerRecord);
      } else {
         throw new IndexOutOfBoundsException("Index " + index + " is outside of [0; " + this.size + ")");
      }
   }

   public int size() {
      return this.size;
   }

   public int getRecordSize() {
      return this.recordSize;
   }

   public int arraysPerRecord() {
      return this.arraysPerRecord;
   }

   public int[] cut(int index) {
      if (index >= 0 && index < this.recordSize) {
         int[] result = new int[this.size];

         for (int i = 0; i < this.size; i++) {
            result[i] = this.data[index];
            index += this.recordSize;
         }

         return result;
      } else {
         throw new IndexOutOfBoundsException("Index " + index + " is outside of [0; " + this.recordSize + ")");
      }
   }

   public class Record {
      int offset;
      int arrayOffset;

      Record(int offset, int arrayOffset) {
         this.offset = offset;
         this.arrayOffset = arrayOffset;
      }

      public int getPosition() {
         return this.offset / RecordArray.this.recordSize;
      }

      public int get(int index) {
         if (index >= RecordArray.this.recordSize) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index + " of " + RecordArray.this.recordSize);
         } else {
            return RecordArray.this.data[this.offset + index];
         }
      }

      public int size() {
         return RecordArray.this.recordSize;
      }

      public int[] getArray(int index) {
         if (index > RecordArray.this.arraysPerRecord) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index + " of " + RecordArray.this.arraysPerRecord);
         }

         int start = RecordArray.this.substart[this.arrayOffset + index];
         int end = RecordArray.this.substart[this.arrayOffset + index + 1];
         return Arrays.copyOfRange(RecordArray.this.subdata, start, end);
      }

      public int numArrays() {
         return RecordArray.this.arraysPerRecord;
      }
   }
}
