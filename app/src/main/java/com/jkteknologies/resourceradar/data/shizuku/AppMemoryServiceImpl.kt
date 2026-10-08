package com.jkteknologies.resourceradar.data.shizuku

import android.app.ActivityManager
import android.content.Context

/**
 * Runs INSIDE the Shizuku server process with shell/root identity (005
 * research.md R-02, data-model §6) — the analyzer never instantiates it;
 * Shizuku loads it by name and passes a Context (the server-v13 constructor
 * the R-03 floor guarantees). There the public SDK's uid-based restrictions
 * do not apply (`com.android.shell` holds `REAL_GET_TASKS`), so the plain
 * `ActivityManager` calls legitimately see every process and its PSS.
 *
 * This one read-only method is the feature's entire privileged surface
 * (FR-010): anything beyond reading memory information does not belong here.
 * Failures throw to the caller — the analyzer side maps them to the null
 * pass (contract clause 2).
 */
class AppMemoryServiceImpl(private val context: Context) : IAppMemoryService.Stub() {

    override fun readRunningProcessMemory(): List<AppProcessMemory> {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val processes = activityManager.runningAppProcesses.orEmpty().filter { it.pid > 0 }
        if (processes.isEmpty()) return emptyList()
        val pids = processes.map { it.pid }.toIntArray()
        val memoryInfos = activityManager.getProcessMemoryInfo(pids) ?: return emptyList()
        // getProcessMemoryInfo returns results aligned with the requested pid order.
        return processes.mapIndexedNotNull { index, process ->
            val totalPssKb = memoryInfos.getOrNull(index)?.totalPss
            val processName = process.processName
            if (totalPssKb == null || processName.isNullOrBlank()) {
                null // a process that vanished between listing and reading contributes nothing
            } else {
                // totalPss is KB — bytes from here on (contract clause 2).
                AppProcessMemory(processName, totalPssKb.toLong() * 1024L)
            }
        }
    }
}
