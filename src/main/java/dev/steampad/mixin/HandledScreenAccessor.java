package dev.steampad.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the top-left origin of a container screen so the virtual cursor can compute slot centers
 * for soft "snap-to-slot" magnetism (Bedrock-style inventory navigation).
 */
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {

    @Accessor("leftPos")
    int steampad$getX();

    @Accessor("topPos")
    int steampad$getY();

    // GUI panel size — with x/y this gives the container's screen rect, used to decide whether a
    // mod-owned button's stored coordinates are ABSOLUTE screen px or RELATIVE to the panel origin
    // (Traveler's Backpack stores them relative — verified in its 1.21.10-fabric source, its own
    // inButton() subtracts getGuiLeft()/getGuiTop() before hit-testing). Field names verified with
    // javap against the mapped 1.21.10 jar: backgroundWidth / backgroundHeight.

    @Accessor("imageWidth")
    int steampad$getBackgroundWidth();

    @Accessor("imageHeight")
    int steampad$getBackgroundHeight();
}
