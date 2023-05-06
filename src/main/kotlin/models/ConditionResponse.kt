package models

import kotlinx.serialization.Serializable

@Serializable
data class ConditionResponse(
    val text: String,
    val icon: String,
    val code: Int
)