package port.compiler;

import java.util.Map;
import java.util.Set;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;

/**
 * Ronda 80 (frente O): esperas activas sobre {@code Thread.isAlive()} sin sleep ni wait.
 *
 * Tras Esc -> QUIT la página quedó al 100 % de CPU después de "STATE: exit IngameState"
 * (/tmp/pz80v). {@code IngameState.exit()} (javap del jar fijado) llama, en este orden,
 * {@code WorldStreamer.stop()} = {@code finished=true; while (worldStreamer.isAlive()) MainThread.busyWait();}
 * ({@code busyWait} sólo vacía la cola de invokeOnMain), {@code LightingThread.stop()} =
 * {@code finished=true; while (lightingThread.isAlive());} y, vía {@code IsoRegions.reset()},
 * {@code IsoRegionWorker.stop()} = {@code finished=true; ...; while (thread.isAlive());}. En TeaVM los
 * hilos son corrutinas: el hilo esperado (dormido en {@code Thread.sleep}) sólo ve {@code finished}
 * si la fibra del juego cede, y estas tres esperas nunca ceden. El resto de las esperas del jar sobre
 * {@code isAlive()} ya duermen ({@code MapCollisionData.stop}, {@code PolygonalMap2.stop},
 * {@code PathfindNativeThread.stopThread}, ...).
 *
 * Reescribe cada {@code Thread.isAlive()} de esos tres métodos por
 * {@code port.runtime.JdkSupport.aliveYielding(Thread)}: mismo valor, más la cesión de
 * {@code onSpinWait}. Es planificación de hilos, no cambia reglas del juego. El build falla si un método
 * deja de existir o ya no contiene la llamada (otra versión del jar).
 */
public final class SpinWaitCompatibility implements ClassHolderTransformer {
    private static final MethodDescriptor STOP = new MethodDescriptor("stop", ValueType.VOID);
    static final Map<String, Set<MethodDescriptor>> TARGETS = Map.of(
        "zombie.iso.WorldStreamer", Set.of(STOP),
        "zombie.iso.LightingThread", Set.of(STOP),
        "zombie.iso.areas.isoregion.IsoRegionWorker", Set.of(STOP));
    private static final MethodDescriptor IS_ALIVE = new MethodDescriptor("isAlive", ValueType.BOOLEAN);
    static final MethodReference ALIVE_YIELDING = new MethodReference("port.runtime.JdkSupport", "aliveYielding",
        ValueType.object("java.lang.Thread"), ValueType.BOOLEAN);

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        Set<MethodDescriptor> methods = TARGETS.get(cls.getName());
        if (methods == null) return;
        for (MethodDescriptor descriptor : methods) {
            MethodHolder method = cls.getMethod(descriptor);
            if (method == null || method.getProgram() == null)
                throw new IllegalStateException("SpinWaitCompatibility: missing " + cls.getName() + "." + descriptor);
            int rewritten = 0;
            for (var block : method.getProgram().getBasicBlocks())
                for (var instruction : block)
                    if (instruction instanceof InvokeInstruction invoke && isThreadIsAlive(invoke)) {
                        var thread = invoke.getInstance();
                        invoke.setType(InvocationType.SPECIAL);
                        invoke.setInstance(null);
                        invoke.setMethod(ALIVE_YIELDING);
                        invoke.setArguments(thread);
                        rewritten++;
                    }
            if (rewritten == 0)
                throw new IllegalStateException("SpinWaitCompatibility: no Thread.isAlive() in " + cls.getName() + "." + descriptor);
        }
    }

    private static boolean isThreadIsAlive(InvokeInstruction invoke) {
        return invoke.getInstance() != null && invoke.getMethod().getClassName().equals("java.lang.Thread")
            && invoke.getMethod().getDescriptor().equals(IS_ALIVE);
    }
}
