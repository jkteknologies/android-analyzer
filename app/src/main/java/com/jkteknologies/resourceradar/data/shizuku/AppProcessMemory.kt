package com.jkteknologies.resourceradar.data.shizuku

import android.os.Parcel
import android.os.Parcelable
import com.jkteknologies.resourceradar.domain.ProcessMemory

/**
 * The AIDL parcelable (005 data-model §6): one process's reading marshalled
 * across the UserService boundary. Hand-written CREATOR — no kotlin-parcelize
 * dependency (research.md R-11).
 */
data class AppProcessMemory(
    val processName: String,
    val pssBytes: Long,
) : Parcelable {

    private constructor(parcel: Parcel) : this(
        processName = parcel.readString() ?: "",
        pssBytes = parcel.readLong(),
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(processName)
        parcel.writeLong(pssBytes)
    }

    /** Maps to the pure domain value — the reader aggregates app-side (R-06). */
    fun toDomain(): ProcessMemory = ProcessMemory(processName, pssBytes)

    companion object CREATOR : Parcelable.Creator<AppProcessMemory> {
        override fun createFromParcel(parcel: Parcel): AppProcessMemory = AppProcessMemory(parcel)

        override fun newArray(size: Int): Array<AppProcessMemory?> = arrayOfNulls(size)
    }
}
