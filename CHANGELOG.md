# Changelog

All notable changes to Shelf are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

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

[Unreleased]: https://github.com/ThalzeronLabs/compose-showcase/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/ThalzeronLabs/compose-showcase/releases/tag/v1.0.0
