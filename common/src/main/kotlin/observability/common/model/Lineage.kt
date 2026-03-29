package observability.common.model

import java.util.UUID

data class Lineage(
    val id: UUID,
    val sources: List<StorageEntity>,
    val targets: List<StorageEntity>,
    val lineageType: LineageType,
    val unixTimestamp: Long,
)