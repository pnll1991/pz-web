package port;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import port.natives.ClipperEngine;
import port.natives.ClipperOffsetEngine;

/**
 * Round 80: buffer formats of the PZClipper64 natives, with synthetic geometry. Each test writes the
 * input the way the game's callers do (big-endian direct buffers, filled with relative puts) and reads
 * the output the way they do (clear(), then relative gets).
 */
class ClipperEngineTest {
    static ByteBuffer points(float... xy) {
        ByteBuffer bb = ByteBuffer.allocateDirect(Math.max(64, xy.length * 4));
        for (float v : xy) bb.putFloat(v);
        return bb;
    }

    static float signedArea(float[] xy) { // standard shoelace, y up
        double a = 0;
        for (int i = 0; i < xy.length / 2; i++) {
            int j = (i + 1) % (xy.length / 2);
            a += (double) xy[i * 2] * xy[j * 2 + 1] - (double) xy[j * 2] * xy[i * 2 + 1];
        }
        return (float) (a / 2);
    }

    static float[] ring(ByteBuffer out) {
        int n = out.getShort();
        float[] xy = new float[n * 2];
        for (int i = 0; i < xy.length; i++) xy[i] = out.getFloat();
        return xy;
    }

    /** addPath (count, xy floats) + getPolygon (short n, points, short holes, per hole short n, points). */
    @Test void addPathAndGetPolygonFormat() {
        ClipperEngine c = new ClipperEngine();
        // Outer square 0..10 and a hole 2..4 given as a clip path of a difference (ctDifference = 3).
        c.addPath(4, points(0, 0, 10, 0, 10, 10, 0, 10), false, true);
        c.addPath(4, points(2, 2, 4, 2, 4, 4, 2, 4), true, true);
        assertEquals(1, c.generatePolygons(3, 0, 0));
        ByteBuffer out = ByteBuffer.allocateDirect(256);
        out.clear();
        assertEquals(0, c.getPolygon(0, out));
        assertEquals(0, out.position(), "the native does not move the Java position");
        float[] outer = ring(out);
        assertEquals(8, outer.length);
        assertEquals(100f, signedArea(outer), 1e-3f);
        assertTrue(signedArea(outer) > 0, "outer ring comes out counter-clockwise (y up)");
        assertEquals(1, out.getShort());
        float[] hole = ring(out);
        assertEquals(-4f, signedArea(hole), 1e-3f);
    }

    /** CollideWithObstaclesPoly reverses the ring and uses the left normal: it must point outwards. */
    @Test void outerOrientationGivesOutwardNormalsAfterTheGameReversesIt() {
        ClipperEngine c = new ClipperEngine();
        c.addPath(4, points(0, 10, 10, 10, 10, 0, 0, 0), false, true); // clockwise input
        assertEquals(0, c.generatePolygons(3, 0, 0), "FillRule.Positive drops negative-wound subjects");
        c.clear();
        c.addPath(4, points(0, 0, 10, 0, 10, 10, 0, 10), false, true);
        assertEquals(1, c.generatePolygons(3, 0, 0));
        ByteBuffer out = ByteBuffer.allocateDirect(128);
        c.getPolygon(0, out);
        float[] xy = ring(out);
        // reversed order, edge (p[i] -> p[i+1]); left normal (-dy, dx) must point away from the centre 5,5
        int n = xy.length / 2;
        float ax = xy[(n - 1) * 2], ay = xy[(n - 1) * 2 + 1], bx = xy[(n - 2) * 2], by = xy[(n - 2) * 2 + 1];
        float nx = -(by - ay), ny = bx - ax, mx = (ax + bx) / 2 - 5, my = (ay + by) / 2 - 5;
        assertTrue(nx * mx + ny * my > 0);
    }

    /** Fixed point: floor(v * 1000 + 0.5), back as int / 1000. */
    @Test void coordinatesRoundTripThroughScale1000() {
        ClipperEngine c = new ClipperEngine();
        c.addPath(3, points(0.0004f, 0, 1.2346f, 0, 0, 1.0f), false, true);
        assertEquals(1, c.generatePolygons(2, 0, 0));
        ByteBuffer out = ByteBuffer.allocateDirect(128);
        c.getPolygon(0, out);
        float[] xy = ring(out);
        boolean found = false;
        for (int i = 0; i < xy.length; i += 2) if (xy[i] == 1.235f) found = true;
        assertTrue(found, "1.2346 rounds to 1235 / 1000");
        for (float v : xy) assertEquals(Math.round(v * 1000) / 1000f, v, 0f);
    }

