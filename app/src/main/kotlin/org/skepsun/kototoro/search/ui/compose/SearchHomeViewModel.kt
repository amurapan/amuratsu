package org.skepsun.kototoro.search.ui.compose

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.skepsun.kototoro.explore.data.ContentSourcesRepository
import javax.inject.Inject

@HiltViewModel
internal class SearchHomeViewModel @Inject constructor(sourcesRepository: ContentSourcesRepository) : ViewModel() {
    val sources = sourcesRepository.observeEnabledSourceSnapshots()
}
