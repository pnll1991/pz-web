package port.compat.javax.xml.transform.stream;
import java.io.*;
public class StreamResult implements port.compat.javax.xml.transform.Result {
    private OutputStream output;private Writer writer;private String id;
    public StreamResult(){}public StreamResult(OutputStream output){this.output=output;}public StreamResult(Writer writer){this.writer=writer;}public StreamResult(File file){id=file.toURI().toString();}public StreamResult(String id){this.id=id;}
    public OutputStream getOutputStream(){return output;}public void setOutputStream(OutputStream value){output=value;}public Writer getWriter(){return writer;}public void setWriter(Writer value){writer=value;}
    public String getSystemId(){return id;}public void setSystemId(String id){this.id=id;}public void setSystemId(File file){id=file.toURI().toString();}
}
