package dev.steampad.emote.bend;

import dev.steampad.compat.mc.ModelPartCompat;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

/**
 * The actual "bend" deformation math — a full per-vertex port (with attribution, MIT-licensed source)
 * of <a href="https://github.com/KosmX/bendy-lib">KosmX/bendy-lib</a>'s {@code IBendable#applyBend}
 * and {@code BendableCuboid.Builder}, adapted from official Mojang mappings (its target, MC 1.21.4) to
 * this project's Yarn 1.21.10 mappings and JOML API, verified method-by-method with {@code javap}
 * against the real mapped 1.21.10 jar before writing a line of this (same discipline as every other
 * mixin in this project).
 *
 * <p><b>Why a port instead of the real dependency (D103):</b> bendy-lib's only build past MC 1.19 for
 * the version actually needed (5.1, targeting 1.21.4) is published to GitHub Packages (auth-gated, no
 * Maven Central artifact past the 2021-era 2.0.4) and mixins directly into {@code ModelPart.Cube} —
 * the exact class this project already got burned depending on a mismatched-version build of (STATE.md
 * Fix 12). Confirmed via {@code javap} that the 1.21.10 {@code ModelPart.Cuboid} constructor gained an
 * extra {@code Set<Direction>} parameter versus bendy-lib's 1.21.4 target — a real, concrete API
 * change in exactly the class it mixins into, not a hypothetical risk.
 *
 * <p><b>One deliberate, documented simplification versus the reference:</b> bendy-lib rebuilds each
 * cuboid's UV-mapped quads from scratch (u, v, textureWidth, textureHeight, mirror) to subdivide them
 * finely (one quad per texture pixel). Re-deriving that layout by hand would be the highest-risk part
 * of this port (easy to get subtly wrong, and wrong there means visibly broken/stretched textures).
 * Instead, this subdivides vanilla's OWN already-correctly-UV-mapped {@link ModelPart.Polygon}s (built by
 * vanilla's real constructor, never touched here) via plain bilinear interpolation of each quad's 4
 * known-good corners (position AND uv together) — mathematically exact for a planar quad, and it means
 * this code never needs to know the cuboid's texture-atlas layout at all. Subdivision only happens
 * along whichever of the quad's two parametric axes actually runs along the bend direction (a "cap"
 * face perpendicular to the bend axis needs none — see {@link #buildSubQuads}); "side" faces get
 * subdivided into enough slices to read as a smooth curve rather than a hard two-piece hinge.
 */
public final class CuboidBender {

    private CuboidBender() {}

    /** Minimum/maximum slices a "side" face (one that spans the bend axis) is subdivided into — floor
     *  guarantees at least a gentle curve even for a tiny cuboid; ceiling bounds the per-frame cost for
     *  a long one (a torso/leg cuboid is ~12 model units long, well under the cap). */
    private static final int MIN_SLICES = 2;
    private static final int MAX_SLICES = 12;
    /** A face is treated as a "cap" (perpendicular to the bend axis, never subdivided — see class doc)
     *  when its normal is within this cosine of parallel/antiparallel to the bend direction. */
    private static final float CAP_DOT_THRESHOLD = 0.9f;

    /** One triangle-free quad of the subdivided mesh, in REST (unbent) space — precomputed once per
     *  cuboid and re-bent every frame the emote's bend channel changes. */
    private record SubQuad(Vector3f[] restPos, float[] u, float[] v, Vector3f normal) {}

    /** A plane in 3D space (normal + signed distance), used the same way the reference uses it — to
     *  tell how far a vertex sits from the cuboid's two bend-axis end-faces and from the bend's own
     *  hinge plane. Port of {@code IBendable.Plane}. */
    private record Plane(Vector3f normal, float normDistance) {
        static Plane through(Vector3f normal, Vector3f point) {
            Vector3f n = new Vector3f(normal).normalize();
            return new Plane(n, -n.dot(point));
        }
        float distanceTo(Vector3f pos) {
            return normal.dot(pos) + normDistance;
        }
    }

