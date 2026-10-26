import java.lang.classfile.ClassFile;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.constant.ClassDesc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

/** Read-only call-site metadata. Never writes method bodies or loads game classes. */
public final class BytecodeMetadata {
    public static void main(String[] args) throws Exception {
        try (var jar = new ZipFile(Path.of(args[0]).toFile())) {
            List<ZipEntry> entries = new ArrayList<>(jar.stream().filter(e -> e.getName().endsWith(".class") &&
                    (e.getName().startsWith(args[1]) || e.getName().equals("zombie/core/Core.class")))
                    .sorted((a,b) -> a.getName().compareTo(b.getName())).toList());
            var visited = new HashSet<String>();
            for (int next = 0; next < entries.size(); next++) {
                var entry = entries.get(next);
                if (!visited.add(entry.getName())) continue;
                var cls = ClassFile.of().parse(jar.getInputStream(entry).readAllBytes());
                for (var method : cls.methods()) {
                    if (method.code().isEmpty()) continue;
                    List<Instruction> instructions = new ArrayList<>();
                    for (var element : method.code().get()) if (element instanceof Instruction i) instructions.add(i);
                    String caller = cls.thisClass().asInternalName().replace('/', '.') + "#" +
                            method.methodName().stringValue() + method.methodType().stringValue();
                    for (int i = 0; i < instructions.size(); i++) {
                        if (!(instructions.get(i) instanceof InvokeInstruction invoke)) continue;
                        String name = invoke.name().stringValue(), descriptor = invoke.type().stringValue();
                        String owner = invoke.owner().asInternalName();
                        if (name.equals("setExposed") && descriptor.equals("(Lzombie/Lua/LuaManager$Exposer;)V")) {
                            var helper = jar.getEntry(owner + ".class");
                            if (helper == null) throw new IllegalStateException("Missing exposure helper: " + owner);
                            entries.add(helper);
                            System.out.println("H\t" + owner.replace('/', '.') + "\t" + caller + "\t" + name + descriptor);
                            continue;
                        }
                        if (owner.equals("zombie/core/GameVersion") && name.equals("<init>")) {
                            int count = invoke.typeSymbol().parameterCount();
                            List<String> constants = new ArrayList<>();
                            for (int j = i - count; j < i && j >= 0; j++) {
                                if (instructions.get(j) instanceof ConstantInstruction c &&
                                        (c.constantValue() instanceof Integer || c.constantValue() instanceof String))
                                    constants.add(c.constantValue().toString());
                            }
                            if (constants.size() == count)
                                System.out.println("V\t" + caller + "\t" + descriptor + "\t" + String.join("\t", constants));
                        }
                        if (!name.equals("setExposed") && !name.equals("exposeLikeJava") && !name.equals("exposeGlobalFunctions")) continue;
                        String exposed = null;
                        if (descriptor.equals("(Ljava/lang/Class;)V") && i > 0 &&
                                instructions.get(i-1) instanceof ConstantInstruction c && c.constantValue() instanceof ClassDesc type &&
                                type.isClassOrInterface()) exposed = type.descriptorString().substring(1, type.descriptorString().length()-1).replace('/', '.');
                        if (descriptor.equals("(Ljava/lang/Object;)V") && i >= 3 &&
                                instructions.get(i-1) instanceof InvokeInstruction init && init.name().equalsString("<init>") &&
                                init.type().equalsString("()V") && instructions.get(i-2).opcode() == Opcode.DUP &&
                                instructions.get(i-3) instanceof NewObjectInstruction object &&
                                object.className().equals(init.owner())) exposed = object.className().asInternalName().replace('/', '.');
                        System.out.println((exposed == null ? "U" : "R") + "\t" +
                                (exposed == null ? "dynamic" : exposed) + "\t" + caller + "\t" +
                                owner.replace('/', '.') + "#" + name + descriptor);
                    }
                }
            }
        }
    }
}
