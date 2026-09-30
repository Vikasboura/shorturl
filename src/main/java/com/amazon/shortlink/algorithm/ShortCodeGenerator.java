package com.amazon.shortlink.algorithm;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Generates cryptographically secure, collision-resilient short codes
 * and validates custom vanity aliases.
 */
@Component
public class ShortCodeGenerator implements CodeGenerator {

    private static final String BASE62_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int DEFAULT_CODE_LENGTH = 7;
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,30}$");
    
    // Reserved system paths that cannot be claimed as custom aliases
    private static final Set<String> RESERVED_KEYWORDS = Set.of(
            "api", "stats", "shorten", "health", "metrics", "actuator",
            "admin", "docs", "swagger", "swagger-ui", "v3", "favicon.ico"
    );

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a random Base62 short code of default length (7 characters).
     *
     * @return 7-character Base62 string
     */
    public String generate() {
        return generate(DEFAULT_CODE_LENGTH);
    }

    /**
     * Generates a random Base62 short code of specified length.
     *
     * @param length Number of characters (must be >= 1)
     * @return Base62 string
     */
    public String generate(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("Length must be at least 1");
        }
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(BASE62_ALPHABET.length());
            chars[i] = BASE62_ALPHABET.charAt(index);
        }
        return new String(chars);
    }

    /**
     * Validates whether a custom alias meets format constraints and is not a reserved system path.
     *
     * @param alias The custom vanity alias
     * @return true if valid and unreserved; false otherwise
     */
    public boolean isValidCustomAlias(String alias) {
        if (alias == null || alias.trim().isEmpty()) {
            return false;
        }
        String normalized = alias.trim().toLowerCase();
        if (RESERVED_KEYWORDS.contains(normalized)) {
            return false;
        }
        return ALIAS_PATTERN.matcher(alias).matches();
    }
}
