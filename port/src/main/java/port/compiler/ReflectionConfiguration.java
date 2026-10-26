package port.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.teavm.extension.spi.reflection.SimpleReflectionPolicy;

public final class ReflectionConfiguration extends SimpleReflectionPolicy {
    private record PublicApi(java.util.Set<String> signatures, java.util.Set<String> names) {}
    private final java.util.Map<String, java.util.Optional<PublicApi>> jdkApis = new java.util.HashMap<>();
    @Override public java.util.Collection<org.teavm.extension.introspect.IntrospectMember> classAccessibleMembers(
            org.teavm.extension.introspect.IntrospectClass<?> cls) {
        var members = super.classAccessibleMembers(cls);
        if (cls == null || !(cls.name().startsWith("java.") || cls.name().startsWith("javax."))) return members;
        var api = jdkApis.computeIfAbsent(cls.name(), ReflectionConfiguration::jdkApi);
        if (api.isEmpty()) return members; // A bundled third-party javax class is not a JDK class.
        return members.stream().filter(member -> {
            String signature = signature(member);
            // An unresolved type must not hide a real API from dependency diagnostics.
            return signature == null ? api.get().names.contains(member.name()) : api.get().signatures.contains(signature);
        }).toList();
    }
    private static java.util.Optional<PublicApi> jdkApi(String name) {
        try {
            // Platform loader only: never load or initialize any game class.
            Class<?> type = Class.forName(name, false, ClassLoader.getPlatformClassLoader());
            var signatures = new java.util.HashSet<String>(); var names = new java.util.HashSet<String>();
            for (var method : type.getMethods()) {
                names.add(method.getName()); signatures.add(method.getName() + java.util.Arrays.toString(java.util.Arrays.stream(method.getParameterTypes()).map(Class::getTypeName).toArray()) + ":" + method.getReturnType().getTypeName());
            }
            for (var constructor : type.getConstructors()) {
                names.add("<init>"); signatures.add("<init>" + java.util.Arrays.toString(java.util.Arrays.stream(constructor.getParameterTypes()).map(Class::getTypeName).toArray()) + ":void");
            }
            for (var field : type.getFields()) { names.add(field.getName()); signatures.add("field:" + field.getName() + ":" + field.getType().getTypeName()); }
            return java.util.Optional.of(new PublicApi(signatures, names));
        } catch (ClassNotFoundException e) { return java.util.Optional.empty(); }
    }
    private static String typeName(org.teavm.extension.introspect.IntrospectClass<?> type) {
        if (type == null) return null;
        if (!type.isArray()) return type.name();
        String component = typeName(type.componentType()); return component == null ? null : component + "[]";
    }
    private static String signature(org.teavm.extension.introspect.IntrospectMember member) {
        if (member instanceof org.teavm.extension.introspect.IntrospectMethod method) {
            var parameters = new java.util.ArrayList<String>();
            for (var parameter : method.parameters()) { String type = typeName(parameter.type()); if (type == null) return null; parameters.add(type); }
            String result = method.isConstructor() ? "void" : typeName(method.returnType());
            return result == null ? null : (method.isConstructor() ? "<init>" : method.name()) + parameters + ":" + result;
        }
        if (member instanceof org.teavm.extension.introspect.IntrospectField field) {
            String type = typeName(field.type()); return type == null ? null : "field:" + field.name() + ":" + type;
        }
        return null;
    }
    @Override protected void setup() {
        try {
            var names = new HashSet<String>();
            for (String raw : Files.readAllLines(Path.of(System.getProperty("pz.reflection")))) {
                String name = raw.strip();
                if (!name.isEmpty() && !name.startsWith("#")) names.add(name);
            }
            selectClasses(cls -> cls != null && java.util.Set.of("zombie.util.LazyValue", "zombie.util.ResettableLazyValue", "port.runtime.Task4Checks$HandleProbe").contains(cls.name())).reflectableFields(field -> true).reflectablePublicMethods();
            selectClasses(cls -> cls != null && names.contains(cls.name())).reflectablePublicMembers().foundByName();
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read reflection configuration", error);
        }
    }
}
