package dev.steampad.emote;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.RadialConfig;
import dev.steampad.radial.RadialActionType;
import dev.steampad.radial.RadialSlot;
import dev.steampad.util.LogUtil;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Controls the dedicated EMOTE WHEEL's lifecycle — open/close, stick navigation, execution — fully
 * independent of {@link dev.steampad.radial.RadialMenuController} (FASE 63 follow-up).
 *
 * <p>The emote wheel used to be just another entry inside the regular radial wheel list, sharing
 * that controller's single static open/page/slots state (feedback: "la rueda de emotes con la de
 * menu radial deben ser independientes no deben compartir información... si se necesita poner más
 * ruedas para emotes no se interpongan") — opening one could clobber the other's in-progress
 * selection, and every emote wheel counted against the user's own {@code MAX_WHEELS} budget. This
 * class mirrors {@code RadialMenuController}'s proven open/select/execute mechanics (same
 * sticky-selection stick math, same carousel/haptics behavior, same {@link RadialSlot} data so the
 * existing {@link dev.steampad.radial.RadialRenderer} draws it with zero changes) but reads and
 * writes {@link RadialConfig#emoteWheels} — a wholly separate list — and keeps its own runtime
 * state, so the two wheel systems can never interfere with each other.
 */
public final class EmoteWheelController {

    private static boolean open = false;
    private static int selectedSlot = -1;
    private static long activeHandle = 0L;
    private static int slotCount = 0;
    private static List<RadialSlot> slots = new ArrayList<>();
    private static int page = 0;

    private EmoteWheelController() {}

    public static boolean isOpen() { return open; }
    public static int getSelectedSlot() { return selectedSlot; }
    public static List<RadialSlot> getSlots() { return slots; }
    public static long getActiveHandle() { return activeHandle; }
    public static int getPage() { return page; }

    /** How many emote wheels the active controller has configured (forward-looking — today's UI only
     *  manages the first one, but this already reflects however many exist). */
    public static int wheelCount() {
        if (activeHandle == 0L) return 1;
        RadialConfig cfg = ConfigManager.getRadialConfig(activeHandle);
        cfg.normalize();
        return Math.max(1, cfg.emoteWheelCount());
    }

    /** LB/RB while the emote wheel is open: carousel to the previous/next emote wheel, if more than
     *  one is configured. */
    public static void switchPage(int dir) {
        if (!open) return;
        int n = wheelCount();
        if (n <= 1) return;
        page = Math.floorMod(page + (dir >= 0 ? 1 : -1), n);
        loadSlots(activeHandle);
        selectedSlot = -1;
        dev.steampad.radial.RadialRenderer.onPageSwitched(dir);
        LogUtil.debug("Emote wheel switched to page {}", page);
    }

    /** Opens (creating the dedicated emote wheel on first use) and loads its slots. */
    public static void open(long handle) {
        if (open) return;
        activeHandle = handle;
        RadialConfig cfg = ConfigManager.getRadialConfig(handle);
        int idx = cfg.ensureEmoteWheel();
        if (idx < 0) return;   // MAX_WHEELS emote wheels already exist — nothing to open
        ConfigManager.saveRadialConfig(handle);   // persist the wheel if just created
        page = idx;
        loadSlots(handle);
        open = true;
        selectedSlot = -1;
        dev.steampad.radial.RadialRenderer.resetBlob();   // the jelly blob starts from the wheel center
        LogUtil.debug("Emote wheel opened for controller {}", handle);
    }

    public static void close() {
        if (!open) return;
        open = false;
        if (selectedSlot >= 0 && selectedSlot < slots.size()) {
            RadialSlot slot = slots.get(selectedSlot);
            if (!slot.isEmpty() && slot.triggerOnRelease) executeSlot(slot);
        }
        selectedSlot = -1;
        LogUtil.debug("Emote wheel closed.");
    }

    /** Close without executing (used when entering the editor). */
    public static void dismiss() {
        open = false;
        selectedSlot = -1;
    }

    /** Update selection based on right-stick input — same sticky-selection behavior as the regular
     *  radial menu (see {@code RadialMenuController#updateAnalog} for why). */
    public static void updateAnalog(float x, float y) {
        if (slotCount <= 0) return;
        float mag = (float) Math.sqrt(x * x + y * y);
        if (mag < 0.35f) return;
        double angle = Math.atan2(y, x) + Math.PI / 2.0;
        if (angle < 0) angle += 2 * Math.PI;
        double sector = 2 * Math.PI / slotCount;
        int next = (int) ((angle + sector / 2) / sector) % slotCount;
        if (next != selectedSlot) {
            selectedSlot = next;
            dev.steampad.haptics.HapticsController.radialSelectPulse();
        }
    }

    /** ON_CLICK trigger: confirm the current selection. */
    public static void confirmSelection() {
        if (!open || selectedSlot < 0 || selectedSlot >= slots.size()) return;
        RadialSlot slot = slots.get(selectedSlot);
        if (!slot.isEmpty() && !slot.triggerOnRelease) executeSlot(slot);
    }

    /** Press-to-activate (A while the wheel is open): plays the highlighted emote immediately. Pair
     *  with {@link #dismiss()} so the subsequent close doesn't play it a second time. */
    public static void activateSelected() {
        if (!open || selectedSlot < 0 || selectedSlot >= slots.size()) return;
        RadialSlot slot = slots.get(selectedSlot);
        if (!slot.isEmpty()) executeSlot(slot);
    }

    /** Opens the emote wheel's editor (Botones → Configurar rueda de emotes). */
    public static void openEditorForSelected() {
        if (!open) return;
        long handle = activeHandle;
        dismiss();
        MinecraftClient.getInstance().setScreen(
                new dev.steampad.screen.EmoteWheelScreen(null, handle));
    }

    private static void executeSlot(RadialSlot slot) {
        if (slot.type != RadialActionType.EMOTE) return;   // this wheel only ever holds EMOTE slots
        LogUtil.debug("Executing emote wheel slot emote={}", slot.actionValue);
        EmoteAnimator.playLocal(slot.actionValue);
    }

    private static void loadSlots(long handle) {
        RadialConfig cfg = ConfigManager.getRadialConfig(handle);
        cfg.normalize();
        int count = cfg.emoteWheelCount();
        if (count == 0) { slotCount = 0; slots = new ArrayList<>(); return; }
        if (page >= count) page = 0;
        slotCount = cfg.slotCountForEmote(page);
        slots = new ArrayList<>();
        List<RadialConfig.SlotConfig> raw = cfg.slotsForEmote(page);
        for (int i = 0; i < slotCount; i++) {
            RadialConfig.SlotConfig sc = raw.get(i);
            RadialActionType type;
            try { type = RadialActionType.valueOf(sc.type); }
            catch (IllegalArgumentException e) { type = RadialActionType.NONE; }
            slots.add(new RadialSlot(type, sc.action, sc.iconType, sc.iconValue,
                    sc.displayName, "ON_RELEASE".equals(sc.trigger)));
        }
    }
}
