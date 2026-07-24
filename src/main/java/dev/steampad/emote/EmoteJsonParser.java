package dev.steampad.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.steampad.util.LogUtil;

import java.io.Reader;
import java.util.Locale;
import java.util.Map;

/**
 * Parser for the community player-emote JSON format (the one shared as loose {@code .json} files in
 * emote collections/Discords and popularized by Emotecraft). Originally clean-room from the public
 * docs (D080); as of v0.59.0 verified line-by-line against the REAL reference deserializer —
 * playerAnimator's {@code AnimationJson} (<b>MIT</b>, see DECISIONS D099) — which fixed three
 * subtle divergences:
 * <ul>
 *   <li>{@code degrees} defaults to <b>true</b> ({@code !node.has("degrees") || …} in the
 *       reference), not false — files that omit it store rotations in degrees.</li>
 *   <li>{@code turn} adds an EXTRA keyframe at the same tick with {@code value + 2π×turn}
 *       (rotation axes only, applied after degree conversion) — it is NOT folded into the base
 *       keyframe's value.</li>
 *   <li>{@code easingArg} (per move) and {@code easeBeforeKeyframe} (per emote) exist and shape
 *       playback; both are honored now.</li>
 * </ul>
 *
 * <p>Documented shape:
 * <pre>{@code
 * { "name": "...", "author": "...", "description": "...",
 *   "emote": {
 *     "isLoop": "false" | true, "returnTick": 2, "beginTick": 0, "endTick": 60, "stopTick": 61,
 *     "degrees": false, "easeBeforeKeyframe": false,
 *     "moves": [ { "tick": 15, "easing": "EASEINOUTQUAD", "easingArg": 1.5, "turn": 0,
 *                  "head": { "x": 0.0, "pitch": 1.5, ... }, ... } ] } }
 * }</pre>
 * Permissive by design: unknown keys (including the documented free-form "comment") are ignored,
 * missing metadata falls back to the filename, "isLoop" may be a bool or a string, and the
 * "bend"/"axis"/scale channels (need a body-splitting render layer this mod doesn't have) are
 * skipped without failing the file. Matching the reference, {@code isLoop} only takes effect when
 * {@code returnTick} is also present. Position values are ABSOLUTE PIVOTS in vanilla model space
 * (see {@link EmoteData}'s class doc).
 */
public final class EmoteJsonParser {

    private EmoteJsonParser() {}

    /** Parses one emote file. Returns null (with a log line) if the file isn't a valid emote. */
    public static EmoteData parse(String id, Reader reader) {
        try {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject emote = root.getAsJsonObject("emote");
            if (emote == null) {
                LogUtil.warn("[SteamPad] Emote '{}' has no \"emote\" block — skipped.", id);
                return null;
            }

            String name = str(root, "name", id);
            String author = str(root, "author", "");
            String description = str(root, "description", "");

            // Reference: degrees defaults TRUE; loop only honored when returnTick is present too.
            boolean degrees = bool(emote, "degrees", true);
            boolean loop = emote.has("isLoop") && emote.has("returnTick") && bool(emote, "isLoop", false);
            float begin = f(emote, "beginTick", 0f);
            float end = f(emote, "endTick", 20f);
            float stop = f(emote, "stopTick", end);   // <=end → the constructor clamps to end+3
            float ret = f(emote, "returnTick", 0f);

            EmoteData data = new EmoteData(id, name, author, description, loop, begin, end, stop, ret);
            data.easingBefore = bool(emote, "easeBeforeKeyframe", false);

            JsonArray moves = emote.getAsJsonArray("moves");
            if (moves != null) {
                for (JsonElement el : moves) {
                    if (!el.isJsonObject()) continue;
                    parseMove(data, el.getAsJsonObject(), degrees);
                }
            }
            return data;
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] Could not parse emote '{}': {}", id, t.getMessage());
            return null;
        }
    }

    /** One "moves" entry: a tick + easing + one or more body-part objects with axis values. */
    private static void parseMove(EmoteData data, JsonObject move, boolean degrees) {
        float tick = f(move, "tick", 0f);
        Easing easing = Easing.parse(str(move, "easing", "linear"));
        Float easingArg = move.has("easingArg") && move.get("easingArg").isJsonPrimitive()
                ? fObj(move.get("easingArg")) : null;
        int turn = (int) f(move, "turn", 0f);

        for (Map.Entry<String, JsonElement> e : move.entrySet()) {
            EmoteData.Part part = partFor(e.getKey());
            if (part == null || !e.getValue().isJsonObject()) continue;
            JsonObject axes = e.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> ax : axes.entrySet()) {
                EmoteData.Axis axis = axisFor(ax.getKey());
                if (axis == null || !ax.getValue().isJsonPrimitive()) continue;
                float value;
                try {
                    value = ax.getValue().getAsFloat();
                } catch (Throwable t) {
                    continue;   // non-numeric (e.g. a nested "comment") — ignore
                }
                boolean rotation = axis == EmoteData.Axis.PITCH || axis == EmoteData.Axis.YAW
                        || axis == EmoteData.Axis.ROLL;
                if (rotation && degrees) value = (float) Math.toRadians(value);
                data.addKeyframe(part, axis, tick, value, easing, easingArg);
                // "turn": N full extra rotations — the reference adds a SECOND keyframe at the same
                // tick (after the base one) so the axis spins through the turns, then lands.
                if (rotation && turn != 0) {
                    data.addKeyframe(part, axis, tick,
                            (float) (value + Math.PI * 2 * turn), easing, easingArg);
                }
            }
        }
    }

    private static EmoteData.Part partFor(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "head" -> EmoteData.Part.HEAD;
            case "torso", "body" -> EmoteData.Part.TORSO;
            case "rightarm", "right_arm" -> EmoteData.Part.RIGHT_ARM;
            case "leftarm", "left_arm" -> EmoteData.Part.LEFT_ARM;
            case "rightleg", "right_leg" -> EmoteData.Part.RIGHT_LEG;
            case "leftleg", "left_leg" -> EmoteData.Part.LEFT_LEG;
            default -> null;   // "tick"/"easing"/"turn"/"comment"/unknown — not a body part
        };
    }

    private static EmoteData.Axis axisFor(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "x" -> EmoteData.Axis.X;
            case "y" -> EmoteData.Axis.Y;
            case "z" -> EmoteData.Axis.Z;
            case "pitch" -> EmoteData.Axis.PITCH;
            case "yaw" -> EmoteData.Axis.YAW;
            case "roll" -> EmoteData.Axis.ROLL;
            // "bend"/"axis"/scale: torso bending & scaling — unsupported (needs a split-cuboid
            // model layer). Skipped silently so those emotes still play their base channels.
            default -> null;
        };
    }

    // ---- Tolerant primitive readers (the format allows strings for bools, ints for floats…) ----

    private static String str(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        try {
            if (e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
            return Boolean.parseBoolean(e.getAsString().trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static float f(JsonObject o, String key, float def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return def;
        try {
            return e.getAsFloat();
        } catch (Throwable t) {
            return def;
        }
    }

    private static Float fObj(JsonElement e) {
        try {
            return e.getAsFloat();
        } catch (Throwable t) {
            return null;
        }
    }
}
