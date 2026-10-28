package port.compat.javax.xml.bind;

import java.util.*;

/**
 * JAXB entry point for the browser port. The pinned jar's real factory (com.sun.xml.bind) is not
 * reachable in Wasm GC, so a context here only knows the classes it was asked about and delegates
 * to port.runtime.PortXmlBeans. Anything outside that subset throws JAXBException with its identity.
 */
public class JAXBContext {
    public static final String JAXB_CONTEXT_FACTORY = "javax.xml.bind.JAXBContextFactory";
    private final Class<?>[] classes;

    private JAXBContext(Class<?>[] classes) {
        this.classes = classes == null ? new Class<?>[0] : classes.clone();
    }

    public static JAXBContext newInstance(Class<?>... contextPath) throws JAXBException {
        return newInstance(contextPath, null);
    }

    public static JAXBContext newInstance(Class<?>[] contextPath, Map<String, ?> properties) throws JAXBException {
        if (contextPath == null || contextPath.length == 0) throw new JAXBException("JAXBContext needs at least one class");
        for (Class<?> cls : contextPath) {
            if (cls == null) throw new JAXBException("JAXBContext received a null class");
            if (cls.isPrimitive() || cls.isArray() || cls.isEnum())
                throw new JAXBException("Not a JAXB bean class: " + cls.getName());
        }
        return new JAXBContext(contextPath);
    }

    public static JAXBContext newInstance(String contextPath) throws JAXBException {
        throw new JAXBException("JAXB context paths are not resolvable in this AOT build: " + contextPath);
    }

    public static JAXBContext newInstance(String contextPath, ClassLoader loader) throws JAXBException {
        throw new JAXBException("JAXB context paths are not resolvable in this AOT build: " + contextPath);
    }

    public static JAXBContext newInstance(String contextPath, ClassLoader loader, Map<String, ?> properties) throws JAXBException {
        throw new JAXBException("JAXB context paths are not resolvable in this AOT build: " + contextPath);
    }

    public Marshaller createMarshaller() { return new port.runtime.PortMarshaller(this); }
    public Unmarshaller createUnmarshaller() { return new port.runtime.PortUnmarshaller(this); }

    public Class<?>[] beanClasses() { return classes; }

    /** Resolves the bean for a DOM element name, exactly as declared by @XmlRootElement/@XmlType. */
    public Class<?> resolve(String tag, Class<?> preferred) throws JAXBException {
        if (preferred != null) return preferred;
        for (Class<?> cls : classes) if (port.runtime.PortXmlBeans.rootNameOf(cls).equals(tag)) return cls;
        if (classes.length == 1) return classes[0];
        throw new JAXBException("No JAXB class of this context maps to element <" + tag + ">");
    }
}
