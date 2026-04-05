package observability.storage.memory

import observability.common.StateService
import observability.common.model.DataIncident
import observability.common.model.StorageEntity
import java.util.UUID

class InMemoryStateService : StateService {

    private val children = mutableMapOf<StorageEntity, MutableSet<StorageEntity>>()
    private val parents = mutableMapOf<StorageEntity, MutableSet<StorageEntity>>()
    private val incidents = mutableMapOf<StorageEntity, MutableList<DataIncident>>()

    override fun getActiveIncidentsRecursively(storageEntity: StorageEntity): List<DataIncident> {
        val result = mutableListOf<DataIncident>()
        val visited = mutableSetOf<StorageEntity>()
        val queue = ArrayDeque<StorageEntity>()
        queue.add(storageEntity)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            incidents[current]?.let { result.addAll(it) }
            parents[current]?.forEach { queue.add(it) }
        }
        return result
    }

    override fun getChildrenRecursively(storageEntity: StorageEntity): List<StorageEntity> {
        val result = mutableListOf<StorageEntity>()
        val visited = mutableSetOf(storageEntity)
        val queue = ArrayDeque<StorageEntity>()
        children[storageEntity]?.forEach { if (visited.add(it)) queue.add(it) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)
            children[current]?.forEach { if (visited.add(it)) queue.add(it) }
        }
        return result
    }

    override fun link(source: StorageEntity, target: StorageEntity) {
        children.getOrPut(source) { mutableSetOf() }.add(target)
        parents.getOrPut(target) { mutableSetOf() }.add(source)
    }

    override fun unlink(source: StorageEntity, target: StorageEntity): List<StorageEntity> {
        children[source]?.remove(target)
        parents[target]?.remove(source)
        return getChildrenRecursively(target)
    }

    override fun registerChange(storageEntity: StorageEntity, changeType: String): List<StorageEntity> {
        val incident = DataIncident(
            id = UUID.randomUUID(),
            data = storageEntity,
            incidentType = changeType,
        )
        incidents.getOrPut(storageEntity) { mutableListOf() }.add(incident)
        return getChildrenRecursively(storageEntity)
    }

    override fun unregisterChange(storageEntity: StorageEntity, changeType: String): List<StorageEntity> {
        val entityIncidents = incidents[storageEntity] ?: return emptyList()
        val removed = entityIncidents.filter { it.incidentType == changeType }
        entityIncidents.removeAll { it.incidentType == changeType }
        if (removed.isEmpty()) return emptyList()
        return getChildrenRecursively(storageEntity)
    }
}
