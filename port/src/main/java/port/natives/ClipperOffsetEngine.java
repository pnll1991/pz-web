package port.natives;

import com.github.micycle1.clipper2.engine.PolyTree64;
import com.github.micycle1.clipper2.offset.ClipperOffset;
import com.github.micycle1.clipper2.offset.EndType;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Round 80: our implementation of the zombie.vehicles.ClipperOffset natives (library PZClipper64), on
 * Clipper2-java. Same scale (1000), buffer rules and output format as {@link ClipperEngine}; the
 * offsetter uses the library's settings (miter limit 2, arc tolerance 0). IsoMetaGrid and Zone use it
 * to outline polylines (JoinType.Miter, EndType.Butt, delta = width / 2).
 */
public final class ClipperOffsetEngine {
    private static long nextHandle = 1;
    private final long handle = nextHandle++;
    private final ClipperOffset offset = new ClipperOffset(2.0, 0.0);
    private final List<ClipperEngine.Polygon> polygons = new ArrayList<>();

    /** Opaque non-zero value the game stores in ClipperOffset.address (it never reads it back). */
    public long handle() { return handle; }

    /** Drops the input paths; the last result stays readable. */
    public void clear() { offset.clear(); }

    /** count x (float x, float y) from index 0; joinType/endType are ClipperOffset$JoinType/EndType ordinals. */
    public void addPath(int count, ByteBuffer bb, int joinType, int endType) {
        offset.addPath(ClipperEngine.readPath(bb, 0, count), ClipperEngine.joinType(joinType), endType(endType));
    }

    static EndType endType(int value) {
        return switch (value) {
            case 0 -> EndType.Polygon; case 1 -> EndType.Joined; case 2 -> EndType.Butt;
            case 3 -> EndType.Square; case 4 -> EndType.Round;
            default -> throw new IllegalArgumentException("PZClipper64: unknown end type " + value);
        };
    }

    public void execute(double delta) {
        polygons.clear();
        PolyTree64 tree = new PolyTree64();
        offset.execute(delta * ClipperEngine.SCALE, tree);
        ClipperEngine.flatten(tree, polygons);
    }

    public int getPolygonCount() { return polygons.size(); }

    /** Same layout as Clipper.getPolygon: outer ring, hole count, holes. Returns 0. */
    public int getPolygon(int index, ByteBuffer bb) {
        if (index < 0 || index >= polygons.size())
            throw new Error("vector::_M_range_check: __n (which is " + index + ") >= this->size() (which is "
                + polygons.size() + ")");
        ClipperEngine.Polygon polygon = polygons.get(index);
        ClipperEngine.Writer w = new ClipperEngine.Writer(bb);
        w.path(polygon.outer);
        w.putShort(polygon.holes.size());
        for (var hole : polygon.holes) w.path(hole);
        return 0;
    }
}
