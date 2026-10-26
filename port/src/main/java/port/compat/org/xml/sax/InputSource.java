package port.compat.org.xml.sax;
public class InputSource {
    private java.io.InputStream bytes;private java.io.Reader chars;private String encoding,systemId,publicId;
    public InputSource(){}public InputSource(java.io.InputStream bytes){this.bytes=bytes;}public InputSource(java.io.Reader chars){this.chars=chars;}public InputSource(String systemId){this.systemId=systemId;}
    public java.io.InputStream getByteStream(){return bytes;}public void setByteStream(java.io.InputStream bytes){this.bytes=bytes;}
    public java.io.Reader getCharacterStream(){return chars;}public void setCharacterStream(java.io.Reader chars){this.chars=chars;}
    public String getEncoding(){return encoding;}public void setEncoding(String encoding){this.encoding=encoding;}
    public String getSystemId(){return systemId;}public void setSystemId(String value){systemId=value;}public String getPublicId(){return publicId;}public void setPublicId(String value){publicId=value;}
}
