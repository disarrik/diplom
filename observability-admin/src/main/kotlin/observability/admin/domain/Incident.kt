package observability.admin.domain

import kotlinx.serialization.Serializable

@Serializable
enum class IncidentStatus { open, resolved }

@Serializable
data class Incident(
    val id: String,
    val title: String,
    val incidentType: String,
    val rootDsId: String,
    val affectedDsIds: List<String>,
    val teamId: String?,
    val assigneeId: String?,
    val status: IncidentStatus,
    val openedAt: String,
    val resolvedAt: String? = null,
    val integrations: List<Integration> = emptyList(),
    val events: List<IncidentEvent> = emptyList(),
)
