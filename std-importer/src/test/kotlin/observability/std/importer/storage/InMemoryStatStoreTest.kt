package observability.std.importer.storage

import observability.common.model.TableStorageEntity
import observability.std.importer.stat.Stat
import observability.std.importer.stat.StatType
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryStatStoreTest {

    private val statType = StatType(Long::class.java, "test.metric")

    @Test
    fun `append and history ordered by timestamp`() {
        val store = InMemoryStatStore()
        val table = TableStorageEntity("ns", "t")
        store.append(Stat(1L, statType, table, 100L))
        store.append(Stat(3L, statType, table, 300L))
        store.append(Stat(2L, statType, table, 200L))

        val history = store.history(statType)
        assertEquals(listOf(1L, 2L, 3L), history.map { it.value })
    }

    @Test
    fun `isolates stat type ids`() {
        val store = InMemoryStatStore()
        val table = TableStorageEntity("ns", "t")
        val otherType = StatType(Long::class.java, "other.metric")
        store.append(Stat(1L, statType, table, 1L))
        store.append(Stat(9L, otherType, table, 1L))

        assertEquals(1, store.history(statType).size)
        assertEquals(9L, store.history(otherType).single().value)
    }
}
