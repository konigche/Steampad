package dev.steampad.input;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two pieces of the screen↔group link that are pure decision.
 *
 * <p>The segment match is what keeps a short mod id from linking a screen that has nothing to do with
 * it, and the strings below are the REAL ones from the reporter's installed mods. The rest of the link
 * (which group a keybind belongs to, which screen is open) needs a live client and is covered in
 * hardware.
 */
class ModKeybindContextTest {

    private static final String BACKPACK_SCREEN =
            "com.tiviacz.travelersbackpack.client.screens.backpackscreen";

    @Test
    @DisplayName("a mod's screen class carries its group id — the real class from the installed jar")
    void screenClassCarriesItsGroup() {
        assertTrue(ModKeybindContext.nameCarries(BACKPACK_SCREEN, "travelersbackpack"));
    }

    @Test
    @DisplayName("another mod's screen never links: a chest is not a backpack")
    void otherScreensDoNotLink() {
        assertFalse(ModKeybindContext.nameCarries(
                "net.minecraft.client.gui.screens.inventory.containerscreen", "travelersbackpack"));
        assertFalse(ModKeybindContext.nameCarries(BACKPACK_SCREEN, "punchy"));
    }

    @Test
    @DisplayName("a short id may not match inside a longer word — 'tide' is not 'inventory'")
    void shortIdsDoNotMatchInsideWords() {
        assertFalse(ModKeybindContext.nameCarries(
                "net.minecraft.client.gui.screens.inventory.inventoryscreen", "tide"),
                "the substring is there, but not as a segment");
        assertTrue(ModKeybindContext.nameCarries("com.example.tide.client.tidescreen", "tide"),
                "as its own package segment it does match");
    }

    @Test
    @DisplayName("an empty group never links anything — vanilla keybinds are not scoped")
    void emptyGroupNeverLinks() {
        assertFalse(ModKeybindContext.nameCarries(BACKPACK_SCREEN, ""));
    }

    @Test
    @DisplayName("what a screen claims is remembered, and only what it claims")
    void screenOwnershipIsLearned() {
        assertFalse(ModKeybindContext.isScreenOwned("key.punchy.triggerable.sword_inspect"));
        ModKeybindContext.rememberScreenHandled("key.travelersbackpack.sort");
        assertTrue(ModKeybindContext.isScreenOwned("key.travelersbackpack.sort"));
        assertFalse(ModKeybindContext.isScreenOwned("key.punchy.triggerable.sword_inspect"));
        ModKeybindContext.rememberScreenHandled(null);   // must not blow up or claim anything
        ModKeybindContext.rememberScreenHandled("");
        assertFalse(ModKeybindContext.isScreenOwned(""));
    }
}
