package port.compat.javax.xml.parsers;
import java.io.*;
import java.nio.charset.*;
import port.compat.org.w3c.dom.Document;
import org.xml.sax.*;
public class DocumentBuilder {
    private final boolean namespaces,disallowDoctype;
    DocumentBuilder(boolean namespaces,boolean disallowDoctype){this.namespaces=namespaces;this.disallowDoctype=disallowDoctype;}
    public Document newDocument(){return port.shim.BrowserDom.document(namespaces);}
    public Document parse(File file)throws IOException,SAXException{try(var stream=new FileInputStream(file)){return parse(stream);}}
    public Document parse(InputStream input)throws IOException,SAXException{return parse(new InputSource(input));}
    public Document parse(String uri)throws IOException,SAXException{throw new IOException("DocumentBuilder.parse URI requires an explicitly supplied local stream: "+uri);}
    public Document parse(InputSource input)throws IOException,SAXException{
        String source;
        if(input.getCharacterStream()!=null){var writer=new StringWriter();input.getCharacterStream().transferTo(writer);source=writer.toString();}
        else if(input.getByteStream()!=null){byte[] bytes=input.getByteStream().readAllBytes();Charset charset=StandardCharsets.UTF_8;
            if(input.getEncoding()!=null)charset=Charset.forName(input.getEncoding());
            else if(bytes.length>=2&&((bytes[0]&255)==255&&(bytes[1]&255)==254||(bytes[0]&255)==254&&(bytes[1]&255)==255))charset=StandardCharsets.UTF_16;
            else {String header=new String(bytes,0,Math.min(256,bytes.length),StandardCharsets.ISO_8859_1);var match=java.util.regex.Pattern.compile("^<\\?xml[^>]*encoding\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(header);if(match.find())charset=Charset.forName(match.group(1));}
            source=new String(bytes,charset);if(!source.isEmpty()&&source.charAt(0)=='\ufeff')source=source.substring(1);
        }else throw new IOException("XML InputSource has no local stream");
        if(disallowDoctype&&source.contains("<!DOCTYPE"))throw new SAXException("DOCTYPE is disabled");
        if(source.contains("<!ENTITY"))throw new SAXException("Custom XML entity declarations are unavailable in browser DOMParser");
        return port.shim.BrowserDom.parse(source,namespaces);
    }
}
