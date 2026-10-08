// Feature 005 (contracts/shizuku-memory.md, FR-010): the feature's ENTIRE
// privileged surface — one batched, read-only round trip executed with the
// Shizuku server's shell/root identity.
package com.jkteknologies.androidanalyzer.data.shizuku;

import com.jkteknologies.androidanalyzer.data.shizuku.AppProcessMemory;

interface IAppMemoryService {
    List<AppProcessMemory> readRunningProcessMemory();
}
