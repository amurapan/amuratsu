# Media navigation fork: first implementation

Base: Kototoro `v2.4.6` (`0470d6db3721196516348dc5769bf820871766e7`).
Local branch: `feature/media-navigation`. The original concept is in `output/media-navigation.html`.

## Behavior

- Fresh installations use Home, Search, Library and On device as their primary destinations. Existing custom navigation remains configurable in Settings. On device retains the upstream local files and downloaded content screen.
- Search is a separate top-level destination with its own navigation stack. It starts with all media and all source ecosystems selected, regardless of the active manga, novel or anime space.
- The search entry, overlay and results provide All / Manga / Books / Anime & video chips. Books includes novels and fanfiction supplied by compatible sources.
- The search landing has one input; the shell's duplicate search bar and browsing filters are hidden on this destination.
- The search landing links directly to source management, including extension repository setup.
- Search uses enabled sources by default. Selecting All restores all media; it does not enable disabled sources or install extensions.
- A source preset selected in search belongs to that search. It neither inherits nor changes the preset used for browsing a media space. Explicit presets are carried in search navigation, including serialized space sessions.
- Opening a result uses the existing content details and appropriate reader/player. Normal Back returns to the existing search entry and ViewModel. Upstream readers, player, downloads and source adapters are reused.
- The input and landing-page media selection use saved Compose state. Result list position and live filters retain the existing screen/ViewModel state while its navigation entry exists. Changes made to result filters are not yet written back to the serialized route for process-death recovery.
- Results and details retain upstream fullscreen navigation. This iteration does not introduce a new global bottom bar on those screens.

## Sharing boundary

`core-ui/.../search/MediaFilterChips.kt` is the shared presentation component. Android search and the desktop source-type filter both use it. Android resource labels, Hilt and Navigation 3 stay in `app`. The global search landing is Android-specific; desktop does not yet have the same global search route.

No source ecosystem, reader or player is reimplemented. No additional dependency or signing setup is introduced. Branding and application identity remain upstream for this development iteration.

## Validation

Use JDK 17 or newer and an Android SDK matching `app/build.gradle`.

```sh
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --no-daemon \
  --tests 'org.skepsun.kototoro.search.ui.multi.SearchViewModelTest' \
  --tests 'org.skepsun.kototoro.search.ui.suggestion.SearchSuggestionViewModelTest' \
  --tests 'org.skepsun.kototoro.main.ui.navigation3.SpaceNavigationSnapshotMapperTest' \
  --tests 'org.skepsun.kototoro.core.prefs.MainNavigationLimitTest' \
  --tests 'org.skepsun.kototoro.space.data.DefaultSpaceSessionValidatorTest'
./gradlew -PwithDesktopApp -PmihonCompatibilityDirectory=build/desktop-compatibility \
  :desktop-app:compileKotlin
./gradlew :app:assembleDebug
```

Desktop compilation requires the pinned compatibility JARs described in `desktop-app/README.md`.

Recorded on 2026-11-18: the five scoped Android test classes above passed all **32 tests**, with no failures or skips. Android Kotlin and test-source compilation succeeded as part of that run. The final `:app:assembleDebug` plus the same scoped tests passed again after the header cleanup. The desktop filter suite passed **3 tests**, including its Compose UI interaction test under Xvfb. That check also exposed an existing missing Legado icon; desktop now reuses Android's bitmap, and the ecosystem expectation includes the already supported Legado runtime.

Regression coverage exercises all-media search with an active browsing preset, narrowing to video then restoring all media, search-local presets, cancellation of old searches, and restoring the Search tab/query/scope after details, tab switches and a session snapshot.

## Android smoke check and artifacts

The final APK was installed on an isolated Android 15 / API 35 x86_64 Google APIs emulator with ARM64 translation, using Russian app resources. Verified launch, the four default destinations, one search input on the landing, choosing video while the active space remains Manga, opening the empty results screen, Back restoring `Frieren` and the selected video filter, and opening the source manager from the landing. The device had no enabled content sources, so this checks navigation and scope state rather than actual remote search results.

- APK: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (about 192 MiB).
- Identity: `org.skepsun.kototoro.debug`, label `Kototoro Dev`, version `2.4.6`, minimum API 26, ARM64 only.
- Final screen: `build/reports/media-navigation/search-home-final.png`.
- UI hierarchy evidence: `build/reports/media-navigation/search-home-final.xml`, `search-results-final.xml`, `search-back-final.xml`, `source-settings-confirmed.xml`.
- Test reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `desktop-app/build/reports/tests/test/index.html`.

