package observability.admin.domain

import kotlinx.serialization.Serializable

@Serializable
data class Team(
    val id: String,
    val name: String,
    val handle: String,
    val slack: String,
)