    /** Everything about a cuboid's bend geometry that depends only on its REST shape + bend direction
     *  — computed once (lazily, on first real bend) and reused every frame; only {@link #bendAxis}/
     *  {@link #bendValue} change per frame. */
    public static final class Precomputed {
        private final List<SubQuad> subQuads;
        private final Vector3f directionVec;
        /** {@code direction.getRotationQuaternion()} as a 3x3 basis — reinterprets the (cos,0,sin)
         *  bend-axis vector in this cuboid's own bend-direction frame, computed once (Port of the
         *  reference's per-call {@code Direction#getRotation()} use, hoisted out of the per-frame
         *  hot path since it never changes for a given cuboid). */
        private final Matrix3f axisBasis;
        private final Vector3f pivot;
        private final Plane basePlane;
        private final Plane otherPlane;
        private final float halfSize;
        private final boolean invertBend;

        private Precomputed(List<SubQuad> subQuads, Vector3f directionVec, Matrix3f axisBasis, Vector3f pivot,
                            Plane basePlane, Plane otherPlane, float halfSize, boolean invertBend) {
            this.subQuads = subQuads;
            this.directionVec = directionVec;
            this.axisBasis = axisBasis;
            this.pivot = pivot;
            this.basePlane = basePlane;
            this.otherPlane = otherPlane;
            this.halfSize = halfSize;
            this.invertBend = invertBend;
        }

        /** Package-private, test-only: the bend pivot (D105 regression check — must be the cuboid's
         *  true geometric center, not an artifact of which vertex a scan happened to visit first). */
        Vector3f pivotForTesting() {
            return new Vector3f(pivot);
        }
    }

