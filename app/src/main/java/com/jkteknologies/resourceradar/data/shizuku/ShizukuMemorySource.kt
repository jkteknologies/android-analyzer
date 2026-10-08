package com.jkteknologies.resourceradar.data.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.jkteknologies.resourceradar.data.AppMemoryReader
import com.jkteknologies.resourceradar.domain.aggregateProcessMemory
import rikka.shizuku.Shizuku

/**
 * The AppMemoryReader over the Shizuku UserService (005 research.md R-07,
 * contracts/shizuku-memory.md clause 6) — the reader family's one stateful
 * member, by documented exception: the IAppMemoryService connection is bound
 * lazily on the first read (the bind must run on the main thread — the
 * connection callbacks arrive there — and is awaited on the caller's
 * background executor, ≤ 5 s), then kept until a call fails (any Throwable
 * drops the cache; the next read rebinds) or the process ends. Never unbound
 * on Details disposal: the holder dies on every tab switch, and the server
 * owns the service process against our process's binder link.
 *
 * ponytail: no in-flight watchdog — a hung binder call blocks its own pass
 * until process death; real deaths surface as DeadObjectException and are
 * handled. Upgrade path: a read-timeout wrapper if hardware ever shows hangs.
 */
class ShizukuMemorySource(context: Context) : AppMemoryReader {

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, AppMemoryServiceImpl::class.java.name),
    ).version(1)

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var bound: IAppMemoryService? = null

    override fun read(): Map<String, Long?>? = try {
        val service = ensureBound() ?: return null
        val processes = service.readRunningProcessMemory()
        aggregateProcessMemory(processes.map { it.toDomain() })
    } catch (_: Throwable) {
        dropBound()
        null
    }

    private fun ensureBound(): IAppMemoryService? {
        bound?.let { return it }
        synchronized(this) {
            bound?.let { return it }
            val latch = CountDownLatch(1)
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    bound = IAppMemoryService.Stub.asInterface(service)
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    dropBound()
                }
            }
            main.post {
                try {
                    Shizuku.bindUserService(userServiceArgs, connection)
                } catch (_: Throwable) {
                    latch.countDown() // bind failed — this read yields null; the next rebinds
                }
            }
            latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return bound
        }
    }

    private fun dropBound() {
        bound = null
    }

    private companion object {
        const val BIND_TIMEOUT_SECONDS = 5L
    }
}
