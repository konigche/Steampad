package dev.steampad.input;

/**
 * Captures the current game context to determine which ActionSet to activate
 * and which input bindings are relevant this tick.
 */
public final class InputDispatchContext {

    public final boolean hasScreenOpen;
    public final boolean isInGame;
    public final boolean isRadialOpen;
    public final boolean isVMouseActive;
    public final String screenClass; // simple class name for debug

    public InputDispatchContext(boolean hasScreenOpen, boolean isInGame,
                                boolean isRadialOpen, boolean isVMouseActive,
                                String screenClass) {
        this.hasScreenOpen = hasScreenOpen;
        this.isInGame = isInGame;
        this.isRadialOpen = isRadialOpen;
        this.isVMouseActive = isVMouseActive;
        this.screenClass = screenClass;
    }

    public static InputDispatchContext inGame() {
        return new InputDispatchContext(false, true, false, false, "null");
    }

    public static InputDispatchContext inScreen(String screenClass) {
        return new InputDispatchContext(true, false, false, false, screenClass);
    }

    @Override
    public String toString() {
        return "DispatchContext[screen=" + hasScreenOpen + ", inGame=" + isInGame +
               ", radial=" + isRadialOpen + ", vmouse=" + isVMouseActive + "]";
    }
}
