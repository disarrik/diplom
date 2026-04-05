package observability.std.importer.storage

import observability.std.importer.stat.Stat
import observability.std.importer.stat.StatType

interface StatStore {
    fun append(stat: Stat<*, *>)

    fun <T> history(statType: StatType<T>): List<Stat<T, *>>
}