The initial emulator renderer crashed at the host process level; the final search flow was rerun with the software renderer and guest Vulkan disabled. This is not a physical-device or performance validation.

Still to validate with enabled live sources: mixed remote results, opening actual manga/novel/fanfiction chapters, anime playback, downloads and offline resume. The existing per-media readers/player are reused; their live content flows were not exercised in this iteration. No external source repository is bundled or auto-enabled.

## Material You personal home

The approved design is implemented by `core-ui/.../home/MediaHomeScreen.kt`. Android's
`HomeScreen` and desktop's `DesktopHomePanel` both render that page; the hosts map their
existing history, tracker and suggestion repositories into `MediaHomeItem` and supply covers
and navigation callbacks. There is no bundled sample library or additional app dependency.

- Home owns its header, global search entry, format chips, recent reading cards, updates,
  recommendations and Add sheet. Android hides the former shell header and extra resume
  action on this route. The cover opens details; Continue launches the appropriate existing
  reader/player with stored chapter, page or playback position.
- Android's home format is saved separately in its ViewModel's `SavedStateHandle`. It does
  not change a space, library scope or search preset. The first history observation determines
  when the loading indicator can become an empty state.
- Colours use Material colour roles from the existing application theme, including Android
  wallpaper colours on supported versions and the existing dark/AMOLED palettes. Reader
  colours, including sepia, remain independently configurable in the existing reader.
- Fresh defaults use the standard bottom navigation and a left navigation rail with labels
  on larger Android windows. Explicit navigation preferences remain respected.
- The shared page switches to two columns when its available width is at least 720 dp;
  larger font scales retain a single column. The bounded list supports touch, mouse dragging
  and normal desktop scrolling. Add exposes only capabilities supplied by the host.
- Desktop retains source-scoped searching: its entry says **Browse sources**, opens the
  existing browser, and does not claim to perform Android's global search. Desktop has no
  import action on this page. Android launches its existing file import dialog.

The preceding screenshots and validation record describe the earlier navigation iteration;
Material You validation is recorded separately below.

### Material You validation (2026-11-20)

- `:app:assembleDebug` and `:core-ui:verifyUiStrings` passed. Fresh interface style now
  defaults to `MATERIAL_3_EXPRESSIVE`; an explicit saved style remains respected.
- The five Android navigation/search suites listed above passed again: **32 tests**.
  `AppSettingsNavIndicatorMigrationTest` passed **6 tests**, including the explicit legacy
  iOS preference fixture and existing navigation migration behavior.
- With `-PwithDesktopApp -PmihonCompatibilityDirectory=build/desktop-compatibility`, the
  Linux/Xvfb desktop run compiled the real consumer and passed **8 tests** across
  `MediaHomeScreenTest`, `DesktopHomeMouseGesturesTest`, `DesktopFloatingChromeTest`, and
  `DesktopSourceFilterTest`. These cover loading versus empty, small-screen Add/import,
  independent format/search/details/resume actions, nested resume buttons, light/dark
  phone and two-column layouts, mouse drag and host scrolling. Older full application
  probes were adjusted to the new entry points but were not rerun in full.
- Android API 35 emulator: imported a self-authored EPUB using Home → Add → Import,
  opened it from On device, read three pages and returned to Home. The card showed 12%.
  Continue reopened exactly the same text (paragraphs 12–14); XML text equality is recorded
  in `build/reports/material-home/resume-verification.txt`. Sepia was selected through the
  existing novel reader options sheet.
- Home's Manga filter hides the novel; opening search from that state keeps search on All.
  Returning Home restores the Manga chip. The source-free empty state remains actionable.
- Android screenshots and UI hierarchies are in `build/reports/material-home/`.
  Shared Compose layout captures are in `desktop-app/build/reports/desktop-smoke/`.
  Screenshots use a test book in an isolated emulator; it is not bundled in the APK.

Environment limits: the emulator's SwiftShader host renderer crashed once. The repeat run
used ANGLE/SwiftShader (`-gpu swangle`, guest Vulkan disabled). During its cold boot both
Launcher and the app reported no-focused-window input timeouts under CPU/memory pressure;
subsequent interaction and resume checks completed. This is not a physical-device or
performance certification. Live source search, network manga/anime playback and Windows/
macOS system integration were not exercised by these home-screen checks.

