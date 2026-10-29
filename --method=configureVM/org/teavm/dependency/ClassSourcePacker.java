package org.teavm.dependency;

import java.util.Collection;
import org.teavm.model.ClassReaderSource;

public interface ClassSourcePacker {
   ClassReaderSource pack(ClassReaderSource var1, Collection<? extends String> var2);
}
