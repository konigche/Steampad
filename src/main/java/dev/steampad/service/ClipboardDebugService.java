package dev.steampad.service;

import dev.steampad.steam.SteamRuntimeDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Generates the debug dump and copies it to the system clipboard.
 */
public final class ClipboardDebugService {

    private ClipboardDebugService() {}

    public static void copyToClipboard() {
        String dump = SteamRuntimeDiagnostics.generateDump();
        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard(dump);
        if (mc.player != null) {
            mc.player.displayClientMessage(
                Component.literal("§a[SteamPad] §f").append(Component.translatable("steampad.notification.debug_copied")),
                true
            );
        }
    }
}
