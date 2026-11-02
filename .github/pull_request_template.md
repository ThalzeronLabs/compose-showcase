## What and why

<!-- What does this change, and why? Link the issue: Closes #123 -->

## How it was tested

<!-- New or changed tests, and anything you checked by hand on a device. -->

## Checklist

- [ ] `./gradlew testDebugUnitTest -Proborazzi.test.verify=true` passes (every module, including screenshots)
- [ ] `./gradlew :app:assembleRelease :benchmark:assembleBenchmarkRelease` still builds
- [ ] UI changed on purpose: screenshots re-recorded with `./gradlew :app:recordRoborazziDebug` and committed
- [ ] Room entity changed: version bumped, new schema in `core/data/schemas`, migration and `ShelfMigrationTest` case added
- [ ] `CHANGELOG.md` updated under `Unreleased` if users will notice the change
