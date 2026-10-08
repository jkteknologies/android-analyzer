package com.jkteknologies.resourceradar.data

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File
import com.jkteknologies.resourceradar.data.shizuku.ShizukuAccess
import com.jkteknologies.resourceradar.data.shizuku.ShizukuMemorySource
import com.jkteknologies.resourceradar.domain.AppClassification
import com.jkteknologies.resourceradar.domain.AppInventory
import com.jkteknologies.resourceradar.domain.InstalledApp
import com.jkteknologies.resourceradar.domain.ApplicationInventory
import com.jkteknologies.resourceradar.domain.BatteryReading
import com.jkteknologies.resourceradar.domain.CoreCount
import com.jkteknologies.resourceradar.domain.CoreTier
import com.jkteknologies.resourceradar.domain.CoreTiers
import com.jkteknologies.resourceradar.domain.MemoryReading
import com.jkteknologies.resourceradar.domain.ShizukuAccessState
import com.jkteknologies.resourceradar.domain.StorageReading
import com.jkteknologies.resourceradar.domain.memoryBytesFor

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

    /**
     * Usage-access appop check (004 R-02): reflects only the current grant
     * state — the app declares `PACKAGE_USAGE_STATS`, the user flips it on the
     * Settings usage-access page. `unsafeCheckOpNoThrow` reports without
     * throwing.
     */
    val usageAccessStatus: UsageAccessStatus = UsageAccessStatus {
        val appOps = appContext.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            appContext.applicationInfo.uid,
            appContext.packageName,
        )
        mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 005 (R-03/R-04, contracts/shizuku-memory.md): the Shizuku seams for the
     * Details wiring — state ladder, authorization forwarder, change source —
     * plus the privileged memory source the inventory read merges below.
     */
    private val shizukuAccess = ShizukuAccess(appContext)
    private val shizukuMemorySource = ShizukuMemorySource(appContext)

    val shizukuAccessStatus: ShizukuAccessStatus = shizukuAccess.status
    val shizukuAuthorizer: ShizukuAuthorizer = shizukuAccess.authorizer
    val shizukuChangeSource: ShizukuChangeSource = shizukuAccess.changeSource

    /**
     * Full inventory read (004 R-01/R-02, contract clauses 1–2): one
     * `getInstalledApplications(0)` enumeration; display names via
     * `loadLabel`, classification through the shared [isSystemApplication]
     * bit test — and only while usage access is granted, one
     * `StorageStatsManager.queryStatsForPackage` per package (code + data +
     * cache) from the same snapshot. Without the grant no per-package storage
     * call is made and every `storageBytes` is `null`; a granted but failing
     * per-package query leaves that one app's figure `null` without failing
     * the inventory.
     */
    val installedAppReader: InstalledAppReader = InstalledAppReader {
        val usageGranted = usageAccessStatus.granted()
        val storageStats =
            if (usageGranted) appContext.getSystemService(StorageStatsManager::class.java) else null
        // 005 (R-06, contract clause 1): one state read per pass; the privileged
        // memory read happens ONLY when authorized — otherwise no snapshot is
        // requested and every memoryBytes stays null (FR-006).
        val memorySnapshot = if (shizukuAccessStatus.state() == ShizukuAccessState.AUTHORIZED) {
            shizukuMemorySource.read()
        } else {
            null
        }
        val user = Process.myUserHandle()
        val apps: List<InstalledApp?> = appContext.packageManager.getInstalledApplications(0).map { info ->
            val storageBytes = storageStats?.runCatching {
                queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, user)
                    .let { it.appBytes + it.dataBytes + it.cacheBytes }
            }?.getOrNull()
            InstalledApp.create(
                packageName = info.packageName,
                displayName = info.loadLabel(appContext.packageManager).toString(),
                classification = if (isSystemApplication(info.flags)) {
                    AppClassification.SYSTEM
                } else {
                    AppClassification.USER
                },
                storageBytes = storageBytes,
                // 005 R-06: the snapshot's absent key is a truthful zero (installed,
                // not running); a failed pass or failed figure is null — never a
                // wrong number (FR-002/FR-006/FR-008).
                memoryBytes = memoryBytesFor(memorySnapshot, info.packageName),
            )
        }
        // One invalid entry fails the whole read (never-clamp, 004 FR-010) — no partial inventories.
        if (apps.any { it == null }) return@InstalledAppReader null
        AppInventory.create(apps.filterNotNull())
    }
    /**
     * Processor core tiers (004 R-04, contract clause 3): groups logical cores
     * by their sysfs `cpuinfo_max_freq`. Fallback INSIDE the reader — a failed
     * or partial walk, a frequency-set disagreeing with the core count, or a
     * single frequency for all cores yields the single-tier
     * `CoreTiers(Runtime.availableProcessors())`; `null` only if even that
     * fails (which cannot practically happen — `availableProcessors()` ≥ 1).
     */
    val coreTierReader: CoreTierReader = CoreTierReader {
        val freqs = File("/sys/devices/system/cpu")
            .listFiles { _, name -> name.startsWith("cpu") && name.drop(3).all { it.isDigit() } }
            .orEmpty()
            .mapNotNull { cpu ->
                File(cpu, "cpufreq/cpuinfo_max_freq").takeIf { it.canRead() }
                    ?.readText()?.trim()?.toLongOrNull()
            }
            .filter { it > 0 }
        val total = Runtime.getRuntime().availableProcessors()
        // ponytail: the fallback tier carries a placeholder frequency (1 Hz) — a
        // single tier renders as the plain count, so the value never displays;
        // swap for a real cluster API if Android ever exposes one.
        val fallback = listOf(CoreTier(total, 1L))
        CoreTiers.create(
            totalCount = total,
            tiers = freqs.groupingBy { it }.eachCount().map { (hz, n) -> CoreTier(n, hz) },
        ) ?: CoreTiers.create(total, fallback)
    }
}
