package ai.openclaw.android.rbm

import ai.openclaw.android.chat.OutgoingAttachment
import java.util.Base64

object RbmTalkAttachmentFactory {
  fun buildLatestImageAttachment(
    frame: RbmFrame?,
    nowMs: Long,
    maxAgeMs: Long = 4_000,
  ): OutgoingAttachment? {
    if (frame == null) return null
    val age = nowMs - frame.capturedAtMs
    if (age < 0 || age > maxAgeMs) return null

    return OutgoingAttachment(
      type = "image",
      mimeType = "image/jpeg",
      fileName = "rbm-${frame.capturedAtMs}.jpg",
      base64 = Base64.getEncoder().encodeToString(frame.jpegData),
    )
  }
}
