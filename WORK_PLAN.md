# VVAD Player — Work Plan: Fixes and Features

> Status: planning only. No source code is changed by this document.
> Architecture reference: [ARCHITECTURE.md](ARCHITECTURE.md)

## 1. Confirmed decisions

| Topic | Decision |
|---|---|
| “Playlist” | Means the current playback queue (`QueueScreen`). The app does not currently support server-side Navidrome playlists. |
| Album queueing | Support both **Play next** and **Add to queue** for a whole album. |
| Queue removal | Show an explicit remove button. The currently playing queue item remains protected from removal. |
| Reorder implementation | Manual drag-and-drop with a drag handle, no new dependency. Analysis and fallback are below. |
| Search history | Global per device, not per server/account. |
| Singles | Albums with exactly one track are treated as singles. |
| Player bar swipe | Swipe left = next, swipe right = previous. |
| Player screen close | Swipe down closes the full-screen player. |
| Queue reorder meaning | Reorder the current playback queue, not a server playlist. |

## 2. Reorder approach analysis

Two options were considered for queue drag-and-drop.

### Option A — Manual Compose drag handle (selected)

**How:** Add a drag handle to each queue row. Track the dragged item and offset with Compose gesture APIs. Use `LazyListState.layoutInfo` to find the target index, call a new `PlaybackManager.moveQueueItem(from, to)`, and keep ExoPlayer in sync with `moveMediaItem`.

**Pros:**
- No new third-party dependency.
- Full control over the drag handle, haptics, visuals, and current-track protection.
- Works well for this screen: one homogeneous `LazyColumn`, fixed-height rows, no grids or sections.
- Avoids version-compatibility risk with the project's pinned Compose BOM `2024.06.00` and Kotlin `1.9.24`.

**Cons:**
- More custom code than using a library.
- Auto-scroll near the top/bottom edges must be implemented manually.
- Item movement animations require extra care.
- Gesture edge cases need manual testing.

### Option B — Third-party reorderable library

**Example:** `sh.calvin.reorderable:reorderable`.

**Pros:**
- Less custom gesture code.
- Auto-scroll, reorder state, and animations are handled by the library.
- Generally more polished out of the box.

**Cons:**
- Adds a dependency and a maintenance/compatibility surface.
- The library version must match the app's Compose and Kotlin versions.
- More risk for a small app whose queue screen is structurally simple.

### Decision

Use **Option A: manual drag handle** first.

Rationale: the queue is a single fixed-row `LazyColumn`, and keeping the dependency surface small matters more than saving a moderate amount of code. If manual reordering cannot deliver smooth auto-scroll and item animation during implementation, fall back to a reorderable library instead of shipping a janky interaction.

## 3. Scope

In scope:

1. Observable favorites / list hearts.
2. Centered multiline album title.
3. Queue reordering.
4. Queue remove button instead of swipe.
5. Album-level queue actions: Play next and Add to queue.
6. Player bar swipe to skip.
7. Player screen swipe down to close.
8. Global search history.
9. One-track albums detected as singles.
10. Multi-disc album display hardening.
11. Reliable double-tap queue-next behavior.
12. Verification and documentation updates.

Out of scope:

- Server-side Navidrome playlist browsing, creation, or reordering.
- A full MVVM/DI refactor.
- Persisting the queue across process death.
- Persisting download jobs across process death.
- Automated UI test suite.

## 4. Work breakdown

### Phase 0 — Baseline and verification setup

**WP-00 — Capture baseline and test targets**

- Build the current app and record current behavior for each item.
- Capture raw album JSON for a multi-disc album and a one-track album to confirm field names (`discNumber`, `songCount`, etc.).
- Define a manual test matrix covering:
  - Album list favorites.
  - Current and non-current queue items.
  - Duplicate tracks in the queue.
  - Album queueing from empty and non-empty queue states.
  - Swipe/tap/seek gesture conflicts.
  - Search history after restart.
  - Singles tab classification.
  - Multi-disc headers.
  - Double-tap on current and non-current tracks.
- Effort: S.
- Files: none.

### Phase 1 — Core state and queue foundations

**WP-01 — Make favorites observable**

**Root cause:** `favoriteTrackIds` in `PlaybackManager.kt:82` is a plain `mutableSetOf`. Compose does not observe it, so `isTrackFavorite()` in list rows does not trigger recomposition. Only `isCurrentTrackFavorite` is observable.

