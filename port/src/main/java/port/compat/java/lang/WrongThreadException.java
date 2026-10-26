package port.compat.java.lang;
public class WrongThreadException extends RuntimeException { public WrongThreadException(){super("Confined arena accessed from another thread");} }
