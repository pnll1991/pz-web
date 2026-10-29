package org.teavm.parsing.resource;

import java.io.InputStream;
import java.util.Date;

public interface Resource {
   InputStream open();

   Date getModificationDate();
}
