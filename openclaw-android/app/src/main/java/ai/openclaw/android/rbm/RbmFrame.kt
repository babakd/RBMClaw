package ai.openclaw.android.rbm

data class RbmFrame(
  val jpegData: ByteArray,
  val capturedAtMs: Long,
  val width: Int,
  val height: Int,
)
