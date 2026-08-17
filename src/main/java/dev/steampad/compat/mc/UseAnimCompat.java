package dev.steampad.compat.mc;

import net.minecraft.world.item.ItemStack;

/**
 * Version shim for the item-use animation enum, renamed in 1.21.2:
 * {@code net.minecraft.world.item.UseAnim} (&le; 1.21.1) became {@code ItemUseAnimation} (&ge; 1.21.2).
 *
 * <p>Deliberately exposes <em>questions</em> rather than the enum itself. A conditional import would
 * not be enough — the simple name appears at every comparison site
 * ({@code action == ItemUseAnimation.BOW}), so the type would leak into three subsystems and need a
 * conditional at each one. Asking "is this a ranged aim?" keeps the renamed type contained here and
 * leaves the call sites reading as intent instead of as version plumbing.
 *
 * <p>Constant names themselves ({@code BOW}, {@code CROSSBOW}, {@code SPEAR}, {@code EAT},
 * {@code DRINK}) are identical in both versions — only the enclosing type was renamed.
 */
public final class UseAnimCompat {

    private UseAnimCompat() {}

    /**
     * Whether using this stack means aiming a ranged weapon — bow, crossbow or trident. Drives the
     * aim assist and the shoulder-camera pull-in.
     */
    public static boolean isRangedAim(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        //? if >=1.21.2 {
        var action = stack.getUseAnimation();
        return action == net.minecraft.world.item.ItemUseAnimation.BOW
                || action == net.minecraft.world.item.ItemUseAnimation.CROSSBOW
                || action == net.minecraft.world.item.ItemUseAnimation.SPEAR;
        //?} else {
        /*var action = stack.getUseAnimation();
        return action == net.minecraft.world.item.UseAnim.BOW
                || action == net.minecraft.world.item.UseAnim.CROSSBOW
                || action == net.minecraft.world.item.UseAnim.SPEAR;
        *///?}
    }

    /** Whether using this stack means eating or drinking — drives the consume haptic. */
    public static boolean isConsuming(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        //? if >=1.21.2 {
        var action = stack.getUseAnimation();
        return action == net.minecraft.world.item.ItemUseAnimation.EAT
                || action == net.minecraft.world.item.ItemUseAnimation.DRINK;
        //?} else {
        /*var action = stack.getUseAnimation();
        return action == net.minecraft.world.item.UseAnim.EAT
                || action == net.minecraft.world.item.UseAnim.DRINK;
        *///?}
    }
}
