package com.amazon.shortlink.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62EncoderTest {

    @Test
    @DisplayName("Should encode zero correctly")
    void testEncodeZero() {
        assertThat(Base62Encoder.encode(0)).isEqualTo("0");
        assertThat(Base62Encoder.decode("0")).isZero();
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 61, 62, 12345, 9876543210L, Long.MAX_VALUE / 2})
    @DisplayName("Should encode and decode back to original value (round-trip)")
    void testRoundTrip(long original) {
        String encoded = Base62Encoder.encode(original);
        assertThat(encoded).isNotEmpty();
        long decoded = Base62Encoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    @DisplayName("Should throw exception for negative values")
    void testNegativeValue() {
        assertThatThrownBy(() -> Base62Encoder.encode(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be non-negative");
    }

    @Test
    @DisplayName("Should throw exception for invalid Base62 characters")
    void testInvalidCharacters() {
        assertThatThrownBy(() -> Base62Encoder.decode("abc$123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Base62 character");

        assertThatThrownBy(() -> Base62Encoder.decode(""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Base62Encoder.decode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
