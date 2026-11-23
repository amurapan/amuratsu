package org.skepsun.kototoro.extensions.repo

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendedExtensionRepositoryTest {

    @Test
    fun `previously added index variants match their recommendation`() {
        assertTrue(RecommendedExtensionRepository.KEIYOUSHI.matchesIndex(
            "https://raw.githubusercontent.com/keiyoushi/extensions/refs/heads/repo/index.min.json",
        ))
        assertTrue(RecommendedExtensionRepository.YUZONO.matchesIndex(
            "https://raw.githubusercontent.com/mojuru/anime-repo/repo/index.pb",
        ))
        assertFalse(RecommendedExtensionRepository.YUZONO.matchesIndex(
            "https://raw.githubusercontent.com/mojuru/anime-repo/fork/index.pb",
        ))
    }
}
