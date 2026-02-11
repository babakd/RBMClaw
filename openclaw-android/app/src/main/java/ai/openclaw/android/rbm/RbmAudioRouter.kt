package ai.openclaw.android.rbm

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class RbmAudioRouter(
  private val context: Context,
) {
  companion object {
    private const val tag = "OpenClaw/RbmAudioRouter"
  }

  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  @Volatile private var initialized = false
  @Volatile private var scoConnected = false
  private var waitingCallback: ((Boolean) -> Unit)? = null

  private val scoReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) return
        val state =
          intent.getIntExtra(
            AudioManager.EXTRA_SCO_AUDIO_STATE,
            AudioManager.SCO_AUDIO_STATE_ERROR,
          )
        when (state) {
          AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
            scoConnected = true
            waitingCallback?.invoke(true)
            waitingCallback = null
          }
          AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
            scoConnected = false
          }
          AudioManager.SCO_AUDIO_STATE_ERROR -> {
            scoConnected = false
            waitingCallback?.invoke(false)
            waitingCallback = null
          }
        }
      }
    }

  fun initialize() {
    if (initialized) return
    context.registerReceiver(scoReceiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
    initialized = true
  }

  @Suppress("DEPRECATION")
  @SuppressLint("MissingPermission")
  suspend fun startScoAndWait(timeoutMs: Long = 3_000): Boolean {
    if (!initialized) initialize()
    if (!audioManager.isBluetoothScoAvailableOffCall) {
      Log.w(tag, "Bluetooth SCO unavailable off-call")
      return false
    }
    if (scoConnected) return true

    return try {
      audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
      audioManager.startBluetoothSco()
      audioManager.isBluetoothScoOn = true
      withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
          waitingCallback = { ok ->
            if (continuation.isActive) continuation.resume(ok)
          }
          continuation.invokeOnCancellation {
            waitingCallback = null
          }
        }
      } == true || scoConnected
    } catch (err: Throwable) {
      Log.w(tag, "startScoAndWait failed: ${err.message ?: err::class.java.simpleName}")
      false
    }
  }

  @Suppress("DEPRECATION")
  fun stopSco() {
    try {
      audioManager.stopBluetoothSco()
      audioManager.isBluetoothScoOn = false
      audioManager.mode = AudioManager.MODE_NORMAL
      scoConnected = false
    } catch (err: Throwable) {
      Log.w(tag, "stopSco failed: ${err.message ?: err::class.java.simpleName}")
    }
  }

  fun release() {
    stopSco()
    if (initialized) {
      try {
        context.unregisterReceiver(scoReceiver)
      } catch (_: Throwable) {
        // ignore
      }
      initialized = false
    }
  }
}
