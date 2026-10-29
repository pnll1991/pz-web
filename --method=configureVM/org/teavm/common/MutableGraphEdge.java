package org.teavm.common;

public class MutableGraphEdge {
   MutableGraphEdge back;
   MutableGraphNode first;
   MutableGraphNode second;

   public MutableGraphNode getFirst() {
      return this.first;
   }

   public void setFirst(MutableGraphNode first) {
      this.back.setSecond(first);
   }

   public MutableGraphNode getSecond() {
      return this.second;
   }

   public void setSecond(MutableGraphNode second) {
      if (this.second != second) {
         this.second.edges.remove(this.first);
         this.first.edges.remove(this.second);
         if (!second.edges.containsKey(this.first)) {
            this.second = second;
            this.back.first = second;
            second.edges.put(this.first, this.back);
            this.first.edges.put(second, this);
         } else {
            this.first = null;
            this.second = null;
            this.back.first = null;
            this.back.second = null;
         }
      }
   }

   public MutableGraphEdge getBack() {
      return this.back;
   }

   @Override
   public String toString() {
      return String.valueOf(this.second.getTag());
   }
}
