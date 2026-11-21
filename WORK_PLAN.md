# VVAD Player — Work Plan: Pagination, Settings, and Player Links

> Status: implemented. This document is retained as the implementation plan and verification checklist.
> Current shipped behavior is documented in [ARCHITECTURE.md](ARCHITECTURE.md).

## 1. Requested items

| ID | Item | Area | Effort |
|---|---|---|---|
| WP-01 | Clicking a song name while playing from a playlist opens the wrong route; clicking the playlist name on the player opens the wrong route | Playback / Player | M |
| WP-02 | Add server-side pagination to album, artist, and search APIs | Data | M |
| WP-03 | Add infinite scroll to Albums and Artists screens | UI | M |
| WP-04 | Add pagination controls to Search results | UI | M |
| WP-05 | Make vibration intensity setting actually change haptics | Settings / Haptics | S |
| WP-06 | Add configurable homepage row order/visibility and move the home-mixes prefix setting there | Home / Settings | M |
| WP-07 | Merge Transcoding and Image Quality settings; make option buttons fill width and move Unlimited to its own line | Settings UI | S |
| WP-08 | Add a primary/accent color setting under System | Theme / Settings | M |
| WP-09 | Add bottom-nav customization: reorder, hide, Home protected | Navigation / Settings | M |
| WP-10 | Build, regression-test, and update README/ARCHITECTURE | Verification / Docs | M |

## 2. Approach decisions

| Topic | Decision |
|---|---|
| Pagination transport | Use Navidrome's native `_start` / `_end` query parameters. Read `X-Total-Count` when present; otherwise stop when a page is short or empty. |
| Albums/Artists paging | Infinite scroll in both list and grid modes, with a footer spinner and a graceful end state. |
| Search paging | Per-section **Load more** actions. Search results are heterogeneous (artists are a horizontal row), so automatic combined infinite scroll is less predictable. |
| Player link fix | Track the song's album separately from the playback source (album or playlist). Extend `Track` with album identity, and carry source context on queue entries. |
| Homepage customization | Persist row order and hidden rows. Use up/down reorder controls, not drag-and-drop, to keep the settings UI simple and reliable. |
| Settings grouping | Replace `TRANSCODING` and `IMAGE_QUALITY` with one `PLAYBACK_QUALITY` category. Move playlist prefix into a new `HOMEPAGE` category. |
| Primary color | Persist an ARGB value in DataStore, apply it through `VVADPlayerTheme`, and remove hardcoded `VVADRed` usages from screens. Choose `onPrimary` automatically from luminance. |
| Bottom navigation | Persist order and hidden routes. Home is always visible and cannot be hidden. |
| Haptics | Map intensity to both amplitude and duration/waveform, and use distinct durations as a fallback on devices without amplitude control. |

## 3. Scope

In scope:

1. Player screen album/playlist link correctness.
2. Paged album and artist APIs.
3. Albums/Artists infinite scroll.
4. Search per-section pagination.
5. Working vibration intensity.
6. Configurable homepage rows and relocation of the playlist prefix setting.
7. Combined playback-quality settings with improved option layout.
8. Configurable primary color.
9. Configurable bottom navigation.
10. Verification and documentation updates.

Out of scope:

- Server-side playlist editing.
- A ViewModel/DI refactor.
- Offline pagination (offline views still show local cached albums).
- Persisting the playback or download queue across process death.
- Replacing DataStore with another settings store.
- Redesigning the Settings screen navigation model.

## 4. Work breakdown

### Phase 0 — Baseline

**WP-00 — Capture current behavior and test targets**

- Build the current app and record the exact broken behaviors:
  - Player song-title tap while playing from a playlist.
  - Player top-title tap while playing from a playlist.
  - Large album/artist libraries and broad searches.
- Prepare test data:
  - A library or filter with more than 100 albums and more than 100 artists.
  - A search query that returns more than the current caps.
  - A multi-track playlist and a multi-disc album.
  - A device/emulator with amplitude-capable vibration and, if possible, one without.
- Define a manual test matrix covering:
  - Player links from album playback and playlist playback.
  - Album/artist list and grid pagination, sorting during load, and end-of-list.
  - Search load-more per section and query changes mid-load.
  - Vibration off/on, Light/Medium/Strong, and test button.
  - Homepage row reorder, hide, restore, and app restart.
  - Settings category layout on a narrow screen.
  - Primary color changes across player, lists, settings, and selection controls.
  - Navbar reorder, hide, restart, and attempting to hide Home.
