package models

import kotlinx.serialization.Serializable

@Serializable
data class Alert(
    val headline: String,
    val msgtype: String,
    val severity: String,
    val urgency: String,
    val areas: String,
    val category: String,
    val certainty: String,
    val event: String,
    val note: String,
    val effective: String,
    val expires: String,
    val desc: String,
    val instruction: String,
)
