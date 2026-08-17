package dev.steampad.config;

/**
 * Visual layout ("skin") of the radial wheel — orthogonal to {@link PixelTheme} (which only picks
 * colors). Both axes are independently configurable per controller: any skin can be painted with any
 * theme. Adding a new skin later means adding a constant here plus a case in
 * {@code RadialRenderer.render()}'s dispatch — nothing else in the codebase needs to know skins exist,
 * since every wheel (Menú Radial, Emotes, Item Radial) already renders through that one shared method.
 */
public enum RadialSkin {
    /** Round chips on a donut ring — the wheel's original look, unchanged. */
    CLASSIC,
    /** Trapezoid slices forming a full ring around a center hub, selected slice highlighted with a
     *  filled color + halo border, per-icon stack-count badges — matches the reference mock the user
     *  provided for an item/inventory-style wheel. */
    SEGMENTED
}
