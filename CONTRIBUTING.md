# Contributing to Shelf

Thanks for taking the time! Shelf is a reference app, so a contribution is welcome when it makes the app a better
example of how to build Android apps, not only when it adds features. Small, focused pull requests are the easiest
to review.

Looking for a place to start? Issues labelled
[`good first issue`](https://github.com/ThalzeronLabs/compose-showcase/labels/good%20first%20issue) are scoped,
point at the files involved and say when they are done. Comment on one to claim it.

## Setup

You need JDK 17+ and the Android SDK (Android Studio installs both). No API key is needed: the app uses the public
Open Library API.

```bash
git clone --recursive https://github.com/ThalzeronLabs/compose-showcase
cd compose-showcase
./gradlew :app:installDebug
```

Forgot `--recursive`? Run `git submodule update --init`: the bottom sheets come from the
[compose-bottom-sheets](https://github.com/ThalzeronLabs/compose-bottom-sheets) submodule.

## Before you open a pull request

```bash
./gradlew :app:verifyRoborazziDebug   # every unit test, plus the screenshot comparison (what CI runs)
./gradlew :app:assembleRelease        # the R8 build must still work
```

- **Changed the UI on purpose?** Re-record the screenshots with `./gradlew :app:recordRoborazziDebug` and commit the
  updated images in `docs/screenshots`. Reviewers look at the image diff on GitHub.
- **Changed a Room entity?** Bump the database version, commit the new schema JSON that the build writes to
  `app/schemas`, add a migration (an `AutoMigration` when Room can infer it) and a case in `ShelfMigrationTest`.
- **Added a dependency?** Put the version in `gradle/libs.versions.toml` and say in the PR why it is worth its size;
  the README tracks the release APK size.
- **New behaviour needs a test.** ViewModels are tested with Turbine and fakes on the plain JVM; anything that touches
  Room or Android runs under Robolectric. No emulator is needed for any test.

## Code style

- Kotlin official code style (`kotlin.code.style=official`); keep lines under about 120 characters.
- One source of truth: screens read state from a ViewModel `StateFlow` and send intents back. Keep composables that
  render a screen stateless (`SearchContent`, `ShelfContent`) so previews and screenshot tests can use them.
- Comments explain *why*, not *what*.
- Code, comments, commits and docs are in English.

## Commits and pull requests

- Write commit subjects in the imperative ("Add sort order to the shelf"), under about 70 characters.
- Fill in the pull request template; link the issue with `Closes #123`.
- Add a line under `## [Unreleased]` in `CHANGELOG.md` for anything a user would notice.
- Pull requests are squash-merged, so the PR title becomes the commit on `main`.

## Releases

Maintainers bump `versionName` in `app/build.gradle.kts`, move the `Unreleased` notes in `CHANGELOG.md` under the new
version and push a `vX.Y.Z` tag. The release workflow then builds, signs and attaches the APK.

## Reporting bugs and ideas

Use the issue templates. For a bug, the Android version, the device and the steps to reproduce make all the
difference. Please do not include personal data in screenshots or logs.

By contributing you agree that your contributions are licensed under the [Apache-2.0 License](LICENSE).
