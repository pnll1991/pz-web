package port.runtime;

import java.lang.reflect.*;
import java.util.*;
import port.compat.javax.xml.bind.JAXBException;
import port.compat.javax.xml.bind.annotation.*;
import port.compat.org.w3c.dom.*;

/**
 * Bean <-> DOM mapping for the JAXB subset the pinned jar actually uses.
 * Only public fields, the JAXB annotations present on them and JAXB's own default names are read;
 * anything outside that subset raises JAXBException instead of guessing a mapping.
 */
public final class PortXmlBeans {
    private PortXmlBeans() {}
    private static final String DEFAULT = "$default$";

    public static void marshal(Object bean, Document document, Node parent) throws JAXBException {
        if (bean == null) throw new JAXBException("Cannot marshal a null object");
        Element root = document.createElement(rootName(bean.getClass()));
        parent.appendChild(root);
        writeBean(bean, root, document);
    }

    public static Object unmarshal(Class<?> type, Element root) throws JAXBException {
        if (root == null) throw new JAXBException("No root element to unmarshal");
        Object bean = instantiate(type);
        readBean(bean, root);
        return bean;
    }


    /** Element name the RI would use for this class; falls back to the simple name without a check. */
    public static String rootNameOf(Class<?> cls) {
        XmlRootElement root = cls.getAnnotation(XmlRootElement.class);
        if (root != null && !root.name().equals(DEFAULT)) return root.name();
        XmlType type = cls.getAnnotation(XmlType.class);
        if (type != null && !type.name().equals(DEFAULT)) return type.name();
        return cls.getSimpleName();
    }

    private static String rootName(Class<?> cls) throws JAXBException {
        XmlRootElement root = cls.getAnnotation(XmlRootElement.class);
        if (root != null && !root.name().equals(DEFAULT)) return root.name();
        XmlType type = cls.getAnnotation(XmlType.class);
        if (type != null && !type.name().equals(DEFAULT)) return type.name();
        if (root != null || type != null) return cls.getSimpleName();
        throw new JAXBException("No @XmlRootElement or @XmlType on " + cls.getName());
    }

    private static void writeBean(Object bean, Element element, Document document) throws JAXBException {
        for (Field field : fields(bean.getClass())) {
            if (field.isAnnotationPresent(XmlTransient.class)) continue;
            Object value;
            try { value = field.get(bean); }
            catch (ReflectiveOperationException | IllegalArgumentException error) {
                throw new JAXBException("Cannot read " + field, error);
            }
            writeField(field, value, element, document);
        }
    }

