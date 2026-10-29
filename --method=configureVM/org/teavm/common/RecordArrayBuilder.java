package org.teavm.common;

public class RecordArrayBuilder {
   private int recordSize;
   private int arraysPerRecord;
   private int size;
   private IntegerArray data = new IntegerArray(1);
   private IntegerArray substart = new IntegerArray(1);
   private IntegerArray subdata = new IntegerArray(1);
   private IntegerArray subnext = new IntegerArray(1);

   public RecordArrayBuilder(int recordSize, int arraysPerRecord) {
      this.recordSize = recordSize;
      this.arraysPerRecord = arraysPerRecord;
   }

   public RecordArrayBuilder.Record get(int index) {
      if (index >= 0 && index < this.size) {
         return new RecordArrayBuilder.Record(index * this.recordSize, index * this.arraysPerRecord);
      } else {
         throw new IndexOutOfBoundsException("Index " + index + " is outside of [0; " + this.size + ")");
      }
   }

   public RecordArrayBuilder.Record add() {
      int offset = this.data.size();

      for (int i = 0; i < this.recordSize; i++) {
         this.data.add(0);
      }

      int arrayOffset = this.substart.size();

      for (int i = 0; i < this.arraysPerRecord; i++) {
         this.substart.add(-1);
      }

      this.size++;
      return new RecordArrayBuilder.Record(offset, arrayOffset);
   }

   public int size() {
      return this.size;
   }

   public int getRecordSize() {
      return this.recordSize;
   }

   public int getArraysPerRecord() {
      return this.arraysPerRecord;
   }

   public RecordArray build() {
      int[] builtSubstart = new int[this.substart.size() + 1];
      IntegerArray builtSubdata = new IntegerArray(1);

      for (int i = 0; i < this.substart.size(); i++) {
         for (int ptr = this.substart.get(i); ptr >= 0; ptr = this.subnext.get(ptr)) {
            builtSubdata.add(this.subdata.get(ptr));
         }

         builtSubstart[i + 1] = builtSubdata.size();
      }

      int[] builtSubdataArray = builtSubdata.getAll();

      for (int i = 1; i < builtSubstart.length; i++) {
         int start = builtSubstart[i - 1];
         int end = builtSubstart[i];
         int h = (builtSubstart[i] - start) / 2;

         for (int j = 0; j < h; j++) {
            int tmp = builtSubdataArray[start + j];
            builtSubdataArray[start + j] = builtSubdataArray[end - j - 1];
            builtSubdataArray[end - j - 1] = tmp;
         }
      }

      return new RecordArray(this.recordSize, this.arraysPerRecord, this.size, this.data.getAll(), builtSubstart, builtSubdataArray);
   }

   public class Record {
      private int offset;
      private int arrayOffset;

      public Record(int offset, int arrayOffset) {
         this.offset = offset;
         this.arrayOffset = arrayOffset;
      }

      public int getPosition() {
         return this.offset / RecordArrayBuilder.this.recordSize;
      }

      public int get(int index) {
         if (index >= RecordArrayBuilder.this.recordSize) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index + " of " + RecordArrayBuilder.this.recordSize);
         } else {
            return RecordArrayBuilder.this.data.get(index + this.offset);
         }
      }

      public void set(int index, int value) {
         if (index >= RecordArrayBuilder.this.recordSize) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index + " of " + RecordArrayBuilder.this.recordSize);
         }

         RecordArrayBuilder.this.data.set(index + this.offset, value);
      }

      public int size() {
         return RecordArrayBuilder.this.recordSize;
      }

      public int numArrays() {
         return RecordArrayBuilder.this.arraysPerRecord;
      }

      public RecordArrayBuilder.SubArray getArray(int index) {
         if (index > RecordArrayBuilder.this.arraysPerRecord) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index + " of " + RecordArrayBuilder.this.arraysPerRecord);
         } else {
            return RecordArrayBuilder.this.new SubArray(this.arrayOffset + index);
         }
      }
   }

   public class SubArray {
      private int offset;

      public SubArray(int offset) {
         this.offset = offset;
      }

      public int[] getData() {
         IntegerArray array = new IntegerArray(1);

         for (int ptr = RecordArrayBuilder.this.substart.get(this.offset); ptr >= 0; ptr = RecordArrayBuilder.this.subnext.get(ptr)) {
            array.add(RecordArrayBuilder.this.subdata.get(ptr));
         }

         int[] result = array.getAll();
         int half = result.length / 2;

         for (int i = 0; i < half; i++) {
            int tmp = result[i];
            result[i] = result[result.length - i - 1];
            result[result.length - i - 1] = tmp;
         }

         return result;
      }

      public void clear() {
         RecordArrayBuilder.this.substart.set(this.offset, -1);
      }

      public void add(int value) {
         int ptr = RecordArrayBuilder.this.substart.get(this.offset);
         RecordArrayBuilder.this.substart.set(this.offset, RecordArrayBuilder.this.subdata.size());
         RecordArrayBuilder.this.subdata.add(value);
         RecordArrayBuilder.this.subnext.add(ptr);
      }
   }
}
