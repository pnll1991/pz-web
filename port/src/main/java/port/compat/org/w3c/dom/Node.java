package port.compat.org.w3c.dom;
public interface Node {
    short ELEMENT_NODE=1,ATTRIBUTE_NODE=2,TEXT_NODE=3,CDATA_SECTION_NODE=4,PROCESSING_INSTRUCTION_NODE=7,COMMENT_NODE=8,DOCUMENT_NODE=9;
    String getNodeName();String getNodeValue();void setNodeValue(String value);short getNodeType();
    Node getParentNode();NodeList getChildNodes();Node getFirstChild();Node getLastChild();Node getNextSibling();Node getPreviousSibling();
    NamedNodeMap getAttributes();Document getOwnerDocument();Node appendChild(Node child);Node insertBefore(Node child,Node reference);Node removeChild(Node child);Node replaceChild(Node child,Node old);
    boolean hasChildNodes();boolean hasAttributes();void normalize();String getTextContent();void setTextContent(String text);
    String getNamespaceURI();String getLocalName();String getPrefix();boolean isSameNode(Node other);Node cloneNode(boolean deep);
}