    private static void writeField(Field field, Object value, Element element, Document document) throws JAXBException {
        XmlElements choices = field.getAnnotation(XmlElements.class);
        XmlAttribute attribute = field.getAnnotation(XmlAttribute.class);
        if (attribute != null) {
            if (value != null) element.setAttribute(attributeName(attribute.name(), field.getName()), text(value));
            return;
        }
        String name = elementName(field);
        List<Object> items = new ArrayList<>();
        if (value instanceof Collection<?> collection) items.addAll(collection);
        else if (value != null && value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) items.add(Array.get(value, i));
        } else if (value != null) items.add(value);
        for (Object item : items) {
            if (item == null) continue;
            Element child = document.createElement(choiceName(choices, item.getClass(), name));
            element.appendChild(child);
            if (isLeaf(item.getClass())) child.setTextContent(text(item));
            else writeBean(item, child, document);
        }
    }

    private static void readBean(Object bean, Element element) throws JAXBException {
        for (Field field : fields(bean.getClass())) {
            if (field.isAnnotationPresent(XmlTransient.class)) continue;
            XmlAttribute attribute = field.getAnnotation(XmlAttribute.class);
            if (attribute != null) {
                String raw = element.getAttribute(attributeName(attribute.name(), field.getName()));
                if (raw != null && !raw.isEmpty()) assign(field, bean, raw);
                continue;
            }
            XmlElements choices = field.getAnnotation(XmlElements.class);
            String name = elementName(field);
            List<Element> found = new ArrayList<>();
            for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() != Node.ELEMENT_NODE) continue;
                Element childElement = (Element) child;
                if (choices != null) { if (!choiceMatches(choices, childElement.getTagName())) continue; }
                else if (!childElement.getTagName().equals(name)) continue;
                found.add(childElement);
            }
            if (found.isEmpty()) continue;
            Class<?> declared = field.getType();
            boolean many = Collection.class.isAssignableFrom(declared) && declared != Object.class;
            if (many) {
                Collection<Object> target = collection(field, bean);
                for (Element child : found)
                    target.add(readValue(choiceType(choices, child.getTagName(), elementType(field)), child));
            } else {
                Class<?> type = field.getType().isArray() ? field.getType().getComponentType()
                        : choiceType(choices, found.get(0).getTagName(), field.getType());
                Object single = readValue(type, found.get(0));
                if (field.getType().isArray()) {
                    Object array = Array.newInstance(field.getType().getComponentType(), found.size());
                    Array.set(array, 0, single);
                    for (int i = 1; i < found.size(); i++) Array.set(array, i, readValue(type, found.get(i)));
                    assignArray(field, bean, array);
                } else assign(field, bean, single);
            }
        }
    }

    private static Object instantiate(Class<?> type) throws JAXBException {
        try {
            Object bean = type.getDeclaredConstructor().newInstance();
            if (bean == null) throw new JAXBException("Constructor of " + type.getName() + " produced no instance");
            return bean;
        } catch (ReflectiveOperationException error) {
            throw new JAXBException("Cannot instantiate " + type.getName() + " for unmarshalling", error);
        }
    }

    private static Object readValue(Class<?> type, Element element) throws JAXBException {
        if (isLeaf(type)) return convert(type, element.getTextContent());
        // Ronda 64: leer un elemento XML con el tipo de la propia coleccion es un error our, no del
        // juego. Antes, para `public ArrayList<FileGuidPair> files` se pasaba field.getType()
        // (java.util.ArrayList) como tipo de cada hijo: se instanciaba un ArrayList vacio por cada
        // <files> y el juego hacia `checkcast zombie/FileGuidPair` en FileGuidTable#setModID, con
        // RuntimeError "illegal cast" (stack del pageerror en /tmp/diag-menu64b.log). Ahora se lanza
        // JAXBException honesto en vez de meter objetos de otra clase en la coleccion.
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type))
            throw new JAXBException("Element <" + element.getTagName() + "> has no declared item type for "
                    + type.getName());
        return unmarshal(type, element);
    }

    private static void assign(Field field, Object bean, Object value) throws JAXBException {
        try {
            field.set(bean, value instanceof String text ? convert(field.getType(), text) : value);
        } catch (ReflectiveOperationException | IllegalArgumentException error) {
            throw new JAXBException("Cannot write " + field, error);
        }
    }

    private static void assignArray(Field field, Object bean, Object array) throws JAXBException {
        try { field.set(bean, array); }
        catch (ReflectiveOperationException | IllegalArgumentException error) { throw new JAXBException("Cannot write " + field, error); }
    }

    private static Collection<Object> collection(Field field, Object bean) throws JAXBException {
        try {
            Object current = field.get(bean);
            if (current instanceof Collection<?> existing) return (Collection<Object>) existing;
            if (current != null) throw new JAXBException("Field " + field + " is not a mutable collection");
            Class<?> type = field.getType();
            Object created;
            if (type == List.class || type == Collection.class) created = new ArrayList<>();
            else if (type == Set.class) created = new LinkedHashSet<>();
            else created = type.getDeclaredConstructor().newInstance();
            if (!(created instanceof Collection<?> collection)) throw new JAXBException("Cannot create collection for " + field);
            field.set(bean, created);
            return (Collection<Object>) created;
        } catch (ReflectiveOperationException | IllegalArgumentException error) {
            throw new JAXBException("Cannot use collection field " + field, error);
        }
    }

    private static Object convert(Class<?> type, String raw) throws JAXBException {
        if (raw == null) raw = "";
        if (type == String.class) return raw;
        if (type == boolean.class || type == Boolean.class) {
            if (raw.equals("true") || raw.equals("false")) return Boolean.valueOf(raw);
            throw new JAXBException("Not a boolean: " + raw);
        }
        try {
            if (type == int.class || type == Integer.class) return Integer.valueOf(raw.strip());
            if (type == long.class || type == Long.class) return Long.valueOf(raw.strip());
            if (type == double.class || type == Double.class) return Double.valueOf(raw.strip());
            if (type == float.class || type == Float.class) return Float.valueOf(raw.strip());
            if (type == short.class || type == Short.class) return Short.valueOf(raw.strip());
            if (type == byte.class || type == Byte.class) return Byte.valueOf(raw.strip());
            if (type == char.class || type == Character.class) {
                if (raw.length() != 1) throw new JAXBException("Not a char: " + raw);
                return raw.charAt(0);
            }
        } catch (NumberFormatException error) { throw new JAXBException("Cannot parse " + raw + " as " + type.getName(), error); }
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                XmlEnumValue label = enumValue(type, constant);
                if (label != null ? label.value().equals(raw) : ((Enum<?>) constant).name().equals(raw)) return constant;
            }
            throw new JAXBException("No enum constant " + type.getName() + "." + raw);
        }
        throw new JAXBException("Field type is outside the supported JAXB subset: " + type.getName());
    }

    private static XmlEnumValue enumValue(Class<?> type, Object constant) {
        try { return type.getField(((Enum<?>) constant).name()).getAnnotation(XmlEnumValue.class); }
        catch (NoSuchFieldException error) { return null; }
    }

    private static String text(Object value) throws JAXBException {
        if (value instanceof Enum<?> constant) {
            XmlEnumValue label = enumValue(constant.getDeclaringClass(), constant);
            return label != null ? label.value() : constant.name();
        }
        if (isLeaf(value.getClass())) return String.valueOf(value);
        throw new JAXBException("Value type is outside the supported JAXB subset: " + value.getClass().getName());
    }

    private static boolean isLeaf(Class<?> type) {
        return type == String.class || type == Boolean.class || type == Integer.class || type == Long.class
                || type == Double.class || type == Float.class || type == Short.class || type == Byte.class
                || type == Character.class || type.isPrimitive() || type.isEnum();
    }

    private static List<Field> fields(Class<?> cls) throws JAXBException {
        List<Field> result = new ArrayList<>();
        for (Field field : cls.getFields()) if (!Modifier.isStatic(field.getModifiers())) result.add(field);
        for (Field field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(XmlTransient.class)) continue;
            boolean already = result.stream().anyMatch(existing -> existing.getName().equals(field.getName()));
            if (already) continue;
            boolean annotated = field.isAnnotationPresent(XmlElement.class) || field.isAnnotationPresent(XmlElements.class)
                    || field.isAnnotationPresent(XmlAttribute.class) || field.isAnnotationPresent(XmlID.class)
                    || field.isAnnotationPresent(XmlIDREF.class);
            if (Modifier.isPublic(field.getModifiers()) || annotated) result.add(field);
            else if (!cls.getName().startsWith("zombie.") && !cls.getName().startsWith("net."))
                throw new JAXBException("Non-public field without a JAXB annotation: " + field);
        }
        return result;
    }

    private static String elementName(Field field) {
        XmlElement element = field.getAnnotation(XmlElement.class);
        if (element != null && !element.name().equals(DEFAULT)) return element.name();
        XmlElements choices = field.getAnnotation(XmlElements.class);
        if (choices != null && choices.value().length > 0 && !choices.value()[0].name().equals(DEFAULT)) return choices.value()[0].name();
        return field.getName();
    }

    // Ronda 64: el RI de JAXB usa el parametro generico declarado del campo como tipo de los
    // elementos de una coleccion. Sin esa informacion (ni @XmlElement(type=...)) no se puede
    // adivinar: se lanza JAXBException con el nombre del campo en vez de usar la coleccion.
    private static Class<?> elementType(Field field) throws JAXBException {
        XmlElement element = field.getAnnotation(XmlElement.class);
        if (element != null && element.type() != Object.class) return element.type();
        Class<?> declared = field.getType();
        if (declared.isArray()) return declared.getComponentType();
        if (field.getGenericType() instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length == 1 && arguments[0] instanceof Class<?> item) return item;
        }
        throw new JAXBException("Cannot determine the element type of collection field " + field.getName()
                + " of " + field.getDeclaringClass().getName()
                + ": no @XmlElement(type=...) and no single generic parameter");
    }

    private static String attributeName(String declared, String fallback) {
        return declared.equals(DEFAULT) ? fallback : declared;
    }

    private static String choiceName(XmlElements choices, Class<?> itemType, String fallback) {
        if (choices == null) return fallback;
        for (XmlElement choice : choices.value())
            if (choice.type() == itemType || itemType.isAssignableFrom(choice.type()))
                return choice.name().equals(DEFAULT) ? itemType.getSimpleName() : choice.name();
        return itemType.getSimpleName();
    }

    private static boolean choiceMatches(XmlElements choices, String tag) {
        for (XmlElement choice : choices.value())
            if ((choice.name().equals(DEFAULT) ? choice.type().getSimpleName() : choice.name()).equals(tag)) return true;
        return false;
    }

    private static Class<?> choiceType(XmlElements choices, String tag, Class<?> declared) throws JAXBException {
        if (choices == null) return declared;
        for (XmlElement choice : choices.value()) {
            String name = choice.name().equals(DEFAULT) ? choice.type().getSimpleName() : choice.name();
            if (name.equals(tag)) return choice.type();
        }
        throw new JAXBException("Element <" + tag + "> is not one of the declared types of " + declared.getName());
    }
}
