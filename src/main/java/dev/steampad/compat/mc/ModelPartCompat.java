package dev.steampad.compat.mc;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3fc;

/**
 * Version shim for {@link ModelPart}'s vertex/quad internals, which the emote bend math reads
 * per-frame. Two <em>separate</em> boundaries here, both measured with {@code javap} against the real
 * remapped jars rather than assumed:
 *
 * <ul>
 *   <li><b>{@code Polygon.vertices()} / {@code normal()} and the 2-arg {@code Polygon} constructor —
 *       from 1.21.2.</b> Absent in 1.21.1, present in 1.21.3. In 1.21.1 the fields are
 *       {@code public final} instead, and the only constructor is the 9-arg one.</li>
 *   <li><b>{@code Vertex.x()/y()/z()/u()/v()} — from 1.21.9.</b> Absent in 1.21.8, present in
 *       1.21.10: {@code Vertex} stopped holding a {@code Vector3f pos} and became a record of three
 *       floats.</li>
 * </ul>
 *
 * <p><b>Scope of what is verified.</b> Only the two versions this project actually builds — 1.21.1 and
 * 1.21.10 — were validated end to end. Jars for 1.21.2, 1.21.6, 1.21.7 and 1.21.9 were not available
 * locally, so each boundary is stated as the tightest range the probe could prove. Worse, the
 * in-between versions are genuinely awkward: 1.21.5 and 1.21.8 have a {@code pos()} accessor but leave
 * {@code u}/{@code v} package-private with no accessor, so <em>neither</em> branch below compiles
 * there as written. Adding a variant in that range therefore needs its own branch (or an access
 * widener), and the compiler will say so immediately — it cannot fail silently.
 *
 * <p>Construction deliberately uses APIs common to both versions where possible: the 5-float
 * {@code Vertex} constructor is identical everywhere, so {@link ModelPart.Vertex} is still built
 * directly at the call site and only {@code Polygon} needed wrapping.
 */
public final class ModelPartCompat {

    private ModelPartCompat() {}

    /**
     * A baked rest pose's offsets. {@code PartPose} went the same way as {@code Vertex}: public final
     * floats before 1.21.9, a record with accessors after. Rotation fields ({@code xRot} etc.) are not
     * shimmed because nothing reads them — add them the same way if that changes.
     */
    public static float poseX(net.minecraft.client.model.geom.PartPose pose) {
        //? if >=1.21.9 {
        return pose.x();
        //?} else {
        /*return pose.x;*/
        //?}
    }

    public static float poseY(net.minecraft.client.model.geom.PartPose pose) {
        //? if >=1.21.9 {
        return pose.y();
        //?} else {
        /*return pose.y;*/
        //?}
    }

    public static float poseZ(net.minecraft.client.model.geom.PartPose pose) {
        //? if >=1.21.9 {
        return pose.z();
        //?} else {
        /*return pose.z;*/
        //?}
    }

    /** The quad's four vertices, in quad-loop order. */
    public static ModelPart.Vertex[] vertices(ModelPart.Polygon quad) {
        //? if >=1.21.2 {
        return quad.vertices();
        //?} else {
        /*return quad.vertices;*/
        //?}
    }

    /**
     * The quad's face normal. Returned as the read-only {@code Vector3fc} view, which works on both
     * sides: 1.21.10 already exposes it as {@code Vector3fc}, and 1.21.1's mutable {@code Vector3f}
     * implements that interface. Callers that need to modify it copy into their own vector.
     */
    public static Vector3fc normal(ModelPart.Polygon quad) {
        //? if >=1.21.2 {
        return quad.normal();
        //?} else {
        /*return quad.normal;*/
        //?}
    }

    public static float x(ModelPart.Vertex vertex) {
        //? if >=1.21.9 {
        return vertex.x();
        //?} else {
        /*return vertex.pos.x();*/
        //?}
    }

    public static float y(ModelPart.Vertex vertex) {
        //? if >=1.21.9 {
        return vertex.y();
        //?} else {
        /*return vertex.pos.y();*/
        //?}
    }

    public static float z(ModelPart.Vertex vertex) {
        //? if >=1.21.9 {
        return vertex.z();
        //?} else {
        /*return vertex.pos.z();*/
        //?}
    }

    public static float u(ModelPart.Vertex vertex) {
        //? if >=1.21.9 {
        return vertex.u();
        //?} else {
        /*return vertex.u;*/
        //?}
    }

    public static float v(ModelPart.Vertex vertex) {
        //? if >=1.21.9 {
        return vertex.v();
        //?} else {
        /*return vertex.v;*/
        //?}
    }

    /**
     * Builds a quad from vertices that <em>already carry their final UVs</em>, plus an explicit normal.
     *
     * <p>Straightforward from 1.21.2 on — that is exactly what the 2-arg constructor does. On 1.21.1
     * the only constructor available is the 9-arg one, which derives UVs from a texture-atlas rect and
     * <b>overwrites every vertex's u/v via {@code remap()}</b>, and derives the normal from a
     * {@link net.minecraft.core.Direction}. That is destructive here: the bend math computes UVs by
     * bilinear interpolation of vanilla's own already-correct quads precisely so it never has to know
     * the atlas layout (see {@code CuboidBender}'s class doc), so letting the constructor recompute
     * them would produce visibly stretched textures.
     *
     * <p>So the 1.21.1 branch calls it with throwaway texture parameters and then puts the real data
     * back. Two details make that safe, both confirmed in the 1.21.1 bytecode rather than assumed:
     * the constructor stores the <em>same</em> array instance it was handed (so overwriting its
     * elements afterwards is enough), and it assigns {@code this.normal = direction.step()}, which
     * allocates a fresh {@code Vector3f} — vanilla itself mutates that vector in place for mirrored
     * quads, which is what proves it is not a shared instance that could be corrupted.
     */
    public static ModelPart.Polygon polygon(ModelPart.Vertex[] vertices, Vector3fc normal) {
        //? if >=1.21.2 {
        return new ModelPart.Polygon(vertices, normal);
        //?} else {
        /*ModelPart.Vertex[] scratch = vertices.clone();
        ModelPart.Polygon quad = new ModelPart.Polygon(
                scratch, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, false, net.minecraft.core.Direction.UP);
        // quad.vertices IS scratch — undo the constructor's remap by restoring the originals.
        System.arraycopy(vertices, 0, quad.vertices, 0, vertices.length);
        quad.normal.set(normal.x(), normal.y(), normal.z());
        return quad;
        *///?}
    }
}
