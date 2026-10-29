package org.teavm.vm;

import java.io.IOException;
import java.io.OutputStream;

public interface BuildTarget {
   OutputStream createResource(String var1) throws IOException;
}
