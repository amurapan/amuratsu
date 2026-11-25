package org.skepsun.kototoro.search.ui.multi

import android.util.Log
import androidx.collection.LongSet
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.jsonsource.SourceTypeIdentifier
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.model.UnknownContentSource
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.prefs.observeAsStateFlow
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.explore.data.ContentSourcesRepository
import org.skepsun.kototoro.explore.data.SourcePreset
import org.skepsun.kototoro.explore.data.SourcePresetsRepository
import org.skepsun.kototoro.favourites.domain.FavouritesRepository
import org.skepsun.kototoro.history.data.HistoryRepository
import org.skepsun.kototoro.list.domain.ContentListMapper
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import org.skepsun.kototoro.search.domain.ALL_SEARCH_CONTENT_KINDS
import org.skepsun.kototoro.search.domain.ALL_SOURCE_TYPES
import org.skepsun.kototoro.search.domain.AdvancedSearchParams
import org.skepsun.kototoro.search.domain.SearchKind
import org.skepsun.kototoro.search.domain.SearchFilters
import org.skepsun.kototoro.search.domain.SearchV2Helper
import org.skepsun.kototoro.search.domain.matches
import org.skepsun.kototoro.search.domain.searchContentKindsFromNames
import org.skepsun.kototoro.search.domain.sourceTypesFromNames
import java.util.Locale

private const val MAX_PARALLELISM = 4
private const val TAG = "SearchViewModel"

