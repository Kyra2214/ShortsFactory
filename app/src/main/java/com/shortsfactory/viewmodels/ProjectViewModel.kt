package com.shortsfactory.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.shortsfactory.app.work.ShortsWorkScheduler
import com.shortsfactory.app.work.WorkKeys
import com.shortsfactory.data.local.entity.ProjectEntity
import com.shortsfactory.data.local.entity.ShortEntity
import com.shortsfactory.data.repository.ProjectRepository
import com.shortsfactory.data.repository.ShortRepository
import com.shortsfactory.data.repository.VideoImporter
import com.shortsfactory.domain.pipeline.PipelineProgress
import com.shortsfactory.domain.pipeline.PipelineStage
import com.shortsfactory.domain.pipeline.StageProgress
import com.shortsfactory.domain.pipeline.StageState
import com.shortsfactory.domain.pipeline.VideoEngine
import com.shortsfactory.projects.CandidateUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val shortRepository: ShortRepository,
    private val videoImporter: VideoImporter,
    private val videoEngine: VideoEngine,
    private val workScheduler: ShortsWorkScheduler
) : ViewModel() {

    private val _project = MutableStateFlow<ProjectEntity?>(null)
    private val _progress = MutableStateFlow<PipelineProgress?>(null)
    private val _candidates = MutableStateFlow<List<CandidateUi>>(emptyList())
    private val _selectedPreset = MutableStateFlow("30s")
    private val _localPath = MutableStateFlow<String?>(null)
    private val _createdProjectId = MutableStateFlow<Long?>(null)
    private val _error = MutableStateFlow<String?>(null)
    private var candidateObservationJob: Job? = null
    private var analysisObservationJob: Job? = null

    val progress: StateFlow<PipelineProgress?> = _progress.asStateFlow()
    val candidates: StateFlow<List<CandidateUi>> = _candidates.asStateFlow()
    val selectedPreset: StateFlow<String> = _selectedPreset.asStateFlow()
    val createdProjectId: StateFlow<Long?> = _createdProjectId.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(projectId: Long) {
        viewModelScope.launch {
            val project = projectRepository.getById(projectId) ?: return@launch
            _createdProjectId.value = project.id
            _project.value = project
            _localPath.value = project.videoUri
            _progress.value = progressFromProject(project)
            observeCandidates(projectId)
            observeAnalysis(projectId)
        }
    }

    fun setSource(uriStr: String) {
        viewModelScope.launch {
            _error.value = null
            _progress.value = initialProgress()
            try {
                val result = when {
                    uriStr.startsWith("http://") || uriStr.startsWith("https://") ->
                        videoImporter.downloadFromUrl(uriStr)
                    uriStr.isNotEmpty() ->
                        videoImporter.importFromUri(Uri.parse(uriStr))
                    else -> VideoImporter.ImportResult.Failure("Fonte de vídeo não informada.")
                }
                when (result) {
                    is VideoImporter.ImportResult.Success -> {
                        val info = videoEngine.probe(result.localPath)
                        val projectName = File(result.localPath).nameWithoutExtension.ifEmpty { "Novo projeto" }
                        val projectId = projectRepository.insert(
                            ProjectEntity(
                                name = projectName,
                                videoUri = result.localPath,
                                videoName = projectName,
                                videoDurationMs = info.durationMs,
                                videoWidth = info.width,
                                videoHeight = info.height,
                                videoSizeBytes = File(result.localPath).length(),
                                sourceType = if (uriStr.startsWith("http")) "url" else "local",
                                sourceUrl = if (uriStr.startsWith("http")) uriStr else null,
                                analysisStatus = "idle",
                                analysisProgress = 0f
                            )
                        )
                        _createdProjectId.value = projectId
                        _project.value = projectRepository.getById(projectId)
                        _localPath.value = result.localPath
                        updateProgressProjectId(projectId)
                        updateStage(PipelineStage.VideoInput, StageState.COMPLETED, 1f)
                        observeCandidates(projectId)
                        observeAnalysis(projectId)
                    }
                    is VideoImporter.ImportResult.Failure -> {
                        _error.value = result.message
                        _progress.value = null
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Falha ao importar o vídeo."
                _progress.value = null
            }
        }
    }

    fun runAnalysis() {
        val path = _localPath.value
        val projectId = _createdProjectId.value
        if (path == null || projectId == null) {
            _error.value = "Importe um vídeo antes de iniciar a análise."
            return
        }
        _error.value = null
        _progress.value = _progress.value ?: initialProgress(projectId)
        workScheduler.enqueueAnalysis(projectId, _selectedPreset.value)
    }

    fun cancelAnalysis() {
        _createdProjectId.value?.let(workScheduler::cancelAnalysis)
        videoEngine.cancel()
    }

    fun changePreset(preset: String) {
        _selectedPreset.value = preset
    }

    fun getProjectId(): Long = _createdProjectId.value ?: -1L

    fun getLocalPath(): String? = _localPath.value

    override fun onCleared() {
        candidateObservationJob?.cancel()
        analysisObservationJob?.cancel()
        videoEngine.cancel()
        super.onCleared()
    }

    private fun observeCandidates(projectId: Long) {
        candidateObservationJob?.cancel()
        candidateObservationJob = viewModelScope.launch {
            shortRepository.observeByProject(projectId).collectLatest { entities ->
                _candidates.value = entities.map(::toCandidateUi)
            }
        }
    }

    private fun observeAnalysis(projectId: Long) {
        analysisObservationJob?.cancel()
        analysisObservationJob = viewModelScope.launch {
            workScheduler.observeAnalysis(projectId).collectLatest { infos ->
                val info = infos.firstOrNull() ?: return@collectLatest
                val progress = info.progress.getFloat(WorkKeys.PROGRESS, _project.value?.analysisProgress ?: 0f)
                _progress.value = progressFromWork(projectId, info, progress)
                if (info.state == WorkInfo.State.FAILED) {
                    _error.value = info.outputData.getString(WorkKeys.ERROR) ?: "A análise falhou."
                } else if (info.state == WorkInfo.State.SUCCEEDED) {
                    _error.value = null
                }
            }
        }
    }

    private fun progressFromWork(projectId: Long, info: WorkInfo, totalProgress: Float): PipelineProgress {
        val stageName = info.progress.getString(WorkKeys.STAGE)
        val stageIndex = PipelineStage.entries.indexOfFirst { it.name == stageName }
        val normalizedIndex = stageIndex.coerceAtLeast(0)
        val stateForCurrent = when (info.state) {
            WorkInfo.State.FAILED -> StageState.FAILED
            WorkInfo.State.CANCELLED -> StageState.CANCELLED
            WorkInfo.State.SUCCEEDED -> StageState.COMPLETED
            WorkInfo.State.RUNNING -> StageState.PROCESSING
            else -> StageState.PENDING
        }
        val stages = PipelineStage.entries.mapIndexed { index, stage ->
            when {
                info.state == WorkInfo.State.SUCCEEDED -> StageProgress(stage, StageState.COMPLETED, 1f)
                index < normalizedIndex -> StageProgress(stage, StageState.COMPLETED, 1f)
                index == normalizedIndex -> StageProgress(stage, stateForCurrent, totalProgress)
                else -> StageProgress(stage, StageState.PENDING)
            }
        }
        return PipelineProgress(projectId, stages)
    }

    private fun progressFromProject(project: ProjectEntity): PipelineProgress? {
        if (project.analysisStatus == "idle") return null
        val state = when (project.analysisStatus) {
            "done" -> StageState.COMPLETED
            "failed" -> StageState.FAILED
            "cancelled" -> StageState.CANCELLED
            "running" -> StageState.PROCESSING
            else -> StageState.PENDING
        }
        val completed = (project.analysisProgress * PipelineStage.entries.size).toInt()
        return PipelineProgress(
            projectId = project.id,
            stages = PipelineStage.entries.mapIndexed { index, stage ->
                when {
                    state == StageState.COMPLETED -> StageProgress(stage, StageState.COMPLETED, 1f)
                    index < completed -> StageProgress(stage, StageState.COMPLETED, 1f)
                    index == completed -> StageProgress(stage, state, project.analysisProgress)
                    else -> StageProgress(stage, StageState.PENDING)
                }
            }
        )
    }

    private fun initialProgress(projectId: Long = _createdProjectId.value ?: 0L) = PipelineProgress(
        projectId = projectId,
        stages = PipelineStage.entries.map { StageProgress(it, StageState.PENDING) }
    )

    private fun updateProgressProjectId(projectId: Long) {
        _progress.value = _progress.value?.copy(projectId = projectId)
    }

    private fun updateStage(stage: PipelineStage, state: StageState, progress: Float) {
        _progress.value = _progress.value?.copy(
            stages = _progress.value!!.stages.map {
                if (it.stage == stage) it.copy(state = state, progress = progress) else it
            }
        )
    }

    private fun toCandidateUi(entity: ShortEntity) = CandidateUi(
        id = entity.id,
        score = entity.score,
        startMs = entity.startMs,
        endMs = entity.endMs,
        title = entity.title,
        hook = entity.hook,
        topic = entity.topic,
        reason = entity.reason
    )
}
