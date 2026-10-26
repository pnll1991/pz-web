import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Names/descriptors/access sites only. No method bodies, loading or execution. */
public final class ApiUsage {
    public static void main(String[] args) throws Exception {
        var prefixes = Files.readAllLines(Path.of(args[1]));
        var rows = new TreeSet<String>();
        try (var jar = new ZipFile(args[0])) {
            for (var entry : jar.stream().filter(e -> e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")).toList()) {
                var cls = ClassFile.of().parse(jar.getInputStream(entry).readAllBytes());
                for (var method : cls.methods()) if (method.code().isPresent()) {
                    for (var instruction : method.code().get()) {
                        String owner, name, type, opcode;
                        if (instruction instanceof InvokeInstruction i) {
                            owner=i.owner().asInternalName().replace('/','.'); name=i.name().stringValue(); type=i.type().stringValue(); opcode=i.opcode().name();
                        } else if (instruction instanceof FieldInstruction i) {
                            owner=i.owner().asInternalName().replace('/','.'); name=i.name().stringValue(); type=i.type().stringValue(); opcode=i.opcode().name();
                        } else continue;
                        if (prefixes.stream().noneMatch(owner::startsWith)) continue;
                        rows.add(owner+"\t"+name+"\t"+type+"\t"+opcode+"\t"+cls.thisClass().asInternalName().replace('/','.')+"#"+method.methodName().stringValue());
                    }
                }
            }
        }
        rows.forEach(System.out::println);
    }
}
