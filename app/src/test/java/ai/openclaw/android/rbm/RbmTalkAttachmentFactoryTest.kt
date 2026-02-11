package ai.openclaw.android.rbm

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RbmTalkAttachmentFactoryTest {
  @Test
  fun buildLatestImageAttachmentReturnsNullWhenFrameMissing() {
    val attachment =
      RbmTalkAttachmentFactory.buildLatestImageAttachment(
        frame = null,
        nowMs = 1_000L,
      )

    assertNull(attachment)
  }

  @Test
  fun buildLatestImageAttachmentReturnsNullWhenFrameIsStale() {
    val frame =
      RbmFrame(
        jpegData = byteArrayOf(1, 2, 3),
        capturedAtMs = 1_000L,
        width = 640,
        height = 360,
      )

    val attachment =
      RbmTalkAttachmentFactory.buildLatestImageAttachment(
        frame = frame,
        nowMs = 8_000L,
        maxAgeMs = 4_000L,
      )

    assertNull(attachment)
  }

  @Test
  fun buildLatestImageAttachmentBuildsJpegAttachmentForFreshFrame() {
    val frameBytes = byteArrayOf(7, 8, 9, 10)
    val frame =
      RbmFrame(
        jpegData = frameBytes,
        capturedAtMs = 2_000L,
        width = 1280,
        height = 720,
      )

    val attachment =
      RbmTalkAttachmentFactory.buildLatestImageAttachment(
        frame = frame,
        nowMs = 4_000L,
        maxAgeMs = 4_000L,
      )

    assertNotNull(attachment)
    assertEquals("image", attachment?.type)
    assertEquals("image/jpeg", attachment?.mimeType)
    assertEquals("rbm-2000.jpg", attachment?.fileName)
    val decoded = Base64.getDecoder().decode(attachment?.base64)
    assertArrayEquals(frameBytes, decoded)
  }
}
