package dev.li2fox.vibepetcore.license;

public enum LicenseStatus {
    VALID,
    INVALID_LICENSE,
    LICENSE_DISABLED,
    LICENSE_EXPIRED,
    PRODUCT_MISMATCH,
    TOO_MANY_ACTIVATIONS,
    SERVER_REVOKED,
    IP_NOT_ALLOWED,
    SERVER_ERROR;

    public boolean isValid() {
        return this == VALID;
    }

    public static LicenseStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return SERVER_ERROR;
        }
        try {
            return LicenseStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return SERVER_ERROR;
        }
    }
}