**Work:**
- Replace the set with observable state, e.g. `mutableStateMapOf<String, Boolean>` or `mutableStateOf<Set<String>>`.
- Make `isTrackFavorite(trackId)` read observable state.
- Make `toggleFavorite(track)` update optimistically and roll back if the star/unstar request fails.
- Update the initial `getStarredTrackIds()` load to update the same observable state.
- Keep `isCurrentTrackFavorite` synchronized for the player screen.

**Files:**
- `data/PlaybackManager.kt` (`:82-85`, `:190-196`, `:410-430`)
- `ui/screens/AlbumScreen.kt` (`:493-508`)
- `ui/screens/PlayerScreen.kt` (`:197-205`)

**Acceptance:**
- Toggling a heart in an album list updates immediately.
- The heart state stays correct after scrolling and after app restart.
- Star/unstar failure restores the previous visual state.
- Player screen and list hearts agree for the current track.

**Depends on:** none. **Effort:** S.

---

**WP-02 — Refactor queue identity and queue operations**

**Root cause:** queue entries are identified only by track ID. Duplicate tracks break removal, and queue list keys include the index, which is unstable during reordering. Compose queue state and ExoPlayer media items are also updated separately.

**Work:**
- Add a stable `queueId` to `PlaybackManager.QueueEntry`.
- Use `queueId` for queue row keys and for remove/reorder operations.
- Add helpers:
  - `removeQueueItem(queueId)` or `removeQueueItemAt(index)`.
  - `moveQueueItem(from, to)`.
  - `appendAlbumToQueue(tracks, albumId, albumName, coverArtUrl)`.
  - `queueAlbumNext(tracks, albumId, albumName, coverArtUrl)`.
- Keep ExoPlayer synchronized:
  - Remove: `removeMediaItem`.
  - Move: `moveMediaItem`.
  - Append: `addMediaItem`.
  - Queue next: `addMediaItem` at the correct insertion index.
- Recompute `currentQueueIndex` from the currently playing queue entry after mutations.
- Preserve current-track protection: the UI must not remove the current item.
- Handle empty queue transitions predictably:
  - Add-to-queue / play-next with an empty queue starts playback from the first added track.
- Keep duplicate tracks independently removable and reorderable.

**Files:**
- `data/PlaybackManager.kt` (`:47-52`, `:256-356`, `:446-493`)
- `ui/screens/QueueScreen.kt`

**Acceptance:**
- Two copies of the same track can be removed independently.
- Reordering before/after the current item keeps the correct track playing.
- Removing an item before the current one updates the current index correctly.
- Clearing the queue still keeps only the current track.
- Adding an album to an empty queue starts playback from the first track.

**Depends on:** none. **Effort:** M.

### Phase 2 — UI fixes and queue features

**WP-03 — Center long multiline album titles**

**Root cause:** the album title in `AlbumScreen.kt:302-306` does not set `textAlign` and does not fill the available width. Wrapped lines default to start alignment.

**Work:**
- Set `textAlign = TextAlign.Center`.
- Use `Modifier.fillMaxWidth()` with horizontal padding so wrapped lines center within the album header.
- Apply the same treatment to any other album-title surface that can wrap to multiple lines. One-line marquee titles are unaffected.

**Files:**
- `ui/screens/AlbumScreen.kt` (`:302-306`)

**Acceptance:**
- A long album title that wraps to multiple lines is visually centered.
- Short titles remain centered and unchanged.
- No layout regression on the cover, artist names, or year.

**Depends on:** none. **Effort:** S.

---

**WP-04 — Queue remove button instead of swipe**

**Work:**
- Remove `SwipeToDismissBox` from `QueueScreen.kt:128-164`.
- Add a trailing delete `IconButton` to non-current rows.
- Keep the current row protected: hide or disable its remove action.
- Remove now-unused imports such as `SwipeToDismissBox`, `SwipeToDismissBoxValue`, and `animateColorAsState`.
- Wire removal through the new queue operation from WP-02.
- Ensure row click still seeks to that queue item.

**Files:**
- `ui/screens/QueueScreen.kt`

**Acceptance:**
- Every removable row has a clear delete button.
- The current row has no active remove action.
- Removing a row does not seek or start playback accidentally.
- Removing the last removable row leaves the current track intact.

**Depends on:** WP-02. **Effort:** S.

---

**WP-05 — Queue drag-to-reorder**

**Decision:** manual drag handle, no new dependency unless implementation quality is insufficient.

**Work:**
- Add a drag handle (`Icons.Default.DragHandle`) to queue rows.
- Implement drag detection on the handle with `pointerInput` / `detectDragGesturesAfterLongPress`.
- Track:
  - Dragged queue ID.
  - Drag offset.
  - Current target index from `LazyListState.layoutInfo.visibleItemsInfo`.
