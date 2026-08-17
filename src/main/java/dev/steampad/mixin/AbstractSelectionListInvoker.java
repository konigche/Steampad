package dev.steampad.mixin;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes vanilla's own {@code protected} "scroll this entry into view" method on
 * {@link AbstractSelectionList}, so D-pad navigation of a vanilla entry list (world select, server
 * list, resource packs…) scrolls the list the way keyboard navigation does.
 *
 * <p><b>Why this is needed at all</b> — established by disassembling the real mapped jars, not
 * assumed. {@code AbstractSelectionList.setFocused(GuiEventListener)} already selects the entry, and
 * on 1.21.1 it also scrolls… but only behind an input-type check:
 *
 * <pre>
 *   setSelected(entry);
 *   if (minecraft.getLastInputType().isKeyboard()) ensureVisible(entry);   // 1.21.1
 * </pre>
 *
 * SteamPad drives the cursor by injecting real mouse movement, so the last input type is never
 * "keyboard" while playing on a pad — the selection moved but the list never scrolled (reported:
 * "si selecionas si baja, pero visualmente no baja la lista solo la selección"). On 1.21.10
 * {@code setFocused} does not scroll at all. Calling the scroll method explicitly, right after
 * {@code setFocused}, fixes both versions without depending on that input-type check and without
 * mutating vanilla's global input-type state.
 *
 * <p><b>Version boundary</b> — the method was renamed, but its descriptor is identical. Verified via
 * {@code javap -s} on both mapped jars:
 * <ul>
 *   <li>1.21.1 — {@code protected void ensureVisible(E)}</li>
 *   <li>1.21.10 — {@code protected void scrollToEntry(E)}</li>
 * </ul>
 * both erasing to {@code (Lnet/minecraft/client/gui/components/AbstractSelectionList$Entry;)V}. Only
 * the name is conditional; the signature is shared. An {@code @Invoker} name that does not exist in
 * the target fails at STARTUP, not at build time, so these two names are the load-bearing part.
 *
 * <p>Pure accessor — adds no logic and changes no behaviour on its own (CLAUDE.md restriction 4).
 */
@Mixin(AbstractSelectionList.class)
public interface AbstractSelectionListInvoker {

    //? if >=1.21.9 {
    @Invoker("scrollToEntry")
    void steampad$scrollToEntry(AbstractSelectionList.Entry<?> entry);
    //?} else {
    /*@Invoker("ensureVisible")
    void steampad$scrollToEntry(AbstractSelectionList.Entry<?> entry);
    *///?}
}
