# Quick Test Reference Card

## 🚀 Common Commands

```bash
# Run all P1 critical tests
./gradlew ciTest

# Run specific test class
./gradlew :core-test:test --tests "P1_ReducerCriticalTest"

# Run specific test method
./gradlew :core-test:test --tests "P1_ReducerCriticalTest.step increments revision exactly once"

# Run all tests with detailed output
./gradlew :core-test:test --info

# Force rerun tests (ignore up-to-date)
./gradlew :core-test:test --rerun-tasks

# Build any module (auto-runs tests)
./gradlew :core:build
./gradlew :adapter-console:build

# Clean and rebuild everything
./gradlew clean build
```

## 📊 Test Suite Overview

| File | Tests | Purpose |
|------|-------|---------|
| `P1_ReducerCriticalTest.kt` | 13 | Core game loop |
| `P1_GameStateInitializationTest.kt` | 8 | Valid startup |
| `P1_SerializationTest.kt` | 12 | Save/load |
| `P1_HashingTest.kt` | 13 | Replays |
| `P1_InvariantVerificationTest.kt` | 14 | Corruption detection |
| `P1_CommandValidationTest.kt` | 10 | Command safety |
| **TOTAL** | **70** | **All critical paths** |

## 🎯 What's Tested

### ✅ Will Catch These Bugs

- ❌ Game crashes on AdvanceDay
- ❌ State corruption after invalid command
- ❌ Save file corruption
- ❌ Replay desync
- ❌ Negative money/trophies
- ❌ ID collisions
- ❌ Invalid initial state
- ❌ Event ordering bugs
- ❌ Business rule violations

### ⚠️ Won't Catch These (Yet)

- UI bugs
- Performance issues
- Multi-day scenario bugs
- Race conditions (not applicable - single-threaded)

## 📁 Test Files Location

```
Guilds-of-Bash/
├── core-test/
│   ├── src/test/kotlin/test/
│   │   ├── P1_ReducerCriticalTest.kt
│   │   ├── P1_GameStateInitializationTest.kt
│   │   ├── P1_SerializationTest.kt
│   │   ├── P1_HashingTest.kt
│   │   ├── P1_InvariantVerificationTest.kt
│   │   └── P1_CommandValidationTest.kt
│   └── build/
│       ├── test-results/test/  (XML results)
│       └── reports/tests/test/index.html  (HTML report)
└── P1_TEST_IMPLEMENTATION_SUMMARY.md  (full docs)
```

## 🔍 Test Results

**View HTML report:**
```
open core-test/build/reports/tests/test/index.html
```

**Check XML results:**
```
cat core-test/build/test-results/test/*.xml
```

**Quick status:**
```bash
find core-test/build/test-results -name "*.xml" | \
  xargs grep -h "testsuite name" | \
  sed 's/.*name="\([^"]*\)".*tests="\([^"]*\)".*failures="\([^"]*\)".*/\1: \2 tests, \3 failures/'
```

## 🐛 Debugging Failed Tests

**Get detailed failure info:**
```bash
./gradlew :core-test:test --info | grep -A 20 "FAILED"
```

**Run single failing test:**
```bash
./gradlew :core-test:test --tests "ClassName.test method name" --info
```

**Enable stack traces:**
```bash
./gradlew :core-test:test --stacktrace
```

## 🔧 CI/CD Setup

**GitHub Actions:**
```yaml
- name: Run Tests
  run: ./gradlew ciTest
```

**GitLab CI:**
```yaml
test:
  script:
    - ./gradlew ciTest
```

**Jenkins:**
```groovy
sh './gradlew ciTest'
```

## ⚡ Performance

- **Execution time:** ~100ms for all 70 tests
- **Fast enough for:** Every commit, pre-push hooks, CI on every PR
- **Not needed for:** Watch mode (too fast anyway)

## 📈 Current Status

```
✅ 70 tests
✅ 0 failures
✅ 100% pass rate
✅ Runs on every build
✅ CI/CD ready
```

## 🆘 Troubleshooting

**Tests not running?**
```bash
# Check test discovery
./gradlew :core-test:test --dry-run

# Clean build cache
./gradlew clean

# Check test task exists
./gradlew tasks --group verification
```

**Build fails but tests pass?**
```bash
# Tests might be passing but compile errors exist
./gradlew :core:compileKotlin
```

**Need to skip tests temporarily?**
```bash
# NOT RECOMMENDED for CI, but useful for quick iterations
./gradlew build -x test
```

## 📚 Related Documentation

- **Full test docs:** `P1_TEST_IMPLEMENTATION_SUMMARY.md`
- **Detailed test coverage:** `core-test/TEST_SUMMARY.md`
- **Spec documents:** K9.1-K12 specs (original implementation specs)

---

**Last Updated:** 2026-01-18
**Test Count:** 70
**Pass Rate:** 100%
**Status:** ✅ PRODUCTION READY
