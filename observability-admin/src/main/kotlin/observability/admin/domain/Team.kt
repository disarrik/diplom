package observability.admin.domain

import kotlinx.serialization.Serializable

@Serializable
data class Team(
    val id: String,
    val name: String,
    val handle: String,
    val extensions: Map<String, Map<String, String>> = emptyMap(),
)
