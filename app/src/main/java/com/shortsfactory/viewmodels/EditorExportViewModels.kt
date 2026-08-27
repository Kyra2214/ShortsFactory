package com.shortsfactory.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shortsfactory.data.repository.ProjectRepository
import com.shortsfactory.data.repository.ShortRepository
import com.shortsfactory.domain.model.BatchExportProgress
import com.shortsfactory.export.ShortsProcessingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val shortRepository: ShortRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _title = MutableStateFlow("")
    private val _hook = MutableStateFlow("")
    private val _description = MutableStateFlow("")
    private val _hashtags = MutableStateFlow("")
    private val _cta = MutableStateFlow("")
    private val _startMs = MutableStateFlow(0L)
    private val _endMs = MutableStateFlow(0L)
    private val _videoDurationMs = MutableStateFlow(0L)
    private var shortId: Long = 0L
    private var projectId: Long = 0L

    val title: StateFlow<String> = _title.asStateFlow()
    val hook: StateFlow<String> = _hook.asStateFlow()
    val description: StateFlow<String> = _description.asStateFlow()
    val hashtags: StateFlow<String> = _hashtags.asStateFlow()
    val cta: StateFlow<String> = _cta.asStateFlow()
    val startMs: StateFlow<Long> = _startMs.asStateFlow()
    val endMs: StateFlow<Long> = _endMs.asStateFlow()
    val videoDurationMs: StateFlow<Long> = _videoDurationMs.asStateFlow()

    fun load(id: Long) {
        shortId = id
        viewModelScope.launch {
            val entity = shortRepository.getById(id) ?: return@launch
            projectId = entity.projectId
            _title.value = entity.title
            _hook.value = entity.hook
            _description.value = entity.description
            _hashtags.value = entity.hashtags
            _cta.value = entity.cta
            _startMs.value = entity.startMs
            _endMs.value = entity.endMs
            _videoDurationMs.value = projectRepository.getById(entity.projectId)?.videoDurationMs
                ?: entity.endMs
        }
    }

    fun saveMetadata(
        title: String,
        description: String,
        hashtags: String,
        cta: String,
        start: Long,
        end: Long
    ) {
        val normalizedStart = start.coerceAtLeast(0L)
        val normalizedEnd = end.coerceAtLeast(normalizedStart)
        viewModelScope.launch {
            shortRepository.updateMetadata(
                id = shortId,
                title = title.trim(),
                description = description.trim(),
                hashtags = hashtags.trim(),
                cta = cta.trim(),
                startMs = normalizedStart,
                endMs = normalizedEnd
            )
            _title.value = title.trim()
            _description.value = description.trim()
            _hashtags.value = hashtags.trim()
            _cta.value = cta.trim()
            _startMs.value = normalizedStart
            _endMs.value = normalizedEnd
        }
    }

    fun getProjectId(): Long = projectId
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val shortRepository: ShortRepository,
    private val processingManager: ShortsProcessingManager
) : ViewModel() {

    private val _shortsCount = MutableStateFlow(0)
    private val _progress = MutableStateFlow<BatchExportProgress?>(null)
    private val _error = MutableStateFlow<String?>(null)
    private var projectId: Long = 0L

    val shortsCount: StateFlow<Int> = _shortsCount.asStateFlow()
    val progress: StateFlow<BatchExportProgress?> = _progress.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(id: Long) {
        projectId = id
        viewModelScope.launch {
            _shortsCount.value = shortRepository.getByProject(id).size
        }
    }

    fun startExport(platforms: List<String>, quality: String, resolution: String, fps: Int) {
        if (_progress.value?.isRunning == true) return
        _error.value = null
        viewModelScope.launch {
            runCatching {
                processingManager.exportBatch(
                    projectId = projectId,
                    platforms = platforms,
                    quality = quality,
                    resolution = resolution,
                    fps = fps,
                    onProgress = { _progress.value = it }
                )
            }.onFailure { throwable ->
                _error.value = throwable.message ?: "Falha ao exportar os Shorts."
            }
        }
    }

    fun cancel() {
        processingManager.cancel()
    }
}
