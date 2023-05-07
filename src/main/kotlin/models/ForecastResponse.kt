package models

data class ForecastResponse(
    val location: Location,
    val current: CurrentResponse,
    val forecast: Forecast,
    val alert: Alerts
)