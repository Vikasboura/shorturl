package com.amazon.shortlink.algorithm;

/**
 * Interface defining contract for short code generation and alias validation.
 * Adheres to Dependency Inversion Principle (DIP).
 */
public interface CodeGenerator {

    String generate();

    String generate(int length);

    boolean isValidCustomAlias(String alias);
}
