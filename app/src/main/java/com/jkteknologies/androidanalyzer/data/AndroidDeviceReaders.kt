package com.jkteknologies.androidanalyzer.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import com.jkteknologies.androidanalyzer.domain.ApplicationInventory
import com.jkteknologies.androidanalyzer.domain.BatteryReading
import com.jkteknologies.androidanalyzer.domain.CoreCount
import com.jkteknologies.androidanalyzer.domain.MemoryReading
import com.jkteknologies.androidanalyzer.domain.StorageReading

/**
 * Platform-backed one-shot readers (contracts/device-readers.md; research.md
 * R-02..R-05, R-01). Each reader performs exactly ONE platform read, validates
 * it through the domain factories, and returns `null` on unavailable or
 * nonsensical results — no receivers registered, no caching, no state (FR-011).
 * Thrown platform exceptions are left to the caller (the home state holder)
 * per the common clauses.
 */
class AndroidDeviceReaders(context: Context) {

    private val appContext = context.applicationContext

    /** One `ActivityManager.getMemoryInfo` read (R-02): totalMem / availMem. */
    val memoryReader: MemoryReader = MemoryReader {
        val info = ActivityManager.MemoryInfo()
        appContext.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        MemoryReading.create(totalBytes = info.totalMem, availableBytes = info.availMem)
    }

    /** One `StatFs` read of the data partition (R-03): totalBytes / availableBytes (not freeBytes). */
    val storageReader: StorageReader = StorageReader {
        val stats = StatFs(Environment.getDataDirectory().absolutePath)
        StorageReading.create(totalBytes = stats.totalBytes, availableBytes = stats.availableBytes)
    }

    /**
     * One sticky `ACTION_BATTERY_CHANGED` read (R-04): `registerReceiver(null, …)`
     * returns the last sticky broadcast immediately without registering anything.
     * Null intent, negative level, or non-positive scale → `null`.
     */
    val batteryReader: BatteryReader = BatteryReader {
        val intent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return@BatteryReader null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0 || scale <= 0) {
            null
        } else {
            BatteryReading.create(
                levelPercent = level * 100 / scale,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL,
            )
        }
    }

    /** `Runtime.availableProcessors()` (R-05). */
    val coreCountReader: CoreCountReader = CoreCountReader {
        CoreCount.create(Runtime.getRuntime().availableProcessors())
    }

    /**
     * Full package enumeration (R-01): requires the declared
     * `QUERY_ALL_PACKAGES` permission; classification per [isSystemApplication]
     * (updated preinstalled apps keep FLAG_SYSTEM and stay system, FR-005).
     */
    val applicationCounter: ApplicationCounter = ApplicationCounter {
        val apps: List<ApplicationInfo> = appContext.packageManager.getInstalledApplications(0)
        val nonSystem = apps.count { !isSystemApplication(it.flags) }
        ApplicationInventory.create(nonSystemCount = nonSystem, totalCount = apps.size)
    }
}