- Effort: S. Files: none.

### Phase 1 — Playback identity and player links

**WP-01 — Fix player album/playlist links**

**Root cause:**

- Playlist playback calls `replaceQueue(tracks, selectedIndex, playlistId, playlistName, coverUrl)` in `MainActivity.kt:477-485`.
- `PlaybackManager` stores that single ID as the album ID for every queue entry (`PlaybackManager.kt:271-278`).
- `PlayerScreen` uses `currentAlbumId` for both the top title and the song title (`PlayerScreen.kt:159-206`).
- Therefore, while playing from a playlist, both clicks navigate to `album/<playlistId>`, which is not an album.
- `Track` has no album identity (`ui/models/Album.kt:30-37`), and the playlist parsers discard per-song album IDs (`NavidromeManager.kt:571-629`).

**Work:**

- Extend `Track` with optional `albumId`, `albumName`, and cover identity fields so playlist entries retain their own album. Keep defaults so existing constructors continue to work.
- Populate those fields in:
  - `parseTracks()` for native playlist/album responses.
  - `parseSubsonicPlaylist()` for Subsonic playlist entries.
  - Any other track parser that receives `albumId` / `album_id` / `album`.
- Separate the **track's album** from the **playback source**:
  - Add source context to `QueueEntry` (source type, source ID, source name, source cover).
  - Source types: `album` and `playlist`.
  - Album queueing keeps today's behavior: source = the album, track album = the album.
  - Playlist queueing uses source = the playlist, but each entry's album = the track's own album.
- Add queue APIs or optional source parameters for playlist playback:
  - Replace queue from a playlist.
  - Queue one playlist track next.
  - Append one playlist track.
- Update `PlaybackManager` state and transition handling:
  - Expose `currentSourceId`, `currentSourceType`, `currentSourceName`.
  - Update them in `updateCurrentTrackFromPlayer()`, `replaceQueue()`, and `clearQueueExceptCurrent()`.
  - Preserve source and track album metadata when adding/removing/moving queue items.
- Update `PlayerScreen`:
  - Top title uses the playback source name.
  - Top-title click navigates to the playlist or album according to source type.
  - Song-title click always opens the current track's album.
  - If the track album ID is missing, disable that click instead of navigating to a wrong route.
- Update `MainActivity`:
  - Add an `onPlaylistClick` callback that closes the player overlay and navigates to `playlist/<id>`.
  - Update `PlaylistScreen` callbacks to pass playlist source context and per-track album identity.
  - Keep album playback using the album source path.

**Files:**

- `ui/models/Album.kt` (`:30-37`)
- `data/NavidromeManager.kt` (`:571-646`, `:739-798`)
- `data/PlaybackManager.kt` (`:48-54`, `:76-78`, `:222-234`, `:252-310`, `:312-398`, `:567-584`)
- `ui/screens/PlayerScreen.kt` (`:44-50`, `:159-206`)
- `ui/screens/PlaylistScreen.kt` (`:181-225`)
- `MainActivity.kt` (`:463-503`, `:612-627`)

**Acceptance:**

- Playing from a playlist and tapping the song title opens the song's album.
- Playing from a playlist and tapping the playlist name at the top opens that playlist.
- Playing from an album still opens the album from both the top title and the song title.
- Queue-next and append from a playlist keep correct album identity for later playback.
- Missing album metadata never navigates to `album/<playlistId>`.

**Depends on:** none. **Effort:** M.

### Phase 2 — Pagination

**WP-02 — Add paged album, artist, and search APIs**

**Root cause:** the native API calls currently cap results with a single `_end` parameter: albums/artists at 500 (`NavidromeManager.kt:245-293`), album search at 30, artist search at 10, and song search at 20 (`:371-421`). Playlist detail uses 500 songs and artist detail uses 200 albums. There is no continuation parameter and no total-count handling.

**Work:**

- Add a small paging result type, e.g. `PagedResult<T>(items, total, hasMore)`.
- Extend native album and artist queries with `start`/`limit`:
  - `_start=<start>&_end=<start + limit>`.
  - Keep existing callers working via default arguments.
- Add paged search methods for albums, artists, and songs with the same `start`/`limit` pattern.
- Parse `X-Total-Count` when the server provides it. If absent:
  - `hasMore = items.size >= requestedLimit`.
  - Stop after an empty page or when no new IDs were returned.
