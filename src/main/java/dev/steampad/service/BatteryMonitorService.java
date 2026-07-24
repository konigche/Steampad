package dev.steampad.service;

import dev.steampad.config.ConfigManager;
import dev.steampad.steam.SteamInputManager;
import dev.steampad.util.LogUtil;
import dev.steampad.util.TimeUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Monitors battery level of the active controller and notifies the player
 * when it drops below a threshold.
 */
public final class BatteryMonitorService {

    private static final float LOW_BATTERY_THRESHOLD = 0.2f;
    private static final long CHECK_INTERVAL_MS = 60_000L; // check every 60 seconds

    private static long lastCheckMs = 0L;
    private static boolean lowBatteryNotified = false;

    private BatteryMonitorService() {}

    /** Called every game tick. */
    public static void tick() {
        if (!ConfigManager.getGlobal().notifyLowBattery) return;
        if (!TimeUtil.hasElapsed(lastCheckMs, CHECK_INTERVAL_MS)) return;
        lastCheckMs = TimeUtil.nowMs();

        long activeHandle = ActiveControllerService.getActiveHandle();
        if (activeHandle == 0L) return;

        SteamInputManager.getState(activeHandle).ifPresent(state -> {
            float battery = state.battery;
            if (battery < 0f) return; // unknown battery level

            if (battery <= LOW_BATTERY_THRESHOLD && !lowBatteryNotified) {
                notifyLowBattery((int) (battery * 100));
                lowBatteryNotified = true;
            } else if (battery > LOW_BATTERY_THRESHOLD) {
                lowBatteryNotified = false; // reset so we notify again if it drops
            }
        });
    }

    private static void notifyLowBattery(int percentageInt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(
                Text.literal("§e[SteamPad] §f").append(Text.translatable("steampad.notification.low_battery", percentageInt)),
                true // actionbar, not chat
            );
            LogUtil.info("Low battery notification sent: {}%", percentageInt);
        }
    }
}
