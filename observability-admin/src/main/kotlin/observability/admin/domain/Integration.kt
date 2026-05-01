package observability.admin.domain

import kotlinx.serialization.Serializable

@Serializable
data class Integration(
    val type: String,
    val label: String,
    val url: String,
    val pluginId: String? = null,
    val extra: Map<String, String> = emptyMap(),
)
