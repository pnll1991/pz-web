package port.compat.javax.xml.bind.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)@Target({ElementType.TYPE})
public @interface XmlRootElement {String namespace() default "##default";String name() default "$default$";}
