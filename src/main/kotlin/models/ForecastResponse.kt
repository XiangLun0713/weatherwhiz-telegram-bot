package models

import kotlinx.serialization.Serializable

@Serializable
data class ForecastResponse(
    val location: Location,
    val current: Current,
    val forecast: Forecast,
    val alerts: Alerts
)