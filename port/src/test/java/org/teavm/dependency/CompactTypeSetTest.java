package org.teavm.dependency;

import java.util.HashSet;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.teavm.model.ValueType;
import static org.junit.jupiter.api.Assertions.*;

class CompactTypeSetTest {
    @Test void matchesIdentitySetForInternedTypesAndIndependentConsumers() {
        DependencyType[] types = new DependencyType[20000];
        for (int i = 0; i < types.length; i++)
            types[i] = new DependencyType(ValueType.object("fixture.Type" + i), i);
        var reference = new HashSet<DependencyType>();
        var compact = new CompactTypeSet();
        var independent = new CompactTypeSet();
        var random = new Random(7429);
        for (int i = 0; i < 100000; i++) {
            var type = types[random.nextInt(types.length)];
            assertEquals(reference.add(type), compact.add(type));
        }
        for (var type : types) {
            assertEquals(reference.add(type), compact.add(type));
            assertTrue(independent.add(type));
            assertFalse(independent.add(type));
        }
    }
}
