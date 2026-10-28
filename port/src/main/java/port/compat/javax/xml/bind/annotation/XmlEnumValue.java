package port.compat.javax.xml.bind.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)@Target({ElementType.FIELD})
public @interface XmlEnumValue {String value();}
