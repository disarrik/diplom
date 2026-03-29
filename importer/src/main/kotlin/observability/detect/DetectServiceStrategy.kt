package observability.detect

import observability.common.model.event.DataChangeEvent
import observability.common.model.stat.Stat
import observability.common.model.stat.StatType

interface DetectServiceStrategy<T> {
    fun detect(stats: List<Stat<T, *>>): List<DataChangeEvent>
    fun supports(): StatType<T>
}