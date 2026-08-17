package dev.steampad.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code KeyMapping.clickCount} so a controller action can trigger a TAP-style keybind that
 * has <b>no physical key assigned at all</b>.
 *
 * <p><b>The gap this closes.</b> Mod actions come in two flavours and SteamPad could only reach one:
 * <ul>
 *   <li><b>Held</b> actions poll {@code isDown()}. {@link dev.steampad.input.KeyTap} already drives
 *       that with {@code kb.setDown(true)}, a public per-instance setter — works unbound.</li>
 *   <li><b>Tap</b> actions poll {@code consumeClick()}, which is the overwhelmingly common shape for
 *       "open my screen" keybinds. It reads and decrements this private counter, and the ONLY vanilla
 *       way to raise it is the static {@code KeyMapping.click(InputConstants.Key)} — which looks the
 *       mapping up by PHYSICAL KEY in the static {@code MAP}. An unbound keybind is in no such map
 *       entry, so the counter could never move and the action could never fire.</li>
 * </ul>
 *
 * <p>Verified with {@code javap} on the mapped jars of both target versions: {@code consumeClick()}
 * is {@code if (clickCount == 0) return false; clickCount--; return true;} and the static
 * {@code click(Key)} is {@code MAP.get(key).clickCount++} — so writing this field is exactly, and
 * only, what a real key press does. Confirmed against a real consumer too: Traveler's Backpack's
 * {@code KeybindHandler} runs {@code while (OPEN_BACKPACK.consumeClick()) { … }} — with the key
 * unbound that loop never entered, which is the reported "necesita una tecla asignada".
 *
 * <p><b>Per-instance is also a correctness fix, not only a reach fix.</b> The static {@code click(Key)}
 * drives whichever mapping happens to own that key in {@code MAP} — one entry per physical key, last
 * writer wins — so with two keybinds sharing a key it can fire the wrong one. That caveat is already
 * documented on {@code KeyTap.hold} for exactly this call; addressing the field on the instance the
 * caller actually holds removes it.
 *
 * <p>Same field name on both versions ({@code clickCount} → intermediary {@code field_1661},
 * javap-confirmed on both), so no version branch is needed.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingClickAccessor {

    @Accessor("clickCount")
    int steampad$getClickCount();

    @Accessor("clickCount")
    void steampad$setClickCount(int clickCount);
}