- Keep result order consistent with the requested sort/order.
- Deduplicate by ID in the data layer or at the first UI accumulation point.
- Do not change the existing non-paged convenience methods unless they become unnecessary.

**Files:**

- `data/NavidromeManager.kt` (`:245-293`, `:371-461`)

**Acceptance:**

- A library with more than 500 albums/artists can be fetched page by page.
- Search can request subsequent pages without repeating the first page.
- `hasMore` becomes false at the true end of the result set.
- Sort and order parameters apply to every page.

**Depends on:** none. **Effort:** M.

---

**WP-03 — Add infinite scroll to Albums and Artists screens**

**Root cause:** `AlbumsScreen` and `ArtistsScreen` fetch once and render the full returned list (`LibraryScreens.kt:438-447`, `:568-577`). They cannot request more than the server cap.

**Work:**

- Add paging state to each screen:
  - Loaded items.
  - Next start index.
  - `isLoadingMore`.
  - `hasMore`.
  - A load generation/counter so stale responses are ignored.
- Load the first page when the screen opens or when sort/order changes.
- Detect near-end scrolling from both `LazyListState` and `LazyGridState` using `layoutInfo.visibleItemsInfo`.
- Guard against duplicate concurrent loads.
- Append new pages, deduplicate by ID, and update `hasMore`.
- Show:
  - A footer spinner while loading another page.
  - A subtle end-of-list state when all items are loaded.
  - A retry row if a page fails and `hasMore` is still true.
- Reset the list and scroll position when sort or order changes, as today.
- Keep layout switching (list/grid) without losing already-loaded items.

**Files:**

- `ui/screens/LibraryScreens.kt` (`:413-543`, `:546-668`)

**Acceptance:**

- Scrolling to the bottom of Albums/Artists loads more items automatically.
- All server items are reachable beyond the previous 500-item cap.
- No duplicate rows appear.
- Changing sort/order starts a fresh paginated list.
- Both list and grid modes paginate correctly.
- A failed page does not put the screen into a permanent loading state.

**Depends on:** WP-02. **Effort:** M.

---

**WP-04 — Add pagination to Search results**

**Root cause:** `SearchScreen` issues one fixed-size request per category and then renders only those results (`LibraryScreens.kt:170-204`, `:338-406`). Album, artist, and song results are capped at 30/10/20.

**Work:**

- Replace the single search request with per-category paged state:
  - `albums`, `artists`, `songs` and their own next start / `hasMore` / loading flags.
- Fetch the first page of all three categories after the existing 300 ms debounce.
- Add a per-section **Load more** action:
  - Albums: full-width action after album rows.
  - Songs: full-width action after song rows.
  - Artists: action below the horizontal artist row.
- Append pages, dedupe by ID, and apply the existing artist ordering rules to the accumulated list.
- Reset all paging state when the query changes.
- Use a query generation token or cancelable job so a slow response from an old query cannot append to a new query.
- Show per-section spinners while loading and hide the action when no more results exist.
- Keep search history recording limited to submit/result-open actions; load-more must not record history.

**Files:**

- `ui/screens/LibraryScreens.kt` (`:145-410`)

**Acceptance:**

- Every matching album, artist, and song can be reached, beyond the old caps.
- Loading more in one section does not affect the others.
- A query change cancels/reset old results.
- No duplicate results appear.
- The UI clearly indicates when a section has no more results.

**Depends on:** WP-02. **Effort:** M.

### Phase 3 — Settings, homepage, theme, and navigation

**WP-05 — Fix vibration intensity**

**Root cause:** `AppHaptics` maps intensity only to `VibrationEffect` amplitude (`AppHaptics.kt:23-47`) while using the same duration for every level. On devices without amplitude control it falls back to `DEFAULT_AMPLITUDE`, making all three levels identical. The current test action always uses the same long-press pattern (`SettingsScreen.kt:360`, `:483-489`).

**Work:**

- Map each intensity to both amplitude and duration/waveform:
  - Light: short, low amplitude.
  - Medium: medium duration and amplitude.
  - Strong: longer/heavier pattern.
- On devices with amplitude control, use the amplitude mapping.
- On devices without amplitude control, use clearly distinct durations or a two-pulse waveform so intensity is still perceptible.
- Keep `performLongPress()` and `performTick()` distinct, and make the test action use a representative long-press pattern.
- Verify that saving intensity while the Settings screen is open recreates the `AppHaptics` instance and updates the composition local (`MainActivity.kt:114-120`).
- Audit all haptic call sites to confirm they use `LocalAppHaptics` rather than platform defaults.

