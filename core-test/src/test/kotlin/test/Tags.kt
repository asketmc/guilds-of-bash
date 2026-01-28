package test

import org.junit.jupiter.api.Tag

/**
 * Meta-annotations for JUnit5 test prioritization.
 *
 * Priority levels (exclusive per test class, unless overridden per method):
 * - P0: Core/heart - app won't start or core loop unusable
 * - P1: Feature broken / critical regression
 * - P2: Normal correctness (non-critical)
 * - P3: Low value / edge / long tail (non-perf)
 *
 * Execution tags (orthogonal):
 * - smoke: Fastest subset (should run inside PR gate)
 * - perf: Performance / load; excluded from PR and push
 */

/**
 * P0: Core/heart - app won't start or core loop unusable.
 * Critical tests that must pass for the application to function at all.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("p0")
annotation class P0

/**
 * P1: Feature broken / critical regression.
 * Tests for critical features that must work correctly.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("p1")
annotation class P1

/**
 * P2: Normal correctness (non-critical).
 * Tests for important but non-critical features.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("p2")
annotation class P2

/**
 * P3: Low value / edge / long tail (non-perf).
 * Tests for edge cases or low-priority scenarios.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("p3")
annotation class P3

/**
 * Perf: Performance / load tests.
 * Excluded from PR and push workflows, run only in nightly/weekly.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("perf")
annotation class Perf

/**
 * Smoke: Marker annotation for ultra-fast PR smoke tests.
 * Use on test classes or test methods to mark them as part of the smoke suite.
 * Retained at runtime so JUnit5 can pick it up via tags.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("smoke")
annotation class Smoke

