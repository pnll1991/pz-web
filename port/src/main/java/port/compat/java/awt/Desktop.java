package port.compat.java.awt;
public final class Desktop {
    private Desktop(){}
    public static boolean isDesktopSupported(){return false;}
    public static Desktop getDesktop(){throw new UnsupportedOperationException("Desktop: OS desktop integration is unavailable in a browser page");}
}
