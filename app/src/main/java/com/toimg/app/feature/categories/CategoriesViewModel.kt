package com.toimg.app.feature.categories

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toimg.app.core.util.MergeSuggestion
import com.toimg.app.core.util.TagNormalizeUtil
import com.toimg.app.data.repository.LocalImageRepositoryImpl
import com.toimg.app.data.repository.TagSynonymRepository
import com.toimg.app.domain.model.CategoryItem
import com.toimg.app.domain.model.DateRangeFilter
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.model.RecognitionResult
import com.toimg.app.domain.model.SearchFilterCriteria
import com.toimg.app.domain.model.SizeRangeFilter
import com.toimg.app.domain.repository.ImageRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val images: List<ImageItem> = emptyList(),
    val recognitions: Map<String, RecognitionResult> = emptyMap(),
    val categories: List<CategoryItem> = emptyList(),
    val mergeSuggestions: List<MergeSuggestion> = emptyList(),
    val searchQuery: String = "",
    val filterCriteria: SearchFilterCriteria = SearchFilterCriteria(),
    val isFilterPanelExpanded: Boolean = false,
    val availableFolders: List<String> = emptyList(),
    val searchDurationMs: Long = 0L,
    val selectedCategory: CategoryItem? = null,
    val selectedImageIds: Set<String> = emptySet(),
    val searchResults: List<ImageItem> = emptyList()
) {
    val imageMap: Map<String, ImageItem>
        get() = images.associateBy { it.id }

    val categoryDetailImages: List<ImageItem>
        get() {
            if (selectedCategory == null) return emptyList()
            return selectedCategory.imageIds.mapNotNull { imageMap[it] }
        }

    val isSearchOrFilterActive: Boolean
        get() = searchQuery.isNotBlank() || filterCriteria.hasActiveFilters
}

class CategoriesViewModel(
    private val repository: ImageRepository = LocalImageRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun init(context: Context) {
        val synonymRepo = TagSynonymRepository(context)
        val customSynonyms = synonymRepo.getCustomSynonyms()

        viewModelScope.launch {
            combine(
                repository.observeImagesFromDb(context),
                repository.observeRecognitions(context)
            ) { images, recognitions ->
                val recMap = recognitions.associateBy { it.imageId }
                val categories = TagNormalizeUtil.buildCategories(images, recMap, customSynonyms)
                val suggestions = TagNormalizeUtil.detectMergeSuggestions(categories)
                val folders = images.map { it.folderName }.filter { it.isNotBlank() }.distinct().sorted()
                Triple(images, recMap, Triple(categories, suggestions, folders))
            }.collect { (images, recMap, triple) ->
                val (categories, suggestions, folders) = triple
                _uiState.update { state ->
                    state.copy(
                        images = images,
                        recognitions = recMap,
                        categories = categories,
                        mergeSuggestions = suggestions,
                        availableFolders = folders
                    )
                }
            }
        }
    }

    fun updateSearchQuery(context: Context, query: String) {
        _uiState.update { state ->
            val newCriteria = state.filterCriteria.copy(query = query)
            state.copy(searchQuery = query, filterCriteria = newCriteria)
        }
        executeSearch(context)
    }

    fun updateDateRange(context: Context, range: DateRangeFilter) {
        _uiState.update { state ->
            val newCriteria = state.filterCriteria.copy(dateRange = range)
            state.copy(filterCriteria = newCriteria)
        }
        executeSearch(context)
    }

    fun updateSizeRange(context: Context, range: SizeRangeFilter) {
        _uiState.update { state ->
            val newCriteria = state.filterCriteria.copy(sizeRange = range)
            state.copy(filterCriteria = newCriteria)
        }
        executeSearch(context)
    }

    fun updateFolderFilter(context: Context, folder: String?) {
        _uiState.update { state ->
            val newFolder = if (state.filterCriteria.folderName == folder) null else folder
            val newCriteria = state.filterCriteria.copy(folderName = newFolder)
            state.copy(filterCriteria = newCriteria)
        }
        executeSearch(context)
    }

    fun updateSensitiveFilter(context: Context, onlySensitive: Boolean) {
        _uiState.update { state ->
            val newCriteria = state.filterCriteria.copy(onlySensitive = onlySensitive)
            state.copy(filterCriteria = newCriteria)
        }
        executeSearch(context)
    }

    fun updateStatusFilter(context: Context, status: String?) {
        _uiState.update { state ->
            val newStatus = if (state.filterCriteria.status == status) null else status
            val newCriteria = state.filterCriteria.copy(status = newStatus)
            state.copy(filterCriteria = newCriteria)
        }
        executeSearch(context)
    }

    fun toggleFilterPanel() {
        _uiState.update { it.copy(isFilterPanelExpanded = !it.isFilterPanelExpanded) }
    }

    fun resetFilters(context: Context) {
        _uiState.update { state ->
            state.copy(
                searchQuery = "",
                filterCriteria = SearchFilterCriteria(),
                searchResults = emptyList(),
                searchDurationMs = 0L
            )
        }
    }

    private fun executeSearch(context: Context) {
        val criteria = _uiState.value.filterCriteria
        if (!criteria.hasActiveFilters) {
            _uiState.update { it.copy(searchResults = emptyList(), searchDurationMs = 0L) }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            repository.searchImagesWithFilters(context, criteria).collect { list ->
                val duration = System.currentTimeMillis() - startTime
                _uiState.update {
                    it.copy(
                        searchResults = list,
                        searchDurationMs = duration
                    )
                }
            }
        }
    }

    fun selectCategory(category: CategoryItem?) {
        _uiState.update { it.copy(selectedCategory = category, selectedImageIds = emptySet()) }
    }

    fun toggleSelection(imageId: String) {
        _uiState.update { state ->
            val set = state.selectedImageIds.toMutableSet()
            if (set.contains(imageId)) {
                set.remove(imageId)
            } else {
                set.add(imageId)
            }
            state.copy(selectedImageIds = set)
        }
    }

    fun selectAllInDetail() {
        _uiState.update { state ->
            val ids = state.categoryDetailImages.map { it.id }.toSet()
            state.copy(selectedImageIds = ids)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedImageIds = emptySet()) }
    }

    fun mergeCategories(context: Context, sourceCategory: String, targetCategory: String) {
        viewModelScope.launch {
            repository.mergeCategory(context, sourceCategory, targetCategory)
            _uiState.update { it.copy(selectedCategory = null) }
        }
    }
}
