package dev.steampad.service;

import dev.steampad.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Plays UI navigation sounds when the user navigates mod screens. */
public final class UiSoundService {

    private UiSoundService() {}

    public static void playNavigate() {
        if (!ConfigManager.getGlobal().uiSounds) return;
        play(0.3f);
    }

    public static void playSelect() {
        if (!ConfigManager.getGlobal().uiSounds) return;
        play(0.5f);
    }

    public static void playBack() {
        if (!ConfigManager.getGlobal().uiSounds) return;
        playBack(0.3f);
    }

    private static void play(float volume) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(
            SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, volume));
    }

    private static void playBack(float volume) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(
            SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, volume));
    }
}
