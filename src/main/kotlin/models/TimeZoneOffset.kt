package models

data class TimeZoneOffset(
    val offsetHours: Long,
    val offsetMinutes: Long,
    val totalOffsetInMillis: Long = ((offsetHours * 60) + offsetMinutes) * 60 * 1000
)
