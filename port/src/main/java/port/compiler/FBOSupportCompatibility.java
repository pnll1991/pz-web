package port.compiler;

import java.util.ArrayList;
import java.util.List;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.InvocationType;

/**
 * Framebuffer-object support gate for the browser backend.
 *
 * Verified cause (rounds 52/53, diag156..diag159): {@code TextureFBO::initInternal} dies with
 * {@code dereferencing a null pointer} in {@code UI_Loading_ModelsAnimations} because
 * {@code TextureFBO.funcs} is null. javap of the pinned jar shows both gates
 * ({@code zombie.core.textures.TextureFBO.checkFBOSupport} and
 * {@code zombie.core.textures.TextureFBODepth.checkFBOSupport}) start with
 * {@code GL.getCapabilities()} + {@code getfield GLCapabilities.OpenGL30} and, when that is false,
 * fall through to {@code GL_ARB_framebuffer_object} / {@code GL_EXT_framebuffer_object} and finally
 * set {@code fboSupported = false} ("zoom disabled"). Those capability fields come from a host
 * OpenGL symbol table that a WebGL2 context does not have, so they stay false here.
 *
 * This transformer replaces only the {@code OpenGL30} field read with the bridge's real FBO probe
 * ({@code port/runtime/gl.mjs#framebufferProbe}): an actual WebGL2 texture attached to an actual
 * WebGL2 framebuffer with a DEPTH24_STENCIL8 renderbuffer, cleared and read back. The probe is true
 * only when WebGL2 reported FRAMEBUFFER_COMPLETE and the clear rendered, so the gate is decided by
 * measured behaviour instead of a desktop capability bit. When the probe is false the game's own
 * fall-through (ARB/EXT, then "Could not create FBO. FBO's not supported.") still runs unchanged,
 * so a broken FBO is never reported as working.
 *
 * The implementation the gate then installs is {@code zombie.core.textures.GLFramebufferObject30},
 * which the build already compiles over {@code port.shim.GL30} (verified in boot.wasm:
 * {@code GLFramebufferObject30::glGenFramebuffers -> GL30::glGenFramebuffers ->
 * GL30C::nglGenFramebuffers}). The ARB/EXT gates are deliberately left untouched: those desktop
 * extensions really are absent in WebGL2 and their entrypoints are unmapped natives that raise.
 */
public final class FBOSupportCompatibility implements ClassHolderTransformer {
    private static final String TEXTURE_FBO = "zombie.core.textures.TextureFBO";
    private static final String TEXTURE_FBO_DEPTH = "zombie.core.textures.TextureFBODepth";
    private static final String CAPABILITIES = "org.lwjgl.opengl.GLCapabilities";
    private static final MethodReference PROBE =
        new MethodReference("port.shim.BrowserGL", "fboProbeComplete", ValueType.BOOLEAN);
    private static final MethodDescriptor CHECK = new MethodDescriptor("checkFBOSupport", ValueType.BOOLEAN);

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals(TEXTURE_FBO) && !cls.getName().equals(TEXTURE_FBO_DEPTH)) return;
        MethodHolder check = cls.getMethod(CHECK);
        if (check == null) throw new IllegalStateException(
            "Inventoried " + cls.getName() + ".checkFBOSupport()Z is missing");
        var program = check.getProgram();
        if (program == null) throw new IllegalStateException(
            cls.getName() + ".checkFBOSupport has no program to patch");
        List<GetFieldInstruction> gates = new ArrayList<>();
        for (int b = 0; b < program.basicBlockCount(); b++) {
            for (Instruction instruction = program.basicBlockAt(b).getFirstInstruction();
                instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof GetFieldInstruction get
                    && get.getField().getClassName().equals(CAPABILITIES)
                    && get.getField().getFieldName().equals("OpenGL30")) gates.add(get);
            }
        }
        if (gates.isEmpty()) throw new IllegalStateException(
            cls.getName() + " has no GLCapabilities.OpenGL30 read - the inventoried FBO gate changed, "
                + "refusing to patch blindly");
        for (GetFieldInstruction gate : gates) {
            Variable produced = gate.getReceiver();
            if (produced == null) throw new IllegalStateException(
                cls.getName() + " reads GLCapabilities.OpenGL30 without storing it, so the probe "
                    + "result cannot be fed to the branch");
            var probe = new InvokeInstruction();
            probe.setType(InvocationType.VIRTUAL);
            probe.setMethod(PROBE);
            probe.setReceiver(produced);
            probe.setLocation(gate.getLocation());
            gate.replace(probe);
        }
    }
}
