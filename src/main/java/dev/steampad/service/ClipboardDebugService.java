package dev.steampad.service;

import dev.steampad.steam.SteamRuntimeDiagnostics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Generates the debug dump and copies it to the system clipboard.
 */
public final class ClipboardDebugService {

    private ClipboardDebugService() {}

    public static void copyToClipboard() {
        String dump = SteamRuntimeDiagnostics.generateDump();
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.keyboard.setClipboard(dump);
        if (mc.player != null) {
            mc.player.sendMessage(
                Text.literal("§a[SteamPad] §f").append(Text.translatable("steampad.notification.debug_copied")),
                true
            );
        }
    }
}
