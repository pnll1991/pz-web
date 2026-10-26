package port.shim;
import java.util.*;
import org.teavm.jso.*;
import port.compat.org.w3c.dom.*;

/** Java DOM nodes wrap the browser XML document, with identity preserved per document. */
public final class BrowserDom {
    private final Map<Integer,DomNode> wrappers=new HashMap<>();
    private final boolean namespaces;
    private BrowserDom(boolean namespaces){this.namespaces=namespaces;}
    public static Document parse(String source,boolean namespaces)throws org.xml.sax.SAXException{
        JSObject document=parseXml(source);String error=parseError(document);if(error!=null)throw new org.xml.sax.SAXException(error);
        return (Document)new BrowserDom(namespaces).wrap(document);
    }
    public static Document document(boolean namespaces){return (Document)new BrowserDom(namespaces).wrap(newDocument());}
    public static String serialize(Node node){return serializeNative(((DomNode)node).node);}
    private DomNode wrap(JSObject node){if(node==null)return null;int id=identity(node);DomNode existing=wrappers.get(id);if(existing!=null)return existing;DomNode created=switch(number(node,"nodeType")){case 9->new DomDocument(node);case 1->new DomElement(node);case 2->new DomAttr(node);case 3,4->new DomText(node);case 7->new DomProcessing(node);default->new DomNode(node);};wrappers.put(id,created);return created;}
    private NodeList list(JSObject nodes){return new NodeList(){public int getLength(){return number(nodes,"length");}public Node item(int index){return wrap(at(nodes,index));}};}
    private class DomNode implements Node {
        final JSObject node;DomNode(JSObject node){this.node=node;}
        public String getNodeName(){return string(node,"nodeName");} public String getNodeValue(){return string(node,"nodeValue");}public void setNodeValue(String value){set(node,"nodeValue",value);}
        public short getNodeType(){return (short)number(node,"nodeType");}
        public Node getParentNode(){return wrap(property(node,"parentNode"));}public Node getFirstChild(){return wrap(property(node,"firstChild"));}public Node getLastChild(){return wrap(property(node,"lastChild"));}public Node getNextSibling(){return wrap(property(node,"nextSibling"));}public Node getPreviousSibling(){return wrap(property(node,"previousSibling"));}
        public NodeList getChildNodes(){return list(property(node,"childNodes"));}
        public NamedNodeMap getAttributes(){JSObject attrs=property(node,"attributes");if(attrs==null)return null;return new NamedNodeMap(){public int getLength(){return number(attrs,"length");}public Node item(int index){return wrap(at(attrs,index));}public Node getNamedItem(String name){return wrap(call(attrs,"getNamedItem",name));}};}
        public Document getOwnerDocument(){return (Document)wrap(property(node,"ownerDocument"));}
        public Node appendChild(Node child){return wrap(mutate(node,"appendChild",((DomNode)child).node,null));}
        public Node removeChild(Node child){return wrap(mutate(node,"removeChild",((DomNode)child).node,null));}
        public Node insertBefore(Node child,Node reference){return wrap(mutate(node,"insertBefore",((DomNode)child).node,reference==null?null:((DomNode)reference).node));}
        public Node replaceChild(Node child,Node old){return wrap(mutate(node,"replaceChild",((DomNode)child).node,((DomNode)old).node));}
        public boolean hasChildNodes(){return getChildNodes().getLength()>0;}public boolean hasAttributes(){var a=getAttributes();return a!=null&&a.getLength()>0;}
        public void normalize(){normalizeNative(node);}public String getTextContent(){return string(node,"textContent");}public void setTextContent(String text){set(node,"textContent",text);}
        public String getNamespaceURI(){return namespaces?string(node,"namespaceURI"):null;}public String getLocalName(){return namespaces?string(node,"localName"):null;}public String getPrefix(){return namespaces?string(node,"prefix"):null;}
        public boolean isSameNode(Node other){return other instanceof BrowserDom.DomNode n&&identity(node)==identity(n.node);}
        public Node cloneNode(boolean deep){return wrap(cloneNative(node,deep));}
    }
    private final class DomDocument extends DomNode implements Document {
        DomDocument(JSObject node){super(node);}
        public Element getDocumentElement(){return (Element)wrap(property(node,"documentElement"));}
        public NodeList getElementsByTagName(String name){return list(call(node,"getElementsByTagName",name));}
        public Element createElement(String name){return (Element)wrap(call(node,"createElement",name));}
        public Element createElementNS(String ns,String name){return (Element)wrap(callTwo(node,"createElementNS",ns,name));}
        public Text createTextNode(String text){return (Text)wrap(call(node,"createTextNode",text));}
        public ProcessingInstruction createProcessingInstruction(String target,String data){return (ProcessingInstruction)wrap(callTwo(node,"createProcessingInstruction",target,data));}
        public Node importNode(Node value,boolean deep){return wrap(importNative(node,((DomNode)value).node,deep));}
    }
    private final class DomElement extends DomNode implements Element {
        DomElement(JSObject node){super(node);}
        public String getTagName(){return string(node,"tagName");}
        public String getAttribute(String name){String value=attribute(node,name);return value==null?"":value;}
        public void setAttribute(String name,String value){attributeSet(node,null,name,value,false);}
        public void removeAttribute(String name){attributeRemove(node,null,name,false);}
        public boolean hasAttribute(String name){return attribute(node,name)!=null;}
        public NodeList getElementsByTagName(String name){return list(call(node,"getElementsByTagName",name));}
        public String getAttributeNS(String ns,String name){String value=attributeNS(node,ns,name);return value==null?"":value;}
        public void setAttributeNS(String ns,String name,String value){attributeSet(node,ns,name,value,true);}
        public void removeAttributeNS(String ns,String name){attributeRemove(node,ns,name,true);}
        public boolean hasAttributeNS(String ns,String name){return attributeNS(node,ns,name)!=null;}
        public Attr setAttributeNode(Attr value){return (Attr)wrap(mutate(node,"setAttributeNode",((DomNode)value).node,null));}
    }
    private final class DomAttr extends DomNode implements Attr {DomAttr(JSObject node){super(node);}public String getName(){return string(node,"name");}public String getValue(){return string(node,"value");}public void setValue(String value){set(node,"value",value);}}
    private final class DomText extends DomNode implements Text {DomText(JSObject node){super(node);}public String getData(){return string(node,"data");}public void setData(String value){set(node,"data",value);}}
    private final class DomProcessing extends DomNode implements ProcessingInstruction {DomProcessing(JSObject node){super(node);}public String getTarget(){return string(node,"target");}public String getData(){return string(node,"data");}}
    @JSBody(params="source",script="return new DOMParser().parseFromString(source,'application/xml');")private static native JSObject parseXml(String source);
    @JSBody(params="doc",script="var nodes=doc.getElementsByTagName('parsererror');for(var i=0;i<nodes.length;i++){var e=nodes[i];if(e.namespaceURI==='http://www.w3.org/1999/xhtml'||e.namespaceURI==='http://www.mozilla.org/newlayout/xml/parsererror.xml')return e.textContent;}return null;")private static native String parseError(JSObject doc);
    @JSBody(script="return document.implementation.createDocument(null,null);")private static native JSObject newDocument();
    @JSBody(params="node",script="return new XMLSerializer().serializeToString(node);")private static native String serializeNative(JSObject node);
    @JSBody(params="node",script="const m=globalThis.__pzXmlIdentity||(globalThis.__pzXmlIdentity={ids:new WeakMap(),next:1});if(!m.ids.has(node))m.ids.set(node,m.next++);return m.ids.get(node);")private static native int identity(JSObject node);
    @JSBody(params={"node","key"},script="return node[key];")private static native JSObject property(JSObject node,String key);
    @JSBody(params={"node","key"},script="return node[key];")private static native String string(JSObject node,String key);
    @JSBody(params={"node","key"},script="return node[key];")private static native int number(JSObject node,String key);
    @JSBody(params={"node","key","value"},script="node[key]=value;")private static native void set(JSObject node,String key,String value);
    @JSBody(params={"list","index"},script="return list.item(index);")private static native JSObject at(JSObject list,int index);
    @JSBody(params={"node","method","arg"},script="return node[method](arg);")private static native JSObject call(JSObject node,String method,String arg);
    @JSBody(params={"node","method","a","b"},script="return node[method](a,b);")private static native JSObject callTwo(JSObject node,String method,String a,String b);
    @JSBody(params={"node","method","child","reference"},script="return node[method](child,reference);")private static native JSObject mutate(JSObject node,String method,JSObject child,JSObject reference);
    @JSBody(params="node",script="node.normalize();")private static native void normalizeNative(JSObject node);
    @JSBody(params={"node","deep"},script="return node.cloneNode(deep);")private static native JSObject cloneNative(JSObject node,boolean deep);
    @JSBody(params={"doc","node","deep"},script="return doc.importNode(node,deep);")private static native JSObject importNative(JSObject doc,JSObject node,boolean deep);
    @JSBody(params={"node","name"},script="return node.getAttribute(name);")private static native String attribute(JSObject node,String name);
    @JSBody(params={"node","ns","name"},script="return node.getAttributeNS(ns,name);")private static native String attributeNS(JSObject node,String ns,String name);
    @JSBody(params={"node","ns","name","value","namespaced"},script="if(namespaced)node.setAttributeNS(ns,name,value);else node.setAttribute(name,value);")private static native void attributeSet(JSObject node,String ns,String name,String value,boolean namespaced);
    @JSBody(params={"node","ns","name","namespaced"},script="if(namespaced)node.removeAttributeNS(ns,name);else node.removeAttribute(name);")private static native void attributeRemove(JSObject node,String ns,String name,boolean namespaced);
}