    /** Overlapping subjects merge (Positive fill); intersection with a clip path (FBORenderOcclusion). */
    @Test void unionAndIntersection() {
        ClipperEngine c = new ClipperEngine();
        c.addPath(4, points(0, 0, 2, 0, 2, 2, 0, 2), false, true);
        c.addPath(4, points(1, 0, 3, 0, 3, 2, 1, 2), false, true);
        assertEquals(1, c.generatePolygons(3, 0, 0));
        c.clear();
        c.addPath(4, points(0, 0, 2, 0, 2, 2, 0, 2), false, true);
        c.addPath(4, points(1, 1, 3, 1, 3, 3, 1, 3), true, true);
        assertEquals(1, c.generatePolygons(1, 0, 0));
        ByteBuffer out = ByteBuffer.allocateDirect(128);
        c.getPolygon(0, out);
        assertEquals(1f, signedArea(ring(out)), 1e-3f);
        c.clear();
        c.addPath(4, points(0, 0, 1, 0, 1, 1, 0, 1), false, true);
        c.addPath(4, points(5, 5, 6, 5, 6, 6, 5, 6), true, true);
        assertEquals(0, c.generatePolygons(1, 0, 0), "disjoint polygons do not overlap");
    }

    /** generatePolygons(delta, jtMiter) offsets the result (HLChunkRegion shrinks by -0.1). */
    @Test void negativeDeltaShrinksTheResult() {
        ClipperEngine c = new ClipperEngine();
        c.addPath(4, points(0, 0, 4, 0, 4, 4, 0, 4), false, true);
        assertEquals(1, c.generatePolygons(3, -0.5, 3));
        ByteBuffer out = ByteBuffer.allocateDirect(128);
        c.getPolygon(0, out);
        assertEquals(9f, signedArea(ring(out)), 1e-3f);
        assertEquals(1, c.generatePolygons(3, 0, 0), "paths stay until clear(): a second run starts again");
        out.clear();
        c.getPolygon(0, out);
        assertEquals(16f, signedArea(ring(out)), 1e-3f);
    }

    /** Open results are appended as polygons without holes; with a delta they are also offset (Butt). */
    @Test void openPathResultsAndButtOffset() {
        ClipperEngine c = new ClipperEngine();
        c.addPath(2, points(0, 0, 10, 0), false, false);
        assertEquals(1, c.generatePolygons(3, 0, 0));
        ByteBuffer out = ByteBuffer.allocateDirect(128);
        c.getPolygon(0, out);
        assertEquals(2, ring(out).length / 2);
        assertEquals(0, out.getShort());
        assertEquals(2, c.generatePolygons(3, 1, 3), "offset band, then the raw open path");
        out.clear();
        c.getPolygon(0, out);
        assertEquals(20f, Math.abs(signedArea(ring(out))), 1e-2f);
    }

    /** addAABB = subject (x1,y1)-(x2,y2), clipAABB = clip; addPolygon = 4-point subject; addLine = open. */
    @Test void rectanglePolygonAndLineInputs() {
        ClipperEngine c = new ClipperEngine();
        c.addAABB(0, 0, 4, 2);
        c.clipAABB(1, 0, 2, 2);
        assertEquals(2, c.generatePolygons(3, 0, 0), "the clip rectangle cuts the subject in two");
        c.clear();
        assertEquals(0, c.generatePolygons(3, 0, 0), "clear() drops the inputs: nothing left");
        c.addPolygon(0, 0, 2, 0, 2, 2, 0, 2);
        assertEquals(1, c.generatePolygons(3, 0, 0));
        ByteBuffer out = ByteBuffer.allocateDirect(128);
        c.getPolygon(0, out);
        assertEquals(4f, signedArea(ring(out)), 1e-3f);
        c.clear();
        c.addLine(0, 0, 5, 0);
        assertEquals(1, c.generatePolygons(3, 0, 0));
        out.clear();
        c.getPolygon(0, out);
        float[] line = ring(out);
        assertEquals(4, line.length);
    }

    /** clear() only drops the inputs: the last result can still be read (as the native keeps it). */
    @Test void clearKeepsTheLastResult() {
        ClipperEngine c = new ClipperEngine();
        c.addAABB(0, 0, 1, 1);
        assertEquals(1, c.generatePolygons(3, 0, 0));
        c.clear();
        ByteBuffer out = ByteBuffer.allocateDirect(64);
        assertEquals(0, c.getPolygon(0, out));
        assertEquals(1f, signedArea(ring(out)), 1e-3f);
    }

