package dev.steampad.emote.bend;

import dev.steampad.compat.mc.ModelPartCompat;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

/**
 * Correctness checks for {@link CuboidBender}'s geometry math — no Minecraft/Mixin runtime needed,
 * {@code ModelPart.Quad}/{@code Vertex} and {@code Direction} are plain data classes.
 */
class CuboidBenderTest {

    /** A simple 4x8x4 box (roughly leg-sized), built the same way vanilla's own cuboid constructor
     *  would lay out its 6 faces — enough for {@link CuboidBender#precompute} to derive real geometry
     *  from, without needing to reverse-engineer vanilla's exact UV atlas layout (this test only cares
     *  about POSITIONS, and uv values are irrelevant to every assertion below). */
    private static ModelPart.Polygon[] box(float minX, float minY, float minZ,
                                        float maxX, float maxY, float maxZ) {
        Vector3f[] c = {
                new Vector3f(minX, minY, minZ), new Vector3f(maxX, minY, minZ),
                new Vector3f(maxX, maxY, minZ), new Vector3f(minX, maxY, minZ),
                new Vector3f(minX, minY, maxZ), new Vector3f(maxX, minY, maxZ),
                new Vector3f(maxX, maxY, maxZ), new Vector3f(minX, maxY, maxZ),
        };
        return new ModelPart.Polygon[]{
                quad(c[0], c[1], c[2], c[3], 0, 0, -1),   // north
                quad(c[5], c[4], c[7], c[6], 0, 0, 1),    // south
                quad(c[4], c[0], c[3], c[7], -1, 0, 0),   // west
                quad(c[1], c[5], c[6], c[2], 1, 0, 0),    // east
                quad(c[3], c[2], c[6], c[7], 0, 1, 0),    // up
                quad(c[4], c[5], c[1], c[0], 0, -1, 0),   // down
        };
    }

    private static ModelPart.Polygon quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                                       float nx, float ny, float nz) {
        ModelPart.Vertex[] verts = {
                new ModelPart.Vertex(a.x(), a.y(), a.z(), 0, 0),
                new ModelPart.Vertex(b.x(), b.y(), b.z(), 1, 0),
                new ModelPart.Vertex(c.x(), c.y(), c.z(), 1, 1),
                new ModelPart.Vertex(d.x(), d.y(), d.z(), 0, 1),
        };
        return ModelPartCompat.polygon(verts, new Vector3f(nx, ny, nz));
    }

    @Test
    void zeroBendLeavesGeometryAtRestPosition() {
        ModelPart.Polygon[] original = box(-2, 0, -2, 2, 8, 2);
        CuboidBender.Precomputed pre = CuboidBender.precompute(original, Direction.UP);
        ModelPart.Polygon[] bent = CuboidBender.apply(pre, 0f, 0f);

        // Collect every ORIGINAL vertex position (from the un-subdivided vanilla quads) and confirm
        // each one still appears (within float error) somewhere in the bent-at-zero mesh — subdivision
        // adds new vertices at face interiors, but never moves an existing corner when bendValue=0.
        for (ModelPart.Polygon q : original) {
            for (ModelPart.Vertex v : ModelPartCompat.vertices(q)) {
                assertTrue(anyVertexNear(bent, ModelPartCompat.x(v), ModelPartCompat.y(v), ModelPartCompat.z(v)),
                        "corner (" + ModelPartCompat.x(v) + "," + ModelPartCompat.y(v) + "," + ModelPartCompat.z(v) + ") missing after a zero bend");
            }
        }
    }

    @Test
    void precomputePivotIsTrueGeometricCenterForAnAsymmetricCuboid() {
        // Real vanilla right_arm cuboid: X spans -3..1 (NOT symmetric around 0), Y 0..12, Z -2..2 —
        // asymmetric on X specifically catches the D105 bug: an earlier version derived the pivot from
        // (minCorner+maxCorner)/2 of whichever two VERTICES a scan happened to hit first at the bend
        // axis's extremes, which only equals the true center when those two vertices are diagonally
        // opposite on the other two axes too — not guaranteed, and wrong for an asymmetric box like
        // this one. The correct pivot (matching bendy-lib's own Builder.build(): bendX=(sizeX+2x)/2,
        // independent per axis) is (-1, 6, 0).
        ModelPart.Polygon[] armLike = box(-3, 0, -2, 1, 12, 2);
        CuboidBender.Precomputed pre = CuboidBender.precompute(armLike, Direction.UP);
        Vector3f pivot = pre.pivotForTesting();

        assertEquals(-1f, pivot.x(), 0.01f, "pivot X should be the box's true center, not an arbitrary vertex's X");
        assertEquals(6f, pivot.y(), 0.01f, "pivot Y should be the box's true center");
        assertEquals(0f, pivot.z(), 0.01f, "pivot Z should be the box's true center, not an arbitrary vertex's Z");
    }

    @Test
    void nonZeroBendVisiblyMovesGeometryButKeepsItFinite() {
        // Deliberately doesn't assert WHICH half rotates vs shears — that split is an internal detail
        // of the ported algorithm (verified against the real bendy-lib source, not re-derived by
        // guessing), and this project's own history (D101) already has one cautionary tale about
        // trusting an anatomical assumption over the actual reference code. What's safe to assert
        // without re-deriving the whole algorithm: a real bend measurably moves SOME geometry, and
        // never produces NaN/garbage.
        ModelPart.Polygon[] original = box(-2, 0, -2, 2, 8, 2);
        CuboidBender.Precomputed pre = CuboidBender.precompute(original, Direction.UP);
        float quarterTurn = (float) (Math.PI / 2);
        ModelPart.Polygon[] bent = CuboidBender.apply(pre, 0f, quarterTurn);

        boolean foundShiftedVertex = false;
        for (ModelPart.Polygon q : bent) {
            for (ModelPart.Vertex v : ModelPartCompat.vertices(q)) {
                assertTrue(Float.isFinite(ModelPartCompat.x(v)) && Float.isFinite(ModelPartCompat.y(v)) && Float.isFinite(ModelPartCompat.z(v)),
                        "bend produced a non-finite vertex position");
                if (!anyVertexNear(original, ModelPartCompat.x(v), ModelPartCompat.y(v), ModelPartCompat.z(v))) {
                    foundShiftedVertex = true;
                }
            }
        }
        assertTrue(foundShiftedVertex, "a 90-degree bend produced no visible geometry change at all");
    }

    private static boolean anyVertexNear(ModelPart.Polygon[] quads, float x, float y, float z) {
        for (ModelPart.Polygon q : quads) {
            for (ModelPart.Vertex v : ModelPartCompat.vertices(q)) {
                if (Math.abs(ModelPartCompat.x(v) - x) < 0.01f && Math.abs(ModelPartCompat.y(v) - y) < 0.01f && Math.abs(ModelPartCompat.z(v) - z) < 0.01f) {
                    return true;
                }
            }
        }
        return false;
    }
}