**Files:**

- `ui/components/AppHaptics.kt` (`:10-48`)
- `ui/screens/SettingsScreen.kt` (`:431-490`)
- `MainActivity.kt` (`:114-120`)

**Acceptance:**

- Changing Light/Medium/Strong changes the test vibration on an amplitude-capable device.
- On a device without amplitude control, the levels still differ in duration/pattern.
- The vibration enable switch still fully disables haptics.
- Existing double-tap, long-press, reorder, and swipe haptics continue to work.

**Depends on:** none. **Effort:** S.

---

**WP-06 — Configurable homepage rows and home-mixes prefix**

**Root cause:** Homepage row order is hardcoded in `HomeScreen.kt:101-205`. The playlist prefix lives in its own `PLAYLISTS` Settings category (`SettingsScreen.kt:46`, `:284-303`), separate from any homepage configuration.

**Work:**

- Define a stable `HomeRow` model in the data layer:
  - Available Offline.
  - Random Albums.
  - Recently Played.
  - Most Played Albums.
  - Most Played Artists.
  - Your mixes.
- Persist in DataStore:
  - Ordered row keys.
  - Hidden row keys.
- Add CredentialsManager flows and save methods, with normalization:
  - Missing keys use the default order.
  - Unknown keys are dropped.
  - Duplicate keys are removed.
- Update `HomeScreen` to:
  - Collect the configuration.
  - Render online sections in the configured order.
  - Skip hidden or empty sections.
  - Skip network calls for disabled sections where practical.
  - Keep the offline fallback view independent of the online section configuration.
- Add a new `HOMEPAGE` Settings category containing:
  - Row reorder controls (up/down arrows).
  - A visibility switch per row.
  - The existing "Playlist name prefix" field.
- Remove the separate `PLAYLISTS` category and move its content into Homepage.
- Preserve the existing behavior where an empty prefix shows all playlists.

**Files:**

- `data/CredentialsManager.kt` (`:13-33`, `:100-130`)
- `ui/screens/HomeScreen.kt` (`:58-89`, `:91-206`)
- `ui/screens/SettingsScreen.kt` (`:39-50`, `:284-303`)

**Acceptance:**

- Homepage sections appear in the configured order after restart.
- Hidden sections stay hidden after restart.
- All six sections can be reordered and disabled; at least the rest of Home remains usable.
- The playlist prefix field works from the Homepage category and still affects the Your mixes row.
- Sorting/hiding settings do not break the offline cached-albums fallback.

**Depends on:** none. **Effort:** M.

---

**WP-07 — Merge playback-quality settings and fix option layout**

**Root cause:**

- `TRANSCODING` and `IMAGE_QUALITY` are separate Settings categories (`SettingsScreen.kt:45-47`).
- Transcoding and cover-size chips are in `FlowRow`s sized by content (`SettingsScreen.kt:236-332`), so they do not use the available horizontal width consistently.
- The `Unlimited` bitrate chip sits in the same row as the numeric bitrates (`SettingsScreen.kt:266-281`) and is too wide for the remaining row.

**Work:**

- Replace `TRANSCODING` and `IMAGE_QUALITY` with one `PLAYBACK_QUALITY` category:
  - Title/description covering streaming format, bitrate, and cover art resolution.
  - Keep the same save behavior and defaults.
- Lay out options in equal-width rows:
  - Formats: four equally weighted chips filling the row.
  - Numeric bitrates: four equally weighted chips filling the row.
  - `Unlimited`: a separate full-width chip on its own line.
  - Small cover sizes: four equally weighted chips.
  - Large cover sizes: four equally weighted chips.
- Use `Row` + `Modifier.weight(1f)` with chips that fill their slot rather than content-sized `FlowRow` items.
- Preserve the RAW behavior:
  - Raw selected forces unlimited bitrate.
  - Numeric bitrates remain disabled while RAW is selected.
- Keep labels short enough to avoid clipping on narrow screens.

**Files:**

- `ui/screens/SettingsScreen.kt` (`:39-50`, `:236-332`)

**Acceptance:**

- Transcoding and image settings appear in one category.
- Option buttons share the horizontal width evenly and do not leave unused right-side space.
- `Unlimited` occupies its own full-width line.
- RAW still disables numeric bitrate choices and selects unlimited.
- Save/restore of all values still works after restart.

**Depends on:** WP-05 and WP-06 only for the shared Settings file edits; logically independent. **Effort:** S.

