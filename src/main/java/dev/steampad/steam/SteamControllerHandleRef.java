package dev.steampad.steam;

import java.util.Objects;

/**
 * Immutable reference to a Steam Input controller handle with resolved metadata.
 */
public final class SteamControllerHandleRef {

    public static final SteamControllerHandleRef NONE = new SteamControllerHandleRef(0L, "None", ControllerType.UNKNOWN);

    public final long handle;
    public final String displayName;
    public final ControllerType type;

    public SteamControllerHandleRef(long handle, String displayName, ControllerType type) {
        this.handle = handle;
        this.displayName = displayName;
        this.type = type;
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
