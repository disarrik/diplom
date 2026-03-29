package observability.common.model.event

import observability.common.model.StorageEntity

data class DataChangeEvent(
    val unixTimestamp: Long,
    val data: StorageEntity,
    val changeType: String,
)