package port.compat.java.util;
public class MissingFormatArgumentException extends IllegalFormatException {
    private final String specifier;
    public MissingFormatArgumentException(String specifier){this.specifier=java.util.Objects.requireNonNull(specifier);}
    public String getFormatSpecifier(){return specifier;}
    @Override public String getMessage(){return "Format specifier '"+specifier+"'";}
}
