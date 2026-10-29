package org.teavm.common;

import java.util.Arrays;

public class IntegerStack {
   private int[] buffer;
   private int head;

   public IntegerStack(int capacity) {
      this.buffer = new int[capacity];
   }

   public void push(int value) {
      if (this.head == this.buffer.length) {
         this.buffer = Arrays.copyOf(this.buffer, Math.max(this.buffer.length * 2, 1));
      }

      this.buffer[this.head++] = value;
   }

   public int pop() {
      return this.buffer[--this.head];
   }

   public int peek() {
      return this.buffer[this.head - 1];
   }

   public boolean isEmpty() {
      return this.head == 0;
   }
}