- On target change, call `PlaybackManager.moveQueueItem(from, to)`.
- Implement auto-scroll when dragging near the top or bottom edge.
- Add haptic feedback on drag start, target changes, and drop.
- Add a visual drag state: elevation/scale/background tint on the dragged row.
- Keep the current row movable; only removal is protected.
- Ensure row click is not triggered by drag gestures.

**Files:**
- `ui/screens/QueueScreen.kt`
- `data/PlaybackManager.kt` (WP-02 helpers)

**Acceptance:**
- Dragging a row changes its queue position.
- ExoPlayer order matches the visible order.
- The same track continues playing after moving items around it.
- Auto-scroll works for queues longer than one screen.
- Reordering a duplicate track moves the correct copy.
- Tapping a row still seeks to that item.
- Fallback: if manual drag cannot meet this quality bar, replace the custom drag implementation with a compatible reorderable library.

**Depends on:** WP-02. **Effort:** L.

---

**WP-06 — Album queueing: Play next and Add to queue**

**Work:**
- Add album-level queue actions in `AlbumScreen`, near the download control or in an overflow menu:
  - **Play next**: insert all album tracks immediately after the current track.
  - **Add to queue**: append all album tracks to the end.
- Add callbacks in `MainActivity.kt:391-432`.
- Add `PlaybackManager` methods from WP-02.
- For an empty queue, either action starts playback from the album's first track.
- Show brief feedback, similar to the existing download status message.
- Preserve album track order, including multi-disc order.
- Avoid inserting duplicate copies of the entire album when a queue action is already in progress; disable the action briefly if needed.

**Files:**
- `ui/screens/AlbumScreen.kt`
- `MainActivity.kt`
- `data/PlaybackManager.kt`

**Acceptance:**
- “Play next” inserts the whole album after the current track.
- “Add to queue” appends the whole album.
- Both work when the queue is empty.
- Both work for multi-disc albums in disc/track order.
- The queue screen immediately reflects the added tracks.

**Depends on:** WP-02. **Effort:** M.

### Phase 3 — Gestures

**WP-07 — Player bar swipe to skip**

**Work:**
- Add horizontal drag handling to `PlayerStripe`.
- Swipe left → `PlaybackManager.next()`.
- Swipe right → `PlaybackManager.previous()`.
- Use a threshold, e.g. 64–80 dp, so small movements do not skip.
- Add a small translated/affine feedback while dragging and settle back afterward.
- Preserve tap-to-open-player behavior.
- Do not steal horizontal gestures from the play/pause button.
- Consider disabling swipe when only one track is available.
- Add optional haptic feedback on successful skip.

**Files:**
- `ui/components/PlayerStripe.kt`

**Acceptance:**
- Swiping left from the bar advances to the next track.
- Swiping right goes to the previous track.
- A normal tap still opens the full player.
- Small accidental drags do not change tracks.
- The play/pause button continues to work independently.

**Depends on:** none. **Effort:** M.

---

**WP-08 — Player screen swipe down to close**

**Work:**
- Add vertical drag handling to the `PlayerScreen` root.
- Track drag offset and animate the overlay down while dragging.
- If the drag passes a threshold (e.g. 120 dp or 25% of screen height), call `onClose()`.
- If below threshold, animate back to the resting position.
- Keep the existing `BackHandler`.
- Preserve the seek slider's horizontal drag and the play/pause controls.
- Optionally start the drag only from the background/header to reduce accidental closes while interacting with controls.

**Files:**
- `ui/screens/PlayerScreen.kt`

**Acceptance:**
- Swipe down past the threshold closes the player.
- A short drag snaps back.
- Seek bar dragging still seeks and does not close the player.
- System back and the down-arrow button still close the player.

**Depends on:** none. **Effort:** M.

---

**WP-09 — Fix double-tap queue-next**

**Root cause hypothesis:** the first tap of a double tap triggers `onTrackClick`/`replaceQueue`, which changes `currentTrackId` and recomposes the track row. That can reset `combinedClickable`'s double-tap detection before the second tap is recognized.

**Work:**
- Replace the current `combinedClickable` gesture handling in `AlbumScreen.kt:370-402` with stable gesture detection, e.g. `Modifier.pointerInput(track.id)` using `detectTapGestures(onTap, onDoubleTap, onLongPress)`.
- Use `rememberUpdatedState` for callbacks so recomposition does not restart the gesture detector.
- Keep semantics:
  - Single tap: play track or toggle the current track.
  - Double tap: queue next.
  - Long press: append to queue.
