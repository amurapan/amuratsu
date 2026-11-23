<p align="center">
  <img src="branding/readme-hero-en.svg" alt="Amuratsu — manga, books, and anime. Your stories, all in one place." width="100%" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/status-alpha-D32F2F?style=flat-square" alt="Status: alpha" />
  <img src="https://img.shields.io/badge/Android-8.0%2B-4B6650?style=flat-square" alt="Android 8.0 or later" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache_2.0-665B63?style=flat-square" alt="Apache 2.0 license" /></a>
</p>

<p align="center">
  <a href="#downloads">Downloads</a> ·
  <a href="#features">Features</a> ·
  <a href="#getting-started">Getting started</a> ·
  <a href="#build">Build</a> ·
  <a href="https://github.com/amurapan/amuratsu/issues">Feedback</a>
</p>

**Amuratsu** is an app for manga, light novels, fanfiction, and anime.
One library, search across media types, and a quick way to pick up where you left off.
Built on [Kototoro](https://github.com/Kototoro-app/Kototoro), with its own home screen
and **Material You** design.

> **Alpha builds**
>
> Early builds are available for Android, Windows x64, and Linux x64.
> Source availability and features depend on the platform and the selected extension.

## Downloads

| Platform | Release | Package |
| :--- | :--- | :--- |
| Android 8.0+ | [0.1.0-alpha.5](https://github.com/amurapan/amuratsu/releases/tag/v0.1.0-alpha.5) | ARM64 APK for most phones or the Universal APK for other supported devices |
| Windows x64 | [0.1.1-alpha.1](https://github.com/amurapan/amuratsu/releases/tag/v0.1.1-alpha.1) | MSI installer or portable ZIP. Extract the ZIP before opening Amuratsu. |
| Linux x64 | [0.1.1-alpha.1](https://github.com/amurapan/amuratsu/releases/tag/v0.1.1-alpha.1) | DEB package, tested on Ubuntu 24.04. The embedded player uses the system libmpv. |

## Features

| | |
| :--- | :--- |
| **Your stories**<br>Continue reading or watching from the home screen. Filter by Manga, Books, or Anime. | **One search**<br>Search connected sources across media types without switching sections first. Narrow the results by format when needed. |
| **Material You**<br>Dynamic colors, rounded cards, and layouts that adapt to different screen sizes. | **Reading your way**<br>Light, dark, and AMOLED themes, plus separate reader settings, including sepia. |
| **Your library**<br>Collections, history, downloads, and saved reading progress. | **Local files**<br>Import books, manga archives, and videos. Source and extension integrations are inherited from Kototoro. |

### What you can read and watch

| Format | In the app |
| :--- | :--- |
| Manga and comics | Page reader, chapters, and saved position |
| Light novels, novels, and fanfiction | Text reader, appearance settings, and EPUB/TXT import |
| Anime and video | Video player, episodes, subtitles, and audio track selection |

Content availability and individual features depend on the source and file format.
The app does not include media or subscriptions.

## Getting started

1. Download the package for your platform from [Downloads](#downloads)
   or [build a local preview](#build).
2. On Android, open source setup from Welcome, Home, or Search. On desktop, open **More → Extensions and repositories → Repositories**. Choose and add a catalog.
3. Filter extensions by language and install a source. You can also import local files from Home.
4. Search all formats and use **Continue** on Home to return to your saved position.

The local Android preview targets **ARM64**. The public Android APK uses Amuratsu’s own app ID and signing key.
To move from an older test build, export
a backup in **Settings → Backup and restore**, then restore it in the public app. Reimport local files
separately. A matching EPUB keeps its saved position when reimported with its original filename.
Android updates are available through **About → Updates**. All public packages are available from [GitHub Releases](https://github.com/amurapan/amuratsu/releases).

## Roadmap

- [x] Add separate Home views for manga, books, and anime with shared search.
- [x] Offer Keiyoushi, Yūzōnō, and LNReader Plugins as optional catalogs.
- [x] Prepare signed Android builds and check for updates from Amuratsu Releases.
- [x] Publish desktop alpha builds for Windows and Linux.
- [ ] Test more sources and check scrolling and tab switching on physical devices.

## Build

You need **JDK 17+** and the Android SDK/NDK specified in the [project configuration](app/build.gradle).
Set the SDK path through `ANDROID_HOME` or a local `local.properties` file.

```sh
git clone https://github.com/amurapan/amuratsu.git
cd amuratsu
./gradlew :app:assemblePreview
```

The APK will be generated at:

```text
app/build/outputs/apk/preview/app-arm64-v8a-preview.apk
```

Preview uses release optimizations and a local test signing key.
Use the same signing key to install over a previous test version while keeping its data.
Public Android builds use a separate signing key. See the [desktop build guide](desktop-app/README.md) for desktop development and packaging.

<details>
<summary><strong>For development</strong></summary>

```sh
# Debug build
./gradlew :app:assembleDebug

# Compile check
./gradlew :app:compileDebugKotlin

# JVM tests
./gradlew :app:testDebugUnitTest --no-daemon
```

- [Home screen and navigation](docs/development/media-navigation.md)
- [Branding and compatibility](docs/development/amuratsu-branding.md)
- [Shared UI guidelines](docs/architecture/shared-ui-guidelines.md)

Some technical documentation still describes the upstream Kototoro project. Its package names
and data formats are retained for compatibility; the displayed app name is Amuratsu.

</details>

## Feedback

Found a bug or have an idea? [Open an issue](https://github.com/amurapan/amuratsu/issues).
For bugs, include the app version, device model, Android version, and steps to reproduce the problem.

## Built on

Amuratsu is a fork of [Kototoro](https://github.com/Kototoro-app/Kototoro), which originated from
[Kotatsu](https://github.com/KotatsuApp/Kotatsu). Thanks to skepsun and everyone who contributed to the upstream projects.
Their work, commit history, and copyright notices are preserved.

<details>
<summary><strong>Projects behind the app</strong></summary>

- [Mihon](https://github.com/mihonapp/mihon)
- [Yomihon](https://github.com/yomihon/yomihon)
- [KotatsuApp/Kotatsu](https://github.com/KotatsuApp/Kotatsu) as the original source/UI base that Kototoro was initially derived from
- [kotatsu-redo](https://github.com/Kotatsu-Redo/Kotatsu-Redo) for the implementation inspiration behind automatic background handling of Cloudflare detection
- [Venera](https://github.com/venera-app/venera)
- [Kazumi](https://github.com/Predidit/Kazumi)
- [Light Novel Yuedu Source](https://github.com/ZWolken/Light-Novel-Yuedu-Source)
- [legado-with-MD3](https://github.com/HapeLee/legado-with-MD3)
- [RealCUGAN-ncnn-Android](https://github.com/omeshi1/RealCUGAN-ncnn-Android)
- [manga-translator-ui](https://github.com/hgmzhn/manga-translator-ui)
- [kotatsu-redo](https://github.com/Kotatsu-Redo/Kotatsu-Redo) for the e-ink reading mode
- [BiliPai](https://github.com/jay3-yy/BiliPai) for inspiring the ExoPlayer/Media3-based video super-resolution
- [DropSauce](https://github.com/HuzaifaKhalid1311/DropSauce) for inspiring the Google Drive sync feature
- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) for the liquid glass (backdrop) effect
- [Futon](https://github.com/AppFuton/Futon) for inspiring the global tag blacklist feature

</details>

## License

[Apache License 2.0](LICENSE). Original copyright notice:

```text
Copyright © 2024-2026 Kototoro Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
