package port.natives;

import com.github.micycle1.clipper2.core.ClipType;
import com.github.micycle1.clipper2.core.FillRule;
import com.github.micycle1.clipper2.core.Path64;
import com.github.micycle1.clipper2.core.Paths64;
import com.github.micycle1.clipper2.core.Point64;
import com.github.micycle1.clipper2.engine.Clipper64;
import com.github.micycle1.clipper2.engine.PolyPath64;
import com.github.micycle1.clipper2.engine.PolyTree64;
import com.github.micycle1.clipper2.offset.ClipperOffset;
import com.github.micycle1.clipper2.offset.EndType;
import com.github.micycle1.clipper2.offset.JoinType;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Round 80: our implementation of the zombie.vehicles.Clipper natives (library PZClipper64), on top of
 * Clipper2-java (Boost licence) and earcut4j (ISC). One engine per game Clipper object; the game
 * object holds it in the field added by port.compiler.ClipperNativeCompatibility.
 *
 * Semantics and buffer formats were taken from the game's callers (javap) and checked against the
 * pinned library's exported functions (objdump, local analysis only, nothing copied):
 *  - integer Clipper2 (Clipper64) with scale 1000: a float coordinate becomes floor(v * 1000f + 0.5f),
 *    and comes back as (float) integer / 1000f;
 *  - buffers are read/written from absolute index 0 in the buffer's byte order (the game's buffers are
 *    big-endian), the Java position is not touched, and going past the capacity throws
 *    BufferOverflowException (VisibilityPolygon2.Drawer catches it and grows its buffer);
 *  - generatePolygons(clipType, delta, joinType) executes with FillRule.Positive into a polygon tree,
 *    then, if delta != 0, offsets that tree (closed paths with EndType.Polygon, open results with
 *    EndType.Butt, miter limit 2, arc tolerance 0) by delta * 1000; it returns the number of
 *    polygons (an outer ring and its holes; islands inside holes are polygons of their own; then each
 *    open result path as a polygon without holes) or -1 when Clipper2 fails. The added paths stay,
 *    as in Clipper2, until clear(); clear() does not drop the last result;
 *  - triangulate/triangulate2 run earcut over one polygon (outer ring followed by its holes).
 * Clipper2-java's defaults match the library's (PreserveCollinear true, ReverseSolution false).
 */
public final class ClipperEngine {
    static final float SCALE = 1000f;

    /** Outer ring plus its holes, in Clipper2's output orientation (outer positive area). */
    static final class Polygon {
        final Path64 outer;
        final List<Path64> holes = new ArrayList<>();
        Polygon(Path64 outer) { this.outer = outer; }
    }

    private final Clipper64 clipper = new Clipper64();
    private final List<Polygon> polygons = new ArrayList<>();
    /** Last result tree and open result paths (generateTriangulatePolygons reads the tree). */
    private PolyTree64 tree = new PolyTree64();
    private Paths64 open = new Paths64();

    static long toFixed(float v) { return (long) (int) Math.floor(v * SCALE + 0.5f); }
    static float fromFixed(long v) { return (float) v / SCALE; }

    // ---- input -------------------------------------------------------------------------------

    /** Drops the subject and clip paths only; the last result stays readable. */
    public void clear() { clipper.clear(); }

    /** Closed subject (x1,y1) (x2,y1) (x2,y2) (x1,y2). */
    public void addAABB(float x1, float y1, float x2, float y2) { clipper.addSubject(rect(x1, y1, x2, y2)); }

    /** Closed clip path (x1,y1) (x2,y1) (x2,y2) (x1,y2); with the default ctDifference it is cut away. */
    public void clipAABB(float x1, float y1, float x2, float y2) { clipper.addClip(rect(x1, y1, x2, y2)); }

    /** Closed subject of four points in argument order (CollideWithObstaclesPoly: a vehicle). */
    public void addPolygon(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
        clipper.addSubject(new Path64(point(x1, y1), point(x2, y2), point(x3, y3), point(x4, y4)));
    }

