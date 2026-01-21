package test

import org.junit.jupiter.api.Tag

/**
 * Marker annotation for ultra-fast PR smoke tests.
 * Use on test classes or test methods to mark them as part of the smoke suite.
 * Retained at runtime so JUnit5 can pick it up via tags.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("smoke")
annotation class Smoke
