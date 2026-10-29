package org.teavm.dependency;

class ConsumerWithNode {
   DependencyConsumer[] consumers;
   DependencyNode node;

   ConsumerWithNode(DependencyConsumer[] consumers, DependencyNode node) {
      this.consumers = consumers;
      this.node = node;
   }
}
