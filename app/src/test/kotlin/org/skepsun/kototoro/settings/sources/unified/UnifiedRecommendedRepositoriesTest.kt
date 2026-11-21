package org.skepsun.kototoro.settings.sources.unified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType

class UnifiedRecommendedRepositoriesTest {

    @Test
    fun `setup offers one catalog for each supported format`() {
        assertEquals("Keiyoushi", UnifiedRecommendedRepositories.byKind(UnifiedSourceKind.MIHON).single().name)
        assertEquals("LNReader Plugins", UnifiedRecommendedRepositories.byKind(UnifiedSourceKind.LNREADER).single().name)
        assertEquals("Yūzōnō", UnifiedRecommendedRepositories.byKind(UnifiedSourceKind.ANIYOMI).single().name)
    }

    @Test
    fun `optional catalogs can be activated through the supported repository workflows`() {
        for (catalog in UnifiedRecommendedRepositories.all) {
            assertEquals(UnifiedRepositoryLocationType.REMOTE_URL, catalog.locationType)
            assertTrue(catalog.url.startsWith("https://"))
            assertTrue(UnifiedRepositoryCapability.INSTALL_PACKAGE in catalog.capabilities)
        }
        assertEquals(
            UnifiedRecommendedRepositories.byKind(UnifiedSourceKind.ANIYOMI),
            UnifiedRecommendedRepositories.byExternalType(ExternalExtensionType.ANIYOMI),
        )
    }
}
