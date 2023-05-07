package models

import kotlinx.serialization.Serializable

@Serializable
data class Forecast(
    val forecastday: List<ForecastDay>,
)