package ai.openclaw.android.rbm

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import com.meta.wearable.dat.camera.StreamSession
import com.meta.wearable.dat.camera.startStreamSession
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.camera.types.VideoFrame
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RbmDatManager(
  private val context: Context,
) {
  companion object {
    private const val tag = "OpenClaw/RbmDatManager"
    private const val frameIntervalMs = 1_000L
    private const val jpegQuality = 80
  }

  private val scopeJob = SupervisorJob()
  private val scope = CoroutineScope(scopeJob + Dispatchers.Default)
  private val deviceSelector = AutoDeviceSelector()
  private val frameStore = RbmFrameStore()

  private var streamSession: StreamSession? = null
  private var videoJob: Job? = null
  private var stateJob: Job? = null
  private var lastFrameAtMs = 0L

  private val _registrationState =
    MutableStateFlow<RegistrationState>(RegistrationState.Unavailable())
  val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

  private val _devices = MutableStateFlow<List<DeviceIdentifier>>(emptyList())
  val devices: StateFlow<List<DeviceIdentifier>> = _devices.asStateFlow()

  private val _streamState = MutableStateFlow(StreamSessionState.STOPPED)
  val streamState: StateFlow<StreamSessionState> = _streamState.asStateFlow()

  private val _latestFrame = MutableStateFlow<RbmFrame?>(null)
  val latestFrame: StateFlow<RbmFrame?> = _latestFrame.asStateFlow()

  init {
    scope.launch {
      Wearables.registrationState.collect { state ->
        _registrationState.value = state
      }
    }
    scope.launch {
      Wearables.devices.collect { set ->
        _devices.value = set.toList()
      }
    }
  }

  fun isGlassesAvailable(): Boolean {
    val isRegistered = registrationState.value is RegistrationState.Registered
    return isRegistered && devices.value.isNotEmpty()
  }

  fun startRegistration() {
    Wearables.startRegistration(context)
  }

  fun startUnregistration() {
    Wearables.startUnregistration(context)
  }

  suspend fun checkCameraPermission(): PermissionStatus {
    return Wearables.checkPermissionStatus(Permission.CAMERA)
  }

  suspend fun ensureCameraPermission(
    requestPermission: (suspend (Permission) -> PermissionStatus)? = null,
  ): PermissionStatus {
    var status = checkCameraPermission()
    if (status == PermissionStatus.Granted) return status

    if (requestPermission != null) {
      status = requestPermission(Permission.CAMERA)
    } else {
      startRegistration()
    }
    return status
  }

  fun startStreaming(highResolution: Boolean) {
    if (streamSession != null) return
    if (!isGlassesAvailable()) {
      Log.w(tag, "startStreaming ignored: glasses unavailable")
      return
    }

    val quality = if (highResolution) VideoQuality.HIGH else VideoQuality.MEDIUM
    val config =
      StreamConfiguration(
        videoQuality = quality,
        frameRate = 24,
      )

    lastFrameAtMs = 0L
    streamSession = Wearables.startStreamSession(context, deviceSelector, config)

    videoJob =
      scope.launch {
        streamSession?.videoStream?.collect { frame ->
          processFrame(frame)
        }
      }

    stateJob =
      scope.launch {
        streamSession?.state?.collect { state ->
          _streamState.value = state
        }
      }
  }

  fun stopStreaming() {
    videoJob?.cancel()
    videoJob = null
    stateJob?.cancel()
    stateJob = null
    streamSession?.close()
    streamSession = null
    _streamState.value = StreamSessionState.STOPPED
  }

  fun latestFreshFrame(maxAgeMs: Long): RbmFrame? = frameStore.latestFresh(maxAgeMs)

  fun release() {
    stopStreaming()
    scopeJob.cancel()
  }

  private fun processFrame(frame: VideoFrame) {
    val now = System.currentTimeMillis()
    if (now - lastFrameAtMs < frameIntervalMs) return
    lastFrameAtMs = now

    val jpeg = videoFrameToJpeg(frame) ?: return
    val (width, height) = decodeDimensions(jpeg)
    val frameData =
      RbmFrame(
        jpegData = jpeg,
        capturedAtMs = now,
        width = width,
        height = height,
      )
    frameStore.update(frameData)
    _latestFrame.value = frameData
  }

  private fun videoFrameToJpeg(frame: VideoFrame): ByteArray? {
    return try {
      val buffer = frame.buffer
      val data = ByteArray(buffer.remaining())
      val originalPosition = buffer.position()
      buffer.get(data)
      buffer.position(originalPosition)

      val nv21 = convertI420ToNv21(data, frame.width, frame.height)
      val yuv = YuvImage(nv21, ImageFormat.NV21, frame.width, frame.height, null)
      val output = ByteArrayOutputStream()
      yuv.compressToJpeg(Rect(0, 0, frame.width, frame.height), jpegQuality, output)
      output.toByteArray()
    } catch (err: Throwable) {
      Log.w(tag, "videoFrameToJpeg failed: ${err.message ?: err::class.java.simpleName}")
      null
    }
  }

  private fun decodeDimensions(jpeg: ByteArray): Pair<Int, Int> {
    val options =
      BitmapFactory.Options().apply {
        inJustDecodeBounds = true
      }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
    return options.outWidth to options.outHeight
  }

  private fun convertI420ToNv21(input: ByteArray, width: Int, height: Int): ByteArray {
    val output = ByteArray(input.size)
    val ySize = width * height
    val uvSize = ySize / 4

    input.copyInto(output, destinationOffset = 0, startIndex = 0, endIndex = ySize)

    for (i in 0 until uvSize) {
      output[ySize + (i * 2)] = input[ySize + uvSize + i]
      output[ySize + (i * 2) + 1] = input[ySize + i]
    }

    return output
  }
}
