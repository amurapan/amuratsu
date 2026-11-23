package org.skepsun.kototoro.settings.sources.unified

import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.extensions.repo.RecommendedExtensionRepository

/** Optional third-party catalogs. Adding a catalog does not install any extensions. */
object UnifiedRecommendedRepositories {

    private val apkCapabilities = setOf(
        UnifiedRepositoryCapability.REFRESH,
        UnifiedRepositoryCapability.VERSIONED_INDEX,
        UnifiedRepositoryCapability.INSTALL_PACKAGE,
        UnifiedRepositoryCapability.TRUST_FINGERPRINT,
    )

    val all = listOf(
        recommendation(RecommendedExtensionRepository.KEIYOUSHI, UnifiedSourceKind.MIHON, apkCapabilities),
        recommendation(RecommendedExtensionRepository.YUZONO, UnifiedSourceKind.ANIYOMI, apkCapabilities),
        recommendation(RecommendedExtensionRepository.LNREADER, UnifiedSourceKind.LNREADER),
        recommendation(RecommendedExtensionRepository.PHISHER, UnifiedSourceKind.CLOUDSTREAM),
    )

    private fun recommendation(
        repository: RecommendedExtensionRepository,
        kind: UnifiedSourceKind,
        capabilities: Set<UnifiedRepositoryCapability> = setOf(
            UnifiedRepositoryCapability.REFRESH,
            UnifiedRepositoryCapability.INSTALL_PACKAGE,
        ),
    ) = UnifiedRecommendedRepository(
        kind = kind,
        name = repository.displayName,
        url = repository.indexUrl,
        locationType = UnifiedRepositoryLocationType.REMOTE_URL,
        capabilities = capabilities,
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
