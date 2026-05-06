package observability.common.stat

import observability.common.model.StorageEntity
import java.math.BigDecimal

data class Stat<E : StorageEntity>(
    val value: BigDecimal,
    val statType: StatType,
    val storageEntity: E,
    val unixTimestamp: Long,
)
