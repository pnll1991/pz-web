package port.compiler;
import org.teavm.model.*;
import org.teavm.parsing.ClassRefsRenamer;
import java.util.*;
/** Runtime bridge code and substitution sources must share the same AOT type identities. */
public final class CompatReferences implements ClassHolderTransformer {
    private final ClassRefsRenamer renamer=new ClassRefsRenamer(new ReferenceCache(),name->name.startsWith("port.compat.")?name.substring("port.compat.".length()):name);
    public void transformClass(ClassHolder cls,ClassHolderTransformerContext context){
        if(!cls.getName().startsWith("port.")||cls.getName().startsWith("port.compat.")||cls.getName().startsWith("port.compiler."))return;
        if(renamer.rename(cls)!=cls)throw new IllegalStateException("Runtime bridge unexpectedly renamed its own class");
    }
}
