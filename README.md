# Shelf — a modern Android reference app

**A small, complete reading-list app built the way production Android apps should be built in 2026:**
Jetpack Compose, MVVM with a single source of truth, Hilt, Room, Retrofit + Moshi, Coil, Navigation, DataStore,
unit tests with Turbine, and CI.

Search millions of books (the public [Open Library](https://openlibrary.org/developers/api) API, no key needed), add
them to your shelf, and move them between *Want to read*, *Reading* and *Finished*.

<!-- Screenshots go here -->

## Architecture

```
UI (Compose)          SearchScreen · ShelfScreen · bottom sheets (compose-bottom-sheets)
   │  StateFlow<UiState>, one per screen
ViewModels (Hilt)     SearchViewModel: debounce → flatMapLatest → Loading/Results/Empty/Error
   │                  ShelfViewModel: shelf grouped by status
Repository            ShelfRepository: network for search, Room as the single source of truth for the shelf
   │
Data                  Retrofit + Moshi (codegen) · Room (KSP, exported schema) · DataStore (theme)
```

**Decisions worth noting**

- **Search is reactive, not imperative.** The query is a `StateFlow`; `debounce(400 ms)` + `distinctUntilChanged` +
  `flatMapLatest` cancel stale requests automatically. A retry counter re-runs the same query without hacks.
- **Results know what is already on the shelf** by `combine`-ing the network result with the Room flow, so the badge
  updates the instant a book is saved, with no manual refresh.
- **Loading, empty and error are distinct states**, each with its own UI, instead of one `isLoading` boolean.
- **Questions use awaitable bottom sheets** (`sheets.choose(...)`, `sheets.confirm(...)`) from my
  [compose-bottom-sheets](https://github.com/ThalzeronLabs/compose-bottom-sheets) library, so a two-step flow
  (pick an action → confirm removal) reads as two lines of sequential code.
- **Release builds are minified with R8** and still work: Moshi uses generated adapters, Room and Hilt use KSP.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

| Suite | What it proves |
|---|---|
| `ShelfRepositoryTest` | API → domain mapping (trimming, missing fields, cover URLs), shelf ordering, status changes |
| `ViewModelsTest` | debounce collapses typing into one request, short queries never hit the network, empty vs error states, retry, shelf grouping |

Fakes replace the network and the database, so the suite runs on the plain JVM in seconds.

## Build

```bash
git clone --recursive https://github.com/ThalzeronLabs/compose-showcase
./gradlew :app:assembleRelease
```

## License

Apache-2.0 © Esdras Souza (Thalzeron Labs). Book data and covers © Open Library contributors.
