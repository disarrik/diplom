package observability.admin.ingest

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import observability.admin.domain.Datasource
import observability.admin.domain.Incident
import observability.admin.domain.IncidentEvent
import observability.admin.domain.IncidentStatus
import observability.admin.plugins.PluginRuntime
import observability.admin.store.AdminStore

class IncidentAggregator(
    private val store: AdminStore,
    private val router: IncidentRouter,
    private val plugins: PluginRuntime,
) {
    private val mutex = Mutex()
    private val stillAffected = mutableMapOf<String, MutableSet<String>>()
    private val rootFinished = mutableMapOf<String, Boolean>()

    suspend fun ingest(event: IncidentEventDto) {
        val affectedDs = resolveDatasource(event.affectedEntity)
        val isRoot = event.affectedEntity == event.rootEntity

        var openedJustNow: Incident? = null
        var resolvedJustNow: Incident? = null

        mutex.withLock {
            val existing = store.getIncident(event.incidentId)
            if (existing == null) {
                if (event.finished) return@withLock
                val rootDs = if (isRoot) affectedDs else resolveDatasource(event.rootEntity)
                val routing = router.route(rootDs)
                val openedEvent = IncidentEvent(
                    type = "opened",
                    at = event.at,
                    actor = "observability-bot",
                    text = "Incident opened from event ${event.incidentType}",
                )
                val events = mutableListOf(openedEvent)
                if (routing.teamId != null) {
                    val team = store.getTeam(routing.teamId)
                    events += IncidentEvent(
                        type = "assigned",
                        at = event.at,
                        actor = "observability-bot",
                        text = "Routed to ${team?.name ?: routing.teamId} — owner of ${rootDs.fqName}",
                    )
                }
                val incident = Incident(
                    id = event.incidentId,
                    title = synthesizeTitle(event.incidentType, rootDs),
                    incidentType = event.incidentType,
                    rootDsId = rootDs.id,
                    affectedDsIds = listOf(affectedDs.id),
                    teamId = routing.teamId,
                    assigneeId = routing.assigneeId,
                    status = IncidentStatus.open,
                    openedAt = event.at,
                    events = events,
                )
                store.saveIncident(incident)
                stillAffected[event.incidentId] = mutableSetOf(affectedDs.id)
                rootFinished[event.incidentId] = false
                openedJustNow = incident
                return@withLock
            }

            if (existing.status == IncidentStatus.resolved) return@withLock

            val tracker = stillAffected.getOrPut(event.incidentId) { existing.affectedDsIds.toMutableSet() }
            val newEvents = existing.events.toMutableList()
            var affectedDsIds = existing.affectedDsIds

            if (event.finished) {
                tracker.remove(affectedDs.id)
                if (isRoot) rootFinished[event.incidentId] = true
                newEvents += IncidentEvent(
                    type = "affected",
                    at = event.at,
                    actor = "observability-bot",
                    text = "Cleared on ${affectedDs.fqName}",
                )
            } else {
                if (affectedDs.id !in affectedDsIds) {
                    affectedDsIds = affectedDsIds + affectedDs.id
                    tracker.add(affectedDs.id)
                    newEvents += IncidentEvent(
                        type = "affected",
                        at = event.at,
                        actor = "observability-bot",
                        text = "Downstream impact on ${affectedDs.fqName}",
                    )
                }
            }

            val rootDone = rootFinished[event.incidentId] == true
            val shouldResolve = event.finished && tracker.isEmpty() && rootDone
            val resolvedAt = if (shouldResolve) event.at else existing.resolvedAt
            val newStatus = if (shouldResolve) IncidentStatus.resolved else existing.status
            if (shouldResolve) {
                newEvents += IncidentEvent(
                    type = "resolved",
                    at = event.at,
                    actor = "observability-bot",
                    text = "Resolved — all affected sources cleared",
                )
            }

            val updated = existing.copy(
                affectedDsIds = affectedDsIds,
                status = newStatus,
                resolvedAt = resolvedAt,
                events = newEvents,
            )
            store.saveIncident(updated)
            if (shouldResolve) resolvedJustNow = updated
        }

        openedJustNow?.let { plugins.fireOpened(it) }
        resolvedJustNow?.let { plugins.fireResolved(it) }
    }

    private suspend fun resolveDatasource(entity: StorageEntityDto): Datasource {
        val existing = store.findDatasourceByFq(entity.namespace, entity.name)
        if (existing != null) return existing
        val ds = Datasource(
            id = "ds_" + java.util.UUID.randomUUID().toString().take(8),
            namespace = entity.namespace,
            name = entity.name,
            type = "unknown",
            host = "",
            teamIds = emptyList(),
        )
        return store.upsertDatasource(ds)
    }

    private fun synthesizeTitle(incidentType: String, rootDs: Datasource): String {
        val human = incidentType.replace('_', ' ').replace('.', ' ')
            .split(' ').filter { it.isNotEmpty() }
            .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
        return "$human on ${rootDs.fqName}"
    }
}
