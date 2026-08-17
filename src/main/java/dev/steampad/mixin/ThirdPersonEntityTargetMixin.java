package dev.steampad.mixin;

import dev.steampad.config.ConfigManager;
import dev.steampad.input.ThirdPersonCameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes "mira funcional" (shoot/attack/mine like first-person) actually redirect ENTITY targets, not
 * just blocks — found by reading {@code GameRenderer.findCrosshairTarget} via {@code javap} against
 * the mapped 1.21.10 jar (verified fact, not a guess): that method computes the block hit via
 * {@code cameraEntity.raycast(...)} (the method {@link ThirdPersonPickMixin} already redirects), but
 * computes the ENTITY hit as a completely SEPARATE step — {@code ProjectileUtil.raycast(cameraEntity,
 * from, to, box, ...)} where {@code from = cameraEntity.getCameraPosVec(tickDelta)} and the aim
 * direction comes from {@code cameraEntity.getRotationVec(tickDelta)} — both read fresh from the
 * PLAYER's real eye position/body-facing, completely bypassing whatever {@link ThirdPersonPickMixin}
 * returned. The method then keeps whichever of the two hits is CLOSER to {@code from}. Since our own
 * free-camera pick was only ever wired into the block-side candidate, and the entity-side candidate
 * kept measuring from (and searching along) the real body facing, entity attacks in third person never
 * reliably followed the free camera — they'd only "happen to" match it when the body already faced
 * roughly the same direction, exactly matching the report that combat "makes third person useless"
 * once the body stops tracking the camera (idle rotate mode NONE, or mid camera-relative-movement).
 *
 * <p>Fix, mirroring the architecture Leawind's Third-Person (MIT) actually uses for this exact problem
 * ({@code LocalPlayerMixin.modifyPickEntity}, a {@code @WrapOperation} over its own engine's entity-hit
 * call that substitutes the search ray's own start/direction): redirect the SAME two reads this method
 * makes on {@code cameraEntity} — {@code getCameraPosVec}/{@code getRotationVec} — to the free camera's
 * own position/look direction. This makes BOTH the distance comparison and the entity search box use
 * the free camera consistently with {@link ThirdPersonPickMixin}'s already-redirected block hit,
 * instead of mixing a free-camera block candidate against a real-body-facing entity candidate.
 *
 * <p>Every other caller of {@code Entity.getCameraPosVec}/{@code getRotationVec} elsewhere in the game
 * (rendering, other mods) is untouched — {@code @Redirect} only rewrites the call sites lexically
 * inside {@code findCrosshairTarget}'s own bytecode.
 */
@Mixin(GameRenderer.class)
public abstract class ThirdPersonEntityTargetMixin {

    private static boolean shouldRedirect(Entity entity) {
        if (!ThirdPersonCameraController.isFreeLookEnabled()) return false;
        if (!ConfigManager.getGlobal().thirdPersonCrosshairEnabled) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity != mc.player) return false;
        if (mc.options.getCameraType().isFirstPerson()) return false;
        // Mirror mode (D123): vanilla targeting semantics — see ThirdPersonPickMixin's own note.
        if (ThirdPersonCameraController.isMirrorMode(mc)) return false;
        // Mounted (D171): same reasoning as ThirdPersonPickMixin — isMounted() is now also one of
        // the reasons isFreeLookEnabled() can be true, so it's excluded here too.
        if (ThirdPersonCameraController.isMounted(mc)) return false;
        return true;
    }

    @Redirect(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 steampad$redirectCameraPos(Entity entity, float tickDelta) {
        if (shouldRedirect(entity)) {
            Vec3 free = ThirdPersonCameraController.currentCameraPos();
            if (free != null) return free;
        }
        return entity.getEyePosition(tickDelta);
    }

    @Redirect(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 steampad$redirectRotationVec(Entity entity, float tickDelta) {
        if (shouldRedirect(entity)) {
            Vec3 free = ThirdPersonCameraController.currentLookVec();
            if (free != null) return free;
        }
        return entity.getViewVector(tickDelta);
    }
}
