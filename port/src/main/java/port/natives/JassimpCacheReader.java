package port.natives;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Rebuilds the AiScene that the jassimp JNI library returned on this PC, from the cache written by
 * tools/jassimp/JassimpCache.java (which runs the pinned libjassimp64.so with the game's flags).
 *
 * Format v1 (DataInput big-endian scalars; buffer payloads verbatim, little-endian native layout):
 *   int magic 'PZJA', int version, long postProcessFlags, int[8] native sizes
 *   (aiVectorKey, aiQuatKey, aiVector3D, float, int, uint, double, long), UTF source, long sourceSize
 *   meshes:     int n; { UTF name, int material, int primitiveTypes, int nv, int nf, bool pure,
 *               int faceBufferSize, buf vertices, buf faces, buf faceOffsets, buf normals,
 *               buf tangents, buf bitangents, 8 x buf colors, 8 x { int uvComponents, [buf] },
 *               int nBones; { UTF name, 16 x float offset, int nW; { int vertex, float weight } } }
 *   materials:  int n; { int nProps; { UTF key, int semantic, int index, int type, byte kind, data } }
 *   animations: int n; { UTF name, double duration, double tps, int nCh;
 *               { UTF node, int nPos, int nRot, int nScale, int pre, int post, buf pos, buf rot, buf scale } }
 *   root:       bool present; node = { UTF name, 16 x float, int nMeshes (-1 = null), int[], int nChildren, node* }
 *   buf = int length (-1 = null) + bytes.
 * Every object is created through {@link JassimpObjects}, i.e. with the library's own constructors
 * and allocators, as the native does.
 */
public final class JassimpCacheReader {
    public static final int MAGIC = 0x505a4a41;
    public static final int VERSION = 1;
    /** Native sizes on linux-x86_64 (and wasm32 layout of the same structs): what loadLibrary() reads. */
    public static final int[] NATIVE_SIZES = {24, 24, 12, 4, 4, 4, 8, 8};
    // AiMesh.allocateDataChannel channel types
    private static final int NORMALS = 0, TANGENTS = 1, BITANGENTS = 2, COLORSET = 3, TEXCOORDS_1D = 4;

    private JassimpCacheReader() { }

    public static Object read(InputStream input, long requestedFlags, String path) throws IOException {
        DataInputStream in = new DataInputStream(input.markSupported() ? input : new java.io.BufferedInputStream(input, 1 << 16));
        if (in.readInt() != MAGIC) throw new IOException("Unable to open file \"" + path + "\": not a preconverted jassimp cache");
        int version = in.readInt();
        if (version != VERSION) throw new IOException(path + ": jassimp cache version " + version + ", expected " + VERSION);
        long flags = in.readLong();
        if (flags != requestedFlags)
            throw new IOException(path + ": jassimp cache was converted with post-process flags 0x" + Long.toHexString(flags)
                + " but the game requested 0x" + Long.toHexString(requestedFlags) + " (re-run tools/jassimp/JassimpCache.java)");
        for (int i = 0; i < NATIVE_SIZES.length; i++) {
            int size = in.readInt();
            if (size != NATIVE_SIZES[i]) throw new IOException(path + ": jassimp cache native size #" + i + " = " + size);
        }
        in.readUTF();   // source path (informational)
        in.readLong();  // source size (informational)

        Object scene = JassimpObjects.newScene();
        int meshes = count(in, path);
        for (int m = 0; m < meshes; m++) JassimpObjects.sceneAddMesh(scene, mesh(in, path));
        int materials = count(in, path);
        for (int m = 0; m < materials; m++) JassimpObjects.sceneAddMaterial(scene, material(in, path));
        int animations = count(in, path);
        for (int a = 0; a < animations; a++) JassimpObjects.sceneAddAnimation(scene, animation(in, path));
        if (in.readBoolean()) JassimpObjects.sceneSetRoot(scene, node(in, null, path));
        if (in.read() != -1) throw new IOException(path + ": trailing bytes in jassimp cache");
        return scene;
    }

    private static int count(DataInputStream in, String path) throws IOException {
        int n = in.readInt();
        if (n < 0) throw new IOException(path + ": negative count in jassimp cache");
        return n;
    }

    /** Copies one length-prefixed payload into the buffer the library allocated (null = absent). */
    private static void fill(DataInputStream in, ByteBuffer target, String what, String path) throws IOException {
        int length = in.readInt();
        if (length < 0) {
            if (target != null) throw new IOException(path + ": " + what + " allocated but missing in cache");
            return;
        }
        if (target == null || target.capacity() != length)
            throw new IOException(path + ": " + what + " size " + length + " != allocated "
                + (target == null ? "null" : String.valueOf(target.capacity())));
        byte[] data = new byte[length];
        in.readFully(data);
        ByteBuffer view = target.duplicate();
        view.clear();
        view.put(data);
    }

    private static boolean present(DataInputStream in) throws IOException {
        in.mark(4);
        int length = in.readInt();
        in.reset();
        return length >= 0;
    }

    private static Object mesh(DataInputStream in, String path) throws IOException {
        Object mesh = JassimpObjects.newMesh();
        String name = in.readUTF();
        int material = in.readInt();
        int primitive = in.readInt();
        int nv = in.readInt();
        int nf = in.readInt();
        boolean pure = in.readBoolean();
        int faceBufferSize = in.readInt();
        JassimpObjects.meshInit(mesh, name, material, primitive, nv, nf, pure, faceBufferSize);
        fill(in, JassimpObjects.meshVertices(mesh), "vertices", path);
        fill(in, JassimpObjects.meshFaces(mesh), "faces", path);
        fill(in, JassimpObjects.meshFaceOffsets(mesh), "faceOffsets", path);
        if (present(in)) JassimpObjects.meshChannel(mesh, NORMALS, 0);
        fill(in, JassimpObjects.meshNormals(mesh), "normals", path);
        if (present(in)) JassimpObjects.meshChannel(mesh, TANGENTS, 0);
        fill(in, JassimpObjects.meshTangents(mesh), "tangents", path);
        if (present(in)) JassimpObjects.meshChannel(mesh, BITANGENTS, 0);
        fill(in, JassimpObjects.meshBitangents(mesh), "bitangents", path);
        for (int c = 0; c < 8; c++) {
            if (present(in)) JassimpObjects.meshChannel(mesh, COLORSET, c);
            fill(in, JassimpObjects.meshColors(mesh, c), "colors" + c, path);
        }
        for (int t = 0; t < 8; t++) {
            int components = in.readInt();
            if (components == 0) continue;
            if (components < 1 || components > 3) throw new IOException(path + ": uv components " + components);
            JassimpObjects.meshChannel(mesh, TEXCOORDS_1D + components - 1, t);
            fill(in, JassimpObjects.meshTexCoords(mesh, t), "texcoords" + t, path);
        }
        int bones = count(in, path);
        for (int b = 0; b < bones; b++) {
            String boneName = in.readUTF();
            Object bone = JassimpObjects.newBone(boneName, matrix(in));
            int weights = count(in, path);
            for (int w = 0; w < weights; w++) JassimpObjects.boneAddWeight(bone, in.readInt(), in.readFloat());
            JassimpObjects.meshAddBone(mesh, bone);
        }
        return mesh;
    }

    private static Object material(DataInputStream in, String path) throws IOException {
        Object material = JassimpObjects.newMaterial();
        int properties = count(in, path);
        for (int p = 0; p < properties; p++) {
            String key = in.readUTF();
            int semantic = in.readInt();
            int index = in.readInt();
            int type = in.readInt();
            int kind = in.readByte();
            Object property;
            switch (kind) {
                case 'S' -> property = JassimpObjects.newPropertyObject(key, semantic, index, type, in.readUTF());
                case 'F' -> property = JassimpObjects.newPropertyObject(key, semantic, index, type, Float.valueOf(in.readFloat()));
                case 'I' -> property = JassimpObjects.newPropertyObject(key, semantic, index, type, Integer.valueOf(in.readInt()));
                case 'C' -> property = JassimpObjects.newPropertyObject(key, semantic, index, type,
                    JassimpObjects.wrapColor4(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()));
                case 'B' -> {
                    in.mark(4);
                    int length = in.readInt();
                    in.reset();
                    if (length < 0) throw new IOException(path + ": null material buffer");
                    property = JassimpObjects.newPropertyBuffer(key, semantic, index, type, length);
                    fill(in, JassimpObjects.propertyBuffer(property), "material " + key, path);
                }
                default -> throw new IOException(path + ": material property kind " + kind);
            }
            JassimpObjects.materialAddProperty(material, property);
        }
        return material;
    }

    private static Object animation(DataInputStream in, String path) throws IOException {
        Object animation = JassimpObjects.newAnimation(in.readUTF(), in.readDouble(), in.readDouble());
        int channels = count(in, path);
        for (int c = 0; c < channels; c++) {
            String node = in.readUTF();
            int pos = in.readInt(), rot = in.readInt(), scale = in.readInt(), pre = in.readInt(), post = in.readInt();
            Object channel = JassimpObjects.newNodeAnim(node, pos, rot, scale, pre, post);
            fill(in, JassimpObjects.nodeAnimPosKeys(channel), "posKeys " + node, path);
            fill(in, JassimpObjects.nodeAnimRotKeys(channel), "rotKeys " + node, path);
            fill(in, JassimpObjects.nodeAnimScaleKeys(channel), "scaleKeys " + node, path);
            JassimpObjects.animationAddChannel(animation, channel);
        }
        return animation;
    }

    private static Object node(DataInputStream in, Object parent, String path) throws IOException {
        String name = in.readUTF();
        float[] matrix = matrix(in);
        int meshCount = in.readInt();
        int[] meshes = null;
        if (meshCount >= 0) {
            meshes = new int[meshCount];
            for (int i = 0; i < meshCount; i++) meshes[i] = in.readInt();
        }
        Object node = JassimpObjects.wrapNode(parent, matrix, meshes, name);
        int children = count(in, path);
        for (int c = 0; c < children; c++) node(in, node, path);
        return node;
    }

    private static float[] matrix(DataInputStream in) throws IOException {
        float[] m = new float[16];
        for (int i = 0; i < 16; i++) m[i] = in.readFloat();
        return m;
    }
}
