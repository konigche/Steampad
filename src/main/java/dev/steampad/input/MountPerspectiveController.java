package dev.steampad.input;

import dev.steampad.config.ConfigManager;
import dev.steampad.config.GlobalConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Auto-switches to third person the instant the local player boards anything rideable (horse, boat,
 * minecart, a mod's own mount/vehicle - {@link net.minecraft.world.entity.Entity#isPassenger()} is a
 * base Entity contract every one of them satisfies identically), and back to first person on
 * dismount, so driving doesn't start out blind from inside the player's own head. Configurable
 * (feedback: request came with its own opt-out) via {@link GlobalConfig#thirdPersonAutoSwitchOnMount},
 * gated behind the master {@link GlobalConfig#thirdPersonCameraEnabled} switch like every other row in
 * the "Mejor Tercera Persona" section (D124).
 *
 * <p>Deliberately mirrors {@code EmoteAnimator}'s already-proven playLocal/restorePerspectiveIfNeeded
 * shape instead of inventing a second mechanism for the same kind of problem: an
 * {@code autoSwitchedPerspective} flag remembers whether THIS class was the one that left first
 * person, so a revert never fights a perspective the player picked themselves mid-ride, and the actual
 * camera cut is eased through {@link PerspectiveTransition} exactly like the emote and manual-cycle
 * cases already are.
 */
public final class MountPerspectiveController {

    private MountPerspectiveController() {}

    /** Last tick's raw passenger state - edge-detected against every tick's fresh read to find the
     *  mount/dismount transition (the {@code wasRealEmotePlaying} pattern EmoteAnimator already uses
     *  for the same kind of one-shot transition). */
    private static boolean wasPassenger = false;
    /** True while the local player is in third person because THIS class put them there - see
     *  EmoteAnimator's own field of the same name for the exact same "don't fight a perspective the
     *  player picked themselves" contract. */
    private static boolean autoSwitchedPerspective = false;
    /** Set on dismount when a revert is owed; consumed once {@link PerspectiveTransition} has a free
     *  slot rather than forced the same tick, so a revert can never be lost just because some other
     *  transition (an emote easing out, a manual cycle) happened to already be running at that exact
     *  moment - it simply waits and fires on a later tick instead. */
    private static boolean pendingRestore = false;

    /** Once per client tick from SteamPadClient, alongside EmoteAnimator.clientTick. */
    public static void clientTick(Minecraft mc) {
        if (mc.player == null) {
            wasPassenger = false;
            autoSwitchedPerspective = false;
            pendingRestore = false;
            return;
        }

        boolean isPassenger = mc.player.isPassenger();
        if (isPassenger && !wasPassenger) {
            onMount(mc);
        } else if (!isPassenger && wasPassenger) {
            onDismount();
        }
        wasPassenger = isPassenger;

        if (pendingRestore && !PerspectiveTransition.isActive()) {
            pendingRestore = false;
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
                PerspectiveTransition.beginExitToFirstPerson(CameraType.THIRD_PERSON_BACK);
            }
            // else: perspective already changed some other way (the player picked one) - nothing to do.
        }
    }

    private static void onMount(Minecraft mc) {
        GlobalConfig g = ConfigManager.getGlobal();
        if (!g.thirdPersonCameraEnabled || !g.thirdPersonAutoSwitchOnMount) return;
        // Never fights a screen (matches EmoteAnimator.playLocal's own guard), and only ever hops OUT
        // of first person - a player already in third person (including mirror mode) picked that
        // themselves, and mounting shouldn't override it.
        if (mc.screen != null || mc.options.getCameraType() != CameraType.FIRST_PERSON) return;

        Vec3 eye = mc.player.getEyePosition();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        autoSwitchedPerspective = true;
        PerspectiveTransition.beginEnter(eye);
    }

    private static void onDismount() {
        if (autoSwitchedPerspective) pendingRestore = true;
        autoSwitchedPerspective = false;
    }
}
