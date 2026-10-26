package port.compat.javax.xml.parsers;
public class DocumentBuilderFactory {
    private boolean namespaces,disallowDoctype;
    public static DocumentBuilderFactory newInstance(){return new DocumentBuilderFactory();}
    public void setNamespaceAware(boolean value){namespaces=value;}
    public void setValidating(boolean value){if(value)throw new UnsupportedOperationException("XML DTD validation is unavailable in browser DOMParser");}
    public void setExpandEntityReferences(boolean value){if(value)throw new UnsupportedOperationException("XML external entity expansion is unavailable in browser DOMParser");}
    public void setFeature(String name,boolean value)throws javax.xml.parsers.ParserConfigurationException{
        switch(name){
            case "http://apache.org/xml/features/disallow-doctype-decl" -> disallowDoctype=value;
            case "http://xml.org/sax/features/external-general-entities","http://xml.org/sax/features/external-parameter-entities","http://apache.org/xml/features/nonvalidating/load-external-dtd" -> {if(value)throw new javax.xml.parsers.ParserConfigurationException("External entities/DTDs are unavailable: "+name);}
            case "http://javax.xml.XMLConstants/feature/secure-processing" -> {if(!value)throw new javax.xml.parsers.ParserConfigurationException("Browser XML parser cannot disable its isolation");}
            default -> throw new javax.xml.parsers.ParserConfigurationException("XML feature unsupported: "+name);
        }
    }
    public DocumentBuilder newDocumentBuilder(){return new DocumentBuilder(namespaces,disallowDoctype);}
}
