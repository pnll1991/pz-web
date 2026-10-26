package port.compat.javax.xml.transform.dom;
import port.compat.org.w3c.dom.Node;
public class DOMSource implements port.compat.javax.xml.transform.Source {
    private Node node;private String id;public DOMSource(){}public DOMSource(Node node){this.node=node;}
    public Node getNode(){return node;}public void setNode(Node node){this.node=node;}public String getSystemId(){return id;}public void setSystemId(String id){this.id=id;}
}
