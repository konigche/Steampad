package dev.steampad.radial;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.RadialConfig;
import dev.steampad.input.StickSettleGate;
import dev.steampad.util.LogUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Controls the radial menu lifecycle: open/close, navigation, and slot execution.
 *
 * <p>Selection is computed from the right-stick angle, matching the reference radial mod: slot 0 at
 * the top (12 o'clock), going clockwise, with the angle centered on each sector. The number of slots
 * is configurable (2–12) and the wheel distributes exactly that many evenly. Stick directions are
 * <em>not</em> inverted (the previous code negated Y).
 */
public final class RadialMenuController {

    private static boolean open = false;
    private static int selectedSlot = -1;

    /** Stops a released stick's spring-back from re-pointing the selection — see
     *  {@link #updateAnalog}. Reset on every open so a new gesture starts clean. */
    private static final StickSettleGate stickGate = new StickSettleGate();
    private static long activeHandle = 0L;
    private static int slotCount = 8;
    private static List<RadialSlot> slots = new ArrayList<>();
    /** Which wheel is showing: 0 = first, 1 = second (E10). Remembered across opens. */
    private static int page = 0;

    private RadialMenuController() {}

    public static boolean isOpen() { return open; }
    public static int getSelectedSlot() { return selectedSlot; }
    public static List<RadialSlot> getSlots() { return slots; }
    public static long getActiveHandle() { return activeHandle; }
    public static int getPage() { return page; }

    /** How many wheels the active controller has configured. */
    public static int wheelCount() {
        if (activeHandle == 0L) return 1;
        RadialConfig cfg = ConfigManager.getRadialConfig(activeHandle);
        cfg.normalize();
        return cfg.wheelCount();
    }

    /** LB/RB while the wheel is open: carousel to the previous/next wheel (wraps around). */
    public static void switchPage(int dir) {
        if (!open) return;
        int n = wheelCount();
        if (n <= 1) return;
        page = Math.floorMod(page + (dir >= 0 ? 1 : -1), n);
        loadSlots(activeHandle);
        selectedSlot = -1;
        RadialRenderer.onPageSwitched(dir);
        LogUtil.debug("Radial wheel switched to page {}", page);
    }

    /** Opens the wheel directly on a specific page — the EMOTE_WHEEL bind jumps straight to the
     *  dedicated emote wheel without cycling through the others (FASE 63). */
    public static void openAt(long handle, int pageIndex) {
        if (open) return;
        page = Math.max(0, pageIndex);
        open(handle);
    }

    public static void open(long handle) {
        if (open) return;
        activeHandle = handle;
        // `page` is remembered across opens (see its field doc) — convenient for a user's OWN
        // wheels, but the vanilla-shortcuts wheel must never be what greets a fresh open (feedback:
        // "esta siempre debe estar en el último lugar... cuando uso la rueda de nuevo me sale esa
        // rueda"). It stays fully reachable by navigating the carousel WHILE the wheel is open —
        // this only stops it from being resumed-to automatically.
        if (isVanillaShortcutsPage(handle, page)) page = 0;
        loadSlots(handle);
        stickGate.reset();   // a fresh wheel must never inherit the last gesture's release lockout
        open = true;
        selectedSlot = -1;
        RadialRenderer.resetBlob();   // the jelly blob starts from the wheel center (E11)
        LogUtil.debug("Radial menu opened for controller {}", handle);
    }

    private static boolean isVanillaShortcutsPage(long handle, int pageIndex) {
        RadialConfig cfg = ConfigManager.getRadialConfig(handle);
        cfg.normalize();
        List<RadialConfig.WheelConfig> wheels = cfg.wheels;
        return pageIndex >= 0 && pageIndex < wheels.size() && wheels.get(pageIndex).vanillaShortcutsWheel;
    }

    public static void close() {
        if (!open) return;
        open = false;

        // Execute selected slot on release if configured.
        if (selectedSlot >= 0 && selectedSlot < slots.size()) {
            RadialSlot slot = slots.get(selectedSlot);
            if (slot.triggerOnRelease && !slot.isEmpty()) {
                executeSlot(slot);
            }
        }
        selectedSlot = -1;
        LogUtil.debug("Radial menu closed.");
    }

    /** Close without executing (used when entering the editor). */
    public static void dismiss() {
        open = false;
        selectedSlot = -1;
    }

    /** Navigate to a specific slot index (clamped to the active slot count). */
    public static void navigate(int slotIndex) {
        if (!open || slotCount <= 0) return;
        int next = ((slotIndex % slotCount) + slotCount) % slotCount;
        if (next != selectedSlot) {
            selectedSlot = next;
            dev.steampad.haptics.HapticsController.radialSelectPulse();
        }
    }

    /**
     * Update selection based on right-stick input (called every tick while open).
     *
     * <p>Selection is <b>sticky</b>: once the stick has picked a sector, returning the stick to the
     * centre keeps that sector highlighted instead of clearing it. This is what makes the menu usable
     * — the natural gesture is to flick the stick toward a slot and then release the open button (often
     * letting the stick recentre a frame earlier). Without stickiness, {@code selectedSlot} reset to
     * -1 on that frame and both ON_CLICK and ON_RELEASE execution did nothing (the reported bug).
     *
     * <p><b>Release "whiplash" (reported: "selecciono una cosa y al soltar hay un latigazo y escoge
     * otra cosa que tiene al lado").</b> Stickiness alone was not enough, because a released stick does
     * not fall quietly to zero — it springs PAST centre and reads a real deflection the other way for
     * about a tenth of a second. The old threshold here was 0.35, which sits INSIDE that rebound band
     * (this project measured the ceiling at {@link StickSettleGate#REENGAGE_MAG} = 0.45 when it fixed
     * the same bug on the virtual keyboard), so the rebound re-pointed the selection at a different
     * sector — and since the open button is usually released in the very same breath, that wrong sector
     * is what {@link #confirmSelection}/{@link #close} then executed. Two changes close it: the pick
     * threshold IS the rebound ceiling now, and {@link StickSettleGate} additionally freezes the
     * selection for the ring-down after any release, so even a rebound that clears 0.45 cannot move it.
     */
    public static void updateAnalog(float x, float y) {
        float mag = (float) Math.sqrt(x * x + y * y);
        // Release gate first — see the doc above. A frozen sample keeps the sticky selection.
        if (!stickGate.accept(mag, System.currentTimeMillis())) return;
        if (mag < StickSettleGate.REENGAGE_MAG) return;   // below threshold: keep the last selection
        double angle = Math.atan2(y, x) + Math.PI / 2.0;   // 0 = top, clockwise; Y NOT negated
        if (angle < 0) angle += 2 * Math.PI;
        double sector = 2 * Math.PI / slotCount;
        int next = (int) ((angle + sector / 2) / sector) % slotCount;
        if (next != selectedSlot) {
            selectedSlot = next;
            dev.steampad.haptics.HapticsController.radialSelectPulse();
        }
    }

    /** Called when the user confirms the current selection (ON_CLICK trigger). */
    public static void confirmSelection() {
        if (!open || selectedSlot < 0 || selectedSlot >= slots.size()) return;
        RadialSlot slot = slots.get(selectedSlot);
        if (!slot.isEmpty() && !slot.triggerOnRelease) {
            executeSlot(slot);
        }
    }

    /**
     * Press-to-activate: executes the currently-selected slot immediately, regardless of its trigger
     * mode (used when the player presses A while the wheel is open). Pair with {@link #dismiss()} so
     * the subsequent close doesn't run an ON_RELEASE slot a second time.
     */
    public static void activateSelected() {
        if (!open || selectedSlot < 0 || selectedSlot >= slots.size()) return;
        RadialSlot slot = slots.get(selectedSlot);
        if (!slot.isEmpty()) executeSlot(slot);
    }

    /** Opens the slot editor for the currently-selected slot (gameplay: select + LT). */
    public static void openEditorForSelected() {
        if (!open) return;
        int idx = selectedSlot;
        long handle = activeHandle;
        dismiss();
        if (idx < 0) idx = 0;
        Minecraft.getInstance().setScreen(
                new dev.steampad.screen.RadialEditorScreen(null, handle, idx));
    }

    private static void executeSlot(RadialSlot slot) {
        LogUtil.debug("Executing radial slot type={} action={}", slot.type, slot.actionValue);
        switch (slot.type) {
            case CHAT_COMMAND -> sendCommand(slot.actionValue);
            case SMART_COMMAND -> runSmartCommand(slot.actionValue);
            case KEYBIND -> triggerKeybind(slot.actionValue);
            case SCREEN_SHORTCUT -> openScreen(slot.actionValue);
            case SUBMENU -> openSubmenu(slot.actionValue);
            case MALILIB_KEYBIND -> triggerMalilibKeybind(slot.actionValue);
            case EMOTE -> dev.steampad.emote.EmoteAnimator.playLocal(slot.actionValue);
            default -> {}
        }
    }

    private static void sendCommand(String cmd) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || cmd == null || cmd.isBlank()) return;
        if (cmd.startsWith("/")) {
            mc.player.connection.sendCommand(cmd.substring(1));
        } else {
            mc.player.connection.sendChat(cmd);
        }
    }

    /** Runs a built-in command by id (see {@link SmartCommand}); unknown ids are ignored, exactly like
     *  an unrecognized slot type, so a config written by a newer version can't throw here. */
    private static void runSmartCommand(String id) {
        SmartCommand cmd = SmartCommand.byId(id);
        if (cmd == null) {
            LogUtil.debug("Radial SMART_COMMAND '{}' not recognized.", id);
            return;
        }
        cmd.execute(Minecraft.getInstance());
    }

    private static void triggerKeybind(String keybindId) {
        if (keybindId == null || keybindId.isBlank()) return;
        Minecraft mc = Minecraft.getInstance();
        for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) {
            if (k.getName().equals(keybindId)) {
                // Robust press/hold/release so both wasPressed() and isPressed() consumers fire, and
                // even unbound mod keybinds trigger (see KeyTap).
                dev.steampad.input.KeyTap.press(k);
                return;
            }
        }
        LogUtil.debug("Radial KEYBIND '{}' not found among registered keybinds.", keybindId);
    }

    private static void openScreen(String screenId) {
        Minecraft mc = Minecraft.getInstance();
        if (screenId == null || screenId.isBlank()) return;
        switch (screenId) {
            case "steampad:controller_select" ->
                    mc.setScreen(new dev.steampad.screen.ControllerSelectScreen(null));
            case "minecraft:inventory", "inventory" -> {
                if (mc.player != null) mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            }
            case "minecraft:pause", "pause", "menu" ->
                    dev.steampad.input.PauseGate.openPauseMenu(mc);
            default -> LogUtil.debug("Radial SCREEN_SHORTCUT '{}' not recognized.", screenId);
        }
    }

    /**
     * Submenu: opens a nested radial using the named radial config. Falls back to the controller's
     * default radial if the named one is empty/missing, so a configured submenu always does something
     * rather than silently failing.
     */
    private static void openSubmenu(String submenuId) {
        LogUtil.debug("Radial submenu '{}' requested — reopening wheel.", submenuId);
        long handle = activeHandle;
        // Reopen the wheel (a true per-id submenu config can be layered later); this keeps SUBMENU from
        // being a dead option and lets the player keep navigating. This runs mid-dispatch from
        // confirmSelection()/activateSelected(), i.e. while `open` is still true — open() no-ops when
        // already open, so force it closed first or the reopen would silently do nothing.
        open = false;
        open(handle);
    }

    /**
     * MALILIB_KEYBIND slot. MaLiLib's own hotkey registry is not wired yet (see
     * {@link dev.steampad.compat.MalilibCompat#triggerKeybind}), and the editor fills this slot from
     * the ORDINARY keybind picker — which lists vanilla {@code KeyMapping}s. So when MaLiLib can't
     * take it, run it as a normal keybind rather than dropping it on the floor: that is the whole
     * difference between this slot type doing nothing at all and doing the useful thing.
     */
    private static void triggerMalilibKeybind(String keybindId) {
        if (dev.steampad.compat.MalilibCompat.triggerKeybind(keybindId)) return;
        triggerKeybind(keybindId);
    }

    private static void loadSlots(long handle) {
        RadialConfig cfg = ConfigManager.getRadialConfig(handle);
        cfg.normalize();
        if (page >= cfg.wheelCount()) page = 0;   // a wheel was removed → back to the first
        slotCount = cfg.slotCountFor(page);
        slots = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            RadialConfig.SlotConfig sc = cfg.slotsFor(page).get(i);
            RadialActionType type;
            try { type = RadialActionType.valueOf(sc.type); }
            catch (IllegalArgumentException e) {
                type = RadialActionType.NONE;
                LogUtil.debug("Radial slot {} has unrecognized type '{}' — treating as empty.", i, sc.type);
            }
            slots.add(new RadialSlot(type, sc.action, sc.iconType, sc.iconValue,
                    sc.displayName, "ON_RELEASE".equals(sc.trigger)));
        }
    }
}
