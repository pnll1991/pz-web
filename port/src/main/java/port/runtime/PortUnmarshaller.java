package port.runtime;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import port.compat.javax.xml.bind.JAXBContext;
import port.compat.javax.xml.bind.JAXBException;
import port.compat.javax.xml.bind.Unmarshaller;
import port.compat.org.w3c.dom.Document;
import port.compat.org.w3c.dom.Element;
import port.compat.org.w3c.dom.Node;
import port.shim.BrowserDom;

/** Unmarshalling through the browser DOM parser and port.runtime.PortXmlBeans. */
public final class PortUnmarshaller implements Unmarshaller {
    private final JAXBContext context;

    public PortUnmarshaller(JAXBContext context) { this.context = context; }

    public Object unmarshal(Node node) throws JAXBException {
        if (node == null) throw new JAXBException("unmarshal source node is null");
        Element root = node instanceof Element element ? element
                : node instanceof Document document ? document.getDocumentElement() : null;
        if (root == null) throw new JAXBException("unmarshal source is not an element or document: " + node.getNodeName());
        return PortXmlBeans.unmarshal(context.resolve(root.getTagName(), null), root);
    }

    public Object unmarshal(InputStream input) throws JAXBException {
        if (input == null) throw new JAXBException("unmarshal source stream is null");
        try {
            byte[] bytes = input.readAllBytes();
            Document document = rootOf(source(bytes, detectCharset(bytes)));
            return PortXmlBeans.unmarshal(context.resolve(document.getDocumentElement().getTagName(), null),
                    document.getDocumentElement());
        } catch (IOException | org.xml.sax.SAXException error) {
            throw new JAXBException("Cannot parse XML for unmarshalling", error);
        }
    }

    public Object unmarshal(Reader reader) throws JAXBException {
        if (reader == null) throw new JAXBException("unmarshal source reader is null");
        try {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) text.append(buffer, 0, read);
            return PortXmlBeans.unmarshal(context.resolve(tagOf(text.toString()), null), rootOf(text.toString()).getDocumentElement());
        } catch (IOException | org.xml.sax.SAXException error) {
            throw new JAXBException("Cannot parse XML for unmarshalling", error);
        }
    }

    public Object unmarshal(File file) throws JAXBException {
        try (var stream = new FileInputStream(file)) { return unmarshal(stream); }
        catch (IOException error) { throw new JAXBException("Cannot read " + file, error); }
    }

    private static Document rootOf(String source) throws org.xml.sax.SAXException, JAXBException {
        Document document = BrowserDom.parse(source, true);
        Element root = document.getDocumentElement();
        if (root == null) throw new JAXBException("XML has no root element");
        return document;
    }

    private static String tagOf(String source) {
        int start = source.indexOf('<');
        while (start >= 0) {
            boolean comment = source.startsWith("<!--", start);
            boolean processing = source.startsWith("<?", start);
            if (comment || processing) {
                int end = source.indexOf(comment ? "-->" : "?>", start + 2);
                start = end < 0 ? -1 : source.indexOf('<', end + 2);
                continue;
            }
            int end = start + 1;
            while (end < source.length() && (Character.isLetterOrDigit(source.charAt(end)) || source.charAt(end) == '_' || source.charAt(end) == ':')) end++;
            return source.substring(start + 1, end);
        }
        return "";
    }

    private static Charset detectCharset(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 254) return StandardCharsets.UTF_16LE;
        if (bytes.length >= 2 && (bytes[0] & 255) == 254 && (bytes[1] & 255) == 255) return StandardCharsets.UTF_16BE;
        String header = new String(bytes, 0, Math.min(256, bytes.length), StandardCharsets.ISO_8859_1);
        var match = java.util.regex.Pattern.compile("^<\\?xml[^>]*encoding\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(header);
        if (match.find()) return Charset.forName(match.group(1));
        return StandardCharsets.UTF_8;
    }

    private static String source(byte[] bytes, Charset charset) {
        String text = new String(bytes, charset);
        return !text.isEmpty() && text.charAt(0) == '\ufeff' ? text.substring(1) : text;
    }
}
