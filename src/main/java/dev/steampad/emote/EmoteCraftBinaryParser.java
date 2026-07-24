package dev.steampad.emote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.steampad.util.LogUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for the {@code .emotecraft} binary container — implementing the REAL format, verified
 * empirically against all 21 of the user's community files (21/21 parse; see DECISIONS D099).
 *
 * <p><b>Provenance (D099, supersedes the D080 clean-room note that used to live here):</b> the
 * container layout was confirmed by reading the actual serializers at the owner's explicit
 * direction — KosmX/emotes' {@code EmotePacket}/{@code EmoteDataPacket}/{@code EmoteHeaderPacket}/
 * {@code EmoteIconPacket} (GPL-3.0, read for byte-layout facts ONLY, no code copied) and
 * KosmX/minecraftPlayerAnimator's {@code AnimationBinary} (<b>MIT</b>, portable with attribution).
 * Every field below was then re-verified byte-for-byte against the 21 real files with a standalone
 * decoder before this Java port was written.
 *
 * <p><b>Container:</b> {@code int netVersion, u8 purpose, u8 subPacketCount}, then per sub-packet
 * {@code u8 id, u8 version, int size, payload[size]}:
 * <ul>
 *   <li>{@code 0x00} data — {@code int tick} + an AnimationBinary of that sub-packet's version</li>
 *   <li>{@code 0x11} header — length-prefixed UTF-8 strings name/description/author (v2 adds
 *       folderpath and badges, not needed here). Strings may be JSON text-component literals
 *       ({@code "Ascend"} or {@code {"translate":…,"fallback":"…"}}) — unwrapped.</li>
 *   <li>{@code 0x12} icon — {@code int size} + a complete PNG</li>
 * </ul>
 *
 * <p><b>AnimationBinary:</b> header {@code int begin/end/stop, u8 isInfinite, int returnToTick,
 * u8 isEasingBefore, u8 nsfw, i8 keyframeSize} (9 = tick+value+ease, 13 adds easingArg in v4);
 * then the parts; then {@code 2×long} UUID. v1 = fixed part order head(6 axes)/body/rightArm/
 * leftArm/rightLeg/leftLeg (8 axes: +bendDir+bend), axis = {@code int count} with −1 = disabled.
 * v2+ = {@code int partCount} + named parts where KNOWN names use pre-built bendable flags (head/
 * leftItem/rightItem: 6 axes; body/torso/arms/legs: 8) and unknown names default bendable; axis =
 * {@code u8 enabled + int count} — with the empirically-required tolerance that older v2 writers
 * (the MineEmotes-era files) still write −1 for a disabled axis. Scale axes exist only in v3+.
 * Keyframe = {@code int tick, float value, i8 easeId} (+{@code float easingArg}, NaN = none, v4+),
 * advanced by {@code keyframeSize} so unknown extra bytes never desync the cursor.
 *
 * <p>Position values are ABSOLUTE PIVOTS in vanilla model space and rotations are radians — no
 * conversion here (see {@link EmoteData}'s class doc for why that matters). {@code body} and
 * {@code torso} both map to {@link EmoteData.Part#TORSO} — normally {@code body} carries
 * position/rotation and {@code torso} carries only bend (dropped, out of render scope), but ONE of
 * the user's 21 real files ("Friendship Round Dance", a MineEmotes export) breaks that pattern:
 * {@code torso} ALSO declares real x/y/z/pitch/yaw/roll keyframes there, alongside a partial
 * {@code body} (x/z/yaw only) — verified by dumping per-source-part axis data with the standalone
 * Node verifier (D100). Merging both unconditionally would interleave two distinct keyframe tracks
 * onto the same channel (a real, confirmed cause of "rotation looks erratic/locked" for that file).
 * {@code parse()} processes {@code body} before {@code torso} and skips any axis {@code torso} tries
 * to add that {@code body} already claimed — body wins on collision (it's the reliable carrier in
 * 20/21 files), with a log line so a future report of this exact file has direct evidence. Bend/
 * scale/item channels are parsed to keep the cursor aligned, then dropped either way.
 */
public final class EmoteCraftBinaryParser {

    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final int SUB_DATA = 0x00, SUB_HEADER = 0x11, SUB_ICON = 0x12;
    private static final int MAX_KEYFRAMES_PER_AXIS = 100_000;
    private static final int MAX_PARTS = 64;

    private EmoteCraftBinaryParser() {}

    // ---- Public API (same surface EmoteLibrary already uses) ----

    /** Extracts the embedded icon PNG from the 0x12 sub-packet; falls back to a raw PNG-signature
     *  scan if the container itself is unreadable. Null if the file has no icon. */
    public static byte[] extractIcon(byte[] bytes) {
        try {
            ByteBuffer buf = wrap(bytes);
            buf.getInt();               // netVersion
            buf.get();                  // purpose
            int count = buf.get() & 0xFF;
            for (int i = 0; i < count; i++) {
                int id = buf.get() & 0xFF;
                buf.get();              // sub-packet version
                int size = buf.getInt();
                int payloadStart = buf.position();
                if (size < 0 || payloadStart + size > bytes.length) break;
                if (id == SUB_ICON) {
                    int iconSize = buf.getInt();
                    if (iconSize > 0 && payloadStart + 4 + iconSize <= bytes.length) {
                        return Arrays.copyOfRange(bytes, payloadStart + 4, payloadStart + 4 + iconSize);
                    }
                }
                buf.position(payloadStart + size);
            }
        } catch (Throwable ignored) {
            // fall through to the signature scan
        }
        int at = indexOf(bytes, PNG_SIGNATURE);
        return at < 0 ? null : Arrays.copyOfRange(bytes, at, bytes.length);
    }

    /** Parses the animation. Returns null (never throws) if the file doesn't decode — the caller
     *  treats that as "unsupported", and the icon may still be extractable independently. */
    public static EmoteData parse(String id, byte[] bytes) {
        try {
            ByteBuffer buf = wrap(bytes);
            buf.getInt();               // netVersion (informational only)
            buf.get();                  // purpose
            int count = buf.get() & 0xFF;

            Anim anim = null;
            String name = null, description = null, author = null;

            for (int i = 0; i < count; i++) {
                int subId = buf.get() & 0xFF;
                int subVersion = buf.get() & 0xFF;
                int size = buf.getInt();
                int payloadStart = buf.position();
                if (size < 0 || payloadStart + size > bytes.length) {
                    LogUtil.warn("[SteamPad] .emotecraft '{}': sub-packet 0x{} overruns the file — stopping.",
                            id, Integer.toHexString(subId));
                    break;
                }
                if (subId == SUB_DATA) {
                    buf.getInt();       // extraTick (network start offset — irrelevant for a file)
                    anim = readAnimationBinary(buf, subVersion, payloadStart + size);
                } else if (subId == SUB_HEADER) {
                    name = unwrapText(readString(buf));
                    description = unwrapText(readString(buf));
                    author = unwrapText(readString(buf));
                }
                buf.position(payloadStart + size);
            }
            if (anim == null) {
                LogUtil.warn("[SteamPad] .emotecraft '{}' has no decodable animation data packet.", id);
                return null;
            }

            EmoteData data = new EmoteData(id,
                    name == null || name.isEmpty() ? id : name,
                    author == null ? "" : author,
                    description == null ? "" : description,
                    anim.isInfinite, anim.beginTick, anim.endTick, anim.stopTick, anim.returnToTick);
            data.easingBefore = anim.isEasingBefore;

            int applied = 0;
            // "body" before "torso": normally a no-op (torso only ever carries bend, which isn't
            // read here) — but see the class doc's collision note. Processing body first means it
            // always wins the rare file where torso ALSO carries real transform data.
            List<String> sourceNames = new ArrayList<>(anim.parts.keySet());
            sourceNames.sort(Comparator.comparingInt(n -> "torso".equals(n) ? 1 : 0));
            for (String sourceName : sourceNames) {
                EmoteData.Part part = partFor(sourceName);
                if (part == null) continue;   // leftItem/rightItem/cape/unknown — out of render scope
                AxisData[] axes = anim.parts.get(sourceName);
                for (int a = 0; a < BASE_AXES.length; a++) {
                    AxisData ax = axes[a];
                    if (ax == null || !ax.enabled) continue;
                    if (data.hasChannel(part, BASE_AXES[a])) {
                        // A DIFFERENT source part already claimed this axis on the shared TORSO part
                        // (body+torso merge) — a genuine collision, not the normal "disabled empty
                        // axis" case (see class doc: "Friendship Round Dance" has REAL transform data
                        // on BOTH "body" and "torso"). Interleaving both would corrupt the channel
                        // (two authors' keyframe tracks on one axis) — first writer (by the body-first
                        // order above) wins; log once so a future report has direct evidence.
                        LogUtil.warn("[SteamPad] .emotecraft '{}': part '{}' also declares {}.{} — "
                                        + "already claimed by an earlier part this axis merges with; "
                                        + "its {} keyframe(s) were skipped to avoid corrupting the channel.",
                                id, sourceName, part, BASE_AXES[a], ax.frames.size());
                        continue;
                    }
                    // Merge-friendly: only ever turn an axis ON (body+torso share TORSO — torso's
                    // disabled x/y/z must not clobber body's enabled ones).
                    data.setAxisEnabled(part, BASE_AXES[a], true);
                    for (AxisKf kf : ax.frames) {
                        data.addKeyframe(part, BASE_AXES[a], kf.tick, kf.value,
                                Easing.fromId(kf.ease), kf.easingArg);
                        applied++;
                    }
                }
            }
            LogUtil.info("[SteamPad] Parsed .emotecraft '{}' (binary v{}): '{}' by '{}', ticks {}..{}/{}, "
                            + "loop={} return={}, {} keyframes across {} parts.",
                    id, anim.version, data.name, data.author, (int) data.beginTick, (int) data.endTick,
                    (int) data.stopTick, data.loop, (int) data.returnTick, applied, anim.parts.size());
            return data;
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] Could not parse .emotecraft binary '{}': {}", id, String.valueOf(t));
            return null;
        }
    }

    // ---- AnimationBinary ----

    private record AxisKf(int tick, float value, byte ease, Float easingArg) {}

    private static final class AxisData {
        boolean enabled;
        final List<AxisKf> frames = new ArrayList<>();
    }

    private static final class Anim {
        int version;
        int beginTick, endTick, stopTick, returnToTick;
        boolean isInfinite, isEasingBefore;
        int keyframeSize;
        final Map<String, AxisData[]> parts = new LinkedHashMap<>();
    }

    private static final EmoteData.Axis[] BASE_AXES = {
            EmoteData.Axis.X, EmoteData.Axis.Y, EmoteData.Axis.Z,
            EmoteData.Axis.PITCH, EmoteData.Axis.YAW, EmoteData.Axis.ROLL,
    };

    /** v1's fixed part order + bendable flags, from playerAnimator's AnimationBuilder (MIT):
     *  head is NOT bendable (6 axes); body/arms/legs are (8 axes). */
    private static final String[][] V1_PARTS = {
            {"head", ""}, {"body", "bend"}, {"rightArm", "bend"},
            {"leftArm", "bend"}, {"rightLeg", "bend"}, {"leftLeg", "bend"},
    };

    /** v2+'s pre-built part registry: known names carry fixed bendable flags; unknown names default
     *  to bendable (AnimationBuilder.getOrCreatePart's behavior). */
    private static boolean isBendable(String partName) {
        return switch (partName) {
            case "head", "leftItem", "rightItem" -> false;
            default -> true;
        };
    }

    private static Anim readAnimationBinary(ByteBuffer buf, int version, int end) {
        Anim anim = new Anim();
        anim.version = version;
        anim.beginTick = buf.getInt();
        anim.endTick = buf.getInt();
        anim.stopTick = buf.getInt();
        anim.isInfinite = buf.get() != 0;
        anim.returnToTick = buf.getInt();
        anim.isEasingBefore = buf.get() != 0;
        buf.get();                                  // nsfw flag — unused here
        anim.keyframeSize = buf.get();
        if (anim.keyframeSize < 9) throw new IllegalStateException("keyframeSize " + anim.keyframeSize);

        if (version >= 2) {
            int partCount = buf.getInt();
            if (partCount < 0 || partCount > MAX_PARTS) {
                throw new IllegalStateException("partCount " + partCount);
            }
            for (int i = 0; i < partCount; i++) {
                String partName = readString(buf);
                anim.parts.put(partName, readPart(buf, anim, isBendable(partName), true, end));
            }
        } else {
            for (String[] p : V1_PARTS) {
                anim.parts.put(p[0], readPart(buf, anim, !p[1].isEmpty(), false, end));
            }
        }
        buf.getLong();                              // UUID hi
        buf.getLong();                              // UUID lo
        if (buf.position() > end) throw new IllegalStateException("overran data sub-packet");
        return anim;
    }

    /** One part: the 6 base axes (kept), then bendDir+bend if bendable and scaleX/Y/Z if v3+ —
     *  parsed only to advance the cursor correctly (out of render scope). */
    private static AxisData[] readPart(ByteBuffer buf, Anim anim, boolean bendable, boolean scalable, int end) {
        AxisData[] axes = new AxisData[BASE_AXES.length];
        for (int a = 0; a < BASE_AXES.length; a++) axes[a] = readAxis(buf, anim, end);
        if (bendable) {
            readAxis(buf, anim, end);               // bendDir
            readAxis(buf, anim, end);               // bend
        }
        if (scalable && anim.version >= 3) {
            readAxis(buf, anim, end);               // scaleX
            readAxis(buf, anim, end);               // scaleY
            readAxis(buf, anim, end);               // scaleZ
        }
        return axes;
    }

    private static AxisData readAxis(ByteBuffer buf, Anim anim, int end) {
        AxisData axis = new AxisData();
        int count;
        if (anim.version >= 2) {
            axis.enabled = buf.get() != 0;
            count = buf.getInt();
            // Empirically-required tolerance (13 of the 21 real files): older v2 writers keep v1's
            // "-1 = disabled" convention alongside the v2 enabled byte.
            if (count == -1) {
                count = 0;
                axis.enabled = false;
            }
        } else {
            count = buf.getInt();
            axis.enabled = count >= 0;
            if (count < 0) count = 0;
        }
        if (count < 0 || count > MAX_KEYFRAMES_PER_AXIS
                || buf.position() + (long) count * anim.keyframeSize > end) {
            throw new IllegalStateException("axis keyframe count " + count + " overruns sub-packet");
        }
        for (int i = 0; i < count; i++) {
            int base = buf.position();
            int tick = buf.getInt();
            float value = buf.getFloat();
            byte ease = buf.get();
            Float easingArg = null;
            if (anim.version >= 4 && anim.keyframeSize >= 13) {
                float arg = buf.getFloat();
                if (!Float.isNaN(arg)) easingArg = arg;
            }
            axis.frames.add(new AxisKf(tick, value, ease, easingArg));
            buf.position(base + anim.keyframeSize);
        }
        return axis;
    }

    // ---- Small helpers ----

    private static EmoteData.Part partFor(String name) {
        return switch (name) {
            case "head" -> EmoteData.Part.HEAD;
            case "body", "torso" -> EmoteData.Part.TORSO;   // merged — see class doc
            case "rightArm" -> EmoteData.Part.RIGHT_ARM;
            case "leftArm" -> EmoteData.Part.LEFT_ARM;
            case "rightLeg" -> EmoteData.Part.RIGHT_LEG;
            case "leftLeg" -> EmoteData.Part.LEFT_LEG;
            default -> null;
        };
    }

    private static ByteBuffer wrap(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
    }

    /** Length-prefixed UTF-8 string ({@code int len + bytes}). */
    private static String readString(ByteBuffer buf) {
        int len = buf.getInt();
        if (len < 0 || len > 100_000 || buf.position() + len > buf.limit()) {
            throw new IllegalStateException("string length " + len);
        }
        byte[] raw = new byte[len];
        buf.get(raw);
        return new String(raw, StandardCharsets.UTF_8);
    }

    /** Header strings may be JSON text-component literals ({@code "Ascend"} or
     *  {@code {"translate":…,"fallback":"The Honored One"}}) — unwrap to plain display text. */
    private static String unwrapText(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        try {
            if (s.startsWith("{")) {
                JsonObject o = JsonParser.parseString(s).getAsJsonObject();
                if (o.has("fallback")) return o.get("fallback").getAsString();
                if (o.has("text")) return o.get("text").getAsString();
                if (o.has("translate")) return o.get("translate").getAsString();
            } else if (s.startsWith("\"")) {
                return JsonParser.parseString(s).getAsString();
            }
        } catch (Throwable ignored) {
            // not JSON — use verbatim
        }
        return s;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
