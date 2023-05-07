package models

import kotlinx.serialization.Serializable

@Serializable
data class Alerts(
    val alert: List<Alert>
)
