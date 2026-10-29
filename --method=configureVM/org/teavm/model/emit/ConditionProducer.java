package org.teavm.model.emit;

@FunctionalInterface
public interface ConditionProducer {
   ConditionEmitter produce();
}
