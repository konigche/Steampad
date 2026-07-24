package dev.steampad.steam;

import com.codedisaster.steamworks.SteamController;
import com.codedisaster.steamworks.SteamControllerHandle;
import dev.steampad.util.LogUtil;

/**
 * Sends vibration / haptic feedback to the active controller via ISteamController.
 */
public final class SteamHapticsService {

    private SteamHapticsService() {}

    /**
     * Sends a rumble pulse. leftSpeed and rightSpeed are in range [0, 0xFFFF].
     */
    public static void triggerVibration(long handle, int leftSpeed, int rightSpeed) {
        if (!SteamBootstrap.isInputAvailable()) return;
        SteamController raw = SteamInputManager.getRawController();
        if (raw == null) return;
        try {
            raw.triggerVibration(new SteamControllerHandle(handle),
                    (short) leftSpeed, (short) rightSpeed);
        } catch (Throwable t) {
            LogUtil.debug("triggerVibration failed: {}", t.getMessage());
        }
    }

    /**
     * Convenience: fire a pulse with intensity in [0.0, 1.0].
     */
    public static void pulse(long handle, float intensity) {
        int scaled = (int) (Math.min(1f, Math.max(0f, intensity)) * 0xFFFF);
        triggerVibration(handle, scaled, scaled);
    }

    /**
     * Sends asymmetric haptic: separate left motor, right motor.
     */
    public static void triggerHaptic(long handle, float leftMotor, float rightMotor,
                                     float leftTrigger, float rightTrigger) {
        // ISteamController only exposes triggerVibration (no trigger-motor separation).
        // Blend trigger intensities into motor for reasonable haptic approximation.
        float left  = Math.min(1f, leftMotor  + leftTrigger  * 0.5f);
        float right = Math.min(1f, rightMotor + rightTrigger * 0.5f);
        triggerVibration(handle, (int)(left * 0xFFFF), (int)(right * 0xFFFF));
    }
}