---

**WP-08 — Add a primary color setting under System**

**Root cause:** the accent is fixed to `VVADRed` (`ui/theme/Color.kt:5`, `:10-12`) and hardcoded again in `PlayerScreen.kt:37`, `:249`, `:275-292`. There is no persisted user preference.

**Work:**

- Add a DataStore preference for the primary color, stored as an ARGB integer or hex string, defaulting to the current `VVADRed`.
- Add a `primaryColor` flow and save method to `CredentialsManager`.
- Update `VVADPlayerTheme` to accept a primary color parameter and build the dark/light schemes from it:
  - Set `primary`, `primaryContainer`, and `tertiary` consistently.
  - Compute `onPrimary` / `onPrimaryContainer` from luminance so light accents get dark content.
- Collect the color in `MainActivity` and pass it to `VVADPlayerTheme`.
- Replace hardcoded `VVADRed` usages in `PlayerScreen` with `MaterialTheme.colorScheme.primary`:
  - Favorite icon.
  - Buffered progress.
  - Slider thumb and active track.
- Add a **Primary color** control to the System settings section:
  - A row/grid of preset color swatches.
  - A visible selected state.
  - A reset-to-default action.
- Keep system bars black as they are today.

**Files:**

- `data/CredentialsManager.kt` (`:13-33`, `:100-130`)
- `ui/theme/Color.kt` (`:5-12`)
- `ui/theme/Theme.kt` (`:19-92`)
- `ui/screens/PlayerScreen.kt` (`:37`, `:243-295`)
- `ui/screens/SettingsScreen.kt` (`:357-362`, `:431-490`)
- `MainActivity.kt` (`:112-120`)

**Acceptance:**

- Changing the color updates the app accent immediately.
- The chosen color survives restart.
- Primary-colored text/icons, sliders, and selection controls use the chosen color.
- Light accent colors remain readable because `onPrimary` switches appropriately.
- Reset restores the default VVAD red.
- No hardcoded accent color remains in the player UI.

**Depends on:** none. **Effort:** M.

---

**WP-09 — Add bottom-navigation customization**

**Root cause:** the five navigation items are hardcoded in `MainActivity.kt:270-338`, with route-selection mapping at `:161-168`. There is no persisted order or visibility configuration.

**Work:**

- Define a stable navigation item model:
  - Route, label, icon.
  - Home, Search, Albums, Playlists, Artists.
- Persist in DataStore:
  - Ordered route keys.
  - Hidden route keys.
- Add CredentialsManager flows and save methods with normalization:
  - Unknown routes are dropped.
  - Missing routes are appended using the default order.
  - Home is always inserted and cannot be hidden, even if a malformed preference says otherwise.
- Update `MainActivity` to render `NavigationBarItem`s from the configured list instead of the hardcoded order.
- Keep `selectedNavRoute` logic working for hidden routes:
  - If the current route is hidden, no item is selected; Home remains the guaranteed fallback.
- Add a `NAVIGATION` Settings category:
  - Visibility switch per item; Home switch disabled.
  - Up/down reorder controls.
  - Reset-to-default action.
- Persist changes immediately and refresh the bar without restarting the activity.
- Ensure navigating to a hidden top-level route from other UI still works, but the hidden item simply does not appear in the bar.

**Files:**

- `MainActivity.kt` (`:149-196`, `:252-342`)
- `data/CredentialsManager.kt` (`:13-33`, `:100-130`)
- `ui/screens/SettingsScreen.kt` (`:39-50`, `:368-409`)

**Acceptance:**

- Navbar items can be reordered and hidden, and the result survives restart.
- Home cannot be hidden.
- At least Home remains available if preferences are corrupted.
- The selected item highlight still follows the current route.
- Navigation behavior and back-stack handling are unchanged for visible items.

**Depends on:** none. **Effort:** M.

### Phase 4 — Verification and documentation

**WP-10 — Build, regression pass, and docs**

**Work:**

- Build: `.\gradlew.bat :app:assembleDebug`.
- Run the manual matrix from WP-00.
- Verify no regressions in:
  - Album/artist/search navigation.
  - Playback, queue next/append, favorites, and scrobbling.
  - Offline downloads and offline playback.
  - Search history.
  - Existing haptics and gestures.
  - Settings persistence across restart.
- If lint is available and stable, run `.\gradlew.bat :app:lintDebug`; record the existing known lint-tooling issue if it still occurs.
- Update `README.md`:
  - Player album/playlist link behavior.
  - Pagination/load-more behavior.
  - Homepage customization.
  - Combined playback-quality settings.
  - Primary color and navbar customization.
