# Reading List — Plan

Replaces the Notion "Reading List" page with a phone-first app in the Groom Hub
suite. It reuses the Habit Tracker's design language: pastel identity colours,
20dp cards, the settings card layout, haptics, confetti and the three-page pager.

## What the Notion page did, and where it goes

| Notion pattern | In the app |
|---|---|
| "Current Reads" | **Reading** tab, with page progress and a start date |
| "Reading List" | **To Read** tab (the landing page) |
| "Books I've Read" grouped by category | **Read** tab, grouped by year, with stats |
| Sections like "from angela:", "charlie morgan:" | A **From** field (who recommended it) with one-tap suggestions; the To Read list can be grouped by recommender |
| Inline asides like "(guidebook for life - favourite book)" | A **Why** field, shown as a quote in the book's overview |
| Reading tips like "plow through until the talking fish" | **Notes** |
| Dated headings like "19/11/25", "27 Jul 24" | An **added** date on every item; can group by month added |
| "2026:" priority list | **Up Next** star, pinned to the top |
| "Download" section | **Need a copy** flag, with a "To Get" filter |
| "authors:", "all robert greene books", "choose one 1800s tycoon" | Item **types**: Book, Series, Author, Topic, Article, Reading List |
| "People's Lists" (Jim Kwik, Nat Eliason, Modern Wisdom) | A Reading List item with a **link** |
| "Articles (on reMarkable)" | An Article item with a link |
| Categories (Self Improvement, Fiction, Theology…) | **Shelves**: user-editable, each with its own icon and colour |
| Free-form notes ("read as much as you can…") | A **Notebook** page |

## Low-friction capture

1. **FAB on every tab.** The new item's status follows the tab you're on.
   Quick-add shows only Title, Author, From, Why and Status. "More details"
   opens the rest.
2. **Share sheet.** Share a link or text from any app (Goodreads, Amazon,
   YouTube, a message) and a quick-add dialog opens over the app you were in.
   "Title - Author" and "Title by Author" are parsed, and links are kept.
3. **Text selection.** Highlight a title anywhere, then pick "Add to Reading
   List" from the selection menu (`ACTION_PROCESS_TEXT`).
4. **Launcher shortcut.** Long-press the icon and pick "Add a Book".
5. **Home-screen widget.** Shows current reads with progress and has a "+"
   button.
6. **Online lookup (Open Library, no API key).** Suggestions appear as you type.
   Picking one fills in the canonical title, author, cover, page count and year.
   The lookup can be turned off.
7. **Duplicate warning.** "Already on your list · To Read" appears as you type,
   so you don't add the same book twice.
8. **Bulk Add.** Paste many lines ("Title - Author", bullets and numbering are
   fine), then set status, shelf and From once for the whole batch. Good for
   a friend's list or a pasted Notion section.

## Screens

- **To Read** (landing page). Search, Pick for Me (a random pick weighted
  toward Up Next) and settings sit in the header. Filter chips: All, Up Next,
  To Get, each shelf, each non-book type. A sort and group menu: group by
  None, Shelf, Recommender, Month added or Type; sort by Recent, Oldest,
  Title or Author.
- **Reading.** Cards with a progress bar. A soft limit on how many books you
  read at once gives a nudge before you start another one.
- **Read.** Yearly goal progress, stat tiles (this year, all time, pages this
  year), a shelf breakdown, and books grouped by year finished. A collapsible
  "Didn't Finish" section sits at the bottom.
- **Book overview** (tap a card). A cover or shelf badge, title, author, and
  status, type and shelf chips. Then the **Why** quote with who recommended it,
  notes and a link. Below that are status actions: Start Reading, ±10 pages,
  Finished, Didn't Finish, and star rating.
- **Long-press a card** for a quick-action menu: start, finish, Up Next,
  edit, archive.
- **Finished dialog.** A star rating, finish date and takeaways, then confetti.
- **Search.** Searches every status at once, matching title, author, From,
  Why and notes, and ignores accents.
- **Settings**, in the suite's standard order:
  - Appearance: theme, AMOLED, covers.
  - Reminders: a daily reading nudge.
  - Reading: yearly goal and current-reads limit.
  - General: swipe navigation, online lookup, Shelves, Archive, Notebook,
    Bulk Add, Fetch Missing Details, Export and Import JSON.
  - About.
- **Shelves.** Add, rename, recolour, reorder and delete shelves. Deleting a
  shelf keeps its books.
- **Archive.** Restore or delete. Delete is only possible here, behind a
  confirmation.

## Data

- `Library { books, shelves, notes }` is stored as one JSON blob in DataStore,
  the same approach as Habit Tracker. Every field has a default and the parser
  ignores unknown keys, so the schema can grow safely.
- `Book` fields: title, author, kind, status, shelfId, recommendedBy, reason,
  notes, url, coverUrl, pageCount, currentPage, publishedYear, format, upNext,
  toAcquire, rating, review, addedAt, startedOn, finishedOn and archived.
- Export and import use JSON. Import offers **Merge** (adds missing items and
  shelves, matching shelves by name) or **Replace**.
- Unlike Habit Tracker, the library is **included** in Android auto-backup.
  Years of recommendations shouldn't disappear with a lost phone.

## Migration from Notion

The PDF export was converted into a curated import file that is kept **outside
this public repo**:
`../reading-list-notion-import.json`.

- Titles and authors were tidied: spelling fixed, full names added.
- Recommenders, reasons, notes, shelves, Need a Copy, Up Next and dates were
  carried over wherever the page had them.
- Books that appear in both "to read" and "read" lists were merged, and Read
  wins.
- Import it from Settings → Import from JSON → Merge, then run Settings →
  Fetch Missing Details to add covers and page counts.

## Deliberately left out

- **Zen mode.** The app has no single "do the thing" screen to lock down. The
  current-reads limit covers the focus need instead.
- **Swipe actions on cards.** They would conflict with the horizontal pager.
  Long-press menus cover the same actions.
- **Goodreads/StoryGraph sync and CSV import.** Possible later. JSON
  export/import covers backups.
