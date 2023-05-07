package models

import kotlinx.serialization.Serializable

@Serializable
data class TimezoneResponse(
    val location: Location
)