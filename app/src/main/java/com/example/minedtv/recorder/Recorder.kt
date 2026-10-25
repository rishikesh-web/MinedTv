package com.example.minedtv.recorder

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

data class RecorderSettings(
    val clipSeconds: Int = 30,
    /** Longest edge of the recording in px (720p = 1280). */
    val longEdge: Int = 1280,
    val bitrate: Int = 5_000_000,
    val mic: Boolean = true,
    /** Sound from the game itself (Android 10+ playback capture). */
    val gameAudio: Boolean = true
)

sealed interface RecorderStatus {
    data object Idle : RecorderStatus
    data object Starting : RecorderStatus
    data class Recording(val startedAtMs: Long, val width: Int, val height: Int, val mic: Boolean, val gameAudio: Boolean = false) : RecorderStatus
    data class Error(val message: String) : RecorderStatus
}

sealed interface RecorderEvent {
    data object ClipStarted : RecorderEvent
    data class ClipSaved(val file: File, val seconds: Int) : RecorderEvent
    data class ClipFailed(val message: String) : RecorderEvent
}

/**
 * Shared state between the recording service, the floating Clip button (accessibility service)
 * and the Clip screen. All three live in the same process.
 */
object Recorder {
    val status = MutableStateFlow<RecorderStatus>(RecorderStatus.Idle)
    val events = MutableSharedFlow<RecorderEvent>(extraBufferCapacity = 8)
    val settings = MutableStateFlow(RecorderSettings())

    /** True while the floating Clip button's accessibility service is enabled. */
    val clipButtonActive = MutableStateFlow(false)

    /** Package of the last app the user switched to (from the accessibility service). */
    val lastApp = MutableStateFlow<String?>(null)

    @Volatile
    internal var engine: RecorderEngine? = null

    /** Saves the last [RecorderSettings.clipSeconds] of the running recording. */
    fun clip() {
        val e = engine
        if (e == null) {
            events.tryEmit(RecorderEvent.ClipFailed("Start recording first"))
            return
        }
        e.requestClip()
    }

    fun appLabel(context: Context, pkg: String?): String? {
        if (pkg == null) return null
        val pm = context.packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}
