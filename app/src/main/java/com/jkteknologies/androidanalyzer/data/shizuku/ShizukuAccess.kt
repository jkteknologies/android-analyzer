package com.jkteknologies.androidanalyzer.data.shizuku

import android.content.Context
import android.content.pm.PackageManager
import com.jkteknologies.androidanalyzer.data.ShizukuAccessStatus
import com.jkteknologies.androidanalyzer.data.ShizukuAuthorizer
import com.jkteknologies.androidanalyzer.data.ShizukuChangeSource
import com.jkteknologies.androidanalyzer.domain.ShizukuAccessState
import rikka.shizuku.Shizuku

/**
 * The app's entire `rikka.shizuku.*` import surface (005 research.md
 * R-03/R-04): the state ladder, the authorization forwarder, and the change
 * source — stateless adapters over the library's static API. JVM unit tests
 * fake the seams (DeviceReaders.kt) and never touch this file (R-10).
 */
class ShizukuAccess(private val appContext: Context) {

    /**
     * The R-03 ladder — total and terminating, never throws (contract
     * clause 3): every post-ping library throw collapses to NOT_RUNNING
     * (T020 — a dead binder is never "outdated" or "awaiting"), and a failed
     * installed-check is not installed. May perform binder work; belongs on
     * the background executor like the readers.
     */
    val status: ShizukuAccessStatus = ShizukuAccessStatus {
        when {
            !isInstalled() -> ShizukuAccessState.NOT_INSTALLED
            !pingBinder() -> ShizukuAccessState.NOT_RUNNING
            else -> postPingState()
        }
    }

    /**
     * FR-005: forwards to Shizuku's own permission dialog — fire-and-forget.
     * The result never appears here; it arrives as a change-source event and
     * the actual state is re-read from [status]. May throw if the binder died
     * between the state check and the tap — the holder swallows that (R-04).
     */
    val authorizer: ShizukuAuthorizer = ShizukuAuthorizer {
        Shizuku.requestPermission(REQUEST_PERMISSION_CODE)
    }

    /**
     * Binder received (sticky — fires immediately when already received),
     * binder dead, and permission-request results, collapsed into one
     * onChange dispatched on the main thread (contract clause 7). The
     * returned lambda removes all three listeners.
     */
    val changeSource: ShizukuChangeSource = ShizukuChangeSource { onChange ->
        val received = Shizuku.OnBinderReceivedListener { onChange() }
        val dead = Shizuku.OnBinderDeadListener { onChange() }
        val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> onChange() }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        val unsubscribe: () -> Unit = {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
        unsubscribe
    }

    private fun isInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: Throwable) {
        false // NameNotFoundException = not installed; anything else is not usable either
    }

    private fun pingBinder(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    /**
     * The post-ping steps (T020, contract clause 3): the version floor (the
     * v13 protocol the UserService route needs, R-03) then the permission
     * check — one try, so a binder that died between `pingBinder` and either
     * call maps to NOT_RUNNING instead of wrong guidance.
     */
    private fun postPingState(): ShizukuAccessState = try {
        when {
            Shizuku.isPreV11() || Shizuku.getVersion() < 13 -> ShizukuAccessState.OUTDATED
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                ShizukuAccessState.AWAITING_AUTHORIZATION
            else -> ShizukuAccessState.AUTHORIZED
        }
    } catch (_: Throwable) {
        ShizukuAccessState.NOT_RUNNING
    }

    companion object {
        /** The Shizuku manager application's package name (R-05). */
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

        private const val REQUEST_PERMISSION_CODE = 1
    }
}
