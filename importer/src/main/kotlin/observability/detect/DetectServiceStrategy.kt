package observability.detect

import observability.common.model.DataIncident
import observability.common.model.stat.Stat
import observability.common.model.stat.StatType

interface DetectServiceStrategy<T> {
    fun detect(stats: List<Stat<T, *>>): List<DataIncident>
    fun supports(): StatType<T>
}