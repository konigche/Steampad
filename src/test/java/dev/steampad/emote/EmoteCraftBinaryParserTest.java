package dev.steampad.emote;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the Java port of the verified {@code .emotecraft} container spec (D099 — read from the
 * real serializers, then byte-verified against all 21 of the user's community files with a
 * standalone decoder before this port was written). These synthetic containers exercise the exact
 * layout rules that made or broke those real files: the v2 named-part registry with per-name
 * bendable flags, the "-1 still means disabled" older-writer tolerance, keyframeSize striding, the
 * body+torso merge, and the 0x12 icon sub-packet.
 */
class EmoteCraftBinaryParserTest {

    // ---- Synthetic container builder (mirrors the wire format, big-endian throughout) ----

    private static byte[] container(byte[]... subPackets) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeInt(8);                     // netVersion
        out.writeByte(0);                    // purpose
        out.writeByte(subPackets.length);
        for (byte[] sub : subPackets) out.write(sub);
        return bos.toByteArray();
    }

    private static byte[] subPacket(int id, int version, byte[] payload) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeByte(id);
        out.writeByte(version);
        out.writeInt(payload.length);
        out.write(payload);
        return bos.toByteArray();
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(raw.length);
        out.write(raw);
    }

    /** One v2 axis: enabled flag + count + keyframes at 9-byte stride. */
    private static void axis(DataOutputStream out, boolean enabled, float[][] keyframes) throws IOException {
        out.writeByte(enabled ? 1 : 0);
        out.writeInt(keyframes.length);
        for (float[] kf : keyframes) {
            out.writeInt((int) kf[0]);       // tick
            out.writeFloat(kf[1]);           // value
            out.writeByte((int) kf[2]);      // ease id
        }
    }

    /** The older-v2-writer form the user's MineEmotes files use for a DISABLED axis: enabled byte
     *  present but count = -1 (v1's convention leaking through). */
    private static void axisMinusOne(DataOutputStream out) throws IOException {
        out.writeByte(1);
        out.writeInt(-1);
    }

    private static byte[] dataPacketV2() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeInt(0);                     // extraTick
        out.writeInt(0);                     // beginTick
        out.writeInt(40);                    // endTick
        out.writeInt(43);                    // stopTick
        out.writeByte(1);                    // isInfinite
        out.writeInt(5);                     // returnToTick
        out.writeByte(0);                    // isEasingBefore
        out.writeByte(0);                    // nsfw
        out.writeByte(9);                    // keyframeSize
        out.writeInt(2);                     // partCount
        // "rightArm" — bendable in the known-name registry → 8 axes (x y z pitch yaw roll bendDir bend)
        writeString(out, "rightArm");
        axis(out, true, new float[][]{{0, -5f, 0}});                    // x: one absolute-pivot kf
        axisMinusOne(out);                                              // y: -1 tolerance → disabled
        axis(out, false, new float[0][]);                               // z: disabled
        axis(out, true, new float[][]{{0, 0f, 0}, {20, 1.5f, 8}});      // pitch: two kfs (INOUTSINE)
        axis(out, false, new float[0][]);                               // yaw
        axis(out, false, new float[0][]);                               // roll
        axis(out, false, new float[0][]);                               // bendDir (parsed, dropped)
        axis(out, false, new float[0][]);                               // bend (parsed, dropped)
        // "body" — the v2 position/rotation carrier, must merge into TORSO
        writeString(out, "body");
        axis(out, true, new float[][]{{10, 2f, 0}});                    // x
        axis(out, false, new float[0][]);                               // y
        axis(out, false, new float[0][]);                               // z
        axis(out, false, new float[0][]);                               // pitch
        axis(out, false, new float[0][]);                               // yaw
        axis(out, false, new float[0][]);                               // roll
        axis(out, false, new float[0][]);                               // bendDir
        axis(out, false, new float[0][]);                               // bend
        out.writeLong(0L);                   // UUID hi
        out.writeLong(0L);                   // UUID lo
        return subPacket(0x00, 2, bos.toByteArray());
    }

    /** Mirrors the real "Friendship Round Dance" anomaly: BOTH "torso" and "body" declare REAL
     *  (non-bend) x/pitch keyframes for the same tick range, with DIFFERENT values — the genuine
     *  collision case, not the normal "torso = bend only" pattern the other 20/21 files follow. */
    private static byte[] dataPacketV2WithTorsoCollision() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeInt(0);
        out.writeInt(0); out.writeInt(40); out.writeInt(43);
        out.writeByte(0); out.writeInt(0); out.writeByte(0); out.writeByte(0);
        out.writeByte(9);
        out.writeInt(2);                     // partCount: torso, body (torso written FIRST on wire —
                                              // the parser must still process body first logically)
        writeString(out, "torso");
        axis(out, true, new float[][]{{0, 99f, 0}});    // x: torso's OWN (should be dropped)
        axis(out, false, new float[0][]);
        axis(out, false, new float[0][]);
        axis(out, true, new float[][]{{0, 3.0f, 0}});   // pitch: torso's OWN (should be dropped)
        axis(out, false, new float[0][]);
        axis(out, false, new float[0][]);
        axis(out, false, new float[0][]);
        axis(out, false, new float[0][]);
        writeString(out, "body");
        axis(out, true, new float[][]{{0, 2f, 0}});     // x: body's value — must win
        axis(out, false, new float[0][]);
        axis(out, false, new float[0][]);
        axis(out, false, new float[0][]);               // pitch DISABLED on body — torso's is dropped
                                                          // anyway (collision on x already proves the rule)
        axis(out, false, new float[0][]);
        axis(out, false, new float[0][]);
        axis(out, false, new float[0][]);
        axis(out, false, new float[0][]);
        out.writeLong(0L); out.writeLong(0L);
        return subPacket(0x00, 2, bos.toByteArray());
    }

    private static byte[] headerPacket(String name, String author) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        writeString(out, name);
        writeString(out, "a description");
        writeString(out, author);
        return subPacket(0x11, 1, bos.toByteArray());
    }

    // ---- Tests ----

    @Test
    void parsesSyntheticV2Container() throws IOException {
        byte[] file = container(dataPacketV2(), headerPacket("\"Test Dance\"", "Someone"));
        EmoteData d = EmoteCraftBinaryParser.parse("test", file);
        assertNotNull(d, "spec-conformant v2 container must parse");

        assertEquals("Test Dance", d.name, "JSON-literal header strings are unwrapped");
        assertEquals("Someone", d.author);
        assertTrue(d.loop);
        assertEquals(0f, d.beginTick);
        assertEquals(40f, d.endTick);
        assertEquals(43f, d.stopTick);
        assertEquals(5f, d.returnTick);

        assertTrue(d.hasChannel(EmoteData.Part.RIGHT_ARM, EmoteData.Axis.X));
        assertEquals(1, d.keyframeCount(EmoteData.Part.RIGHT_ARM, EmoteData.Axis.X));
        assertFalse(d.hasChannel(EmoteData.Part.RIGHT_ARM, EmoteData.Axis.Y),
                "count = -1 means DISABLED even in v2 (older-writer tolerance, 13/21 real files)");
        assertEquals(2, d.keyframeCount(EmoteData.Part.RIGHT_ARM, EmoteData.Axis.PITCH));
        assertTrue(d.hasChannel(EmoteData.Part.BODY, EmoteData.Axis.X),
                "\"body\" is the WHOLE-MODEL channel (D110), not the torso bone");
        assertFalse(d.hasChannel(EmoteData.Part.TORSO, EmoteData.Axis.X),
                "\"body\" data must NOT leak into the torso bone — merging them was why sitting "
                        + "emotes floated in the air");

        // Absolute-pivot semantics survive the parse: the x keyframe SETS the pivot to -5 (which is
        // also rightArm's default — i.e. the arm stays attached to the shoulder, D099's core fact).
        assertEquals(-5f, d.sample(EmoteData.Part.RIGHT_ARM, EmoteData.Axis.X, 0f), 1e-4);
    }

    /**
     * Real-world regression, REINTERPRETED in D110: "Friendship Round Dance" (a MineEmotes export)
     * declares REAL x/pitch keyframes on BOTH "torso" and "body". Until v0.69.0 this was read as a
     * data COLLISION on one shared channel and "resolved" by dropping torso's track — but the two are
     * not duplicates at all: {@code body} is the whole-model transform and {@code torso} is the body
     * bone (verified in the reference's own {@code PlayerRendererMixin} vs {@code PlayerModelMixin}).
     * The file was always well-formed; the parser's model of it was wrong. Both tracks must now
     * survive intact, on their own separate channels, with nothing dropped.
     */
    @Test
    void bodyAndTorsoAreSeparateChannelsNotAMergeCollision() throws IOException {
        byte[] file = container(dataPacketV2WithTorsoCollision());
        EmoteData d = EmoteCraftBinaryParser.parse("collision", file);
        assertNotNull(d);

        // body: the whole-model channel keeps its own x untouched.
        assertTrue(d.hasChannel(EmoteData.Part.BODY, EmoteData.Axis.X));
        assertEquals(1, d.keyframeCount(EmoteData.Part.BODY, EmoteData.Axis.X));
        assertEquals(2f, d.sample(EmoteData.Part.BODY, EmoteData.Axis.X, 0f), 1e-4,
                "body's own whole-model x value");

        // torso: the bone channel keeps ITS own x — no longer discarded as a "collision".
        assertTrue(d.hasChannel(EmoteData.Part.TORSO, EmoteData.Axis.X),
                "torso's x is its own bone track and must no longer be dropped");
        assertEquals(1, d.keyframeCount(EmoteData.Part.TORSO, EmoteData.Axis.X));
        assertEquals(99f, d.sample(EmoteData.Part.TORSO, EmoteData.Axis.X, 0f), 1e-4,
                "torso keeps its own value; it never belonged to body's channel");

        // pitch is declared only on torso — unchanged by the split, still surfaces on the bone.
        assertTrue(d.hasChannel(EmoteData.Part.TORSO, EmoteData.Axis.PITCH));
        assertEquals(3.0f, d.sample(EmoteData.Part.TORSO, EmoteData.Axis.PITCH, 0f), 1e-4);
        assertFalse(d.hasChannel(EmoteData.Part.BODY, EmoteData.Axis.PITCH),
                "body left pitch disabled — it must stay disabled on the whole-model channel");
    }

    @Test
    void extractsIconFromIconSubPacket() throws IOException {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeInt(png.length);
        out.write(png);
        byte[] file = container(dataPacketV2(), subPacket(0x12, 1, bos.toByteArray()));

        byte[] icon = EmoteCraftBinaryParser.extractIcon(file);
        assertNotNull(icon);
        assertArrayEquals(png, icon, "icon is the 0x12 payload, byte-exact");
    }

    @Test
    void garbageReturnsNullInsteadOfThrowing() {
        assertNull(EmoteCraftBinaryParser.parse("junk", new byte[]{1, 2, 3}));
        assertNull(EmoteCraftBinaryParser.parse("empty", new byte[0]));
        byte[] truncated = new byte[64];     // plausible header, impossible body
        truncated[3] = 8; truncated[5] = 1; truncated[6] = 0x00; truncated[10] = 0x7F;
        assertNull(EmoteCraftBinaryParser.parse("truncated", truncated));
    }
}
