// Feature 005 (contracts/shizuku-memory.md): one process's privileged memory
// reading, marshalled across the UserService boundary. The Kotlin class with
// this exact FQN (data/shizuku/AppProcessMemory.kt) provides the CREATOR.
package com.jkteknologies.resourceradar.data.shizuku;

parcelable AppProcessMemory;
