package observability.std.importer.storage

import observability.std.importer.stat.Stat
import observability.std.importer.stat.StatType
import java.util.concurrent.ConcurrentHashMap

class InMemoryStatStore : StatStore {

    private val byStatTypeId = ConcurrentHashMap<String, MutableList<Stat<*, *>>>()

    override fun append(stat: Stat<*, *>) {
        val list = byStatTypeId.computeIfAbsent(stat.statType.statTypeId) { mutableListOf() }
        synchronized(list) {
            list.add(stat)
            list.sortBy { it.unixTimestamp }
        }
    }

    override fun <T> history(statType: StatType<T>): List<Stat<T, *>> {
        val list = byStatTypeId[statType.statTypeId] ?: return emptyList()
        synchronized(list) {
            @Suppress("UNCHECKED_CAST")
            return list.map { it as Stat<T, *> }
        }
    }
}
