package observability.admin.plugins

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import observability.admin.domain.Incident
import observability.admin.domain.IncidentEvent
import observability.admin.domain.Integration
import observability.admin.domain.Member
import observability.admin.domain.Team
import observability.admin.store.AdminStore
import java.time.Instant

class PluginRuntime(
    private val store: AdminStore,
    private val registry: PluginRegistry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun contextFor(pluginId: String): PluginContext = object : PluginContext {
        override val pluginId = pluginId
        override val kv: PluginKv = StorePluginKv(store, pluginId)
        override suspend fun appendEvent(incidentId: String, event: IncidentEvent) {
            store.mutateIncident(incidentId) { it.copy(events = it.events + event) }
        }
        override suspend fun attachIntegration(incidentId: String, integration: Integration) {
            store.mutateIncident(incidentId) {
                it.copy(
                    integrations = it.integrations + integration,
                    events = it.events + IncidentEvent(
                        type = "integration",
                        at = Instant.now().toString(),
                        actor = "$pluginId-bot",
                        text = "Attached ${integration.label}",
                    ),
                )
            }
        }
        override suspend fun getIncident(incidentId: String): Incident? = store.getIncident(incidentId)
        override suspend fun getTeam(teamId: String): Team? = store.getTeam(teamId)
        override suspend fun getMember(memberId: String): Member? = store.getMember(memberId)
    }

    fun fireOpened(incident: Incident) =
        broadcast(incident) { p, ctx -> p.onIncidentOpened(ctx, incident) }

    fun fireResolved(incident: Incident) =
        broadcast(incident) { p, ctx -> p.onIncidentResolved(ctx, incident) }

    private inline fun broadcast(
        incident: Incident,
        crossinline block: suspend (Plugin, PluginContext) -> Unit,
    ) {
        for (p in registry.all()) {
            val ctx = contextFor(p.id)
            scope.launch {
                try {
                    block(p, ctx)
                } catch (t: Throwable) {
                    System.err.println("[plugin ${p.id}] hook failed for ${incident.id}: ${t.message}")
                }
            }
        }
    }
}

private class StorePluginKv(
    private val store: AdminStore,
    private val pluginId: String,
) : PluginKv {
    override suspend fun get(key: String) = store.pluginKvGet(pluginId, key)
    override suspend fun put(key: String, value: String) = store.pluginKvPut(pluginId, key, value)
    override suspend fun delete(key: String): Boolean = store.pluginKvDelete(pluginId, key)
    override suspend fun list(prefix: String): Map<String, String> = store.pluginKvList(pluginId, prefix)
}
