package dev.steampad.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Exposes {@code ModelPart}'s private {@code cuboids} list so {@code EmoteAnimator} can reach each
 * cuboid's {@code BendableCuboid} (see {@code BendableCuboidMixin}) without reflection. Read-only,
 * thin accessor — no logic (CLAUDE.md restriction 4).
 */
@Mixin(ModelPart.class)
public interface ModelPartCuboidsAccessor {

    @Accessor("cubes")
    List<ModelPart.Cube> steampad$getCuboids();
}
