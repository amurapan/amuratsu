package org.skepsun.kototoro.settings.sources.unified

import org.skepsun.kototoro.core.lnreader.LNReaderRepository
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType

/** Optional third-party catalogs. Adding a catalog does not install any extensions. */
object UnifiedRecommendedRepositories {

    private val apkCapabilities = setOf(
        UnifiedRepositoryCapability.REFRESH,
        UnifiedRepositoryCapability.VERSIONED_INDEX,
        UnifiedRepositoryCapability.INSTALL_PACKAGE,
        UnifiedRepositoryCapability.TRUST_FINGERPRINT,
    )

    val all = listOf(
        UnifiedRecommendedRepository(
            kind = UnifiedSourceKind.MIHON,
            name = "Keiyoushi",
            url = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb",
            locationType = UnifiedRepositoryLocationType.REMOTE_URL,
            capabilities = apkCapabilities,
        ),
        UnifiedRecommendedRepository(
            kind = UnifiedSourceKind.ANIYOMI,
            name = "Yūzōnō",
            url = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.pb",
            locationType = UnifiedRepositoryLocationType.REMOTE_URL,
            capabilities = apkCapabilities,
        ),
        UnifiedRecommendedRepository(
            kind = UnifiedSourceKind.LNREADER,
            name = "LNReader Plugins",
            url = LNReaderRepository.OFFICIAL_REPO_URL,
            locationType = UnifiedRepositoryLocationType.REMOTE_URL,
            capabilities = setOf(UnifiedRepositoryCapability.REFRESH, UnifiedRepositoryCapability.INSTALL_PACKAGE),
        ),
    )

    fun byKind(kind: UnifiedSourceKind): List<UnifiedRecommendedRepository> {
        return all.filter { it.kind == kind }
    }

    fun byExternalType(type: ExternalExtensionType): List<UnifiedRecommendedRepository> {
        return byKind(
            when (type) {
                ExternalExtensionType.MIHON -> UnifiedSourceKind.MIHON
                ExternalExtensionType.ANIYOMI -> UnifiedSourceKind.ANIYOMI
                ExternalExtensionType.IREADER -> UnifiedSourceKind.IREADER
                ExternalExtensionType.JAR -> UnifiedSourceKind.JAR
                ExternalExtensionType.CLOUDSTREAM -> UnifiedSourceKind.CLOUDSTREAM
                ExternalExtensionType.TSUNDOKU -> UnifiedSourceKind.TSUNDOKU
            },
        )
    }
}
