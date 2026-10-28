package port.compat.javax.xml.bind.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)@Target({ElementType.FIELD,ElementType.METHOD,ElementType.PARAMETER})
public @interface XmlAttribute {String name() default "$default$";boolean required() default true;String namespace() default "##default";}
