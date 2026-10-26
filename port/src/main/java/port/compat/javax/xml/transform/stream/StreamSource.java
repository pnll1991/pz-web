package port.compat.javax.xml.transform.stream;
import java.io.*;
public class StreamSource implements port.compat.javax.xml.transform.Source {
    private InputStream input;private Reader reader;private String id,publicId;
    public StreamSource(){}public StreamSource(InputStream input){this.input=input;}public StreamSource(Reader reader){this.reader=reader;}public StreamSource(File file){id=file.toURI().toString();}public StreamSource(String id){this.id=id;}
    public InputStream getInputStream(){return input;}public void setInputStream(InputStream value){input=value;}public Reader getReader(){return reader;}public void setReader(Reader value){reader=value;}
    public String getSystemId(){return id;}public void setSystemId(String id){this.id=id;}public void setSystemId(File file){id=file.toURI().toString();}public String getPublicId(){return publicId;}public void setPublicId(String value){publicId=value;}
}
