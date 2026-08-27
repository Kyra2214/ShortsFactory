package com.shortsfactory.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shortsfactory.data.local.entity.ProjectEntity
import com.shortsfactory.data.repository.AIAnalysisRepository
import com.shortsfactory.data.repository.ProjectRepository
import com.shortsfactory.data.repository.ShortRepository
import com.shortsfactory.data.repository.TranscriptRepository
import com.shortsfactory.data.repository.VideoImporter
import com.shortsfactory.domain.ai.AIProvider
import com.shortsfactory.domain.model.Transcript
import com.shortsfactory.domain.model.TranscriptSegment
import com.shortsfactory.domain.pipeline.AudioExtractorService
import com.shortsfactory.domain.pipeline.CandidateSelector
import com.shortsfactory.domain.pipeline.GenerationConfig
import com.shortsfactory.domain.pipeline.MediaAnalysisPipeline
import com.shortsfactory.domain.pipeline.PipelineProgress
import com.shortsfactory.domain.pipeline.PipelineStage
import com.shortsfactory.domain.pipeline.StageProgress
import com.shortsfactory.domain.pipeline.StageState
import com.shortsfactory.domain.pipeline.TranscriptionService
import com.shortsfactory.domain.pipeline.VideoEngine
import com.shortsfactory.projects.CandidateUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val shortRepository: ShortRepository,
    private val transcriptRepository: TranscriptRepository,
    private val aiAnalysisRepository: AIAnalysisRepository,
    private val videoImporter: VideoImporter,
    private val videoEngine: VideoEngine,
    private val aiProvider: AIProvider
) : ViewModel() {

    private val pipeline = MediaAnalysisPipeline(
        aiProvider = aiProvider,
        candidateSelector = CandidateSelector(),
        audioExtractor = object : AudioExtractorService {
            override suspend fun extract(videoPath: String, outputPath: String) {
                videoEngine.extractAudio(videoPath, outputPath)
            }
        },
        transcription = DemoTranscriptionService(videoEngine)
    )

    private val _project = MutableStateFlow<ProjectEntity?>(null)
    private val _progress = MutableStateFlow<PipelineProgress?>(null)
    private val _candidates = MutableStateFlow<List<CandidateUi>>(emptyList())
    private val _selectedPreset = MutableStateFlow("30s")
    private val _localPath = MutableStateFlow<String?>(null)
    private val _createdProjectId = MutableStateFlow<Long?>(null)
    private val _error = MutableStateFlow<String?>(null)
    private var analysisJob: Job? = null

    val progress: StateFlow<PipelineProgress?> = _progress.asStateFlow()
    val candidates: StateFlow<List<CandidateUi>> = _candidates.asStateFlow()
    val selectedPreset: StateFlow<String> = _selectedPreset.asStateFlow()
    val createdProjectId: StateFlow<Long?> = _createdProjectId.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(projectId: Long) {
        if (_createdProjectId.value == projectId && _localPath.value != null) return
        viewModelScope.launch {
            val project = projectRepository.getById(projectId) ?: return@launch
            _createdProjectId.value = project.id
            _project.value = project
            _localPath.value = project.videoUri
        }
    }

    fun setSource(uriStr: String) {
        viewModelScope.launch {
            _error.value = null
            _progress.value = PipelineProgress(
                projectId = 0,
                stages = PipelineStage.values().map { stage ->
                    StageProgress(
                        stage = stage,
                        state = if (stage == PipelineStage.VideoInput) StageState.PROCESSING else StageState.PENDING
                    )
                }
            )
            try {
                val result = when {
                    uriStr.startsWith("http://") || uriStr.startsWith("https://") ->
                        videoImporter.downloadFromUrl(uriStr)
                    uriStr.isNotEmpty() ->
                        videoImporter.importFromUri(android.net.Uri.parse(uriStr))
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
                                sourceUrl = if (uriStr.startsWith("http")) uriStr else null
                            )
                        )
                        _createdProjectId.value = projectId
                        _project.value = projectRepository.getById(projectId)
                        _localPath.value = result.localPath
                        updateProgressProjectId(projectId)
                        updateStage(PipelineStage.VideoInput, StageState.COMPLETED, 1f)
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
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _error.value = null
            val outcome = pipeline.analyze(
                videoPath = path,
                config = GenerationConfig(
                    preset = _selectedPreset.value,
                    maxCandidates = 12
                ),
                onStageUpdate = { stageProgress ->
                    val current = _progress.value ?: PipelineProgress(
                        projectId = projectId,
                        stages = PipelineStage.values().map { StageProgress(it, StageState.PENDING) }
                    )
                    _progress.value = current.copy(
                        projectId = projectId,
                        stages = current.stages.map { existing ->
                            if (existing.stage == stageProgress.stage) stageProgress else existing
                        }
                    )
                }
            )
            when (outcome) {
                is com.shortsfactory.domain.pipeline.AnalysisOutcome.Success -> {
                    transcriptRepository.save(projectId, outcome.transcript)
                    aiAnalysisRepository.save(projectId, aiProvider.providerName, outcome.result)
                    val ids = shortRepository.insertCandidates(projectId, outcome.selected)
                    _candidates.value = outcome.selected.mapIndexed { index, candidate ->
                        CandidateUi(
                            id = ids.getOrNull(index) ?: index.toLong(),
                            score = candidate.score,
                            startMs = candidate.startMs,
                            endMs = candidate.endMs,
                            title = candidate.title,
                            hook = candidate.hook,
                            topic = candidate.topic,
                            reason = candidate.reason
                        )
                    }
                }
                is com.shortsfactory.domain.pipeline.AnalysisOutcome.Failed -> {
                    _error.value = outcome.message
                }
                is com.shortsfactory.domain.pipeline.AnalysisOutcome.Cancelled -> {
                    _error.value = "Análise cancelada."
                }
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        videoEngine.cancel()
    }

    fun changePreset(preset: String) {
        _selectedPreset.value = preset
    }

    fun getProjectId(): Long = _createdProjectId.value ?: -1L

    fun getLocalPath(): String? = _localPath.value

    override fun onCleared() {
        analysisJob?.cancel()
        videoEngine.cancel()
        super.onCleared()
    }

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

    /** Implementação temporária para manter o fluxo demonstrável até o serviço de transcrição real ser conectado. */
    private class DemoTranscriptionService(private val videoEngine: VideoEngine) : TranscriptionService {
        override suspend fun transcribe(audioPath: String): Transcript {
            val durationMs = videoEngine.probe(audioPath).durationMs
            if (durationMs <= 0) return Transcript(emptyList())
            val segments = buildList {
                var startMs = 0L
                var index = 1
                while (startMs < durationMs) {
                    val endMs = (startMs + 5_000L).coerceAtMost(durationMs)
                    add(TranscriptSegment(startMs, endMs, "[segmento $index]"))
                    startMs = endMs
                    index++
                }
            }
            return Transcript(segments)
        }
    }
}
