Summary (<500 chars)
Split TestHelpers.kt into TestApi.kt, EventAssertions.kt, InvariantAssertions.kt, LockedBoardHelpers.kt, TrophyPipelineHelpers.kt, PerfHelpers.kt, SealedReflectionAssertions.kt. Restored economy helpers and fixed imports. :core-test compiled and its tests executed successfully.

Next steps (short plan)
- Run full repo tests: ./gradlew.bat test (or :core-test:test again) and capture failures.
- Remove the compatibility stub TestHelpers.kt if no references remain.
- Run ./gradlew.bat clean test (handle locked build folders if needed).
- Tidy imports / run linter and commit on branch split/test-helpers.
