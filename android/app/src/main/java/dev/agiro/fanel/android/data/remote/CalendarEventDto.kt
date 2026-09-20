package dev.agiro.fanel.android.data.remote

data class CalendarEventDto(
    val id: String,
    val householdId: String,
    val title: String,
    val date: String,
    val anchorDate: String?,
    val time: String?,
    val durationMinutes: Int?,
    val addedBy: String?,
    val assigneeIds: List<String>?,
    val recurrenceFreq: String?,
    val recurrenceInterval: Int?,
    val recurrenceUntil: String?
)
