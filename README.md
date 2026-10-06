# Reading List

Keeps track of the books people recommend to you: who recommended each one,
why, and what you thought once you'd read it. It replaces a long-running
Notion page. Part of the personal Android app suite, distributed via
[Groom Hub](https://github.com/MatejGroombridge/personal-app-store-frontend).

See [PLAN.md](PLAN.md) for the design: what the Notion page did and where each
part lives in the app.

## Features

- **Three tabs:** Reading · To Read (the landing tab) · Read. Swipe between
  them, as in Habit Tracker.
- **Quick capture:** a + button on every tab, plus:
  - the share sheet ("Add to Reading List")
  - the text-selection menu
  - a launcher shortcut
  - a home-screen widget
- **Recommendation context:** who recommended it, why, and notes. Recommenders
  you've used before are one-tap chips. You can group the list by recommender.
- **Open Library lookup:** suggestions appear as you type and fill in the
  title, author, cover, page count and year. Settings → Fetch Missing Details
  fills in older entries.
- **Duplicate warning** while typing, and **search** across every status.
- **Shelves:** your own categories, each with an icon and colour. Item types
  cover books, series, authors, topics, articles and other people's lists.
- **Up Next** pins items to the top of To Read. **To Get** marks items you
  still need a copy of. **Pick for Me** chooses something at random.
- **Reading progress:** ±10 pages at a time, a finish dialog with rating and
  takeaways, a yearly goal with pace, per-shelf stats, and an optional limit
  on how many books you read at once.
- **Bulk Add** for pasted lists, a **Notebook** for free-form notes, a daily
  reading reminder, and an archive.
- **Backups:** JSON export and import (merge or replace). The library is also
  included in Android auto-backup.

## Build

Requires JDK 17, Android SDK 35.

```bash
./gradlew :app:assembleDebug
```

Unit tests cover the parsing, matching and list-query logic:

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

The helper:

- bumps `versionName` and `versionCode` in `app/build.gradle.kts`
- prepends a new entry to `CHANGELOG.md`
- commits, tags `vX.Y.Z` and pushes

The push triggers `.github/workflows/release.yml`, which builds and signs the
APK, attaches it to a GitHub Release and patches the central manifest. Within
about 3 minutes the Groom Hub app on your phone offers the new version.

## AI Agent

[`agent.md`](agent.md) is the suite-wide guide for AI coding agents (and human
developers). It covers architecture, conventions, the design language, build
config, signing and the release workflow.

## Repo layout

```
.
├── .github/workflows/release.yml   ← release pipeline
├── app/src/main/java/dev/matejgroombridge/readinglist/
│   ├── MainActivity.kt             ← nav host + three-tab pager
│   ├── QuickAddActivity.kt         ← share / text-selection / shortcut capture
│   ├── data/                       ← models, DataStore repository, Open Library client, settings
│   ├── domain/                     ← parsing, matching, sorting/grouping, stats (pure Kotlin)
│   ├── notifications/              ← daily reading reminder
│   ├── ui/                         ← screens, components, theme
│   └── widget/                     ← Glance home-screen widget
├── app/src/test/                   ← unit tests for domain/
├── bin/changeset                   ← interactive release helper
├── CHANGELOG.md
├── PLAN.md                         ← product plan / Notion → app mapping
└── gradle/libs.versions.toml       ← dependency catalog
```
