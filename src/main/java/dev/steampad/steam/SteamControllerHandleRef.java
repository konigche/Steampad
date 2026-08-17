package dev.steampad.steam;

import java.util.Objects;

/**
 * Immutable reference to a Steam Input controller handle with resolved metadata.
 */
public final class SteamControllerHandleRef {

    public static final SteamControllerHandleRef NONE = new SteamControllerHandleRef(0L, "None", ControllerType.UNKNOWN);

    /** 8BitDo's USB vendor ID — stable across every 8BitDo product/mode, unlike the display name
     *  (e.g. an Ultimate 2 switched to its Xbox-compatible mode reports as a generic XInput device by
     *  name, but the VID never changes). Shared here so brand-cosmetic code (icon/texture folder) has
     *  one source of truth instead of re-deriving it. */
    public static final int EIGHTBITDO_VENDOR_ID = 0x2DC8;

    public final long handle;
    public final String displayName;
    public final ControllerType type;
    /** USB vendor/product ID, when the backend can read it (currently SDL3 only). 0 = unknown — GLFW
     *  and Steam Input refs always report 0 here; brand-cosmetic code must treat 0 as "no VID/PID
     *  signal available" and fall back to name-string sniffing, not as "vendor 0x0000". */
    public final int vendorId;
    public final int productId;
    /** USB bcdDevice / driver-reported product version, 0 when unknown. Recorded alongside VID/PID for
     *  {@link dev.steampad.service.ControllerIdentity}; not used to match on its own, because a pad can
     *  legitimately report a different version across driver stacks. */
    public final int productVersion;
    /**
     * Per-UNIT serial number, {@code ""} when the backend can't read one (always for GLFW and Steam
     * Input; for SDL3 only when the pad was opened through its HIDAPI driver). This is the ONLY signal
     * that separates two controllers of the same model, so identity resolution uses it when present —
     * but never depends on it, since the same pad reports one on HIDAPI and nothing on evdev.
     */
    public final String serial;

    public SteamControllerHandleRef(long handle, String displayName, ControllerType type) {
        this(handle, displayName, type, 0, 0);
    }

    public SteamControllerHandleRef(long handle, String displayName, ControllerType type, int vendorId, int productId) {
        this(handle, displayName, type, vendorId, productId, 0, "");
    }

    public SteamControllerHandleRef(long handle, String displayName, ControllerType type,
                                    int vendorId, int productId, int productVersion, String serial) {
        this.handle = handle;
        this.displayName = displayName;
        this.type = type;
        this.vendorId = vendorId;
        this.productId = productId;
        this.productVersion = productVersion;
        this.serial = serial == null ? "" : serial.trim();
    }

    public boolean isValid() {
        return handle != 0L;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SteamControllerHandleRef)) return false;
        return handle == ((SteamControllerHandleRef) o).handle;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(handle);
    }

    @Override
    public String toString() {
        return "ControllerRef[handle=" + handle + ", name=" + displayName + ", type=" + type + "]";
    }

    /** Identifies the controller category for icon/theme selection. */
    public enum ControllerType {
        UNKNOWN,
        XBOX,
        PLAYSTATION,
        STEAM_CONTROLLER,
        STEAM_DECK,
        SWITCH,
        GENERIC
    }
}
