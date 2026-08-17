# Reading List

A reading list that sorts itself. Search a book, tap Want to Read, and it
lands in the right genre section without you filing anything. Queue up what
you're reading next, keep a note of who recommended each book, and log what
you've finished.

Part of the personal Android app suite, distributed via
[Groom Hub](https://github.com/MatejGroombridge/personal-app-store-frontend).

## What it does

- **Import a list** — paste an existing document of titles, one per line
  (bullets, numbering and "Title by Author" all work). Every line is looked up,
  you review the matches, and the whole lot lands on the shelf sorted by genre.
  This is the migration path off a plain document.
- **Search books** — Open Library's catalogue, no API key required.
- **Automatic genre sorting** — books are filed into one of 26 curated genre
  sections by a rule-based classifier that scores Open Library's (very noisy)
  subject tags. Anything it can't call confidently goes to **Unsorted** rather
  than being guessed at. Every book can be re-filed by hand.
- **Up Next** — a priority queue for the books you actually intend to read
  next, reorderable with up / down / move-to-top.
- **Where it came from** — a free-text note plus a source tag (YouTube,
  Podcast, Friend, …) on every book, so you never lose track of why a title is
  on the list. The shelf filters by source, so "what did I add off YouTube and
  still haven't read?" is one tap.
- **Filter the shelf** — free-text over title, author and the recommendation
  note.
- **Read shelf** — finished books with 5-star ratings, plus totals for books,
  pages, and books read this year.

## Architecture

```
data/
├── model/        Book, Genre catalogue, RecSource catalogue
├── network/      Ktor client + Open Library API and DTOs
├── repository/   BookRepository — the shelf, one JSON blob in DataStore
└── settings/     Settings + SettingsRepository
domain/
└── GenreClassifier.kt    subject tags → genre section
ui/
├── components/   BookRow, BookCover, BookDetailSheet, StarRating, Confetti
├── screens/      Library, UpNext, Read, Search, ImportList, Settings
├── theme/        Theme, Type, BookColors, GenreIcons
├── LibraryViewModel.kt   shelf state, grouping, filtering, all mutations
├── SearchViewModel.kt    debounced Open Library search
├── ImportViewModel.kt    paste → parse → match → review → bulk add
└── SettingsViewModel.kt
```

State is `ViewModel` + `StateFlow`; persistence is a single JSON blob per
DataStore, decoded with `ignoreUnknownKeys` so new fields never break an
existing shelf.

### Why Open Library and not Google Books

Google Books now rejects keyless requests (HTTP 429, daily quota of zero), and
an API key can't be committed to a public repo or shipped inside an APK
safely. Open Library needs no key and has no quota. The trade-off is much
messier genre metadata, which is what `GenreClassifier` exists to clean up.

## Build

Requires JDK 17, Android SDK 35.

```bash
./gradlew :app:assembleDebug
```

Run the genre classifier tests:

```bash
./gradlew :app:testDebugUnitTest
```

For a signed release build, set up `keystore.properties` at the repo root:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=main
keyPassword=...
```

then `./gradlew :app:assembleRelease`.

## Release

Cut a new version with the changeset helper:

```bash
./bin/changeset
```

It bumps `versionName` + `versionCode` in `app/build.gradle.kts`, prepends a
new entry to `CHANGELOG.md`, commits, tags `vX.Y.Z`, and pushes — which
triggers `.github/workflows/release.yml` to build, sign, attach the APK to a
GitHub Release, and patch the central manifest. Within ~3 minutes the Groom
Hub app on your phone offers the new version.

## AI Agent

This repo includes an [`agent.md`](agent.md) with a full guide for AI coding
agents (and human developers) building on top of the project template —
covering architecture, conventions, build config, signing, the release
workflow, and more.
