package observability.admin.domain

import kotlinx.serialization.Serializable

@Serializable
data class IncidentEvent(
    val type: String,
    val at: String,
    val actor: String,
    val text: String,
    val detail: String? = null,
)
