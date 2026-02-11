package ai.openclaw.android

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.meta.wearable.dat.core.Wearables

class NodeApp : Application() {
  companion object {
    private const val tag = "OpenClaw/NodeApp"
  }

  val runtime: NodeRuntime by lazy { NodeRuntime(this) }

  override fun onCreate() {
    super.onCreate()
    try {
      Wearables.initialize(this)
      Log.d(tag, "Meta Wearables DAT initialized")
    } catch (err: Throwable) {
      Log.e(tag, "Meta Wearables DAT init failed: ${err.message ?: err::class.java.simpleName}")
    }

    if (BuildConfig.DEBUG) {
      StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .build(),
      )
      StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .build(),
      )
    }
  }
}
