package observability.std.importer.stat

import observability.common.model.StorageEntity

data class Stat<T, E : StorageEntity>(
    val value: T,
    val statType: StatType<T>,
    val storageEntity: E,
    val unixTimestamp: Long,
)
