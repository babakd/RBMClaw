package ai.openclaw.android.rbm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RbmFrameStoreTest {
  @Test
  fun latestFreshReturnsFrameWithinWindow() {
    val nowMs = 10_000L
    val store = RbmFrameStore(nowMs = { nowMs })
    val frame =
      RbmFrame(
        jpegData = byteArrayOf(1, 2, 3),
        capturedAtMs = 9_600L,
        width = 640,
        height = 360,
      )

    store.update(frame)
    val fresh = store.latestFresh(maxAgeMs = 500L)

    assertNotNull(fresh)
    assertEquals(640, fresh?.width)
    assertEquals(360, fresh?.height)
  }

  @Test
  fun latestFreshReturnsNullWhenStale() {
    val nowMs = 20_000L
    val store = RbmFrameStore(nowMs = { nowMs })
    store.update(
      RbmFrame(
        jpegData = byteArrayOf(9),
        capturedAtMs = 18_000L,
        width = 1280,
        height = 720,
      ),
    )

    val stale = store.latestFresh(maxAgeMs = 1_000L)
    assertNull(stale)
  }

  @Test
  fun latestFreshReturnsNullForFutureTimestamp() {
    val nowMs = 5_000L
    val store = RbmFrameStore(nowMs = { nowMs })
    store.update(
      RbmFrame(
        jpegData = byteArrayOf(5),
        capturedAtMs = 5_500L,
        width = 320,
        height = 240,
      ),
    )

    val fresh = store.latestFresh(maxAgeMs = 1_000L)
    assertNull(fresh)
  }
}
