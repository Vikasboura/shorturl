package com.amazon.shortlink.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortCodeGeneratorTest {

    private ShortCodeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ShortCodeGenerator();
    }

    @Test
    @DisplayName("Should generate 7-character string by default")
    void testDefaultLength() {
        String code = generator.generate();
        assertThat(code).hasSize(7);
        assertThat(code).matches("^[0-9a-zA-Z]{7}$");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 7, 10, 15})
    @DisplayName("Should generate string of requested length")
    void testCustomLength(int length) {
        String code = generator.generate(length);
        assertThat(code).hasSize(length);
        assertThat(code).matches("^[0-9a-zA-Z]{" + length + "}$");
    }

    @Test
    @DisplayName("Should reject invalid lengths")
    void testInvalidLength() {
        assertThatThrownBy(() -> generator.generate(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should generate unique codes with high entropy (no collisions across 1,000 samples)")
    void testEntropy() {
        Set<String> uniqueCodes = new HashSet<>();
        int count = 1000;
        for (int i = 0; i < count; i++) {
            uniqueCodes.add(generator.generate());
        }
        assertThat(uniqueCodes).hasSize(count);
    }

    @ParameterizedTest
    @ValueSource(strings = {"my-alias", "campaign2026", "deal_prime", "aws123", "Short_Code"})
    @DisplayName("Should accept valid custom aliases")
    void testValidCustomAliases(String alias) {
        assertThat(generator.isValidCustomAlias(alias)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "a", "too-long-alias-that-exceeds-thirty-characters-limit-12345", "bad char", "bad@char", "api", "shorten", "admin", "metrics", "health"})
    @DisplayName("Should reject invalid or reserved custom aliases")
    void testInvalidOrReservedAliases(String alias) {
        assertThat(generator.isValidCustomAlias(alias)).isFalse();
    }
}
