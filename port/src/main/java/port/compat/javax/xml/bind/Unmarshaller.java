package port.compat.javax.xml.bind;

import java.io.*;
import port.compat.org.w3c.dom.Node;

/** Unmarshaller contract used by the pinned jar; only the members the game actually calls. */
public interface Unmarshaller {
    Object unmarshal(Node node) throws JAXBException;
    Object unmarshal(InputStream input) throws JAXBException;
    Object unmarshal(Reader reader) throws JAXBException;
    Object unmarshal(File file) throws JAXBException;
}
