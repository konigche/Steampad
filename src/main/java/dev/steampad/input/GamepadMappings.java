package dev.steampad.input;

import dev.steampad.util.LogUtil;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads SDL-format gamepad mappings into BOTH GLFW and SDL3 so more controllers — notably 8BitDo pads
 * (Ultimate 2, Pro 3, SN30) in their various modes — are recognized as standard gamepads with the
 * correct layout, REGARDLESS of which backend ends up reading them.
 *
 * <p>Two sources, both optional and additive:
 * <ol>
 *   <li>Bundled {@code assets/steampad/gamecontrollerdb.txt} — a curated set focused on 8BitDo.</li>
 *   <li>User file {@code config/steampad/gamecontrollerdb.txt} — drop the full SDL community DB or a
 *       hand-made line for your exact device; this overrides/extends the bundled set.</li>
 * </ol>
 *
 * <p>Fixed in v0.57.0 (D097, feedback: "el lag parece ser con los joysticks... pasa con el 8bitdo pero
 * con el fisico de la ROG no pasa") — this used to teach ONLY GLFW ({@code glfwUpdateGamepadMappings}),
 * never SDL3. A real hardware log confirmed the 8BitDo's handle carried the GLFW tag the entire
 * session (not SDL3, unlike the ROG Ally's own pad) — {@code SDL_GetGamepads()} only enumerates
 * devices SDL's OWN mapping DB already recognizes as a gamepad, so an 8BitDo pad libSDL3's bundled DB
 * doesn't cover fell all the way back to GLFW's joystick-polling path in {@link GlfwSnapshotSource}
 * instead of SDL3's own HIDAPI-capable, purpose-built one — a plausible source of exactly the kind of
 * device-specific stick lag reported. Now pushes the SAME mapping lines to
 * {@link dev.steampad.input.sdl.Sdl3GamepadProvider#loadMappings} too, via {@code SDL_AddGamepadMapping}.
 *
 * <p>{@code glfwUpdateGamepadMappings}/{@code SDL_AddGamepadMapping} both only affect devices whose
 * GUID matches, so loading extra entries never disturbs an already-working controller. Devices still
 * unmatched by either fall back to raw joystick reading in {@link GlfwSnapshotSource}. Must be called
 * after GLFW init (render thread) — and after {@code Sdl3GamepadProvider.init()} for the SDL3 half to
 * actually take effect (see the call site in {@code SteamPadClient}).
 */
public final class GamepadMappings {

    private static boolean loaded = false;
    /** Debug-dump line (D098): what the last loadAll() actually pushed, per backend. */
    private static volatile String lastLoadSummary = "not loaded yet";

    public static String lastLoadSummary() { return lastLoadSummary; }

    private GamepadMappings() {}

    public static void loadAll() {
        if (loaded) return;
        loaded = true;

        StringBuilder sb = new StringBuilder();
        appendBundled(sb);
        appendUserFile(sb);

        if (sb.length() == 0) {
            LogUtil.info("[SteamPad] No extra gamepad mappings to apply.");
            return;
        }
        apply(sb.toString());
    }

    private static void appendBundled(StringBuilder sb) {
        try (InputStream in = GamepadMappings.class.getClassLoader()
                .getResourceAsStream("assets/steampad/gamecontrollerdb.txt")) {
            if (in == null) return;
            sb.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] Failed to read bundled gamepad mappings: {}", t.getMessage());
        }
    }

    private static void appendUserFile(StringBuilder sb) {
        try {
            Path file = FabricLoader.getInstance().getConfigDir()
                    .resolve("steampad").resolve("gamecontrollerdb.txt");
            if (Files.isRegularFile(file)) {
                sb.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
                LogUtil.info("[SteamPad] Loaded user gamepad mappings from {}", file);
            }
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] Failed to read user gamepad mappings: {}", t.getMessage());
        }
    }

    private static void apply(String content) {
        boolean glfwUpdated = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buf = stack.UTF8(content);   // null-terminated UTF-8
            glfwUpdated = GLFW.glfwUpdateGamepadMappings(buf);
            LogUtil.info("[SteamPad] Gamepad mappings applied (8BitDo + user overrides): updated={}", glfwUpdated);
        } catch (Throwable t) {
            LogUtil.warn("[SteamPad] glfwUpdateGamepadMappings failed: {}", t.getMessage());
        }
        // Also teach libSDL3 the SAME mapping lines (D097) — see Sdl3GamepadProvider.loadMappings for
        // why this was missing before and what it fixes. No-op if SDL3 isn't loaded/enabled; requires
        // Sdl3GamepadProvider.init() to have already run (SteamPadClient now inits SDL3 before calling
        // loadAll() here, specifically so this call has a live libSDL3 to register mappings against).
        dev.steampad.input.sdl.Sdl3GamepadProvider.loadMappings(content);
        lastLoadSummary = "GLFW updated=" + glfwUpdated + " | SDL3: "
                + dev.steampad.input.sdl.Sdl3GamepadProvider.mappingStats();
    }
}
