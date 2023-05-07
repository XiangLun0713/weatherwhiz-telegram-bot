package models

import kotlinx.serialization.Serializable

@Serializable
data class ForecastDay(
    val date: String,
    val date_epoch: Long,
    val day: Day,
    val astro: Astro,
    val hour: List<Hour>
)
