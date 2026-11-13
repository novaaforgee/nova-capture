package com.novaaforgee.novacapture.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novaaforgee.novacapture.data.MediaItem
import com.novaaforgee.novacapture.data.MediaStoreRepository
import com.novaaforgee.novacapture.service.FloatingCaptureService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isServiceActive: Boolean = false,
    val recentItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val selectedItem: MediaItem? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaStoreRepository(application)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refreshDashboard()
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val items = repository.getRecentCaptures()
            _uiState.value = _uiState.value.copy(
                isServiceActive = FloatingCaptureService.isServiceRunning,
                recentItems = items,
                isLoading = false
            )
        }
    }

    fun updateServiceStatus(active: Boolean) {
        _uiState.value = _uiState.value.copy(isServiceActive = active)
    }

    fun selectItem(item: MediaItem?) {
        _uiState.value = _uiState.value.copy(selectedItem = item)
    }

    fun exportToGallery(item: MediaItem) {
        viewModelScope.launch {
            val success = repository.exportToGallery(item)
            if (success) {
                refreshDashboard()
            }
        }
    }

    fun deleteItem(item: MediaItem) {
        viewModelScope.launch {
            val deleted = repository.deleteMedia(item.uri)
            if (deleted) {
                refreshDashboard()
            }
        }
    }
}
