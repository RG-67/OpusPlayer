# Opus Player 🎵
**The Midnight Gallery – Your offline music companion**

A dark-themed Android music player app built in Kotlin + XML.  
Matches the design from The Midnight Gallery screens.

---

## Features
| Feature | Details |
|---|---|
| Home | Lists all downloaded MP3s from device storage |
| Now Playing card | Hero card with album art, play/pause |
| Mini Player | Persistent bar above bottom nav when music is playing |
| Full Player | Album art, seekbar, shuffle, repeat, volume, lyrics button |
| Search / Browser | Built-in WebView browser – search any site, tap download links |
| Download Manager | Foreground service with progress bar & cancel support |
| Sleep Timer | Set 5 – 120 min auto-stop in Settings |
| Background Playback | Music continues after app is minimised |
| Notification Controls | Play / Pause / Prev / Next from notification drawer |
| Settings | Dark mode toggle, memory usage bar, download quality, help |

---

## Project Setup in Android Studio

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 (bundled with Android Studio)
- Android device or emulator running API 26+

### Steps

1. **Open the project**
   ```
   File → Open → select the OpusPlayer folder
   ```

2. **Let Gradle sync** – Android Studio will automatically download all dependencies.

3. **Connect a device** (or start an emulator with API 26+).

4. **Run the app** – Click the ▶ Run button or press `Shift + F10`.

5. **Grant permissions** when prompted:
   - *Read Media Audio* (Android 13+) or *Read External Storage*
   - *Post Notifications* (for playback controls in notification drawer)

---

## How to Use

### Playing Music
1. Open the **Home** tab – your downloaded MP3s appear automatically.
2. Tap any song to start playback.
3. The **mini player** bar appears above the bottom navigation.
4. Tap the mini player to open the **full player screen**.

### Downloading Music
1. Go to the **Search** tab.
2. Type a song name (e.g. `Neon Cathedral synthwave`) or a direct URL.
3. The built-in browser opens Google search or the URL.
4. Navigate to any MP3 download site (e.g. free-mp3-download.net, mp3juices.cc).
5. Tap the download button/link on the page – the app intercepts it automatically.
6. The cyan download progress bar appears at the top.
7. After completion, return to **Home** – the song appears in your list.

### Sleep Timer
1. Go to **Settings → Sleep Timer**.
2. Choose a duration (5 min to 2 hours).
3. A countdown appears on the full player screen.
4. Music pauses automatically when the timer finishes.

### Download Quality
Go to **Settings → Download Quality** and select:
- **Lossless** – highest quality (whatever the source offers)
- **High** – 320 kbps
- **Medium** – 192 kbps
- **Low** – 128 kbps

*(Quality setting is metadata only – actual quality depends on the source file.)*

---

## Project Structure

```
OpusPlayer/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/opusplayer/
│       │   ├── model/
│       │   │   ├── Song.kt
│       │   │   └── DownloadItem.kt
│       │   ├── service/
│       │   │   ├── MusicService.kt        ← background playback
│       │   │   └── DownloadService.kt     ← background downloads
│       │   ├── ui/
│       │   │   ├── MainActivity.kt
│       │   │   ├── home/HomeFragment.kt
│       │   │   ├── search/SearchFragment.kt
│       │   │   ├── settings/SettingsFragment.kt
│       │   │   ├── player/PlayerActivity.kt
│       │   │   └── adapters/
│       │   │       ├── TrackAdapter.kt
│       │   │       └── TrendingAdapter.kt
│       │   ├── utils/
│       │   │   ├── MediaScanner.kt
│       │   │   ├── PrefsManager.kt
│       │   │   └── Extensions.kt
│       │   └── viewmodel/
│       │       ├── HomeViewModel.kt
│       │       ├── SearchViewModel.kt
│       │       └── SettingsViewModel.kt
│       └── res/
│           ├── layout/          ← all XML layouts
│           ├── drawable/        ← icons + backgrounds
│           ├── values/          ← colors, strings, themes, dimens
│           ├── navigation/      ← nav graph
│           ├── menu/            ← bottom nav menu
│           └── xml/             ← file_paths for FileProvider
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## Dependencies

| Library | Purpose |
|---|---|
| Navigation Component | Bottom nav + fragment management |
| ViewModel + LiveData | MVVM architecture |
| RecyclerView | Song lists |
| Glide 4.16 | Album art image loading |
| OkHttp 4.12 | HTTP downloads |
| Jsoup 1.17 | HTML parsing (future use) |
| MediaPlayer (Android SDK) | Audio playback |
| Media (AndroidX) | Notification media session |
| Material Components | Switch, BottomNavigationView |
| Coroutines | Async downloads |
| Gson | JSON (recent searches persistence) |
| Parcelize | Song parcelable for intents |

---

## Permissions Used

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` | Read MP3 files from device |
| `WRITE_EXTERNAL_STORAGE` (API ≤ 28) | Save downloads |
| `INTERNET` | WebView browsing + downloads |
| `FOREGROUND_SERVICE` | Keep music playing in background |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14 media service type |
| `POST_NOTIFICATIONS` | Show playback notification |
| `WAKE_LOCK` | Prevent CPU sleep during playback |

---

## Customisation Tips

- **App name**: Edit `app_name` in `res/values/strings.xml`
- **Accent colour**: Change `accent_primary` in `res/values/colors.xml`
- **Trending songs**: Edit the list in `SearchViewModel.kt`
- **Default username**: Change in `PrefsManager.kt` (`"Julian Vance"`)
- **Max recent searches**: Change `MAX_RECENT_SEARCHES` in `PrefsManager.kt`
