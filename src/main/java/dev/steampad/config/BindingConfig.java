package dev.steampad.config;

import dev.steampad.input.*;
import java.util.*;

/**
 * Serializable binding configuration.
 * Stores bindings as a map of action ID → binding descriptor.
 */
public final class BindingConfig {

    public Map<String, BindingEntry> bindings = new LinkedHashMap<String, BindingEntry>();

    public static final class BindingEntry {
        public String type;          // "simple" or "chord"
        public String button;        // DigitalAction name for simple
        public String modifier;      // DigitalAction name for chord modifier
        public String main;          // DigitalAction name for chord main

        public static BindingEntry ofSimple(ControllerState.DigitalAction button) {
            BindingEntry e = new BindingEntry();
            e.type = "simple";
            e.button = button.name();
            return e;
        }

        public static BindingEntry ofChord(ControllerState.DigitalAction modifier,
                                           ControllerState.DigitalAction main) {
            BindingEntry e = new BindingEntry();
            e.type = "chord";
            e.modifier = modifier.name();
            e.main = main.name();
            return e;
        }
    }

    public List<InputBinding> toInputBindings() {
        List<InputBinding> result = new ArrayList<InputBinding>();
        for (Map.Entry<String, BindingEntry> entry : bindings.entrySet()) {
            InputAction action = findAction(entry.getKey());
            if (action == null) continue;
            BindingEntry be = entry.getValue();
            if ("chord".equals(be.type) && be.modifier != null && be.main != null) {
                try {
                    var mod = ControllerState.DigitalAction.valueOf(be.modifier);
                    var main = ControllerState.DigitalAction.valueOf(be.main);
                    result.add(InputBinding.chord(action, new ChordInput(mod, main)));
                } catch (IllegalArgumentException ignored) {}
            } else if ("simple".equals(be.type) && be.button != null) {
                try {
                    var btn = ControllerState.DigitalAction.valueOf(be.button);
                    result.add(InputBinding.simple(action, btn));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return result;
    }

    private static InputAction findAction(String id) {
        for (InputAction a : InputAction.values()) {
            if (a.id.equals(id) || a.name().equals(id)) return a;
        }
        return null;
    }

    public static BindingConfig defaults() {
        BindingConfig cfg = new BindingConfig();
        // Default bindings: map Steam Input digital actions to gameplay actions
        cfg.bindings.put(InputAction.JUMP.id,       BindingEntry.ofSimple(ControllerState.DigitalAction.JUMP));
        cfg.bindings.put(InputAction.SNEAK.id,      BindingEntry.ofSimple(ControllerState.DigitalAction.SNEAK));
        cfg.bindings.put(InputAction.ATTACK.id,     BindingEntry.ofSimple(ControllerState.DigitalAction.ATTACK));
        cfg.bindings.put(InputAction.USE.id,        BindingEntry.ofSimple(ControllerState.DigitalAction.USE));
        cfg.bindings.put(InputAction.SPRINT.id,     BindingEntry.ofSimple(ControllerState.DigitalAction.SPRINT));
        cfg.bindings.put(InputAction.PAUSE.id,      BindingEntry.ofSimple(ControllerState.DigitalAction.PAUSE));
        cfg.bindings.put(InputAction.INVENTORY.id,  BindingEntry.ofSimple(ControllerState.DigitalAction.INVENTORY));
        cfg.bindings.put(InputAction.NEXT_HOTBAR.id, BindingEntry.ofSimple(ControllerState.DigitalAction.NEXT_HOTBAR));
        cfg.bindings.put(InputAction.PREV_HOTBAR.id, BindingEntry.ofSimple(ControllerState.DigitalAction.PREV_HOTBAR));
        cfg.bindings.put(InputAction.OPEN_RADIAL.id, BindingEntry.ofSimple(ControllerState.DigitalAction.OPEN_RADIAL));
        cfg.bindings.put(InputAction.GUI_PRESS.id,   BindingEntry.ofSimple(ControllerState.DigitalAction.GUI_PRESS));
        cfg.bindings.put(InputAction.GUI_BACK.id,    BindingEntry.ofSimple(ControllerState.DigitalAction.GUI_BACK));
        cfg.bindings.put(InputAction.GUI_NAV_UP.id,   BindingEntry.ofSimple(ControllerState.DigitalAction.GUI_NAV_UP));
        cfg.bindings.put(InputAction.GUI_NAV_DOWN.id, BindingEntry.ofSimple(ControllerState.DigitalAction.GUI_NAV_DOWN));
        cfg.bindings.put(InputAction.GUI_NAV_LEFT.id, BindingEntry.ofSimple(ControllerState.DigitalAction.GUI_NAV_LEFT));
        cfg.bindings.put(InputAction.GUI_NAV_RIGHT.id,BindingEntry.ofSimple(ControllerState.DigitalAction.GUI_NAV_RIGHT));
        cfg.bindings.put(InputAction.TOGGLE_VMOUSE.id, BindingEntry.ofSimple(ControllerState.DigitalAction.TOGGLE_VMOUSE));
        return cfg;
    }
}
