package com.jkteknologies.androidanalyzer.domain

/**
 * The Shizuku access ladder's value (005 FR-004 plus the spec's Outdated edge
 * case, research.md R-03): exactly what the Details guidance row renders.
 */
enum class ShizukuAccessState {
    /** No Shizuku package on the device. */
    NOT_INSTALLED,

    /** Installed, but its server is not running (the routine post-reboot state). */
    NOT_RUNNING,

    /** Server older than v13 — the UserService route needs the v13 protocol. */
    OUTDATED,

    /** Running, but this app is not granted in Shizuku. */
    AWAITING_AUTHORIZATION,

    /** Running and granted — per-app memory figures flow. */
    AUTHORIZED,
}

/**
 * One running process's privileged reading (005 data-model §2): the raw
 * material of the per-app memory figure.
 */
data class ProcessMemory(
    val processName: String,
    val pssBytes: Long,
)

/**
 * Aggregates per-process readings into the per-package snapshot (V-S1): a
 * process belongs to the package named by its process name before the first
 * ':' — package names cannot contain ':', so `pkg` and `pkg:service` both
 * resolve to `pkg` (005 FR-002); the entries of one package sum. A negative
 * `pssBytes` on any entry of a package maps that package to `null` — never a
 * negative sum (005 FR-008). Blank process names attribute to nothing and are
 * dropped.
 */
fun aggregateProcessMemory(processes: List<ProcessMemory>): Map<String, Long?> {
    val sums = HashMap<String, Long>()
    val invalid = HashSet<String>()
    for (process in processes) {
        if (process.processName.isBlank()) continue
        val owner = process.processName.substringBefore(':')
        if (process.pssBytes < 0) {
            invalid.add(owner)
        } else {
            sums.merge(owner, process.pssBytes, Long::plus)
        }
    }
    return (sums.keys + invalid).associateWith { owner ->
        if (owner in invalid) null else sums[owner]
    }
}

/**
 * The single merge rule between a snapshot and one inventory row (V-S2, 005
 * FR-002/FR-006/FR-008): a null snapshot (pass unavailable) is not available;
 * an absent package is installed-but-not-running — a truthful zero; a present
 * null is a per-app read failure; a present negative value fails validation;
 * otherwise the value stands.
 */
fun memoryBytesFor(snapshot: Map<String, Long?>?, packageName: String): Long? = when {
    snapshot == null -> null
    !snapshot.containsKey(packageName) -> 0L
    else -> snapshot[packageName]?.takeIf { it >= 0 }
}
