package observability.admin.domain

import kotlinx.serialization.Serializable

@Serializable
data class Member(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val teamIds: List<String>,
)
