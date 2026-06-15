<p align="center">
  <img src="Misc/VP_Logo.png" alt="VP Logo" width="200"/>
</p>

# VP [VVAD Player]

**Navidrome music client for Android.** Focused on convenience and speed.

VP is a native Android client for [Navidrome](https://www.navidrome.org/) music servers, built with Jetpack Compose and Media3/ExoPlayer. Designed for fast, offline-first listening with a clean Material 3 interface.

---

## Features

- **Navidrome Integration** — Browse and stream your entire music library
- **Offline-First** — Cache albums for playback without network
- **Background Playback** — Full Media3/ExoPlayer support with notification controls
- **Queue Management** — Drag-to-reorder, swipe-to-remove, save/load queues
- **Smart Library** — Artists, albums, genres with instant search
- **Material 3 UI** — Adaptive layouts, dynamic theming, edge-to-edge

---

## Playback & Queue Controls

### Player Screen
- **Play/Pause, Next/Previous** — Standard transport controls
- **Seek Bar** — Scrub through tracks with haptic feedback
- **Queue Button** — Opens full queue management
- **Shuffle / Repeat** — Toggle modes (off → all → one)
- **Sleep Timer** — Auto-stop after configurable duration
- **Speed Control** — 0.5× to 2× playback speed

### Queue Screen
- **Now Playing** — Highlighted with animated indicator
- **Drag & Drop** — Reorder tracks with handle
- **Swipe to Remove** — Dismiss individual tracks
- **Clear Queue** — One-tap reset
- **Save/Load Queues** — Persist named playlists locally
- **Add to Queue** — From any album/artist/track list

### Notification & Lock Screen
- Full Media3 media session integration
- Album art, title, artist, playback controls
- Expandable notification with seek bar (Android 11+)

---

## Screenshots

| Library | Album | Player |
|:-------:|:-----:|:------:|
| ![Library](Misc/Screenshots/Screenshot_2026-06-14-22-06-44-617_com.vvad.vp.jpg) | ![Album](Misc/Screenshots/Screenshot_2026-06-14-22-06-33-903_com.vvad.vp.jpg) | ![Player](Misc/Screenshots/Screenshot_2026-06-14-22-06-27-294_com.vvad.vp.jpg) |

| Queue | Cached | Settings |
|:-----:|:------:|:--------:|
| ![Queue](Misc/Screenshots/Screenshot_2026-06-14-22-06-00-138_com.vvad.vp.jpg) | ![Cached](Misc/Screenshots/Screenshot_2026-06-14-22-05-03-422_com.vvad.vp.jpg) | ![Settings](Misc/Screenshots/Screenshot_2026-06-14-22-04-47-709_com.vvad.vp.jpg) |

| Search | Artist | Downloads |
|:------:|:------:|:---------:|
| ![Search](Misc/Screenshots/Screenshot_2026-06-14-22-04-31-734_com.vvad.vp.jpg) | ![Artist](Misc/Screenshots/Screenshot_2026-06-14-22-03-31-559_com.vvad.vp.jpg) | ![Downloads](Misc/Screenshots/Screenshot_2026-06-14-22-03-25-469_com.vvad.vp.jpg) |

---

## Tech Stack

- **Language:** Kotlin 1.9+
- **UI:** Jetpack Compose (Material 3)
- **Architecture:** MVVM + Repository, StateFlow/LiveData
- **Media:** Media3 ExoPlayer 1.3.1, MediaSession
- **Network:** Ktor + Kotlinx Serialization
- **Database:** Room (offline metadata), DataStore (preferences)
- **Images:** Coil 2.5
- **DI:** Manual (no Dagger/Hilt)
- **Min SDK:** 26 (Android 8.0)

---

## Building

```bash
./gradlew assembleDebug
```

Requires a `local.properties` with your Navidrome server URL for testing:
```properties
navidrome.url=https://your-navidrome-instance.com
```

---

## License

MIT License — see [LICENSE](LICENSE) for details.