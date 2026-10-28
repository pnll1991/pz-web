package port.compat.javax.xml.bind;

import java.io.*;
import port.compat.org.w3c.dom.Node;

/**
 * Marshaller contract used by the pinned jar. Only the members the game actually calls are
 * declared; the rest of the RI surface is absent here and would fail as a linkage error.
 */
public interface Marshaller {
    String JAXB_ENCODING = "jaxb.encoding";
    String JAXB_FORMATTED_OUTPUT = "jaxb.formatted.output";
    String JAXB_SCHEMA_LOCATION = "jaxb.schemaLocation";
    String JAXB_NO_NAMESPACE_SCHEMA_LOCATION = "jaxb.noNamespaceSchemaLocation";
    String JAXB_FRAGMENT = "jaxb.fragment";

    abstract class Listener {
        public void beforeMarshal(Object source) {}
        public void afterMarshal(Object source) {}
    }

    void marshal(Object value, Node node) throws JAXBException;
    void marshal(Object value, OutputStream output) throws JAXBException;
    void marshal(Object value, Writer writer) throws JAXBException;
    void marshal(Object value, File file) throws JAXBException;
    void setProperty(String name, Object value) throws PropertyException;
    Object getProperty(String name) throws PropertyException;
    void setListener(Listener listener);
    Listener getListener();
}