    /** Open subject of two points, stored end first: (x2,y2) then (x1,y1). */
    public void addLine(float x1, float y1, float x2, float y2) {
        clipper.addOpenSubject(new Path64(point(x2, y2), point(x1, y1)));
    }

    static Point64 point(float x, float y) { return new Point64(toFixed(x), toFixed(y)); }

    static Path64 rect(float x1, float y1, float x2, float y2) {
        return new Path64(point(x1, y1), point(x2, y1), point(x2, y2), point(x1, y2));
    }

    /** count points of (float x, float y) from index 0; isClip -> clip path, else closed/open subject. */
    public void addPath(int count, ByteBuffer bb, boolean isClip, boolean closed) {
        Path64 path = readPath(bb, 0, count);
        if (isClip) clipper.addClip(path);
        else if (!closed) clipper.addOpenSubject(path);
        else clipper.addSubject(path);
    }

    static Path64 readPath(ByteBuffer bb, int offset, int count) {
        if (count < 0) throw new IllegalArgumentException("PZClipper64: negative point count " + count);
        Path64 path = new Path64(count);
        for (int i = 0; i < count; i++) {
            int at = offset + i * 8;
            if (at + 8 > bb.capacity()) throw new BufferUnderflowException();
            path.add(new Point64(toFixed(bb.getFloat(at)), toFixed(bb.getFloat(at + 4))));
        }
        return path;
    }

    // ---- execution ---------------------------------------------------------------------------

    public int generatePolygons(int clipType, double delta, int joinType) {
        polygons.clear();
        tree = new PolyTree64();
        open = new Paths64();
        if (!clipper.execute(clipType(clipType), FillRule.Positive, tree, open)) return -1;
        if (delta != 0) {
            ClipperOffset offset = new ClipperOffset(2.0, 0.0);
            JoinType join = joinType(joinType);
            addTree(offset, join, tree);
            for (Path64 path : open) offset.addPath(path, join, EndType.Butt);
            tree = new PolyTree64();
            offset.execute(delta * SCALE, tree);
        }
        collect();
        return polygons.size();
    }

    private void collect() {
        flatten(tree, polygons);
        for (Path64 path : open) polygons.add(new Polygon(path));
    }

    /**
     * Free space of the 8x8 cell (cx, cy): the cell square minus the outer rings of the top-level
     * polygons of the last result (holes are not subtracted), with FillRule.EvenOdd. Starts by
     * dropping the added paths; returns the polygon count or -1.
     */
    public int generateTriangulatePolygons(int cx, int cy) {
        clipper.clear();
        long x = cx * 8000L, y = cy * 8000L;
        clipper.addSubject(new Path64(new Point64(x, y), new Point64(x + 8000, y),
            new Point64(x + 8000, y + 8000), new Point64(x, y + 8000)));
        for (int i = 0; i < tree.getCount(); i++) clipper.addClip(tree.get(i).getPolygon());
        polygons.clear();
        tree = new PolyTree64();
        open = new Paths64();
        if (!clipper.execute(ClipType.Difference, FillRule.EvenOdd, tree, open)) return -1;
        collect();
        return polygons.size();
    }

    private static void addTree(ClipperOffset offset, JoinType join, PolyPath64 node) {
        for (int i = 0; i < node.getCount(); i++) {
            PolyPath64 child = node.get(i);
            offset.addPath(child.getPolygon(), join, EndType.Polygon);
            addTree(offset, join, child);
        }
    }

    /** Each outer ring with its direct holes; the islands inside a hole become polygons too. */
    static void flatten(PolyPath64 root, List<Polygon> out) {
        for (int i = 0; i < root.getCount(); i++) {
            PolyPath64 outer = root.get(i);
            Polygon polygon = new Polygon(outer.getPolygon());
            out.add(polygon);
            for (int h = 0; h < outer.getCount(); h++) {
                PolyPath64 hole = outer.get(h);
                polygon.holes.add(hole.getPolygon());
                flatten(hole, out);
            }
        }
    }