    /** triangulate: (x, y) per index, no header; returns the index count. */
    @Test void triangulateFormat() {
        ClipperEngine c = new ClipperEngine();
        c.addPath(4, points(0, 0, 4, 0, 4, 4, 0, 4), false, true);
        c.addPath(4, points(1, 1, 3, 1, 3, 3, 1, 3), true, true);
        assertEquals(1, c.generatePolygons(3, 0, 0));
        ByteBuffer out = ByteBuffer.allocateDirect(1024);
        int n = c.triangulate(0, out);
        assertEquals(0, n % 3);
        assertEquals(8 * 3, n, "a square with a square hole is 8 triangles");
        double area = 0;
        for (int t = 0; t < n / 3; t++) area += Math.abs(signedArea(new float[] {
            out.getFloat(), out.getFloat(), out.getFloat(), out.getFloat(), out.getFloat(), out.getFloat() }));
        assertEquals(12, area, 1e-3);
        assertThrows(BufferOverflowException.class, () -> c.triangulate(0, ByteBuffer.allocateDirect(64)));
    }

    /** triangulate2: short vertexCount, vertices, then short indices; returns the index count. */
    @Test void triangulate2Format() {
        ClipperEngine c = new ClipperEngine();
        c.addAABB(0, 0, 2, 1);
        assertEquals(1, c.generatePolygons(3, 0, 0));
        ByteBuffer out = ByteBuffer.allocateDirect(256);
        int n = c.triangulate2(0, out);
        assertEquals(6, n);
        int vertices = out.getShort();
        assertEquals(4, vertices);
        float[] xy = new float[vertices * 2];
        for (int i = 0; i < xy.length; i++) xy[i] = out.getFloat();
        double area = 0;
        for (int t = 0; t < 2; t++) {
            int a = out.getShort(), b = out.getShort(), d = out.getShort();
            area += Math.abs(signedArea(new float[] { xy[a * 2], xy[a * 2 + 1], xy[b * 2], xy[b * 2 + 1], xy[d * 2], xy[d * 2 + 1] }));
        }
        assertEquals(2, area, 1e-4);
    }

    /** Free space of the 8x8 cell minus the outer rings of the last result. */
    @Test void generateTriangulatePolygonsCutsTheCell() {
        ClipperEngine c = new ClipperEngine();
        c.addAABB(10, 10, 12, 12); // inside cell (1, 1) = [8, 16]^2
        assertEquals(1, c.generatePolygons(3, 0, 0));
        assertEquals(1, c.generateTriangulatePolygons(1, 1));
        ByteBuffer out = ByteBuffer.allocateDirect(256);
        c.getPolygon(0, out);
        assertEquals(64f, signedArea(ring(out)), 1e-3f);
        assertEquals(1, out.getShort());
        assertEquals(-4f, signedArea(ring(out)), 1e-3f);
    }

    /** ClipperOffset: addPath(count, xy, join, end), execute(delta), getPolygonCount, getPolygon format. */
    @Test void clipperOffsetOutlinesAPolyline() {
        ClipperOffsetEngine o = new ClipperOffsetEngine();
        assertNotEquals(0, o.handle());
        ByteBuffer in = points(0, 0, 10, 0);
        in.flip(); // IsoMetaGrid flips before addPath; the native still reads from index 0
        o.addPath(2, in, 3, 2); // Miter, Butt
        o.execute(0.5);
        assertEquals(1, o.getPolygonCount());
        ByteBuffer out = ByteBuffer.allocateDirect(256);
        assertEquals(0, o.getPolygon(0, out));
        assertEquals(10f, Math.abs(signedArea(ring(out))), 1e-2f);
        assertEquals(0, out.getShort());
        o.clear();
        assertEquals(1, o.getPolygonCount(), "clear() drops the inputs, not the result");
        o.execute(0.5);
        assertEquals(0, o.getPolygonCount());
        assertThrows(java.nio.BufferUnderflowException.class, () -> o.addPath(100, ByteBuffer.allocateDirect(16), 3, 2));
    }

    @Test void outputPastCapacityThrowsBufferOverflow() {
        ClipperEngine c = new ClipperEngine();
        c.addPath(4, points(0, 0, 1, 0, 1, 1, 0, 1), false, true);
        assertEquals(1, c.generatePolygons(3, 0, 0));
        assertThrows(BufferOverflowException.class, () -> c.getPolygon(0, ByteBuffer.allocateDirect(16)));
        assertThrows(Error.class, () -> c.getPolygon(1, ByteBuffer.allocateDirect(64)));
    }
}