    /**
     * One-time setup for a cuboid: subdivides vanilla's own {@code originalSides} into a finer mesh
     * along {@code direction}, and derives the fixed geometry (pivot, end-planes, half-size, bend-sign)
     * every subsequent {@link #apply} call needs. Never mutates {@code originalSides} — safe to call
     * with the exact array a vanilla {@code ModelPart.Cuboid} already built.
     */
    public static Precomputed precompute(ModelPart.Polygon[] originalSides, Direction direction) {
        Vector3f directionVec = new Vector3f(direction.step());

        List<SubQuad> subQuads = new ArrayList<>();
        float tMin = Float.MAX_VALUE, tMax = -Float.MAX_VALUE;
        Vector3f minCorner = null, maxCorner = null;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (ModelPart.Polygon quad : originalSides) {
            buildSubQuads(quad, directionVec, subQuads);
            for (ModelPart.Vertex v : ModelPartCompat.vertices(quad)) {
                Vector3f pos = new Vector3f(
                        ModelPartCompat.x(v), ModelPartCompat.y(v), ModelPartCompat.z(v));
                float t = directionVec.dot(pos);
                if (t < tMin) { tMin = t; minCorner = pos; }
                if (t > tMax) { tMax = t; maxCorner = pos; }
                minX = Math.min(minX, pos.x()); maxX = Math.max(maxX, pos.x());
                minY = Math.min(minY, pos.y()); maxY = Math.max(maxY, pos.y());
                minZ = Math.min(minZ, pos.z()); maxZ = Math.max(maxZ, pos.z());
            }
        }
        if (minCorner == null) {
            // No quads at all (shouldn't happen for a real cuboid) — degenerate no-op geometry.
            minCorner = new Vector3f();
            maxCorner = new Vector3f();
            minX = maxX = minY = maxY = minZ = maxZ = 0f;
        }

        // bl / invertBend: same condition the reference uses (IBendable#isBendInverted) — verified by
        // hand against the two directions this project actually registers (DOWN for torso, UP for
        // arms/legs, matching KosmX/minecraftPlayerAnimator's BipedEntityModelMixin): for UP, the FIXED
        // "base" end ends up the top/near-pivot side (shoulder/hip) and the folding "other" end is the
        // bottom (hand/foot) — anatomically correct; for DOWN (torso), base = top (neck, near-pivot),
        // other = bottom (waist/hip, where a sit/crouch pose actually needs to fold). Both confirmed by
        // hand-tracing the plane assignment below against real player-model geometry before trusting it.
        boolean bl = direction == Direction.UP || direction == Direction.SOUTH || direction == Direction.EAST;
        Plane planeAtMax = Plane.through(directionVec, maxCorner);
        Plane planeAtMin = Plane.through(directionVec, minCorner);
        Plane basePlane = bl ? planeAtMax : planeAtMin;
        Plane otherPlane = bl ? planeAtMin : planeAtMax;

        // Bend pivot is the cuboid's true geometric center (bendy-lib's own Builder.build(): bendX =
        // (sizeX + 2x)/2, i.e. per-axis independent min/max) — NOT (minCorner+maxCorner)/2 from a
        // single pair of extreme VERTICES along the bend axis, which only equals the center when those
        // two vertices happen to be diagonally opposite on the OTHER two axes too (not guaranteed by a
        // plain scan — a real bug found analyzing a user report of bent limbs looking detached from the
        // rest of the model, D105).
        Vector3f pivot = new Vector3f((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f);
        float fullSize = tMax - tMin;
        Matrix3f axisBasis = new Matrix3f().set(direction.getRotation());

        return new Precomputed(subQuads, directionVec, axisBasis, pivot, basePlane, otherPlane,
                Math.max(0.001f, fullSize / 2f), bl);
    }

    /** Subdivides one vanilla quad into {@link SubQuad}s along whichever of its two parametric edges
     *  (v0→v1 or v0→v3) actually runs along {@code directionVec} — a "cap" face (both edges roughly
     *  perpendicular to the bend axis) gets exactly one, unsplit sub-quad; a "side" face gets sliced. */
    private static void buildSubQuads(ModelPart.Polygon quad, Vector3f directionVec, List<SubQuad> out) {
        ModelPart.Vertex[] v = ModelPartCompat.vertices(quad);
        Vector3f p0 = vertexPos(v[0]);
        Vector3f p1 = vertexPos(v[1]);
        Vector3f p2 = vertexPos(v[2]);
        Vector3f p3 = vertexPos(v[3]);

        float sSpan = Math.abs(directionVec.dot(p1) - directionVec.dot(p0));
        float rSpan = Math.abs(directionVec.dot(p3) - directionVec.dot(p0));

        int sliceS = 1, sliceR = 1;
        Vector3f normal = new Vector3f(ModelPartCompat.normal(quad));
        boolean isCap = Math.abs(normal.dot(directionVec)) >= CAP_DOT_THRESHOLD;
        if (!isCap) {
            float span = Math.max(sSpan, rSpan);
            int slices = Math.max(MIN_SLICES, Math.min(MAX_SLICES, Math.round(span)));
            if (sSpan >= rSpan) sliceS = slices; else sliceR = slices;
        }

        float[] u = {ModelPartCompat.u(v[0]), ModelPartCompat.u(v[1]),
                     ModelPartCompat.u(v[2]), ModelPartCompat.u(v[3])};
        float[] vv = {ModelPartCompat.v(v[0]), ModelPartCompat.v(v[1]),
                      ModelPartCompat.v(v[2]), ModelPartCompat.v(v[3])};

        for (int i = 0; i < sliceS; i++) {
            float s0 = (float) i / sliceS, s1 = (float) (i + 1) / sliceS;
            for (int j = 0; j < sliceR; j++) {
                float r0 = (float) j / sliceR, r1 = (float) (j + 1) / sliceR;
                Vector3f[] pos = {
                        bilinear(p0, p1, p2, p3, s1, r0),
                        bilinear(p0, p1, p2, p3, s0, r0),
                        bilinear(p0, p1, p2, p3, s0, r1),
                        bilinear(p0, p1, p2, p3, s1, r1),
                };
                float[] uu = {
                        bilinear(u[0], u[1], u[2], u[3], s1, r0),
                        bilinear(u[0], u[1], u[2], u[3], s0, r0),
                        bilinear(u[0], u[1], u[2], u[3], s0, r1),
                        bilinear(u[0], u[1], u[2], u[3], s1, r1),
                };
                float[] vvv = {
                        bilinear(vv[0], vv[1], vv[2], vv[3], s1, r0),
                        bilinear(vv[0], vv[1], vv[2], vv[3], s0, r0),
                        bilinear(vv[0], vv[1], vv[2], vv[3], s0, r1),
                        bilinear(vv[0], vv[1], vv[2], vv[3], s1, r1),
                };
                out.add(new SubQuad(pos, uu, vvv, normal));
            }
        }
    }

    /** A vertex's rest position as a fresh vector, read through the version shim — {@code Vertex}
     *  stopped holding a {@code Vector3f pos} and became three floats in 1.21.9. */
    private static Vector3f vertexPos(ModelPart.Vertex vertex) {
        return new Vector3f(ModelPartCompat.x(vertex), ModelPartCompat.y(vertex),
                ModelPartCompat.z(vertex));
    }

    /** Standard 4-corner bilinear interpolation, corners in {@code v0,v1,v2,v3} quad-loop order
     *  (v0=(0,0), v1=(1,0), v2=(1,1), v3=(0,1) in (s,r) parameter space). */
    private static Vector3f bilinear(Vector3f v0, Vector3f v1, Vector3f v2, Vector3f v3, float s, float r) {
        Vector3f top = new Vector3f(v0).lerp(v1, s);
        Vector3f bottom = new Vector3f(v3).lerp(v2, s);
        return top.lerp(bottom, r);
    }

    private static float bilinear(float v0, float v1, float v2, float v3, float s, float r) {
        float top = v0 + (v1 - v0) * s;
        float bottom = v3 + (v2 - v3) * s;
        return top + (bottom - top) * r;
    }

    /**
     * Re-bends {@code pre}'s subdivided mesh for this frame's {@code bendAxis}/{@code bendValue}
     * (radians) and returns a fresh {@code ModelPart.Quad[]} ready to swap into a cuboid's
     * {@code sides} field. Faithful port of {@code IBendable#applyBend}'s per-vertex loop: a vertex
     * closer to {@code basePlane} gets the shear correction PLUS the full rotation around the bend
     * pivot; a vertex closer to {@code otherPlane} only gets the shear (this is the reference's own
     * behavior, re-verified directly against {@code IBendable.java} — an earlier version of this
     * comment described it backwards, which the D105 investigation caught).
     */
    public static ModelPart.Polygon[] apply(Precomputed pre, float bendAxis, float bendValue) {
        Vector3f axis = new Vector3f((float) Math.cos(bendAxis), 0, (float) Math.sin(bendAxis));
        // pre.axisBasis = direction.getRotationQuaternion() as a 3x3 basis, precomputed once in
        // precompute() — reinterprets the (cos,0,sin) axis vector built above in this cuboid's own
        // bend-direction frame, the same role the reference's per-call Direction#getRotation() plays.
        axis.mul(pre.axisBasis);

        Matrix4f transform = new Matrix4f()
                .translate(pre.pivot.x(), pre.pivot.y(), pre.pivot.z())
                .rotate(bendValue, axis)
                .translate(-pre.pivot.x(), -pre.pivot.y(), -pre.pivot.z());

        Vector3f bendPlaneNormal = new Vector3f(pre.directionVec).cross(axis);
        Plane bendPlane = Plane.through(bendPlaneNormal, pre.pivot);

        ModelPart.Polygon[] result = new ModelPart.Polygon[pre.subQuads.size()];
        for (int qi = 0; qi < pre.subQuads.size(); qi++) {
            SubQuad sq = pre.subQuads.get(qi);
            ModelPart.Vertex[] verts = new ModelPart.Vertex[4];
            for (int i = 0; i < 4; i++) {
                Vector3f bent = bendVertex(sq.restPos()[i], pre, bendPlane, transform, bendValue);
                verts[i] = new ModelPart.Vertex(bent.x(), bent.y(), bent.z(), sq.u()[i], sq.v()[i]);
            }
            result[qi] = ModelPartCompat.polygon(verts, sq.normal());
        }
        return result;
    }

    private static Vector3f bendVertex(Vector3f restPos, Precomputed pre, Plane bendPlane,
                                       Matrix4f transform, float bendValue) {
        Vector3f pos = new Vector3f(restPos);
        float distFromBend = pre.invertBend ? -bendPlane.distanceTo(pos) : bendPlane.distanceTo(pos);
        float distFromBase = pre.basePlane.distanceTo(pos);
        float distFromOther = pre.otherPlane.distanceTo(pos);
        double s = Math.tan(bendValue / 2.0) * distFromBend;

        Vector3f shear = new Vector3f(pre.directionVec);
        if (Math.abs(distFromBase) < Math.abs(distFromOther)) {
            shear.mul((float) (-distFromBase / pre.halfSize * s));
            pos.add(shear);
            Vector4f v4 = new Vector4f(pos, 1f);
            transform.transform(v4);
            return new Vector3f(v4.x(), v4.y(), v4.z());
        } else {
            shear.mul((float) (-distFromOther / pre.halfSize * s));
            pos.add(shear);
            return pos;
        }
    }
}
