package port.compat.javax.xml.bind;

public class PropertyException extends JAXBException {
    public PropertyException(String message){super(message);}
    public PropertyException(String message,String errorCode){super(message,errorCode);}
    public PropertyException(Throwable cause){super(cause);}
    public PropertyException(String message,Throwable cause){super(message,cause);}
    public PropertyException(String message,String errorCode,Throwable cause){super(message,errorCode,cause);}
    public PropertyException(String message,Object value){super(message+" = "+value);}
}
