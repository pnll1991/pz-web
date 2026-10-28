package port.runtime;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import port.compat.javax.xml.bind.JAXBContext;
import port.compat.javax.xml.bind.JAXBException;
import port.compat.javax.xml.bind.Marshaller;
import port.compat.javax.xml.bind.PropertyException;
import port.compat.org.w3c.dom.Document;
import port.compat.org.w3c.dom.Node;
import port.shim.BrowserDom;

/** Marshalling through the browser DOM: the same document/transform path PZXmlUtil uses afterwards. */
public final class PortMarshaller implements Marshaller {
    private final JAXBContext context;
    private final java.util.Map<String, Object> properties = new java.util.HashMap<>();
    private Listener listener;

    public PortMarshaller(JAXBContext context) { this.context = context; }

    public void marshal(Object value, Node node) throws JAXBException {
        if (node == null) throw new JAXBException("marshal target node is null");
        Document document = node instanceof Document d ? d : node.getOwnerDocument();
        if (document == null) throw new JAXBException("marshal target node has no owner document");
        if (listener != null) listener.beforeMarshal(value);
        try { PortXmlBeans.marshal(value, document, node); }
        finally { if (listener != null) listener.afterMarshal(value); }
    }

    public void marshal(Object value, OutputStream output) throws JAXBException {
        if (output == null) throw new JAXBException("marshal target stream is null");
        String text = toString(value);
        try {
            output.write(text.getBytes(charset()));
            if (Boolean.TRUE.equals(properties.get(JAXB_FORMATTED_OUTPUT))) output.write('\n');
            output.flush();
        } catch (IOException error) { throw new JAXBException("Cannot write marshalled XML", error); }
    }

    public void marshal(Object value, Writer writer) throws JAXBException {
        if (writer == null) throw new JAXBException("marshal target writer is null");
        try { writer.write(toString(value)); writer.flush(); }
        catch (IOException error) { throw new JAXBException("Cannot write marshalled XML", error); }
    }

    public void marshal(Object value, File file) throws JAXBException {
        try (var stream = new FileOutputStream(file)) { marshal(value, stream); }
        catch (IOException error) { throw new JAXBException("Cannot open " + file, error); }
    }

    private String toString(Object value) throws JAXBException {
        Document document = BrowserDom.document(true);
        marshal(value, document);
        Node root = document.getDocumentElement();
        if (root == null) throw new JAXBException("Marshalling produced no document element");
        String xml = BrowserDom.serialize(root);
        return Boolean.TRUE.equals(properties.get(JAXB_FORMATTED_OUTPUT)) ? format(xml) : xml;
    }

    private Charset charset() throws JAXBException {
        Object declared = properties.get(JAXB_ENCODING);
        if (declared == null) return StandardCharsets.UTF_8;
        try { return Charset.forName(String.valueOf(declared)); }
        catch (RuntimeException error) { throw new JAXBException("Unsupported JAXB encoding: " + declared, error); }
    }

    /** Line breaks per element: the browser serializer has no indent option. */
    private static String format(String xml) {
        StringBuilder result = new StringBuilder(xml.length() + 64);
        boolean insideTag = false;
        for (int i = 0; i < xml.length(); i++) {
            char c = xml.charAt(i);
            if (c == '<') { if (!insideTag && result.length() > 0) result.append('\n'); insideTag = true; }
            else if (c == '>') { insideTag = false; }
            result.append(c);
        }
        return result.toString();
    }

    public void setProperty(String name, Object value) throws PropertyException {
        if (name == null) throw new PropertyException("null property name");
        switch (name) {
            case JAXB_ENCODING -> {
                if (!(value instanceof String)) throw new PropertyException(name, value);
                try { charset(); } catch (JAXBException error) { throw new PropertyException(error); }
            }
            case JAXB_FORMATTED_OUTPUT, JAXB_FRAGMENT -> {
                if (!(value instanceof Boolean)) throw new PropertyException(name, value);
            }
            case JAXB_SCHEMA_LOCATION, JAXB_NO_NAMESPACE_SCHEMA_LOCATION -> {
                if (value != null && !(value instanceof String)) throw new PropertyException(name, value);
            }
            default -> throw new PropertyException("Unsupported JAXB property in the browser port: " + name);
        }
        properties.put(name, value);
    }

    public Object getProperty(String name) throws PropertyException {
        if (name == null) throw new PropertyException("null property name");
        if (!properties.containsKey(name)) throw new PropertyException("No such JAXB property: " + name);
        return properties.get(name);
    }

    public void setListener(Listener value) { listener = value; }
    public Listener getListener() { return listener; }
}
