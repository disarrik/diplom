package observability.common.model

import java.util.UUID

data class DataIncident(
    val id: UUID,
    val data: StorageEntity,
    val incidentType: String,
    val unixTimestamp: Long,
)