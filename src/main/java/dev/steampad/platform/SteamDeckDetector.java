package dev.steampad.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SteamDeckDetector {

    private SteamDeckDetector() {}

    public static boolean detect() {
        // Env var check
        String envVal = System.getenv("SteamDeck");
        if ("1".equals(envVal)) return true;

        // Linux DMI product name check
        Path dmiPath = Path.of("/sys/class/dmi/id/product_name");
        if (Files.exists(dmiPath)) {
            try {
                String product = Files.readString(dmiPath).trim();
                return product.contains("Jupiter") || product.contains("Galileo");
            } catch (IOException ignored) {}
        }
        return false;
    }
}