The Android shell also refreshes its edge-to-edge system icon appearance when a configuration
changes: MainActivity handles `uiMode` without recreation, so relying only on `onCreate`
left dark status icons on the dark home after a live system-theme switch. The fix is in
`BaseComposeActivity.onConfigurationChanged` and reuses the existing edge-to-edge setup.

Final on-device checks also covered light → dark → light switching without restarting the
app (system icons remain readable), AMOLED's black background, and a 1600×1200 viewport with
the navigation rail on the left. Inbox deliverables are in
`/home/kudo/inbox/kototoro-material-you-app/`: the final ARM64 debug APK, seven Android
screenshots and a short Russian readme. APK SHA-256:
`7ab74a13421a8896e25c14dd735f75cf1a23c73e0a53c49ef872d3ed01a14956`.

### Amuratsu preview and navigation performance

The Material shell no longer observes every scroll offset in composition to animate glass
overlap. Only glass chrome subscribes to the clamped derived overlap; the Material edge
gradient retains its scroll read inside `graphicsLayer`. Home has no glass overlap animation.
Sibling navigation uses a 140 ms crossfade without scaling both screens; details, nested
source lists and immersive search retain their existing motion and independent back stacks.

`./gradlew :app:assemblePreview` produces an ARM64 preview with release R8/resource shrinking
and release application sources. Unlike debug, it does not install StrictMode notification
handlers, LeakCanary or WorkInspector. It is shell-profileable but not debuggable. The local
preview uses the existing debug signing key and `.debug` application ID to update the previous
Inbox install without discarding its data. This key is for local previews, not distribution
releases. Mixed release/nightly task invocations retain the normal multi-ABI outputs.

The preview launcher label is **Amuratsu Preview**, and its displayed version is
`0.1.0-alpha.1`. The internal version code remains 1263 for upgrade compatibility; future
previews must increase it. The upstream default version and date-based nightly scheme are
unchanged. The preview's GitHub update repository is `amurapan/amuratsu`, so its alpha
version is not compared against Kototoro releases. GitCode remains disabled by the existing
update-source selection. Product-wide branding and application ID migration are separate
unfinished fork work.

These changes address identified extra work, not a measured physical-device speedup.
The original user report concerned tab switching and scrolling; phone model is unknown.

The first optimized APK exposed a resource-shrinking issue: `HomeMediaKind.labelKey` is a
dynamic lookup that the current shared string-ID generator does not scan. The Anime chip
fell back to its key. `res/raw/keep.xml` retains `media_home_*` strings in optimized builds;
the debug build previously masked this issue by retaining all resources.

Preview validation uses the existing API 35 test installation and self-authored EPUB:

- The optimized preview installs over debug 2.4.6 using `adb install -r`. APK signing
  certificates match, and the book retains its 12% progress.
- Home, Search, Library and On device render and switch successfully. Continue opens
  exactly the same paragraphs 12–14 as before the upgrade, with the saved sepia theme.
- At a compact 720×1150 viewport, a swipe moves Home content. Search → Home restores the
  exact progress-label bounds from before the tab switch. System animation scales are 1.
- Captures, UI hierarchies and text/bounds comparisons are in
  `build/reports/amuratsu-preview/`. These functional checks use software graphics and
  ARM64 translation; they do not establish physical-device frame rates. JVM suites were
  not rerun for this shell-animation/build-variant change; earlier results remain above.

Final `:app:assemblePreview` passed with the resource retention and update-repository
overrides. Static APK checks confirm version `0.1.0-alpha.1`, no debuggable flag, ARM64-only
native libraries, the previous signing certificate, the Amuratsu update repository, and the
retained Russian Anime label. The final APK installed successfully and rendered all four
home format chips on one row with the book still at 12%. The host emulator aborted once
between checks and was restarted; no app crash appeared in the final launch crash buffer.

Delivery: `/home/kudo/inbox/amuratsu-preview/amuratsu-0.1.0-alpha.1.apk`, 139,223,160 bytes,
with a final home screenshot and Russian instructions. SHA-256:
`118216d69a4ca49b7a73fbb98445c2f2a8e650eb295ff05932e7bf19828ad37a`.
