package port.compat.javax.xml.transform;
public class TransformerFactory {
    protected TransformerFactory(){}
    public static TransformerFactory newInstance(){return new TransformerFactory();}
    public Transformer newTransformer(){return new Transformer();}
    public Transformer newTransformer(Source stylesheet)throws TransformerConfigurationException{throw new TransformerConfigurationException("XSLT stylesheets are not implemented; only XML identity transformation is available");}
    public void setFeature(String name,boolean value)throws TransformerConfigurationException{if(!name.equals("http://javax.xml.XMLConstants/feature/secure-processing")||!value)throw new TransformerConfigurationException("Unsupported XML transformation feature: "+name+"="+value);}
    public boolean getFeature(String name){return name.equals("http://javax.xml.XMLConstants/feature/secure-processing")||name.equals("http://javax.xml.transform.dom.DOMSource/feature")||name.equals("http://javax.xml.transform.dom.DOMResult/feature")||name.equals("http://javax.xml.transform.stream.StreamSource/feature")||name.equals("http://javax.xml.transform.stream.StreamResult/feature");}
}
