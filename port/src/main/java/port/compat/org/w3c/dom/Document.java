package port.compat.org.w3c.dom;
public interface Document extends Node {
    Element getDocumentElement();NodeList getElementsByTagName(String name);Element createElement(String name);Element createElementNS(String namespace,String name);Text createTextNode(String text);ProcessingInstruction createProcessingInstruction(String target,String data);Node importNode(Node node,boolean deep);
}
