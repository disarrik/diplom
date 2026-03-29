package observability.common.model.event

import observability.common.model.StorageEntity

data class LineageEvent(
    val sources: List<StorageEntity>,
    val targets: List<StorageEntity>,
    val lineageType: LineageType,
    val unixTimestamp: Long,
)