# Amuratsu branding

Amuratsu is a fork of Kototoro. The logo is a red bookmark with a play symbol on white.
The Android alpha is the currently validated distribution; desktop and iOS sources are also branded,
but native Windows, macOS and iOS packages have not been published or validated on those platforms.

## Artwork

`branding/amuratsu.svg` is the master. Run `python3 scripts/generate_brand_assets.py` with ImageMagick 7
installed to regenerate Android vectors and legacy launcher images, the splash mark, store artwork,
the documentation icon and the opaque iOS app icon. The desktop build derives its icons from the store artwork.
This adds no runtime dependency. Android themed icons use the system palette; the ordinary icon is red on white.

## Compatibility

- The Android public release uses `io.github.amurapan.amuratsu` with a fixed release certificate.
  Nightly uses `io.github.amurapan.amuratsu.nightly`. Each has separate sync authorities and account identifiers.
- Preview retains `org.skepsun.kototoro.debug` and debug signing to update earlier previews in place.
  The launcher name is Amuratsu; debug builds are Amuratsu Dev and nightly builds are Amuratsu Nightly.
- For preview-to-release migration, export and restore the native backup. Import the media files separately.
  EPUB reimport recovers the position from an unambiguous legacy entry with matching filename, title,
  authors, and chapter layout. Native backup detection accepts old and new application IDs.
- Android release and preview are `0.1.0-alpha.4` (version code `1266`). Nightly version generation remains date based.
- New content links use `amuratsu://content/manga?id=…`. The old `kototoro://` links remain accepted.
  Registered OAuth callbacks keep their existing schemes and client registrations.
- The Android and desktop About pages share `AppProjectLink`, including the Amuratsu repository and issues,
  plus explicit Kototoro and Kotatsu credits. External parser engines, upstream services and import formats
  retain their real names. Copyright and license notices remain intact.
- Public Android update checks use the GitHub tag version and the matching release APK from
  `amurapan/amuratsu`. They require the Amuratsu release certificate. Preview APKs use their own package
  and key, so they do not offer public APKs as in-place updates.
- The automated release builds require `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
  `KEY_PASSWORD`. They verify the certificate against the app's public certificate before building.
  Alpha tags are published after APK validation. Keep the release key and password outside the repository.
- The checked-in iOS feeds are empty.
- Automatic Pages deployment is opt-in through the repository variable `DOCS_PAGES_ENABLED=true`, after Pages
  is configured. Scheduled nightly builds use `NIGHTLY_ENABLED=true`, after signing and packaging secrets are set.
  Both workflows remain available through manual dispatch. Pushing source code alone does not publish a release.

## Validation

Use the repository's Gradle wrapper to run `:core-ui:verifyUiStrings`, focused debug tests for
`ContentLinkResolverTest`, `UnifiedAddRepoDeepLinkTest` and `MangaBakaApiTest`, then `:app:assemblePreview`.
This project exposes JVM unit tests only for the debug variant.
Desktop checks cover `DesktopAboutLinksTest`, `DesktopAppUpdateTest` and `MacosAppUpdateTest`.

For an APK, inspect its label/version, package ID, signing certificate, ABI and retained About strings
with `aapt2` and `apksigner`. Install over the previous preview and check the launcher, About page,
new and old settings links, and saved reading progress. Emulator checks cannot establish physical-device performance.
Native iOS compilation requires Xcode and is not covered by Linux validation.

The desktop catalog verifier also recognizes keys selected by conditional expressions and the shared visual-preset
metadata. Obsolete home/details/player labels were removed from both catalogs; the missing favourite-state label was
added, and the source-category heading now uses the existing translated Android string.

On 2026-11-20, Android validation passed: 20 focused JVM tests, shared string verification and the optimized preview
build. The APK was installed over alpha.1 on the Android 17 emulator with the same signing certificate. Home text,
12% progress, the exact reader position and sepia were preserved. Launcher/splash artwork, About branding and both
settings-link schemes were checked on screen. VitePress built successfully. Local APK and runtime evidence is under
`build/reports/amuratsu-rebrand/` and is not committed.

Desktop compilation and the 942-key catalog check passed. The About/update-report suites passed all 15 tests;
the package-update suites passed 17 tests with one platform-specific test skipped on Linux. Together with Android,
this is 52 passing tests and one skip. The updater fixtures explicitly verify that upstream packages are rejected
and that adapted GitHub payloads accept Amuratsu release and nightly asset names.