- Ensure a double tap does not also perform the single-tap action.
- Keep the existing flash feedback for double tap and long press.
- Add haptic feedback for queue-next and append actions.
- Verify with the current track, non-current tracks, and duplicate tracks already in the queue.

**Files:**
- `ui/screens/AlbumScreen.kt`
- Possibly `data/PlaybackManager.kt` if queue insertion needs adjustment.

**Acceptance:**
- Double-tapping a non-current track adds it after the current track without starting playback.
- Double-tapping the current track also queues it next.
- Single tap still plays the track once.
- Long press still appends to the end.
- Repeated double taps produce the expected number of queue entries.

**Depends on:** WP-02 for reliable queue identity. **Effort:** M.

### Phase 4 — Search and metadata

**WP-10 — Global search history**

**Work:**
- Persist recent queries in `SharedPreferences` (e.g. `search_prefs`), global for the device.
- Store up to 10 recent queries, newest first, deduplicated case-insensitively.
- When the query is blank, show a **Recent searches** section:
  - Tap a history entry to reuse it.
  - Remove a single entry.
  - Clear all history.
- Record a query when:
  - The user submits via the keyboard search action.
  - The user opens a search result.
- Add keyboard search action to the `OutlinedTextField`.
- Ensure history is not recorded for every debounced keystroke.

**Files:**
- `ui/screens/LibraryScreens.kt` (`SearchScreen`)

**Acceptance:**
- Recent searches survive app restart.
- Submitting the same query twice does not create duplicates.
- History is capped at 10 entries.
- Tapping a history entry fills the search field and runs the search.
- Individual removal and clear-all work.
- Search history is shared across servers/accounts on the device.

**Depends on:** none. **Effort:** M.

---

**WP-11 — Detect one-track albums as singles**

**Work:**
- Ensure `songCount` is parsed consistently:
  - `parseAlbumList()` already reads it.
  - Add it to `parseArtistAlbums()`.
- Treat an album as a single when `songCount == 1`.
- Keep existing type/name checks as additional signals.
- Update `ArtistScreen.isSingleOrEP()` to include `songCount == 1`.
- Ensure the Albums tab excludes one-track albums and Singles & EPs includes them.

**Files:**
- `data/NavidromeManager.kt` (`:393-418`, `:673-693`)
- `ui/screens/ArtistScreen.kt` (`:52-83`)

**Acceptance:**
- A one-track album appears in Singles & EPs.
- Multi-track albums still appear in Albums.
- Name-based single/EP detection continues to work.
- Missing or zero `songCount` does not incorrectly classify every album as a single.

**Depends on:** none. **Effort:** S.

---

**WP-12 — Multi-disc album display**

**Current state:** `AlbumScreen` already groups tracks by `discNumber` and shows a header when more than one disc exists. This item hardens and verifies that behavior.

**Work:**
- Normalize missing or zero disc numbers to `1`.
- Ensure tracks are sorted by disc, then track number.
- When multiple discs exist, show clear headers such as **Disc 1 of 2**.
- Keep track numbers restarting per disc.
- Ensure the flat playback list order matches the grouped display order.
- Verify offline cache read/write preserves `discNumber`.
- If headers do not appear against a real server, inspect raw JSON field names and add fallbacks.
- Avoid showing a disc header for single-disc albums.

**Files:**
- `data/NavidromeManager.kt` (`:528-580`)
- `ui/screens/AlbumScreen.kt` (`:346-408`)
- `data/OfflineLibraryManager.kt` (`:290-302`, `:314-341`)

**Acceptance:**
- A two-disc album shows two labeled disc sections.
- Tracks are ordered correctly within and across discs.
- Playback from any track starts with the correct queue order.
- Offline cached multi-disc albums still show correct headers.
- Single-disc albums show no disc header.

**Depends on:** none. **Effort:** M.

### Phase 5 — Verification and documentation

**WP-13 — Build, lint, and manual regression pass**

**Work:**
- Build: `.\gradlew.bat :app:assembleDebug`
- Lint if available: `.\gradlew.bat :app:lintDebug`
- Run the manual test matrix from WP-00.
- Verify no regressions in:
  - Playback and notification controls.
  - Offline download and playback.
  - Search and navigation.
  - Queue add/remove/reorder/clear.
  - Favorites and scrobbling.
