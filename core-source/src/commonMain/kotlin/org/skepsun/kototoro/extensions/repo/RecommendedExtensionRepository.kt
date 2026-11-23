package org.skepsun.kototoro.extensions.repo

/** Optional catalogs shown in source setup. Adding an index leaves extension installation to the user. */
enum class RecommendedExtensionRepository(
    val displayName: String,
    val indexUrl: String,
    private val alternateUrl: String? = null,
) {
    KEIYOUSHI("Keiyoushi", "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb"),
    YUZONO(
        "Yūzōnō",
        "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.pb",
        "https://raw.githubusercontent.com/mojuru/anime-repo/repo/index.pb",
    ),
    LNREADER(
        "LNReader Plugins",
        "https://raw.githubusercontent.com/LNReader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json",
    ),
    PHISHER("Phisher Repo", "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/repo.json");

    /** Recognizes equivalent addresses saved by the manual add flow. */
    fun matchesIndex(url: String): Boolean {
        val path = catalogPath(url)
        return path.equals(catalogPath(indexUrl), ignoreCase = true) ||
            alternateUrl?.let { path.equals(catalogPath(it), ignoreCase = true) } == true
    }

    private fun catalogPath(url: String): String = url.trim().substringBefore('#').substringBefore('?')
        .trimEnd('/')
        .replace("/refs/heads/", "/")
        .removeSuffix("/index.pb")
        .removeSuffix("/index.json")
        .removeSuffix("/index.min.json")
        .removeSuffix("/repo.json")
}
