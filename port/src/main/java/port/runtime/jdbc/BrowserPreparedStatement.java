package port.runtime.jdbc;
import java.sql.SQLException;
import port.compat.java.sql.ResultSet;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
public final class BrowserPreparedStatement extends AbstractPreparedStatement {
    private final String sql;private final Object[] values;private final boolean[] bound;
    BrowserPreparedStatement(BrowserConnection connection,String sql)throws SQLException{super(connection);this.sql=java.util.Objects.requireNonNull(sql);int count=Sqlite.integer(Sqlite.checked(Sqlite.parameters(connection.handle,sql)));values=new Object[count];bound=new boolean[count];}
    private void set(int index,Object value)throws SQLException{check();if(index<1||index>values.length)throw new SQLException("Parameter index: "+index);values[index-1]=value;bound[index-1]=true;}
    public void setObject(int index,Object value)throws SQLException{set(index,value);}
    public void setNull(int index,int type)throws SQLException{set(index,null);}
    public void setString(int index,String value)throws SQLException{set(index,value);}
    public void setBoolean(int index,boolean value)throws SQLException{set(index,value);}
    public void setInt(int index,int value)throws SQLException{set(index,value);}
    public void setLong(int index,long value)throws SQLException{set(index,value);}
    public void setFloat(int index,float value)throws SQLException{set(index,value);}
    public void setDouble(int index,double value)throws SQLException{set(index,value);}
    public void setBytes(int index,byte[] value)throws SQLException{set(index,value==null?null:value.clone());}
    public void setBinaryStream(int index,java.io.InputStream input,int length)throws SQLException{if(input==null){set(index,null);return;}if(length<0)throw new SQLException("Negative stream length");try{byte[] bytes=input.readNBytes(length);if(bytes.length!=length)throw new SQLException("Binary stream shorter than requested length");set(index,bytes);}catch(java.io.IOException error){throw new SQLException(error);}}
    public void clearParameters()throws SQLException{check();java.util.Arrays.fill(values,null);java.util.Arrays.fill(bound,false);}
    public boolean execute()throws SQLException{var params=JSArray.<JSObject>create();for(int i=0;i<values.length;i++){if(!bound[i])throw new SQLException("Parameter is unset: "+(i+1));params.push(Sqlite.binding(values[i]));}return executeBound(sql,params);}
    public int executeUpdate()throws SQLException{if(execute())throw new SQLException("Prepared statement produced a result set");return getUpdateCount();}
    public ResultSet executeQuery()throws SQLException{if(!execute())throw new SQLException("Prepared statement produced no result set");return getResultSet();}
}
