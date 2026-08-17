package dev.steampad.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure identity math for a physical controller: given what a backend reports about a device, produce
 * a <em>persistent</em> key that names the same physical pad tomorrow, after a reconnect, and in a
 * second Minecraft instance.
 *
 * <p><b>Why this class exists (the bug it fixes).</b> Everything that had to remember something
 * per-controller used one of two identifiers, and both are wrong:
 *
 * <ul>
 *   <li><b>The numeric handle</b> ({@code controller_<handle>.json}, {@code bindings_<handle>.json},
 *       {@code radial_<handle>.json}, {@code lastActiveControllerHandle}). SDL hands out a fresh
 *       {@code SDL_JoystickID} for every connection and restarts the counter each process, and GLFW's
 *       joystick id is just the first free slot. So the SAME pad is handle 1 in one session and
 *       handle 4 in the next — and, worse, handle 4 in this session may be the file the OTHER pad
 *       wrote last session. That is literally the report "cuando se conectan recuerdan otras
 *       configuraciones antiguas": the config isn't lost, it's being read off the wrong pad's file.
 *       It also explains why launching with the pads ALREADY connected worked: enumerating at startup
 *       hands out ids in the same order as last time, so the handles happen to line up.</li>
 *   <li><b>The display name</b> ({@code preferredControllerName}, the reconnect migration's
 *       {@code name_index.json}, the cross-instance claim key). A name is not an identifier: the same
 *       8BitDo reports a completely different string depending on the backend and on which
 *       compatibility mode it booted in, and two pads of the same model report the SAME string —
 *       which made the name-keyed migration copy one pad's bindings on top of the other's, and made
 *       "predeterminado" latch onto whichever identical pad enumerated first.</li>
 * </ul>
 *
 * <p><b>What is stable.</b> The USB vendor/product pair. It survives reconnects, reboots, backend
 * changes and renames, and SDL reports it directly while GLFW encodes it in the joystick GUID. For
 * the only case VID/PID cannot separate — two pads of the <em>same model</em> — SDL can additionally
 * report the device's serial number, which is unique per unit.
 *
 * <p>No I/O, no Minecraft, no statics that change under a test: {@link ControllerIdentityService}
 * owns the persistence and calls in here for every decision, so the decisions themselves are unit
 * tested rather than reasoned about.
 */
public final class ControllerIdentity {

    private ControllerIdentity() {}

    /**
     * What a backend can tell us about one physical device. {@code vendorId}/{@code productId} are 0
     * when unknown (GLFW's synthetic XInput GUIDs and Steam Input handles encode no real ids), and
     * {@code serial} is null/blank unless SDL opened the pad through its HIDAPI driver.
     */
    public record Signals(int vendorId, int productId, int productVersion, String serial, String name) {

        public Signals {
            serial = serial == null ? "" : serial.trim();
            name = name == null ? "" : name.trim();
        }

        /** True when the USB ids are usable, i.e. the strong identity path is available. */
        public boolean hasUsbIds() { return vendorId != 0 && productId != 0; }

        public boolean hasSerial() { return !serial.isEmpty(); }

        /** Same physical MODEL — not necessarily the same unit (two identical pads match here). */
        public boolean sameModel(Signals other) {
            if (other == null) return false;
            if (hasUsbIds() || other.hasUsbIds()) {
                return vendorId == other.vendorId && productId == other.productId;
            }
            // No USB ids on either side: the name is all that is left. Weak, but it is exactly the
            // signal the old code used for EVERYTHING, so this can only be an improvement over it.
            return !name.isEmpty() && name.equalsIgnoreCase(other.name);
        }
    }

    /**
     * The key a device gets when nothing about it is known yet — deliberately WITHOUT the serial.
     *
     * <p>The serial is used to <em>match</em> (see {@link #resolve}) but never to name, because it is
     * not reliably available: the same pad reports a serial when SDL opens it with its HIDAPI driver
     * and reports nothing when it falls back to evdev (under Flatpak, with Steam holding the HID
     * device, …). Naming the file after it would mean a pad silently losing its whole configuration
     * the first time it came up on the other driver — the exact failure mode this class exists to
     * remove. Duplicate models get a {@code _2}/{@code _3} suffix from {@link #resolve} instead.
     */
    public static String baseId(Signals s) {
        if (s.hasUsbIds()) {
            return String.format(Locale.ROOT, "%04x-%04x", s.vendorId(), s.productId());
        }
        String n = sanitize(s.name());
        return n.isEmpty() ? "unknown" : "name-" + n;
    }

    /**
     * The persistent key for one device.
     *
     * @param s        what the backend reports about it right now
     * @param known    every key ever assigned, mapped to the signals recorded at the time
     * @param taken    keys already handed to a DIFFERENT device in this same resolution round — two
     *                 connected pads must never collapse onto one key, whatever the registry says
     * @return an existing key when this is a device we have seen before, else a fresh one
     */
    public static String resolve(Signals s, Map<String, Signals> known, Set<String> taken) {
        // Candidates: every remembered device of the same model that isn't already spoken for.
        List<Map.Entry<String, Signals>> sameModel = new ArrayList<>();
        for (Map.Entry<String, Signals> e : known.entrySet()) {
            if (taken.contains(e.getKey())) continue;
            if (s.sameModel(e.getValue())) sameModel.add(e);
        }

        // 1. Exact serial match — the only signal that separates two units of the same model.
        if (s.hasSerial()) {
            for (Map.Entry<String, Signals> e : sameModel) {
                if (s.serial().equalsIgnoreCase(e.getValue().serial())) return e.getKey();
            }
        }

        // 2. A remembered device of this model whose serial we never learned. Deliberately reachable
        //    even when THIS session does have a serial (we learned it since) and, crucially, when this
        //    session does NOT have one but the registry does — see baseId's doc: a pad that comes up
        //    on evdev instead of HIDAPI has no serial to offer and must still find its own profile.
        for (Map.Entry<String, Signals> e : sameModel) {
            if (!e.getValue().hasSerial()) return e.getKey();
        }

        // 3. Same model, different/unknown serial. Only reached with two units of one model where the
        //    serials disagree; picking the lowest key keeps it deterministic instead of map-order
        //    dependent. If this session has a serial the caller records it, so round 2 is exact.
        if (!s.hasSerial() && !sameModel.isEmpty()) {
            String best = null;
            for (Map.Entry<String, Signals> e : sameModel) {
                if (best == null || e.getKey().compareTo(best) < 0) best = e.getKey();
            }
            return best;
        }

        // 4. Genuinely new device: base id, suffixed until free.
        String base = baseId(s);
        if (!known.containsKey(base) && !taken.contains(base)) return base;
        for (int i = 2; i < 64; i++) {
            String candidate = base + "_" + i;
            if (!known.containsKey(candidate) && !taken.contains(candidate)) return candidate;
        }
        return base + "_" + Math.abs(s.serial().hashCode() ^ s.name().hashCode());
    }

    /** Lowercase, filename-safe, bounded — these strings become file names on disk. */
    static String sanitize(String s) {
        if (s == null) return "";
        String out = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        while (out.startsWith("_")) out = out.substring(1);
        while (out.endsWith("_")) out = out.substring(0, out.length() - 1);
        return out.length() > 32 ? out.substring(0, 32) : out;
    }
}
