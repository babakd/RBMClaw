package ai.openclaw.android

import androidx.activity.ComponentActivity
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class WearablesPermissionRequester(activity: ComponentActivity) {
  private val mutex = Mutex()
  private var pending: CompletableDeferred<PermissionStatus>? = null

  private val launcher =
    activity.registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
      val current = pending
      pending = null
      current?.complete(result)
    }

  suspend fun request(permission: Permission, timeoutMs: Long = 30_000): PermissionStatus =
    mutex.withLock {
      val deferred = CompletableDeferred<PermissionStatus>()
      pending = deferred
      withContext(Dispatchers.Main) {
        launcher.launch(permission)
      }
      val status =
        withContext(Dispatchers.Default) {
          withTimeoutOrNull(timeoutMs) { deferred.await() } ?: PermissionStatus.Denied
        }
      if (pending === deferred) {
        pending = null
      }
      status
    }
}
