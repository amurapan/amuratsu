# Linux local preview

This is the same JVM desktop application as the Windows and macOS hosts. The UI, controllers, library, database,
readers, networking and extension execution remain shared; Android/desktop presentation is in core-ui/commonMain.

The image includes its own JVM and the pinned compatibility JARs. It needs an X11 display (XWayland or Xvfb can
provide one), system fonts and the native libraries required by AWT/Skiko. A headless runtime check does not prove
GPU rendering, video playback or compatibility with every Linux distribution.

Embedded playback uses the distribution's libmpv. APT installs `libmpv2` with the Debian package. For the standalone
image, install the library through your package manager. An installed mpv executable can open streams externally.
The preview downloads ncnn tools on demand. Browser-based sources and automatic app updates are unavailable on Linux.

The default data directory is `~/.amuratsu`. Use `--data-dir` to choose a different directory. The runtime check requires an explicit
data directory. This local preview retains the existing compatibility runtime provenance and licence requirements.
