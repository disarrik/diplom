package observability.admin.ingest

import kotlinx.serialization.Serializable

@Serializable
data class StorageEntityDto(
    val kind: String,
    val namespace: String,
    val name: String,
    val field: String? = null,
)

@Serializable
data class IncidentEventDto(
    val incidentId: String,
    val incidentType: String,
    val rootEntity: StorageEntityDto,
    val affectedEntity: StorageEntityDto,
    val finished: Boolean,
    val at: String,
)
