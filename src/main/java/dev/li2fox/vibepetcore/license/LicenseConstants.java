package dev.li2fox.vibepetcore.license;

import java.util.Locale;
import java.util.Set;

/**
 * Встроенные константы лицензии — не настраиваются через config,
 * чтобы нельзя было подменить API или публичный ключ.
 */
public final class LicenseConstants {
    public static final String PRODUCT_ID = "vibepetcore";
    public static final String SERVER_URL = "https://license.vibecraft.one";
    public static final String PUBLIC_KEY = "7BmofxOdqS/n2aLu36MG9q8p3IQ/thGTklyMO0SjDhQ=";

    private static final Set<String> PLACEHOLDER_KEYS = Set.of(
            "",
            "XXXX-XXXX-XXXX-XXXX",
            "YOUR-LICENSE-KEY",
            "PUT-YOUR-LICENSE-KEY-HERE",
            "CHANGE-ME"
    );

    private LicenseConstants() {
    }

    public static boolean isPlaceholderKey(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String normalized = key.trim().toUpperCase(Locale.ROOT);
        if (PLACEHOLDER_KEYS.contains(normalized)) {
            return true;
        }
        return normalized.contains("XXXX") || normalized.contains("YOUR-LICENSE");
    }
}
