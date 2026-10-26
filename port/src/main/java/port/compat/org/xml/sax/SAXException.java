package port.compat.org.xml.sax;
public class SAXException extends Exception {
    public SAXException(){}public SAXException(String message){super(message);}public SAXException(Exception cause){super(cause);}public SAXException(String message,Exception cause){super(message,cause);}
    public Exception getException(){return getCause() instanceof Exception e?e:null;}
}