@HiltViewModel(assistedFactory = SearchViewModel.Factory::class)
class SearchViewModel @AssistedInject constructor(
    @Assisted("query") query: String,
    @Assisted("kind") kind: SearchKind,
    @Assisted("advancedTitle") advancedTitle: String,
    @Assisted("advancedTags") advancedTags: String,
    @Assisted("advancedAuthor") advancedAuthor: String,
    @Assisted("pinnedOnly") initialPinnedOnly: Boolean,
    @Assisted("hideEmpty") initialHideEmpty: Boolean,
    @Assisted("sourceTypeNames") sourceTypeNames: Collection<String>?,
    @Assisted("contentKindNames") contentKindNames: Collection<String>?,
    @Assisted("languagePresetId") initialLanguagePresetId: Long,
    private val mangaListMapper: ContentListMapper,
    private val searchHelperFactory: SearchV2Helper.Factory,
    private val sourcesRepository: ContentSourcesRepository,
    private val sourceTypeIdentifier: SourceTypeIdentifier,
    private val appSettings: AppSettings,
    private val sourcePresetsRepository: SourcePresetsRepository,
    private val historyRepository: HistoryRepository,
    private val favouritesRepository: FavouritesRepository,
) : BaseViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("query") query: String,
            @Assisted("kind") kind: SearchKind,
            @Assisted("advancedTitle") advancedTitle: String,
            @Assisted("advancedTags") advancedTags: String,
            @Assisted("advancedAuthor") advancedAuthor: String,
            @Assisted("pinnedOnly") pinnedOnly: Boolean,
            @Assisted("hideEmpty") hideEmpty: Boolean,
            @Assisted("sourceTypeNames") sourceTypeNames: Collection<String>?,
            @Assisted("contentKindNames") contentKindNames: Collection<String>?,
            @Assisted("languagePresetId") languagePresetId: Long = -1L,
        ): SearchViewModel
    }

    val query = query
    val kind = kind

    val advancedQuery = if (kind == SearchKind.ADVANCED) {
        AdvancedSearchParams(
            query = query,
            title = advancedTitle,
            tags = advancedTags,
            author = advancedAuthor,
        )
    } else null

    private val _filters = MutableStateFlow(
        SearchFilters(
            sourceTypes = sourceTypesFromNames(sourceTypeNames) ?: ALL_SOURCE_TYPES,
            contentKinds = searchContentKindsFromNames(contentKindNames) ?: ALL_SEARCH_CONTENT_KINDS,
            pinnedOnly = initialPinnedOnly,
            // "Hide empty sources" is remembered across searches: callers that open the search screen without a
            // scope never pass it, so the stored preference is the fallback that keeps the user's last choice.
            hideEmpty = initialHideEmpty || appSettings.isSearchHideEmptySourcesEnabled,
            languagePresetId = initialLanguagePresetId,
        ).normalized(),
    )
    val filters = _filters.asStateFlow()
    val languagePresets: StateFlow<List<SourcePreset>> = sourcePresetsRepository.observeAll()
        .stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())
    val globalTagBlacklist: StateFlow<Set<String>> = appSettings.observeAsStateFlow(
        scope = viewModelScope + Dispatchers.IO,
        key = AppSettings.KEY_GLOBAL_TAG_BLACKLIST,
        valueProducer = { appSettings.globalTagBlacklist },
    )
    private val _searchState = MutableStateFlow(SearchResultsState())
    val searchState = _searchState.asStateFlow()

    private var searchJob: Job? = null
    @Volatile
    private var searchGeneration = 0L

    val list: StateFlow<List<ListModel>> = combine(
        searchState,
        filters,
    ) { state, filters ->
        state.asListModels(filters.hideEmpty)
    }.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState))

    internal val titleGroups = list.map(::groupSearchTitles)
        .stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5_000), SearchTitleGroups())

    init {
        doSearch()
    }

    fun getItems(ids: LongSet): Set<Content> {
        val snapshot = searchState.value.sections
        val result = LinkedHashSet<Content>(ids.size)
        snapshot.forEach { x ->
            for (item in x.list) {
                if (item.id in ids) {
                    result.add(item.manga)
                }
            }
        }
        return result
    }

    fun getItems(ids: Set<Long>): Set<Content> {
        val snapshot = searchState.value.sections
        val result = LinkedHashSet<Content>(ids.size)
        snapshot.forEach { x ->
            for (item in x.list) {
                if (item.id in ids) {
                    result.add(item.manga)
                }
            }
        }
        return result
    }

    fun retry() {
        doSearch()
    }

    private val _favouriteMatches = MutableStateFlow<List<org.skepsun.kototoro.favourites.domain.FavouriteSearchMatch>?>(null)
    val favouriteMatches = _favouriteMatches.asStateFlow()

    fun showFavouriteCategories(ids: List<Long>) {
        launchJob(Dispatchers.IO) {
            _favouriteMatches.value = favouritesRepository.findSearchCategories(ids)
        }
    }

    fun dismissFavouriteCategories() {
        _favouriteMatches.value = null
    }

    fun applyFilters(filters: SearchFilters) {
        val normalized = filters.normalized()
        val previous = _filters.value
        val scopeChanged = !previous.hasSameSearchScope(normalized)
        _filters.value = normalized
        if (appSettings.isSearchHideEmptySourcesEnabled != normalized.hideEmpty) {
            appSettings.isSearchHideEmptySourcesEnabled = normalized.hideEmpty
        }
        if (scopeChanged) doSearch()
    }

    fun continueSearch() {
        if (searchState.value.isSearching || !searchState.value.canSearchDisabledSources) return
        val filters = currentFilters()
        val generation = ++searchGeneration
        val prevJob = searchJob
        _searchState.update { it.copy(isSearching = true, canSearchDisabledSources = false) }
        searchJob = launchLoadingJob(Dispatchers.Default) {
            try {
                prevJob?.join()
                val sources = resolveSources(filters, includeDisabled = true)
                updateSearchState(generation) { it.copy(totalSources = it.totalSources + sources.size) }
                searchSources(sources, filters, generation)
            } finally {
                updateSearchState(generation) { it.finished() }
            }
        }
    }

    private fun doSearch() {
        val filters = currentFilters()
        val generation = ++searchGeneration
        val prevJob = searchJob
        prevJob?.cancel()
        _searchState.update {
            it.copy(
                isSearching = true,
                isRefreshing = true,
                completedSources = 0,
                totalSources = 0,
                canSearchDisabledSources = !filters.pinnedOnly,
                retryingSectionKeys = emptySet(),
            )
        }
        searchJob = launchLoadingJob(Dispatchers.Default) {
            try {
                prevJob?.join()
                appendResult(searchHistory(filters), generation)
                appendResult(searchFavorites(filters), generation)
                appendResult(searchLocal(filters), generation)
                val sources = resolveSources(filters, includeDisabled = false)
                updateSearchState(generation) { it.copy(totalSources = sources.size) }
                searchSources(sources, filters, generation)
            } finally {
                updateSearchState(generation) { it.finished() }
            }
        }
    }

    fun retrySource(section: SearchResultsListModel) {
        retrySections(listOf(section))
    }

    fun retryFailedSources() {
        retrySections(searchState.value.sections.filter { it.error != null })
    }

    private fun retrySections(sections: List<SearchResultsListModel>) {
        if (searchState.value.isSearching || sections.isEmpty()) return
        val filters = currentFilters()
        val generation = ++searchGeneration
        val prevJob = searchJob
        _searchState.update {
            it.copy(
                isSearching = true,
                completedSources = 0,
                totalSources = sections.size,
                retryingSectionKeys = sections.mapTo(mutableSetOf()) { section -> section.sectionKey },
            )
        }
        searchJob = launchLoadingJob(Dispatchers.Default) {
            try {
                prevJob?.join()
                val semaphore = Semaphore(MAX_PARALLELISM)
                sections.map { section ->
                    launch {
                        semaphore.withPermit {
                            val result = when {
                                section.titleResId == R.string.history -> searchHistory(filters)
                                section.titleResId == R.string.favourites -> searchFavorites(filters)
                                section.source == LocalMangaSource -> searchLocal(filters)
                                else -> searchSource(section.source, filters)
                            }
                            updateSearchState(generation) {
                                it.withResult(result, section).copy(
                                    completedSources = it.completedSources + 1,
                                    retryingSectionKeys = it.retryingSectionKeys - section.sectionKey,
                                )
                            }
                        }
                    }
                }.joinAll()
            } finally {
                updateSearchState(generation) { it.finished() }
            }
        }
    }

    private suspend fun searchSources(sources: List<ContentSource>, filters: SearchFilters, generation: Long) =
        kotlinx.coroutines.coroutineScope {
            val semaphore = Semaphore(MAX_PARALLELISM)
            sources.map { source ->
                launch {
                    semaphore.withPermit {
                        appendResult(searchSource(source, filters), generation)
                        updateSearchState(generation) { it.copy(completedSources = it.completedSources + 1) }
                    }
                }
            }.joinAll()
        }

    private fun currentFilters(): SearchFilters =
        filters.value

    private suspend fun resolveSources(filters: SearchFilters, includeDisabled: Boolean): List<ContentSource> {
        val sources = when {
            includeDisabled && filters.pinnedOnly -> emptyList()
            includeDisabled -> sourcesRepository.getDisabledSources().sortedByDescending { it.priority() }
            filters.pinnedOnly -> sourcesRepository.getPinnedSources().toList()
            else -> sourcesRepository.getEnabledSources()
        }
        val preset = if (filters.languagePresetId > 0L) {
            sourcePresetsRepository.getById(filters.languagePresetId)
        } else {
            null
        }
        return filterSourcesByType(sources, filters).filter { preset == null || it.name in preset.sources }
    }

    private inline fun updateSearchState(generation: Long, transform: (SearchResultsState) -> SearchResultsState) {
        _searchState.update { if (generation == searchGeneration) transform(it) else it }
    }

    // impl

    private suspend fun searchSource(source: ContentSource, filters: SearchFilters): SearchResultsListModel? = runCatchingCancellable {
        val searchHelper = searchHelperFactory.create(source)
        searchHelper(query, kind, advancedQuery)
    }.fold(
        onSuccess = { result ->
            val visible = result?.manga?.let { found ->
                if (filters.hasCinemaTypeRestriction) {
                    found.filter { item -> filters.contentKinds.any { it.matches(item) } }
                } else {
                    found
                }
            }
            if (result == null || visible.isNullOrEmpty()) {
                null
            } else {
                val list = mangaListMapper.toListModelList(
                    manga = visible,
                    mode = ListMode.GRID,
                )
                Log.d(
                    TAG,
                    "searchSource result source=${source.name} rawCount=${result.manga.size} mappedCount=${list.size} rawCovers=${
                        result.manga.joinToString(limit = 5) { "${it.title}=>${it.coverUrl ?: "<null>"}" }
                    } mappedCovers=${
                        list.joinToString(limit = 5) { "${it.title}=>${it.coverUrl ?: "<null>"}" }
                    }",
                )
                SearchResultsListModel(
                    titleResId = 0,
                    source = source,
                    list = list,
                    error = null,
                    listFilter = result.listFilter,
                    sortOrder = result.sortOrder,
                )
            }
        },
        onFailure = { error ->
            error.printStackTraceDebug()
            SearchResultsListModel(0, source, null, null, emptyList(), error)
        },
    )

    private suspend fun searchHistory(filters: SearchFilters): SearchResultsListModel? = runCatchingCancellable {
        historyRepository.search(query, kind, Int.MAX_VALUE, advancedQuery)
    }.fold(
        onSuccess = { result ->
            val filtered = filterContentBySourceType(result, filters)
            if (filtered.isNotEmpty()) {
                SearchResultsListModel(
                    titleResId = R.string.history,
                    source = UnknownContentSource,
                    list = mangaListMapper.toListModelList(manga = filtered, mode = ListMode.GRID),
                    error = null,
                    listFilter = null,
                    sortOrder = null,
                )
            } else {
                null
            }
        },
        onFailure = { error ->
            SearchResultsListModel(
                titleResId = R.string.history,
                source = UnknownContentSource,
                list = emptyList(),
                error = error,
                listFilter = null,
                sortOrder = null,
            )
        },
    )

    private suspend fun searchFavorites(filters: SearchFilters): SearchResultsListModel? = runCatchingCancellable {
        favouritesRepository.search(query, kind, Int.MAX_VALUE, advancedQuery)
    }.fold(
        onSuccess = { result ->
            val filtered = filterContentBySourceType(result, filters)
            if (filtered.isNotEmpty()) {
                SearchResultsListModel(
                    titleResId = R.string.favourites,
                    source = UnknownContentSource,
                    list = mangaListMapper.toListModelList(
                        manga = filtered,
                        mode = ListMode.GRID,
                        flags = ContentListMapper.NO_FAVORITE,
                    ),
                    error = null,
                    listFilter = null,
                    sortOrder = null,
                )
            } else {
                null
            }
        },
        onFailure = { error ->
            SearchResultsListModel(
                titleResId = R.string.favourites,
                source = UnknownContentSource,
                list = emptyList(),
                error = error,
                listFilter = null,
                sortOrder = null,
            )
        },
    )

    private suspend fun searchLocal(filters: SearchFilters): SearchResultsListModel? = runCatchingCancellable {
        if (isSourceTypeAllowed(LocalMangaSource, filters)) {
            searchHelperFactory.create(LocalMangaSource).invoke(query, kind, advancedQuery)
        } else {
            null
        }
    }.fold(
        onSuccess = { result ->
            if (!result?.manga.isNullOrEmpty()) {
                SearchResultsListModel(
                    titleResId = 0,
                    source = LocalMangaSource,
                    list = mangaListMapper.toListModelList(
                        manga = result.manga,
                        mode = ListMode.GRID,
                        flags = ContentListMapper.NO_SAVED,
                    ),
                    error = null,
                    listFilter = result.listFilter,
                    sortOrder = result.sortOrder,
                )
            } else {
                null
            }
        },
        onFailure = { error ->
            SearchResultsListModel(
                titleResId = 0,
                source = LocalMangaSource,
                list = emptyList(),
                error = error,
                listFilter = null,
                sortOrder = null,
            )
        },
    )

    private fun appendResult(item: SearchResultsListModel?, generation: Long) {
        updateSearchState(generation) { it.withResult(item) }
    }

    private fun filterSourcesByType(sources: Collection<ContentSource>, filters: SearchFilters): List<ContentSource> {
        return sources.filter { isSourceTypeAllowed(it, filters) }
    }

    private fun filterContentBySourceType(manga: List<Content>, filters: SearchFilters): List<Content> {
        return manga.filter { item ->
            sourceTypeIdentifier.getSourceType(item.source.name) in filters.sourceTypes &&
                filters.contentKinds.any { it.matches(item) }
        }
    }

    private fun isSourceTypeAllowed(source: ContentSource, filters: SearchFilters): Boolean {
        return sourceTypeIdentifier.getSourceType(source.name) in filters.sourceTypes &&
            filters.contentKinds.any { it.matches(source) }
    }

    private fun ContentSource.priority(): Int {
        var res = 0
        if (this.getLocale() == Locale.getDefault()) res += 2
        return res
    }

}