- Optionally extract pure queue transformation helpers and add unit tests for:
  - Move before/after current.
  - Remove before/after current.
  - Duplicate track identity.
  - Album queue insertion.

**Acceptance:**
- Build passes.
- Lint has no new errors.
- Manual matrix passes.
- No known blocking gesture conflicts.

**Depends on:** all implementation items. **Effort:** M.

---

**WP-14 — Update README and feature docs**

**Work:**
- Update `README.md` queue controls:
  - Drag to reorder.
  - Remove button instead of swipe.
  - Current item protected.
- Document album queue actions.
- Document player bar swipe and player screen swipe-down.
- Document search history.
- Document singles detection and multi-disc display.
- Update the controls/features sections to match the implemented behavior.

**Files:**
- `README.md`
- `ARCHITECTURE.md` (update known gaps after implementation)

**Acceptance:**
- README does not describe old swipe-to-remove behavior.
- New gestures and queue actions are documented.
- Architecture doc no longer lists completed items as gaps.

**Depends on:** WP-01 through WP-13. **Effort:** S.

## 5. Recommended sequencing

1. **WP-00** baseline.
2. **WP-01** favorites — independent, quick user-visible win.
3. **WP-02** queue model — prerequisite for queue UI and double-tap reliability.
4. **WP-03** album title centering — independent, quick.
5. **WP-04** queue remove button.
6. **WP-05** queue reorder.
7. **WP-06** album queueing.
8. **WP-07** player bar swipe.
9. **WP-08** player screen swipe-down.
10. **WP-09** double-tap queue fix.
11. **WP-10** search history.
12. **WP-11** singles detection.
13. **WP-12** multi-disc display.
14. **WP-13** verification.
15. **WP-14** documentation.

Gestures and search/metadata work are independent and can be done in parallel after WP-02 if desired.

## 6. Effort summary

| ID | Item | Effort | Depends on |
|---|---|---|---|
| WP-00 | Baseline/test matrix | S | — |
| WP-01 | Observable favorites | S | — |
| WP-02 | Queue model and operations | M | — |
| WP-03 | Center multiline album title | S | — |
| WP-04 | Queue remove button | S | WP-02 |
| WP-05 | Queue drag reorder | L | WP-02 |
| WP-06 | Album queueing | M | WP-02 |
| WP-07 | Player bar swipe | M | — |
| WP-08 | Player screen swipe-down | M | — |
| WP-09 | Double-tap queue fix | M | WP-02 |
| WP-10 | Search history | M | — |
| WP-11 | Singles detection | S | — |
| WP-12 | Multi-disc display | M | — |
| WP-13 | Verification | M | All |
| WP-14 | Documentation | S | WP-01–WP-13 |

Effort scale: **S** = small, **M** = medium, **L** = large/riskiest.

## 7. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Manual reorder becomes janky or buggy | Start with manual drag handle. If auto-scroll/animation quality is insufficient, switch to a compatible reorderable library before shipping. |
| Queue list and ExoPlayer diverge | Centralize all mutations in `PlaybackManager`; use stable `queueId`; verify with duplicate tracks and moves around the current index. |
| Gesture conflicts | Scope gestures carefully: drag handle for reorder, horizontal drag on player bar, vertical drag on player background, slider keeps horizontal drag. Test on a real device. |
| Double-tap still triggers single-tap playback | Use stable `pointerInput` keys and `rememberUpdatedState`; verify with recomposition caused by current-track changes. |
| Multi-disc field names vary by server | Verify raw JSON first; add `discNumber`/`disc_number` fallbacks and normalize zero/missing values. |
| One-track albums missing `songCount` | Do not classify on a missing/zero value; keep name/type fallback checks. |
| Search history grows or duplicates | Cap at 10, dedupe case-insensitively, record only on submit/result open. |
| New dependency risk | Avoid it for reorder unless manual implementation fails the acceptance criteria. |

## 8. Definition of done

- All WP items are implemented or explicitly deferred.
- `.\gradlew.bat :app:assembleDebug` succeeds.
- Lint has no new errors.
- Manual regression matrix passes.
- Favorites update immediately in lists.
- Long album titles are centered.
- Queue supports remove buttons and drag reorder.
- Current queue item cannot be removed.
- Albums can be queued with Play next and Add to queue.
- Player bar supports swipe-to-skip.
- Player screen supports swipe-down-to-close.
- Search history persists globally on the device.
- One-track albums appear as singles.
- Multi-disc albums render with correct disc sections.
- Double-tap queues the selected track reliably.
- README and architecture docs match the shipped behavior.
