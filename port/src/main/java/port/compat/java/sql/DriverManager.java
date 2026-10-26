package port.compat.java.sql;
import java.util.*;
import java.sql.SQLException;
public final class DriverManager {
    private static final List<Driver> drivers=new ArrayList<>();
    public static synchronized void registerDriver(Driver driver){Objects.requireNonNull(driver);if(!drivers.contains(driver))drivers.add(driver);}
    public static synchronized void deregisterDriver(Driver driver){drivers.remove(driver);}
    public static Connection getConnection(String url)throws SQLException{return getConnection(url,new Properties());}
    public static Connection getConnection(String url,Properties properties)throws SQLException{
        Objects.requireNonNull(url);Objects.requireNonNull(properties);
        if(url.startsWith("jdbc:sqlite:")){if(!properties.isEmpty())throw new java.sql.SQLFeatureNotSupportedException("SQLite JDBC properties must be configured explicitly");String path=url.substring(12);if(path.isEmpty())path=":memory:";if(!path.equals(":memory:")&&!path.startsWith("/"))path="/"+path;return new port.runtime.jdbc.BrowserConnection(path);}
        for(Driver driver:List.copyOf(drivers)){Connection result=driver.connect(url,properties);if(result!=null)return result;}throw new SQLException("No browser JDBC driver for "+url,"08001");
    }
    public static Connection getConnection(String url,String user,String password)throws SQLException{throw new java.sql.SQLFeatureNotSupportedException("Browser SQLite JDBC does not authenticate remote databases");}
    public static Enumeration<Driver> getDrivers(){return Collections.enumeration(List.copyOf(drivers));}
}
