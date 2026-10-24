package com.example.minedtv.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minedtv.data.AppServices
import com.example.minedtv.data.Clip
import com.example.minedtv.data.share.*
import com.example.minedtv.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ShareStep { Platform, Mode, Direct, AiOptions, Processing, Review, Publishing, Done }

data class ShareUiState(
    val clip: Clip,
    val step: ShareStep = ShareStep.Platform,
    val platform: SharePlatform? = null,
    val mode: ShareMode? = null,
    val options: EnhanceOptions = EnhanceOptions(),
    val job: EnhanceJobStatus? = null,
    val result: EnhanceResult? = null,
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    val selectedHashtags: Set<String> = emptySet(),
    val publishProgress: Float = 0f,
    val postUrl: String? = null,
    val error: String? = null
)

class ShareViewModel(
    clip: Clip,
    private val enhancer: EnhanceRepository = AppServices.enhanceRepository,
    private val publisher: PlatformPublisher = AppServices.platformPublisher
) : ViewModel() {
    private val _state = MutableStateFlow(ShareUiState(clip = clip, caption = clip.title))
    val state: StateFlow<ShareUiState> = _state.asStateFlow()
    private var work: Job? = null

    private fun update(block: ShareUiState.() -> ShareUiState) = _state.update { it.block() }

    fun selectPlatform(platform: SharePlatform) {
        Logger.d("Share platform: ${platform.displayName}")
        update { copy(platform = platform, step = ShareStep.Mode) }
    }

    fun selectMode(mode: ShareMode) {
        val platform = _state.value.platform ?: return
        Logger.d("Share mode: $mode")
        update {
            copy(
                mode = mode,
                step = if (mode == ShareMode.Direct) ShareStep.Direct else ShareStep.AiOptions,
                options = options.copy(targetSeconds = minOf(clip.durationSeconds, platform.idealSeconds)),
                caption = clip.title,
                error = null
            )
        }
    }

    fun updateOptions(options: EnhanceOptions) = update { copy(options = options) }

    fun setCaption(caption: String) = update { copy(caption = caption) }

    fun toggleHashtag(tag: String) = update {
        copy(selectedHashtags = if (tag in selectedHashtags) selectedHashtags - tag else selectedHashtags + tag)
    }

    fun startEnhance() {
        val s = _state.value
        val platform = s.platform ?: return
        work?.cancel()
        update { copy(step = ShareStep.Processing, job = EnhanceJobStatus.Uploading(0f), result = null, error = null) }
        Logger.i("AI enhance started: clip=${s.clip.id} platform=${platform.displayName} options=${s.options}")

        work = viewModelScope.launch {
            try {
                val jobId = enhancer.submit(
                    EnhanceRequest(
                        clipId = s.clip.id,
                        sourceUrl = "minedtv://clips/${s.clip.id}",
                        title = s.clip.title,
                        game = s.clip.game,
                        sourceSeconds = s.clip.durationSeconds,
                        platform = platform,
                        options = s.options
                    )
                )
                Logger.d("AI enhance job submitted: $jobId")
                enhancer.observe(jobId).collect { status ->
                    update { copy(job = status) }
                    when (status) {
                        is EnhanceJobStatus.Completed -> {
                            Logger.i("AI enhance done: ${status.result.durationSeconds}s ${status.result.aspectRatio}")
                            update {
                                copy(
                                    result = status.result,
                                    caption = status.result.suggestedCaption,
                                    hashtags = status.result.hashtags,
                                    selectedHashtags = status.result.hashtags.toSet(),
                                    step = ShareStep.Review
                                )
                            }
                        }
                        is EnhanceJobStatus.Failed -> {
                            Logger.w("AI enhance failed: ${status.message}")
                            update { copy(error = status.message) }
                        }
                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("AI enhance error", e)
                update { copy(error = e.message ?: "Enhancement failed") }
            }
        }
    }

    fun publish() {
        val s = _state.value
        val platform = s.platform ?: return
        val videoUrl = s.result?.videoUrl ?: "minedtv://clips/${s.clip.id}"
        work?.cancel()
        update { copy(step = ShareStep.Publishing, publishProgress = 0f, error = null) }
        Logger.i("Publishing to ${platform.displayName} (${s.mode})")

        work = viewModelScope.launch {
            try {
                publisher.publish(
                    PublishRequest(platform, videoUrl, s.caption, s.hashtags.filter { it in s.selectedHashtags })
                ).collect { status ->
                    when (status) {
                        is PublishStatus.Uploading -> update { copy(publishProgress = status.progress) }
                        is PublishStatus.Published -> {
                            Logger.i("Published: ${status.postUrl}")
                            update { copy(step = ShareStep.Done, postUrl = status.postUrl) }
                        }
                        is PublishStatus.Failed -> {
                            Logger.w("Publish failed: ${status.message}")
                            update { copy(error = status.message) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("Publish error", e)
                update { copy(error = e.message ?: "Upload failed") }
            }
        }
    }

    /** Goes back one step. Returns false when the flow should be closed instead. */
    fun back(): Boolean {
        val s = _state.value
        val previous = when (s.step) {
            ShareStep.Platform, ShareStep.Done -> null
            ShareStep.Mode -> ShareStep.Platform
            ShareStep.Direct, ShareStep.AiOptions -> ShareStep.Mode
            ShareStep.Processing, ShareStep.Review -> ShareStep.AiOptions
            ShareStep.Publishing -> if (s.mode == ShareMode.AiEnhanced) ShareStep.Review else ShareStep.Direct
        } ?: return false
        work?.cancel()
        Logger.d("Share back: ${s.step} -> $previous")
        update { copy(step = previous, error = null) }
        return true
    }
}
