package port.compat.javax.xml.transform;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import port.compat.javax.xml.transform.dom.*;
import port.compat.javax.xml.transform.stream.*;
import port.compat.org.w3c.dom.*;
/** Identity transform through native DOM; no stylesheet or entity resolver is fabricated. */
public class Transformer {
    private final Properties properties=new Properties();
    protected Transformer(){properties.setProperty("method","xml");properties.setProperty("encoding","UTF-8");properties.setProperty("omit-xml-declaration","no");properties.setProperty("indent","no");properties.setProperty("version","1.0");}
    public void setOutputProperty(String name,String value){Objects.requireNonNull(value);switch(name){case "method":if(!Set.of("xml","text").contains(value))throw new IllegalArgumentException("Unsupported transform method: "+value);break;case "encoding":Charset.forName(value);break;case "indent", "omit-xml-declaration":if(!Set.of("yes","no").contains(value))throw new IllegalArgumentException(name);break;case "version":if(!value.equals("1.0"))throw new IllegalArgumentException("Only XML 1.0 supported");break;case "{http://xml.apache.org/xslt}indent-amount":int amount=Integer.parseInt(value);if(amount<0||amount>32)throw new IllegalArgumentException("Indent amount");break;default:throw new IllegalArgumentException("Unsupported XML output property: "+name);}properties.setProperty(name,value);}
    public String getOutputProperty(String name){return properties.getProperty(name);}public Properties getOutputProperties(){return (Properties)properties.clone();}
    public void transform(Source source,Result target)throws TransformerException{
        try{Node node;
            if(source instanceof DOMSource dom)node=Objects.requireNonNull(dom.getNode());
            else if(source instanceof StreamSource stream){var builder=port.compat.javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();var input=new org.xml.sax.InputSource();input.setByteStream(stream.getInputStream());input.setCharacterStream(stream.getReader());if(input.getByteStream()!=null||input.getCharacterStream()!=null)node=builder.parse(input);else try(var file=new FileInputStream(local(stream.getSystemId()))){node=builder.parse(file);}}
            else throw new TransformerException("Only DOMSource and StreamSource are supported");
            if(target instanceof DOMResult dom){Node copy=node.cloneNode(true);if(dom.getNode()==null){if(copy instanceof Document)dom.setNode(copy);else{Document document=port.shim.BrowserDom.document(true);document.appendChild(document.importNode(copy,true));dom.setNode(document);}}else{Node parent=dom.getNode();Document doc=parent instanceof Document d?d:parent.getOwnerDocument();if(copy instanceof Document d)copy=d.getDocumentElement();parent.appendChild(doc.importNode(copy,true));}return;}
            if(!(target instanceof StreamResult stream))throw new TransformerException("Only DOMResult and StreamResult are supported");
            String text;
            if(properties.getProperty("method").equals("text")){text=node instanceof Document d?d.getDocumentElement().getTextContent():node.getTextContent();}
            else{Node copy=node.cloneNode(true);if(properties.getProperty("indent").equals("yes"))indent(copy,0,Integer.parseInt(properties.getProperty("{http://xml.apache.org/xslt}indent-amount","2")),false);text=port.shim.BrowserDom.serialize(copy);if(properties.getProperty("omit-xml-declaration").equals("no"))text="<?xml version=\"1.0\" encoding=\""+properties.getProperty("encoding")+"\"?>\n"+text;}
            if(stream.getWriter()!=null){stream.getWriter().write(text);stream.getWriter().flush();}
            else if(stream.getOutputStream()!=null){stream.getOutputStream().write(text.getBytes(Charset.forName(properties.getProperty("encoding"))));stream.getOutputStream().flush();}
            else try(var output=new OutputStreamWriter(new FileOutputStream(local(stream.getSystemId())),Charset.forName(properties.getProperty("encoding")))){output.write(text);}
        }catch(TransformerException error){throw error;}catch(Exception error){throw new TransformerException(error);}
    }
    private static File local(String id)throws IOException{if(id==null)throw new IOException("Transform has no stream or local systemId");if(id.startsWith("file:"))return new File(java.net.URI.create(id));if(id.contains(":"))throw new IOException("Only local VFS systemIds are supported");return new File(id);}
    private static void indent(Node node,int depth,int amount,boolean preserve){if(node instanceof Element element){String space=element.getAttributeNS("http://www.w3.org/XML/1998/namespace","space");if(space.equals("preserve"))preserve=true;else if(space.equals("default"))preserve=false;}
        List<Node> children=new ArrayList<>();boolean mixed=false;for(Node child=node.getFirstChild();child!=null;child=child.getNextSibling()){children.add(child);if((child.getNodeType()==Node.TEXT_NODE||child.getNodeType()==Node.CDATA_SECTION_NODE)&&!child.getTextContent().isBlank())mixed=true;}
        for(Node child:children)if(child.getNodeType()==Node.ELEMENT_NODE)indent(child,depth+(node instanceof Document?0:1),amount,preserve);
        if(preserve||mixed||children.isEmpty()||node instanceof Document)return;Document document=node.getOwnerDocument();boolean element=false;for(Node child:children)if(child.getNodeType()!=Node.TEXT_NODE){node.insertBefore(document.createTextNode("\n"+" ".repeat((depth+1)*amount)),child);element=true;}else if(child.getTextContent().isBlank())node.removeChild(child);if(element)node.appendChild(document.createTextNode("\n"+" ".repeat(depth*amount)));
    }
}
