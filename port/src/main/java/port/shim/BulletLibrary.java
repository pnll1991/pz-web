package port.shim;

import java.nio.FloatBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Float32Array;
import port.runtime.memory.NativeMemory;

/** Bridges only the Bullet operations our own wasm32 module really implements. */
public final class BulletLibrary {
    private BulletLibrary() { }
    public static void initLogging() { initLoggingNative(); }
    /**
     * Round 52: zombie.core.physics.Bullet.defineVehicleScript(String,float[]) is the JNI entry
     * point the real game calls from VehicleScript.toBullet during script load (241 times in
     * build/real-browser/console-round51.txt, where it raised
     * UnsupportedOperationException: Java_zombie_core_physics_Bullet_defineVehicleScript__Ljava_lang_String_2_3F).
     * The block layout is read from the game itself: 11 scalars (modelScale, mass, rollInfluence,
     * suspensionStiffness, suspensionCompression, suspensionDamping, maxSuspensionTravelCm,
     * suspensionRestLength, wheelFriction, stoppingMovementForce, wheelCount), 5 floats per wheel,
     * then numShapes and the shape blocks. PZBullet's units/ownership are not recovered, so the
     * wasm32 module registers the block verbatim and reports whether it was new, redefined, or
     * rejected; a rejection raises instead of looking like success.
     */
    public static void defineVehicleScript(String name, float[] params) {
        if (params == null) throw new NullPointerException("Bullet.defineVehicleScript requires the parameter block");
        int count = params.length;
        FloatBuffer buffer = NativeMemory.memAllocFloat(count);
        long address = NativeMemory.memAddress(buffer);
        NativeMemory.memCopy(params, address, 0, count);
        Float32Array view = Float32Array.fromJavaBuffer(NativeMemory.memByteBuffer(address, Math.multiplyExact(count, 4)));
        int result = defineVehicleScriptNative(name, view, count);
        NativeMemory.memFree(buffer);
        if (result == 0) throw new IllegalStateException(
            "pzbullet rejected defineVehicleScript(" + name + ", " + count + " floats): "
            + "empty name, empty block, block over 256 floats, or a full registry");
    }
    public static String version() { return versionNative(); }
    public static boolean worldInitialised() { return worldInitialisedNative() != 0; }
    // A missing module must raise, never look like an initialised physics library.
    @JSBody(script="var b=globalThis.pzRuntime&&globalThis.pzRuntime.bullet;if(!b)throw Error('Bullet wasm32 module is not loaded');b.initLogging();")
    private static native void initLoggingNative();
    @JSBody(script="var b=globalThis.pzRuntime&&globalThis.pzRuntime.bullet;if(!b)throw Error('Bullet wasm32 module is not loaded');var v=b.version();if(typeof v!=='string'||v.length===0)throw Error('Bullet wasm32 module reports no version');return v;")
    private static native String versionNative();
    @JSBody(script="var b=globalThis.pzRuntime&&globalThis.pzRuntime.bullet;if(!b)throw Error('Bullet wasm32 module is not loaded');var w=b.worldInitialised();if(typeof w!=='number')throw Error('Bullet wasm32 module reports no world state');return w;")
    private static native int worldInitialisedNative();
    // The module copies the values into its own storage before returning, so the arena block
    // allocated by defineVehicleScript is free right after this call.
    // Returns 1 (new name), 2 (redefinition of a known name), 0 (rejected by the module).
    @JSBody(params={"name","values","count"}, script="var b=globalThis.pzRuntime&&globalThis.pzRuntime.bullet;if(!b)throw Error('Bullet wasm32 module is not loaded');if(typeof b.defineVehicleScript!=='function')throw Error('Bullet wasm32 module has no defineVehicleScript');return b.defineVehicleScript(name,values);")
    private static native int defineVehicleScriptNative(String name, Float32Array values, int count);
}
