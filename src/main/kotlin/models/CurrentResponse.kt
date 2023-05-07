package models

import kotlinx.serialization.Serializable

@Serializable
data class CurrentResponse(
    val location: Location,
    val current: Current
)