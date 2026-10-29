package org.teavm.common;

import java.util.Arrays;

public class IntegerArray {
   private static final int[] emptyData = new int[0];
   private int[] data;
   private int sz;

   private IntegerArray() {
   }

   public IntegerArray(int capacity) {
      this.data = new int[capacity];
   }

   public static IntegerArray of(int... values) {
      IntegerArray array = new IntegerArray();
      array.data = Arrays.copyOf(values, values.length);
      array.sz = values.length;
      return array;
   }

   public void clear() {
      this.sz = 0;
   }

   public void optimize() {
      if (this.sz > this.data.length) {
         this.data = Arrays.copyOf(this.data, this.sz);
      }
   }

   public int[] getAll() {
      return this.sz > 0 ? Arrays.copyOf(this.data, this.sz) : emptyData;
   }

   public int get(int index) {
      return this.data[index];
   }

   public int[] getRange(int start, int end) {
      return Arrays.copyOfRange(this.data, start, end);
   }

   public void set(int index, int value) {
      if (index >= this.sz) {
         throw new IndexOutOfBoundsException("Index " + index + " is greater than the list size " + this.sz);
      }

      this.data[index] = value;
   }

   private void ensureCapacity() {
      if (this.sz > this.data.length) {
         int newCap = this.data.length;

         while (this.sz > newCap) {
            newCap = newCap * 3 / 2 + 1;
         }

         this.data = Arrays.copyOf(this.data, newCap);
      }
   }

   public int size() {
      return this.sz;
   }

   public void addAll(int[] items) {
      int target = this.sz;
      this.sz += items.length;
      this.ensureCapacity();
      System.arraycopy(items, 0, this.data, target, items.length);
   }

   public void add(int item) {
      this.sz++;
      this.ensureCapacity();
      this.data[this.sz - 1] = item;
   }

   public void remove(int index) {
      this.remove(index, 1);
   }

   public void remove(int index, int count) {
      System.arraycopy(this.data, index + count, this.data, index, this.sz - index - count);
      this.sz -= count;
   }

   public boolean contains(int item) {
      for (int i = 0; i < this.sz; i++) {
         if (this.data[i] == item) {
            return true;
         }
      }

      return false;
   }
}