- Update `ARCHITECTURE.md`:
  - New settings keys and categories.
  - Paged API usage.
  - Queue source context and per-track album metadata.
  - Theme accent flow.
  - Remove or revise stale known gaps.

**Files:**

- `README.md`
- `ARCHITECTURE.md`
- No source changes beyond fixes found during verification.

**Acceptance:**

- Debug build succeeds.
- Manual matrix passes.
- Player links work from both album and playlist playback.
- Pagination reaches the full result set.
- All new settings persist and apply immediately.
- Documentation matches shipped behavior.

**Depends on:** WP-01 through WP-09. **Effort:** M.

## 5. Recommended sequencing

1. **WP-00** baseline and test data.
2. **WP-01** player link fix.
3. **WP-02** paging API.
4. **WP-03** Albums/Artists infinite scroll.
5. **WP-04** Search pagination.
6. **WP-05** vibration intensity.
7. **WP-06** homepage rows and playlist prefix move.
8. **WP-07** settings merge and option layout.
9. **WP-08** primary color.
10. **WP-09** navbar customization.
11. **WP-10** verification and docs.

WP-05, WP-08, and WP-09 touch the same Settings file and can be performed in any order after WP-06/WP-07 if conflicts are managed. WP-01 and WP-02 are independent of the settings work.

## 6. Effort summary

| ID | Item | Effort | Depends on |
|---|---|---|---|
| WP-00 | Baseline and test matrix | S | — |
| WP-01 | Player album/playlist link fix | M | — |
| WP-02 | Paged album/artist/search APIs | M | — |
| WP-03 | Albums/Artists infinite scroll | M | WP-02 |
| WP-04 | Search pagination | M | WP-02 |
| WP-05 | Vibration intensity fix | S | — |
| WP-06 | Homepage rows and mixes prefix | M | — |
| WP-07 | Playback-quality settings and layout | S | WP-05, WP-06 |
| WP-08 | Primary color setting | M | — |
| WP-09 | Navbar customization | M | — |
| WP-10 | Verification and docs | M | WP-01–WP-09 |

Effort scale: **S** = small, **M** = medium.

## 7. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Navidrome ignores `_start` or returns no total count | Build URLs with both `_start` and `_end`; deduplicate by ID; stop when a page is empty or adds no new items. |
| Infinite scroll duplicates or races | Keep a monotonically increasing load generation; ignore stale responses; deduplicate before appending. |
| Playlist API omits per-track album IDs | Leave the track album link disabled rather than navigating to the playlist ID; prefer Subsonic `entry[].albumId`, with `album_id` fallback. |
| Queue source and track album get confused | Store them as separate fields on `QueueEntry`; never write the playlist ID into the track album field. |
| Vibration still feels identical on some hardware | Vary duration/waveform as well as amplitude; treat amplitude control as optional. |
| Light primary color reduces contrast | Derive `onPrimary` from luminance and test the preset palette in both themes. |
| Hidden navbar route leaves no selection | Keep Home always visible and treat it as the guaranteed fallback; normalize malformed preferences. |
| Homepage settings corrupt or miss keys after an update | Normalize order/hidden lists against the known `HomeRow` set every time they are read. |
| Settings file conflicts across WP-05, WP-07, WP-08, WP-09 | Apply settings edits in small commits and keep section content in separate composables where possible. |
| Search pagination ordering changes across pages | Apply the existing artist ordering to the accumulated list after each append; rely on server order for albums/songs. |

## 8. Definition of done

- Player song-title taps open the correct album during playlist playback.
- Player top-title taps open the playlist during playlist playback and the album during album playback.
- Albums and Artists screens load all server items through infinite scroll, in list and grid modes.
- Search result sections can load further pages until exhausted.
- Vibration intensity produces perceptibly different feedback and respects the enable switch.
- Homepage rows can be reordered and hidden, persist across restart, and the playlist prefix setting lives in the Homepage category.
- Transcoding and cover-size settings share one category; option buttons fill the row width; Unlimited is on its own line.
- The System section can change the primary color, and the accent applies throughout the app.
- The bottom bar order and visibility are configurable; Home cannot be hidden.
- `.\gradlew.bat :app:assembleDebug` succeeds.
- The manual regression matrix passes.
- `README.md` and `ARCHITECTURE.md` describe the shipped behavior.
