package port.compat.javax.xml.bind.annotation;
import java.lang.annotation.*;

/** Signature subset of the JAXB annotation bundled in the pinned jar (values kept as in the RI). */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD,ElementType.METHOD,ElementType.PARAMETER})
public @interface XmlElement {
    String name() default "$default$";
    boolean nillable() default false;
    boolean required() default true;
    String namespace() default "##default";
    String defaultValue() default "";
    Class type() default Object.class;
}
