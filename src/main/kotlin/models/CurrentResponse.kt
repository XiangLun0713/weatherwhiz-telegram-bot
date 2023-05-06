package models

import kotlinx.serialization.Serializable

@Serializable
data class CurrentResponse(
    val location: LocationResponse,
    val current: CurrentWeatherResponse
)