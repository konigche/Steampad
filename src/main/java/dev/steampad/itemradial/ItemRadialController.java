package dev.steampad.itemradial;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.RadialConfig;
import dev.steampad.radial.RadialActionType;
import dev.steampad.radial.RadialRenderer;
import dev.steampad.radial.RadialSlot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import dev.steampad.compat.mc.InventoryCompat;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Controls the Item Radial (RB = Hotbar, LB = Inventory/Backpack, both categorized per the approved
 * plan â€” swapped from the original LB/RB assignment per hardware feedback, D127). Unlike
 * {@link dev.steampad.radial.RadialMenuController}/{@code EmoteWheelController}, its slots are NOT
 * user-configured â€” they're rebuilt from the live inventory every time the wheel opens (see
 * {@link InventoryScanner}).
 *
 * <p><b>No submenu â€” bloom in place</b> (feedback round 4, replaces the original carousel-slide
 * morph): drilling into a category never opens another screen and never replaces {@link #slots} â€”
 * the outer ring of categories stays fully visible, dimmed except the source, while a satellite
 * "bloom" ring of that category's items opens concentrically INSIDE it (see
 * {@link RadialRenderer.Bloom}/{@link #getBloom()}). Opening is DWELL-driven: keep the stick pointed
 * at a category and its chip fills with a progress arc ({@link #getDwellFillProgress()}); once full,
 * it blooms automatically. Pressing A any time during the dwell skips the wait and opens instantly
 * (see {@link #pressConfirm()}). B collapses the bloom back to the category ring without closing the
 * wheel. {@link RadialRenderer#onPageSwitched(int)}'s carousel-slide is deliberately NOT used for
 * this transition anymore â€” the outer ring never needs to slide since it never leaves the screen.
 *
 * <p><b>No carousel between the two wheels</b> (hardware feedback, D127) â€” unlike the MenÃº
 * Radial/Rueda de Emotes, LB/RB never flip this wheel into the other one while held: each bumper
 * independently opens/closes its own page. {@link #wheelCount()} always reports 1 (no ghost-wheel
 * hint), and the dispatcher never calls a page-switch for this controller.
 *
 * <p><b>Selection is sticky</b> â€” copied verbatim from {@code RadialMenuController#updateAnalog}
 * (mag &lt; 0.35 keeps the last highlighted slot instead of clearing it).
 *
 * <p><b>Live preview, not deferred confirm</b> (hardware feedback, D127): while browsing
 * HOTBAR_PAGE or ITEM_MORPH, every time the focused slot changes the corresponding item is equipped
 * immediately â€” the character's held item updates live as the stick sweeps across slots, exactly
 * like {@code RadialMenuController}'s existing wheels already let you release the bind to confirm
 * instead of requiring an explicit A. Releasing the bind (or pressing A) just closes the wheel; the
 * live-applied equip is already final, there is nothing left to "confirm".
 *
 * <p><b>Category picks never touch hotbar slots 1-6</b> (feedback round 2: "que sustituya las
 * Ãºltimos tres objetos de la hotbar... para que si tiene organizado su inventario la hotbar no
 * modifique su layout del 1-6"; refined round 3: "si por error lo selecciono, debe respetar el que
 * tengo asignado por hotbar"). An item equipped from a CATEGORY_PAGE/ITEM_MORPH pick that is ALREADY
 * in a real hotbar slot (any of 0-8, including 1-6) is only SELECTED there â€” never moved. Only an
 * item living OUTSIDE the hotbar (main inventory or a backpack) gets swapped in, and only into one
 * of the last three hotbar slots ({@link #ROTATING_HOTBAR_SLOTS}, i.e. slots 7/8/9), rotating to the
 * next one each fresh browsing session so repeated picks don't just overwrite each other â€” see
 * {@link #equipAggregatedItemLive}. The dedicated HOTBAR_PAGE wheel (RB) is unaffected: it selects
 * among your real, already-organized hotbar slots directly, never swaps anything.
 *
 * <p><b>Quick tap+release is smart, not a no-op</b> (feedback round 2, "rueda realmente
 * inteligente"): releasing the wheel's bind WITHOUT ever drilling into a category (no A press) still
 * does something useful â€” it re-equips the last item you picked from whichever category was
 * focused, so a fast tap grabs "whatever I used last there" instead of doing nothing. See
 * {@link #autoEquipFocusedCategoryOnQuickClose}.
 */
public final class ItemRadialController {

    public enum Page { HOTBAR, CATEGORY }

    private enum State { CLOSED, HOTBAR_PAGE, CATEGORY_PAGE, ITEM_MORPH }

    private static State state = State.CLOSED;
    private static long activeHandle = 0L;
    /** OUTER ring focus â€” a category index in CATEGORY_PAGE/ITEM_MORPH, or a hotbar entry index in
     *  HOTBAR_PAGE. While ITEM_MORPH, this stays LOCKED to the blooming category (the source) â€”
     *  stick input no longer drives it; see {@link #selectedChild}. */
    private static int selectedSlot = -1;

    /** Stops a released stick's spring-back from re-pointing the selection on any of the three rings
     *  — see {@code RadialMenuController#updateAnalog} for the bug and the evidence. */
    private static final dev.steampad.input.StickSettleGate stickGate =
            new dev.steampad.input.StickSettleGate();
    /** INNER (bloom) ring focus â€” an index into {@link #childSlots}/{@link #categoryBacking}. Only
     *  meaningful in ITEM_MORPH. */
    private static int selectedChild = -1;
    private static List<RadialSlot> slots = new ArrayList<>();
    private static ItemCategory morphCategory;

    // Data backing the current slot list, indexed the same as `slots` â€” needed at confirm time since
    // RadialSlot itself carries only display/icon data, not the source-slot/aggregation info.
    private static List<InventoryScanner.HotbarEntry> hotbarBacking = List.of();
    private static List<InventoryScanner.AggregatedItem> categoryBacking = List.of();
    /** {@link #categoryBacking} pre-rendered as {@link RadialSlot}s for {@link #getBloom()} â€”
     *  computed once when a category blooms ({@link #enterCategory()}), not every render call. */
    private static List<RadialSlot> childSlots = List.of();
    private static Map<ItemCategory, List<InventoryScanner.AggregatedItem>> categorized = Map.of();
    /** Which page {@link #open} was called with â€” kept separate from {@link #state} (which moves to
     *  ITEM_MORPH) so the dispatcher can tell which bumper opened this wheel and knows which one's
     *  release should close it (no carousel between the two anymore â€” see class doc, D127). */
    private static Page rootPage;

    // ---- Dwell-fill (feedback round 4): hold the stick on a category and it opens on its own ----

    /** Seconds the stick must stay on one category before it blooms automatically. A skips this. */
    private static final double DWELL_SECONDS = 0.4;
    /** Which category the dwell timer is currently counting for, -1 = none dwelling. */
    private static int dwellIndex = -1;
    private static long dwellStartNanos = 0L;

    // ---- Category-pick behaviour (feedback round 2) ----

    /** The only hotbar slots a category pick may ever write to (0-indexed 6/7/8 = slots 7/8/9) â€”
     *  protects the player's own 1-6 layout entirely. */
    private static final int[] ROTATING_HOTBAR_SLOTS = {6, 7, 8};
    /** Cursor into {@link #ROTATING_HOTBAR_SLOTS}, advanced once per fresh browsing session â€”
     *  survives across wheel opens/closes for the whole client session so repeated picks keep
     *  cycling instead of always landing on the same slot. */
    private static int rotationCursor = 0;
    /** The rotating slot locked in for the CURRENT browsing session â€” {@code -1} means "not assigned
     *  yet", lazily assigned by the first live-equip of a fresh session (see {@link #open} and
     *  {@link #enterCategory}, which reset it) so every subsequent focus change within the SAME
     *  session keeps swapping into that one slot instead of jumping around. */
    private static int activeMorphTargetSlot = -1;
    /** Last item actually equipped from each category â€” read by
     *  {@link #autoEquipFocusedCategoryOnQuickClose} so a quick tap+release without drilling in still
     *  does something useful. Purely in-memory (not persisted); resets to empty on JVM restart. */
    private static final Map<ItemCategory, Item> lastUsedPerCategory = new HashMap<>();

    private ItemRadialController() {}

    public static boolean isOpen() { return state != State.CLOSED; }
    public static int getSelectedSlot() { return selectedSlot; }
    public static List<RadialSlot> getSlots() { return slots; }
    public static long getActiveHandle() { return activeHandle; }
    public static Page getRootPage() { return rootPage; }

    /** {@link RadialRenderer.Bloom} for the overlay to pass straight into {@code render()} â€” {@code
     *  null} outside CATEGORY_PAGE/ITEM_MORPH (the Hotbar wheel has no categories, no bloom concept).
     *  On CATEGORY_PAGE it carries the dwell-fill progress and no children (the ring stays closed);
     *  on ITEM_MORPH it carries the full-open children list â€” {@link RadialRenderer} eases between
     *  the two on its own, this just reports the current TARGET state each call. */
    public static RadialRenderer.Bloom getBloom() {
        return switch (state) {
            case CATEGORY_PAGE -> selectedSlot < 0 ? null
                    : new RadialRenderer.Bloom(selectedSlot, getDwellFillProgress(), null, -1);
            case ITEM_MORPH -> new RadialRenderer.Bloom(selectedSlot, 1f, childSlots, selectedChild);
            default -> null;
        };
    }

    /** Always 1 â€” no ghost-wheel hint, no carousel between the two pages (D127). Kept as a method
     *  (not removed) only because {@link RadialRenderer#render} still takes a wheelCount param. */
    public static int wheelCount() { return 1; }
    public static int getPage() { return 0; }

    public static void open(long handle, Page startPage) {
        if (state != State.CLOSED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        activeHandle = handle;
        selectedSlot = -1;
        selectedChild = -1;
        morphCategory = null;
        dwellIndex = -1;
        activeMorphTargetSlot = -1;   // fresh pick session â€” next category pick advances the rotation
        rootPage = startPage;
        stickGate.reset();   // a fresh wheel must never inherit the last gesture's release lockout
        rescan(mc.player);
        state = startPage == Page.HOTBAR ? State.HOTBAR_PAGE : State.CATEGORY_PAGE;
        loadPageSlots();
        RadialRenderer.resetBlob();
    }

    /** Sticky analog selection â€” see class doc; identical threshold/logic to
     *  {@code RadialMenuController#updateAnalog}. Routes to whichever ring is currently interactive:
     *  hotbar entries, the category ring (with dwell-fill), or the bloomed children ring.
     *
     *  <p>The release gate sits HERE, at the single entry point, rather than inside each ring's
     *  handler: spring-back is a property of the stick, so all three rings need exactly the same
     *  protection and one check is the only way they cannot drift apart. Zeroing the sample rather
     *  than returning early is deliberate: {@code updateCategoryDwell} is documented to keep its
     *  dwell-fill running while the stick recentres, and freezing only the SELECTION is precisely
     *  what lets it. */
    public static void updateAnalog(float x, float y) {
        float mag = (float) Math.sqrt(x * x + y * y);
        if (!stickGate.accept(mag, System.currentTimeMillis())) {
            x = 0f;   // frozen by the release gate: below every ring's pick threshold, so each one
            y = 0f;   // keeps its sticky selection while the rest of its per-tick logic still runs.
        }
        switch (state) {
            case HOTBAR_PAGE -> updateHotbarSelection(x, y);
            case CATEGORY_PAGE -> updateCategoryDwell(x, y);
            case ITEM_MORPH -> updateChildSelection(x, y);
            case CLOSED -> {}
        }
    }

    private static void updateHotbarSelection(float x, float y) {
        if (slots.isEmpty()) return;
        int next = slotForAnalog(x, y, slots.size());
        if (next < 0 || next == selectedSlot) return;   // below threshold: keep the sticky selection
        selectedSlot = next;
        dev.steampad.haptics.HapticsController.radialSelectPulse();
        liveSelectHotbar();
    }

    /**
     * CATEGORY_PAGE: the stick still picks which category is focused (same math/stickiness as
     * always), but focusing one now also runs a dwell-fill timer (feedback round 4: "el cÃ­rculo de
     * selecciÃ³n... se vaya rellenando... para que cuando se llene... se abra"). Called every tick
     * regardless of whether the stick moved, so the timer keeps advancing even while the player
     * holds the stick still (or lets it recenter â€” sticky selection means the focus survives that
     * too, so the fill keeps going). Moving to a DIFFERENT category cancels and restarts the timer;
     * pressing A (see {@link #pressConfirm()}) skips the wait and opens instantly.
     */
    private static void updateCategoryDwell(float x, float y) {
        if (slots.isEmpty()) return;
        int next = slotForAnalog(x, y, slots.size());
        if (next >= 0 && next != selectedSlot) {
            selectedSlot = next;
            dwellIndex = next;
            dwellStartNanos = System.nanoTime();
            dev.steampad.haptics.HapticsController.radialSelectPulse();
        }
        if (dwellIndex >= 0 && dwellIndex == selectedSlot
                && (System.nanoTime() - dwellStartNanos) / 1.0e9 >= DWELL_SECONDS) {
            enterCategory();
        }
    }

    /** 0..1 fill of the dwell-fill progress arc on the currently-dwelling category chip, for
     *  {@link #getBloom()} â€” 0 while nothing is being dwelled on. */
    private static float getDwellFillProgress() {
        if (dwellIndex < 0 || dwellIndex != selectedSlot || state != State.CATEGORY_PAGE) return 0f;
        double elapsed = (System.nanoTime() - dwellStartNanos) / 1.0e9;
        return (float) Math.min(1.0, elapsed / DWELL_SECONDS);
    }

    /** ITEM_MORPH: the stick now navigates the BLOOMED children instead of the (locked) outer
     *  category ring â€” sticky, but via {@link #childSlotForAnalog} instead of {@link #slotForAnalog}
     *  (the children aren't evenly spaced around a full circle anymore, see that method's doc).
     *  Every real focus change equips live (point 1, feedback round 4: haptics fire for children
     *  too, same pulse as every other selection change in this wheel) and respects the hotbar
     *  exactly like the category-page flow already did (see {@link #equipAggregatedItemLive}). */
    private static void updateChildSelection(float x, float y) {
        if (categoryBacking.isEmpty() || morphCategory == null) return;
        double sourceAngle = -Math.PI / 2 + selectedSlot * (2 * Math.PI / Math.max(1, slots.size()));
        int next = childSlotForAnalog(x, y, sourceAngle, categoryBacking.size(), bloomStep());
        if (next < 0 || next == selectedChild) return;
        selectedChild = next;
        dev.steampad.haptics.HapticsController.radialSelectPulse();
        equipAggregatedItemLive(morphCategory, categoryBacking.get(selectedChild));
    }

    /** The SAME angular step {@link RadialRenderer#drawBloom} sizes/spaces the bloom ring with â€”
     *  derived from this controller's own {@link RadialConfig} (feedback: "vuelve a como estaba
     *  distribuido" â€” a fixed constant here would drift from whatever look the user actually tuned
     *  via chip size/wheel radius, exactly what happened when this was tried). One extra config
     *  lookup per focus change, negligible next to a network click. */
    private static double bloomStep() {
        RadialConfig cfg = ConfigManager.getRadialConfig(activeHandle);
        cfg.normalize();
        int outerChipR = RadialRenderer.chipRadius(cfg);
        int outerRing = RadialRenderer.outerRing(cfg, slots.size());
        return RadialRenderer.bloomChildStepForGeometry(outerRing, outerChipR, categoryBacking.size());
    }

    /** Pure angle-to-slot math, extracted for testability (no {@code MinecraftClient}/haptics touched)
     *  â€” identical threshold/geometry to {@code RadialMenuController#updateAnalog}: slot 0 at 12
     *  o'clock, clockwise. Returns -1 if the stick magnitude is below the sticky threshold. Used for
     *  the OUTER rings (categories, hotbar entries) â€” evenly spaced around a full circle. NOT used
     *  for the bloom's children anymore â€” see {@link #childSlotForAnalog}. */
    static int slotForAnalog(float x, float y, int slotCount) {
        float mag = (float) Math.sqrt(x * x + y * y);
        if (mag < dev.steampad.input.StickSettleGate.REENGAGE_MAG) return -1;
        double angle = Math.atan2(y, x) + Math.PI / 2.0;
        if (angle < 0) angle += 2 * Math.PI;
        double sector = 2 * Math.PI / slotCount;
        return (int) ((angle + sector / 2) / sector) % slotCount;
    }

    /**
     * Which bloom child (0..count-1) the stick is pointing closest to â€” mirrors the EXACT fan
     * angles {@link dev.steampad.radial.RadialRenderer#bloomChildAngle} draws children at, via
     * NEAREST-ANGLE matching instead of {@link #slotForAnalog}'s even-sector division. Real bug this
     * fixes: after D133/D134 made the bloom ring fan out from the source category instead of always
     * covering a full circle, selection kept assuming the OLD full-circle-evenly-spaced layout â€”
     * pointing the stick up could resolve to a child actually drawn up-left, right could resolve to
     * one drawn up, etc. ("entorpece la navegaciÃ³n... apunto arriba y selecciona arriba-izquierda").
     * Nearest-angle matching is a strict generalization: for a FULL circle of evenly spaced points
     * it's mathematically identical to sector division (each point's Voronoi region on a circle IS
     * that sector), so this also covers the many-children case correctly, not just the fan case.
     * Returns -1 below the sticky threshold, same convention as {@link #slotForAnalog}. {@code step}
     * must be the SAME value {@link RadialRenderer#drawBloom} used to draw this frame's children â€”
     * see {@link #bloomStep()}, which derives it from this controller's own {@link RadialConfig}
     * (geometry-derived, not a fixed constant â€” feedback: "vuelve a como estaba distribuido", a
     * fixed step changed the actual spacing versus what the user had already tuned/confirmed).
     */
    static int childSlotForAnalog(float x, float y, double sourceAngle, int count, double step) {
        float mag = (float) Math.sqrt(x * x + y * y);
        if (mag < dev.steampad.input.StickSettleGate.REENGAGE_MAG || count <= 0) return -1;
        // NO "+PI/2" here, unlike slotForAnalog â€” that shift exists there only as an implementation
        // detail of its OWN 0-based bucket-index trick; its NET effect (verified against a reference
        // script before writing this) is that raw atan2(y,x) already equals the render-space chip
        // angle convention (-PI/2 = 12 o'clock, 0 = 3 o'clock) that bloomChildAngle returns â€” adding
        // the shift here would silently rotate every selection by 90Â°.
        double stickAngle = Math.atan2(y, x);
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            double dist = angularDistance(stickAngle, RadialRenderer.bloomChildAngle(sourceAngle, i, count, step));
            if (dist < bestDist) { bestDist = dist; best = i; }
        }
        return best;
    }

    /** Shortest distance (radians, always &ge;0, &le;Ï€) between two angles regardless of winding. */
    private static double angularDistance(double a, double b) {
        double d = Math.abs(a - b) % (2 * Math.PI);
        return d > Math.PI ? 2 * Math.PI - d : d;
    }

    /** A press: context-sensitive. On CATEGORY_PAGE, instantly opens the focused category â€” skips
     *  the dwell-fill wait for players who don't want to hold still (feedback round 4: "A la salta").
     *  On ITEM_MORPH/HOTBAR_PAGE, just closes (the live preview already applied the equip â€” see
     *  class doc, D127). */
    public static void pressConfirm() {
        switch (state) {
            case CATEGORY_PAGE -> enterCategory();
            case HOTBAR_PAGE, ITEM_MORPH -> close();
            case CLOSED -> {}
        }
    }

    /** B press: collapses the bloom back to the category ring, without closing the wheel. No-op at
     *  the top level â€” releasing the wheel's own bind is how the top level cancels, matching the
     *  other wheels. Re-focusing the SAME category afterward needs a fresh dwell (or A) â€” collapsing
     *  is a clean return, not an instant re-open. */
    public static void pressBack() {
        if (state != State.ITEM_MORPH) return;
        state = State.CATEGORY_PAGE;
        selectedChild = -1;
        morphCategory = null;
        categoryBacking = List.of();
        childSlots = List.of();
    }

    /** Closes the whole wheel. Whatever was last live-previewed (see class doc) stays equipped â€”
     *  there is nothing to roll back, browsing already applied it in real time. If the wheel is
     *  closing straight from CATEGORY_PAGE (bumper released without ever pressing A/dwell-opening),
     *  see {@link #autoEquipFocusedCategoryOnQuickClose} first â€” a quick tap should still be useful. */
    public static void close() {
        if (state == State.CATEGORY_PAGE) autoEquipFocusedCategoryOnQuickClose();
        state = State.CLOSED;
        selectedSlot = -1;
        selectedChild = -1;
        morphCategory = null;
        dwellIndex = -1;
        hotbarBacking = List.of();
        categoryBacking = List.of();
        childSlots = List.of();
        categorized = Map.of();
    }

    /**
     * Feedback round 2 ("rueda realmente inteligente"): releasing the bumper while still on
     * CATEGORY_PAGE (no A press to drill in) re-equips the last item remembered for the focused
     * category instead of doing nothing. No-op if no category is focused, it's empty, or nothing has
     * been picked from it yet this client session (first-ever use of a category still needs one
     * manual drill-in â€” there is nothing to "remember" before that).
     */
    private static void autoEquipFocusedCategoryOnQuickClose() {
        if (selectedSlot < 0 || selectedSlot >= ItemCategory.values().length) return;
        ItemCategory cat = ItemCategory.values()[selectedSlot];
        Item remembered = lastUsedPerCategory.get(cat);
        if (remembered == null) return;
        List<InventoryScanner.AggregatedItem> items = categorized.getOrDefault(cat, List.of());
        for (InventoryScanner.AggregatedItem item : items) {
            if (item.representative().getItem() == remembered) {
                equipAggregatedItemLive(cat, item);
                return;
            }
        }
    }

    /** Discards without executing â€” same shape as the other wheels' {@code dismiss()}. */
    public static void dismiss() { close(); }

    /** Opens the bloom for the focused category (dwell completing or A skipping it â€” see
     *  {@link #pressConfirm()}). The outer ring/{@link #selectedSlot} are left exactly as they are â€”
     *  they now represent the bloom's SOURCE chip (renders "selected" the whole time it's open, see
     *  {@link #getBloom()}) â€” only the child data/bloom state changes. No-op on an empty category
     *  (dimmed, not enterable) or if nothing is focused. */
    private static void enterCategory() {
        if (selectedSlot < 0 || selectedSlot >= ItemCategory.values().length) return;
        ItemCategory cat = ItemCategory.values()[selectedSlot];
        List<InventoryScanner.AggregatedItem> items = categorized.getOrDefault(cat, List.of());
        if (items.isEmpty()) return;   // dimmed/empty category â€” not enterable
        morphCategory = cat;
        categoryBacking = items;
        childSlots = new ArrayList<>();
        for (InventoryScanner.AggregatedItem item : items) {
            childSlots.add(itemSlot(item.representative(), item.totalCount()));
        }
        state = State.ITEM_MORPH;
        selectedChild = -1;
        dwellIndex = -1;              // dwell no longer relevant once bloomed
        activeMorphTargetSlot = -1;   // fresh browsing session â€” next equip advances the rotation
    }

    private static void liveSelectHotbar() {
        if (selectedSlot < 0 || selectedSlot >= hotbarBacking.size()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        InventoryCompat.setSelectedSlot(mc.player.getInventory(), hotbarBacking.get(selectedSlot).slotIndex());
    }

    /**
     * Equips {@code item} live. If it's ALREADY sitting in a real hotbar slot â€” any of 0-8, which
     * includes the player's own organized 1-6 â€” it's just SELECTED there, never moved (feedback
     * round 3: "si por error lo selecciono, debe respetar el que tengo asignado por hotbar" â€” the
     * player's layout must survive even an accidental focus on an item they already placed by
     * hand). Only when the item lives OUTSIDE the hotbar (main inventory or offhand) does it get
     * swapped in, and only into a ROTATING slot (never 1-6, see {@link #ROTATING_HOTBAR_SLOTS} and
     * the class doc).
     *
     * <p>The rotating target slot is assigned once per browsing session (lazily, on this method's
     * first ACTUAL swap since {@link #open}/{@link #enterCategory} last reset
     * {@link #activeMorphTargetSlot} to {@code -1} â€” an item that was already in the hotbar never
     * consumes a rotation) and reused for every later swap in that same session, so scrubbing
     * through a category doesn't jump the hand between different hotbar slots â€” only the NEXT fresh
     * session advances to the next rotating slot.
     *
     * <p>Re-resolves the item's CURRENT inventory slot fresh every call rather than trusting the
     * cached {@code AggregatedItem.topLevelSlot()} â€” a previous live swap this same browse session
     * may have already moved that item, so the cached index can go stale after the first swap (the
     * swapped-out item lands wherever the newly-equipped one used to be, not at its original slot).
     */
    private static void equipAggregatedItemLive(ItemCategory cat, InventoryScanner.AggregatedItem item) {
        if (!item.grabbable()) return;   // backpack-only copy â€” shown for awareness, not selectable
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        int freshSlot = findCurrentTopLevelSlot(mc.player, item.representative().getItem());
        if (freshSlot < 0) return;   // moved to a backpack or gone since the wheel opened
        lastUsedPerCategory.put(cat, item.representative().getItem());

        if (freshSlot < Inventory.getSelectionSize()) {
            // Already a real hotbar slot (0-8, includes 1-6) â€” select it, never swap it away.
            if (InventoryCompat.getSelectedSlot(mc.player.getInventory()) != freshSlot) {
                InventoryCompat.setSelectedSlot(mc.player.getInventory(), freshSlot);
            }
            return;
        }

        if (freshSlot == Inventory.SLOT_OFFHAND) {
            // The offhand is a DELIBERATE placement, exactly like the player's own 1-6 layout, so it
            // gets the same protection: focusing it in the wheel must not yank it into the hand
            // (reported: a torch held in the offhand was being moved into slot 7/8/9 on an accidental
            // pick). It only reached the swap below because SLOT_OFFHAND is 40 - outside the hotbar
            // range the guard above tests - even though it is just as much an "already equipped"
            // slot. There is nothing to select either: the offhand is not a selectable hotbar index
            // and the item is already equipped there, so the correct action is none at all.
            return;
        }

        if (activeMorphTargetSlot < 0) {
            activeMorphTargetSlot = ROTATING_HOTBAR_SLOTS[rotationCursor];
            rotationCursor = (rotationCursor + 1) % ROTATING_HOTBAR_SLOTS.length;
        }
        if (InventoryCompat.getSelectedSlot(mc.player.getInventory()) != activeMorphTargetSlot) {
            InventoryCompat.setSelectedSlot(mc.player.getInventory(), activeMorphTargetSlot);
        }
        int screenSlot = toScreenHandlerSlot(freshSlot);
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, screenSlot,
                activeMorphTargetSlot, ClickType.SWAP, mc.player);
    }

    private static int findCurrentTopLevelSlot(Player player, Item item) {
        for (InventoryStacks.IndexedStack s : InventoryStacks.indexedHotbarMainAndOffhand(player)) {
            if (s.stack().getItem() == item) return s.slotIndex();
        }
        return -1;
    }

    /** Converts a {@link Inventory}-space index (hotbar 0-8, main 9-35, offhand
     *  {@link Inventory#SLOT_OFFHAND}) to the {@link InventoryMenu} slot id
     *  {@code clickSlot} expects â€” DIFFERENT numbering for hotbar/offhand, confirmed via {@code javap}
     *  after {@link Inventory#pickSlot} crashed on offhand (it only indexes its own
     *  36-entry {@code main} list, not the screen handler's 46-slot space â€” real crash log, index 40
     *  out of bounds for length 36). {@code clickSlot} is also properly network-synced, unlike the
     *  client-only helper it replaces. */
    private static int toScreenHandlerSlot(int playerInventoryIndex) {
        if (playerInventoryIndex == Inventory.SLOT_OFFHAND) return InventoryMenu.SHIELD_SLOT;
        if (playerInventoryIndex < Inventory.getSelectionSize()) {
            return playerInventoryIndex + InventoryMenu.USE_ROW_SLOT_START;
        }
        return playerInventoryIndex;   // main inventory 9-35 â€” identical numbering in both spaces
    }

    private static void rescan(Player player) {
        List<InventoryScanner.HotbarEntry> hotbar = InventoryScanner.hotbarItems(player);
        hotbarBacking = hotbar;
        categorized = InventoryScanner.categorized(player);
    }

    /** Only ever called from {@link #open()} â€” CATEGORY_PAGE/ITEM_MORPH share the SAME outer slots
     *  (the category ring never leaves the screen, see the class doc), so there's nothing to reload
     *  when {@link #enterCategory()}/{@link #pressBack()} move between those two states. */
    private static void loadPageSlots() {
        switch (state) {
            case HOTBAR_PAGE -> loadHotbarSlots();
            case CATEGORY_PAGE, ITEM_MORPH -> loadCategorySlots();
            case CLOSED -> { slots = new ArrayList<>(); categoryBacking = List.of(); childSlots = List.of(); }
        }
    }

    private static void loadHotbarSlots() {
        List<RadialSlot> out = new ArrayList<>();
        for (InventoryScanner.HotbarEntry e : hotbarBacking) {
            out.add(itemSlot(e.stack()));
        }
        slots = out.isEmpty() ? List.of(RadialSlot.empty()) : out;
        categoryBacking = List.of();
    }

    private static void loadCategorySlots() {
        List<RadialSlot> out = new ArrayList<>();
        for (ItemCategory cat : ItemCategory.values()) {
            List<InventoryScanner.AggregatedItem> items = categorized.getOrDefault(cat, List.of());
            out.add(categorySlot(cat, items));
        }
        slots = out;
        categoryBacking = List.of();
    }

    // RadialActionType.ITEM (not NONE) is load-bearing here: RadialSlot.isEmpty() returns true for
    // ANY slot typed NONE regardless of its icon data, which silently ate every icon in this wheel
    // until it was caught on hardware â€” see RadialActionType.ITEM's own doc.
    private static RadialSlot itemSlot(ItemStack stack) {
        return itemSlot(stack, stack.getCount());
    }

    private static RadialSlot itemSlot(ItemStack stack, int displayCount) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String name = stack.getHoverName().getString();
        String label = displayCount > 1 ? name + " x" + displayCount : name;
        return new RadialSlot(RadialActionType.ITEM, itemId, "ITEM", itemId, label, false, displayCount);
    }

    private static RadialSlot categorySlot(ItemCategory cat, List<InventoryScanner.AggregatedItem> items) {
        String label = Component.translatable("steampad.itemradial.category." + cat.name().toLowerCase()).getString();
        if (items.isEmpty()) {
            return new RadialSlot(RadialActionType.NONE, "", "NONE", "", label, false);
        }
        String itemId = BuiltInRegistries.ITEM.getKey(categoryRepresentative(cat, items).representative().getItem()).toString();
        return new RadialSlot(RadialActionType.ITEM, "cat:" + cat.name(), "ITEM", itemId, label, false);
    }

    /** The category's icon prefers the last item actually picked from it (feedback round 3: "los
     *  iconos de inventario no se actualizan segÃºn la Ãºltima selecciÃ³n") â€” falls back to whatever
     *  the scan found first when nothing's been picked yet this client session, or the remembered
     *  item is no longer present (moved to a backpack, dropped, etc). */
    private static InventoryScanner.AggregatedItem categoryRepresentative(
            ItemCategory cat, List<InventoryScanner.AggregatedItem> items) {
        Item remembered = lastUsedPerCategory.get(cat);
        if (remembered != null) {
            for (InventoryScanner.AggregatedItem item : items) {
                if (item.representative().getItem() == remembered) return item;
            }
        }
        return items.get(0);
    }
}
