package port.compat.javax.xml.bind;

/** Replacement for the JAXB exception that the pinned jar bundles. The real RI is unreachable in Wasm. */
public class JAXBException extends Exception {
    private final String errorCode;
    private Throwable linked;
    public JAXBException(String message){super(message);errorCode=null;}
    public JAXBException(String message,String errorCode){super(message);this.errorCode=errorCode;}
    public JAXBException(Throwable cause){super(cause);errorCode=null;linked=cause;}
    public JAXBException(String message,Throwable cause){super(message,cause);errorCode=null;linked=cause;}
    public JAXBException(String message,String errorCode,Throwable cause){super(message,cause);this.errorCode=errorCode;linked=cause;}
    public String getErrorCode(){return errorCode;}
    public Throwable getLinkedException(){return linked;}
    public void setLinkedException(Throwable value){linked=value;}
    @Override public String toString(){String name=getClass().getName();String message=getMessage();
        return errorCode==null?name+": "+message:name+":"+errorCode+": "+message;}
}
