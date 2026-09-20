package dev.agiro.fanel.android.data.local

import androidx.room.Entity

@Entity(tableName = "calendar_events", primaryKeys = ["householdId", "occurrenceKey"])
data class CalendarEventEntity(
    val householdId: String,
    val occurrenceKey: String,
    val localId: String,
    val remoteId: String?,
    val title: String,
    val date: String,
    val anchorDate: String?,
    val time: String?,
    val durationMinutes: Int?,
    val addedBy: String?,
    val assigneeIds: String,
    val recurrenceFreq: String?,
    val recurrenceInterval: Int?,
    val recurrenceUntil: String?,
    val pendingStatus: String?
) {
    companion object {
        const val PENDING_CREATE = "PENDING_CREATE"
        const val PENDING_UPDATE = "PENDING_UPDATE"
        const val PENDING_DELETE = "PENDING_DELETE"
    }
}
