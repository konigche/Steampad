package dev.steampad.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the recipe book's search field so the virtual keyboard can see it at all.
 *
 * <p><b>Why an accessor is the only way.</b> The recipe book IS a child of the crafting screen
 * ({@code addWidget(this.recipeBookComponent)}), but {@code RecipeBookComponent} implements
 * {@code GuiEventListener} and <b>not</b> {@code ContainerEventHandler} — verified with {@code javap}
 * on the mapped jars of both target versions. So it has no {@code children()} for
 * {@code VirtualKeyboard}'s recursive widget walk to descend into, and its {@code searchBox} field is
 * {@code private}. The walk therefore stopped at the book itself and the search field was invisible
 * to SteamPad: no eligibility, no "[A] Teclado" badge, no auto-open — exactly the report "no sale el
 * teclado en la parte de busqueda del libro de crafteo".
 *
 * <p><b>One accessor covers both versions.</b> The class moved package never, kept the name
 * {@code RecipeBookComponent}, and kept a {@code private EditBox searchBox} field on both 1.21.1
 * (concrete class) and 1.21.10 (generic abstract base of
 * {@code Crafting/FurnaceRecipeBookComponent}) — javap-confirmed on both mapped jars, which is why
 * this needs no version branch.
 */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookSearchAccessor {

    @Accessor("searchBox")
    EditBox steampad$searchBox();
}
