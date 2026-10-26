package port.runtime.jdbc;
import java.sql.SQLException;
import org.teavm.jso.JSObject;
public final class BrowserResultSet extends AbstractResultSet {
    private final BrowserStatement statement;private final JSObject data;private int row=-1,fetchSize;private boolean closed,wasNull;
    BrowserResultSet(BrowserStatement statement,JSObject data){this.statement=statement;this.data=data;}
    private void check()throws SQLException{statement.check();if(closed)throw new SQLException("ResultSet is closed");}
    private int column(int index)throws SQLException{check();if(row<0||row>=Sqlite.rowCount(data))throw new SQLException("ResultSet cursor is not on a row");if(index<1||index>Sqlite.columnCount(data))throw new SQLException("Column index: "+index);wasNull=Sqlite.cellType(data,row,index-1).equals("null");return index-1;}
    public int findColumn(String name)throws SQLException{check();int index=Sqlite.column(data,name);if(index<0)throw new SQLException("Unknown column: "+name);return index+1;}
    public boolean next()throws SQLException{check();if(row<Sqlite.rowCount(data))row++;return row<Sqlite.rowCount(data);}
    public String getString(int index)throws SQLException{return Sqlite.text(data,row,column(index));}
    public String getString(String name)throws SQLException{return getString(findColumn(name));}
    public long getLong(int index)throws SQLException{String text=getString(index);if(text==null)return 0;try{return text.indexOf('.')>=0||text.indexOf('e')>=0||text.indexOf('E')>=0?(long)Double.parseDouble(text):Long.parseLong(text);}catch(NumberFormatException error){throw new SQLException("Invalid integer: "+text,error);}}
    public long getLong(String name)throws SQLException{return getLong(findColumn(name));}
    public int getInt(int index)throws SQLException{return (int)getLong(index);}public int getInt(String name)throws SQLException{return getInt(findColumn(name));}
    public double getDouble(int index)throws SQLException{String text=getString(index);try{return text==null?0:Double.parseDouble(text);}catch(NumberFormatException error){throw new SQLException("Invalid number: "+text,error);}}
    public double getDouble(String name)throws SQLException{return getDouble(findColumn(name));}
    public float getFloat(int index)throws SQLException{return (float)getDouble(index);}public float getFloat(String name)throws SQLException{return getFloat(findColumn(name));}
    public boolean getBoolean(int index)throws SQLException{return getLong(index)!=0;}public boolean getBoolean(String name)throws SQLException{return getBoolean(findColumn(name));}
    public byte[] getBytes(int index)throws SQLException{int col=column(index);if(wasNull)return null;if(!Sqlite.cellType(data,row,col).equals("blob"))return getString(index).getBytes(java.nio.charset.StandardCharsets.UTF_8);var raw=Sqlite.blob(data,row,col);byte[] bytes=new byte[raw.getLength()];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)raw.get(i);return bytes;}
    public byte[] getBytes(String name)throws SQLException{return getBytes(findColumn(name));}
    public java.io.InputStream getBinaryStream(int index)throws SQLException{byte[] bytes=getBytes(index);return bytes==null?null:new java.io.ByteArrayInputStream(bytes);}
    public java.io.InputStream getBinaryStream(String name)throws SQLException{return getBinaryStream(findColumn(name));}
    public boolean wasNull()throws SQLException{check();return wasNull;}
    public int getFetchSize()throws SQLException{check();return fetchSize;}
    public void setFetchSize(int size)throws SQLException{check();if(size<0)throw new SQLException("Negative fetch size");fetchSize=size;}
    public port.compat.java.sql.Statement getStatement()throws SQLException{check();return statement;}
    public boolean isClosed(){return closed||statement.isClosed();}
    public void close(){closed=true;}
}
