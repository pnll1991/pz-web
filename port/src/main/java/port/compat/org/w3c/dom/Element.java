package port.compat.org.w3c.dom;
public interface Element extends Node {
    String getTagName();String getAttribute(String name);void setAttribute(String name,String value);void removeAttribute(String name);boolean hasAttribute(String name);
    NodeList getElementsByTagName(String name);String getAttributeNS(String namespace,String name);void setAttributeNS(String namespace,String name,String value);void removeAttributeNS(String namespace,String name);boolean hasAttributeNS(String namespace,String name);Attr setAttributeNode(Attr attribute);
}
