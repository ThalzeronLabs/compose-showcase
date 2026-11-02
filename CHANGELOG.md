# Changelog

All notable changes to Shelf are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- **Accessibility.** Each book is one item for a screen reader, with its state ("Reading", "Not on your shelf") and
  what a tap does; the cover is no longer read and the badge is not read twice. Shelf sections are headings, the
  search field is labelled, and the Offline indicator, errors and the saved-results banner are announced. From a
  150% system font size the badge moves under the text, so titles, authors and years are no longer cut off.
- Accessibility tests with Google's Accessibility Test Framework (labels, touch targets, contrast) on both screens
  and their bottom sheets, plus checks of what a screen reader hears; screenshots at a 200% font size.
- Compose interaction tests for both screens, run under Robolectric with the real ViewModels, Paging and bottom
  sheets: searching and adding a book through the sheet, the empty, error and offline states, and moving and
  removing books (including cancelling a removal).
- Tests for `SettingsStore` against a real DataStore file: the default, a choice surviving a restart, and an unknown
  stored value falling back to the system theme.

### Fixed

- Secondary text in the light theme (authors, years, hints) had a contrast of 4.47:1, below WCAG AA; it is now
  at least 5.3:1.

## [1.1.1] - 2026-11-02

Same code as 1.1.0, released again so that the tag matches the APK.

### Fixed

- The `v1.1.0` tag points at the merge of #8, one commit before the app's Baseline Profile was added (#9), while the
  APK attached to the 1.1.0 release was built after it. 1.1.1 is that code plus the profile, with the tag and the APK
  built from the same commit.

### Changed

- Releases are now signed with one fixed showcase demo key instead of a new one-off key each time, so from 1.1.1 on
  each version installs over the previous one. If 1.1.0 or older is installed, uninstall it once first. The release
  workflow also rebuilds an existing tag from that tag's own commit, so the APK always matches its tag.

## [1.1.0] - 2026-11-02

Offline-first, paging, background sync and a modular build.

### Added

- **Paging 3** on the search list: Room serves the pages and a `RemoteMediator` fills Room from Open Library as you
  scroll. If the connection drops between pages, the loaded results stay and a "Couldn't load more results" row offers
  a retry that resumes at the same page.
- **Background sync with WorkManager**: the books on your shelf get fresh catalogue data from Open Library (title,
  authors, year, cover) after you save one and every 12 hours, only with a network and with exponential backoff.
  Your reading status is never touched.
- An **Offline** indicator in the top bar that follows the device's network.
- A **Baseline Profile** in the release APK, installed by `profileinstaller`, and a Macrobenchmark module that measures
  cold start-up with and without it. A workflow generates the profile and runs the benchmark on an emulator.
- Tests for the mediator (including a connection dropping between pages), the sync (including a sync cut off
  halfway), the worker's retry rules and scheduling, connectivity and the 2 → 3 migration: 63 tests in all.

### Changed

- **Room is now the single source of truth for search too**: results are always read from the database, so whatever
  was loaded before shows at once, also offline. When a refresh fails, the banner says the results are the ones saved
  on this device.
- Room schema version 3 adds `search_remote_keys` and `books.syncedAt`, with an auto-migration. Shelves from 1.0.0
  are kept.
- The code is split into modules: `:core:data`, `:core:ui`, `:core:testing`, `:feature:search`, `:feature:shelf` and
  `:benchmark`.
- CI runs every module's tests and builds the benchmark module.

## [1.0.0] - 2026-11-01

First public release.

### Added

- Search the Open Library catalogue as you type: debounced, stale requests cancelled, with distinct loading, empty and error states.
- A local shelf in Room with three lists (*Want to read*, *Reading*, *Finished*); search results show which books are already on it.
- **Offline search**: the last results for each query are cached in Room and shown, with an "offline" banner, when Open Library cannot be reached.
- Light, dark and system themes, persisted with DataStore.
- Questions asked with awaitable bottom sheets from [compose-bottom-sheets](https://github.com/ThalzeronLabs/compose-bottom-sheets).
- Room schema version 2 (adds `search_cache`) with an auto-migration from version 1, covered by migration tests.
- Screenshot tests with Roborazzi on the JVM; the images in `docs/screenshots` are the README's screenshots and CI fails if they drift.
- Tests for the offline cache against a real in-memory Room database, and more ViewModel tests with Turbine.
- A release workflow that builds the R8-minified APK, signs it with a throwaway demo key and attaches it to the GitHub release for every `v*` tag.
- `CONTRIBUTING.md`, issue and pull request templates.

[Unreleased]: https://github.com/ThalzeronLabs/compose-showcase/compare/v1.1.1...HEAD
[1.1.1]: https://github.com/ThalzeronLabs/compose-showcase/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/ThalzeronLabs/compose-showcase/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/ThalzeronLabs/compose-showcase/releases/tag/v1.0.0