    static ClipType clipType(int value) {
        return switch (value) { // zombie.vehicles.Clipper.ct* constants
            case 0 -> ClipType.NoClip; case 1 -> ClipType.Intersection; case 2 -> ClipType.Union;
            case 3 -> ClipType.Difference; case 4 -> ClipType.Xor;
            default -> throw new IllegalArgumentException("PZClipper64: unknown clip type " + value);
        };
    }

    static JoinType joinType(int value) {
        return switch (value) { // zombie.vehicles.Clipper.jt* / ClipperOffset$JoinType ordinals
            case 0 -> JoinType.Square; case 1 -> JoinType.Bevel; case 2 -> JoinType.Round; case 3 -> JoinType.Miter;
            default -> throw new IllegalArgumentException("PZClipper64: unknown join type " + value);
        };
    }

    // ---- output ------------------------------------------------------------------------------

    Polygon polygon(int index) {
        if (index < 0 || index >= polygons.size())
            throw new Error("vector::_M_range_check: __n (which is " + index + ") >= this->size() (which is "
                + polygons.size() + ")");
        return polygons.get(index);
    }

    int polygonCount() { return polygons.size(); }

    /** short n, n x (float x, float y), short holeCount, per hole: short n, n x (x, y). Returns 0. */
    public int getPolygon(int index, ByteBuffer bb) {
        Polygon polygon = polygon(index);
        Writer w = new Writer(bb);
        w.path(polygon.outer);
        w.putShort(polygon.holes.size());
        for (Path64 hole : polygon.holes) w.path(hole);
        return 0;
    }

    /** Earcut over polygon index: outer ring then each hole, as one vertex array. */
    static final class Triangulation {
        final List<Point64> vertices = new ArrayList<>();
        List<Integer> indices;
    }

    static Triangulation triangulation(Polygon polygon) {
        Triangulation t = new Triangulation();
        t.vertices.addAll(polygon.outer);
        int[] holes = new int[polygon.holes.size()];
        for (int h = 0; h < holes.length; h++) {
            holes[h] = t.vertices.size();
            t.vertices.addAll(polygon.holes.get(h));
        }
        double[] xy = new double[t.vertices.size() * 2];
        for (int i = 0; i < t.vertices.size(); i++) {
            xy[i * 2] = t.vertices.get(i).x;
            xy[i * 2 + 1] = t.vertices.get(i).y;
        }
        t.indices = earcut4j.Earcut.earcut(xy, holes.length == 0 ? null : holes, 2);
        return t;
    }

    /** Flat triangle list: (float x, float y) per earcut index, no header. Returns the index count. */
    public int triangulate(int index, ByteBuffer bb) {
        Triangulation t = triangulation(polygon(index));
        Writer w = new Writer(bb);
        for (int i : t.indices) w.point(t.vertices.get(i));
        return t.indices.size();
    }

    /** short vertexCount, vertices (float x, float y), then one short per index. Returns the index count. */
    public int triangulate2(int index, ByteBuffer bb) {
        Triangulation t = triangulation(polygon(index));
        Writer w = new Writer(bb);
        w.putShort(t.vertices.size());
        for (Point64 p : t.vertices) w.point(p);
        for (int i : t.indices) w.putShort(i);
        return t.indices.size();
    }

    /** Sequential writer from absolute index 0 that respects the capacity like the native helper. */
    static final class Writer {
        final ByteBuffer bb;
        int at;
        Writer(ByteBuffer bb) { this.bb = bb; }
        void need(int bytes) { if (at + bytes > bb.capacity()) throw new BufferOverflowException(); }
        void putShort(int v) { need(2); bb.putShort(at, (short) v); at += 2; }
        void putFloat(float v) { need(4); bb.putFloat(at, v); at += 4; }
        void point(Point64 p) { putFloat(fromFixed(p.x)); putFloat(fromFixed(p.y)); }
        void path(Path64 path) { putShort(path.size()); for (Point64 p : path) point(p); }
    }
}
