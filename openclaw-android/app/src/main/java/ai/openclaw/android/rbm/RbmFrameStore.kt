package ai.openclaw.android.rbm

class RbmFrameStore(
  private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
  @Volatile private var latest: RbmFrame? = null

  fun update(frame: RbmFrame) {
    latest = frame
  }

  fun latest(): RbmFrame? = latest

  fun latestFresh(maxAgeMs: Long): RbmFrame? {
    val frame = latest ?: return null
    val age = nowMs() - frame.capturedAtMs
    return if (age in 0..maxAgeMs) frame else null
  }
}
