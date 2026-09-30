package com.amazon.shortlink.algorithm;

/**
 * High-performance Base62 Encoder/Decoder.
 * 
 * Uses standard alphanumeric character set:
 * [0-9] (0-9), [a-z] (10-35), [A-Z] (36-61)
 * 
 * Total combinations for 7 characters = 62^7 = 3,521,614,606,208 (~3.52 Trillion).
 */
public final class Base62Encoder {

    private static final String BASE62_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = BASE62_CHARS.length(); // 62
    private static final int[] DECODE_TABLE = new int[128];

    static {
        for (int i = 0; i < DECODE_TABLE.length; i++) {
            DECODE_TABLE[i] = -1;
        }
        for (int i = 0; i < BASE62_CHARS.length(); i++) {
            DECODE_TABLE[BASE62_CHARS.charAt(i)] = i;
        }
    }

    private Base62Encoder() {
        // Utility class
    }

    /**
     * Encodes a non-negative 64-bit integer into Base62.
     *
     * @param value Non-negative integer
     * @return Base62 encoded string
     * @throws IllegalArgumentException if value is negative
     */
    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value must be non-negative: " + value);
        }
        if (value == 0) {
            return "0";
        }

        StringBuilder sb = new StringBuilder();
        long current = value;
        while (current > 0) {
            int remainder = (int) (current % BASE);
            sb.append(BASE62_CHARS.charAt(remainder));
            current /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string back into a 64-bit integer.
     *
     * @param base62Str Base62 encoded string
     * @return Decoded 64-bit integer
     * @throws IllegalArgumentException if string is null, empty, or contains invalid characters
     */
    public static long decode(String base62Str) {
        if (base62Str == null || base62Str.isEmpty()) {
            throw new IllegalArgumentException("Base62 string must not be null or empty");
        }

        long result = 0;
        for (int i = 0; i < base62Str.length(); i++) {
            char c = base62Str.charAt(i);
            if (c >= DECODE_TABLE.length || DECODE_TABLE[c] == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            int digit = DECODE_TABLE[c];
            result = result * BASE + digit;
        }
        return result;
    }
}
