package observability.admin.plugins

import kotlinx.serialization.Serializable
import observability.admin.domain.Incident
import observability.admin.domain.IncidentEvent
import observability.admin.domain.Integration
import observability.admin.domain.Member
import observability.admin.domain.Team

@Serializable
data class PluginDisplayMeta(
    val color: String,
    val iconText: String,
    val cardTitle: String,
)

@Serializable
enum class PluginFieldType { text, url, channel, email, number }

@Serializable
data class PluginFieldSpec(
    val key: String,
    val label: String,
    val type: PluginFieldType = PluginFieldType.text,
    val placeholder: String? = null,
    val required: Boolean = false,
)

@Serializable
enum class PluginCardKind { link, info }

interface PluginKv {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun delete(key: String): Boolean
    suspend fun list(prefix: String = ""): Map<String, String>
}

interface PluginContext {
    val pluginId: String
    val kv: PluginKv
    suspend fun appendEvent(incidentId: String, event: IncidentEvent)
    suspend fun attachIntegration(incidentId: String, integration: Integration)
    suspend fun getIncident(incidentId: String): Incident?
    suspend fun getTeam(teamId: String): Team?
    suspend fun getMember(memberId: String): Member?
}

interface Plugin {
    val id: String
    val label: String
    val displayMeta: PluginDisplayMeta

    val teamFields: List<PluginFieldSpec> get() = emptyList()
    val memberFields: List<PluginFieldSpec> get() = emptyList()
    val cardKind: PluginCardKind get() = PluginCardKind.link

    suspend fun onIncidentOpened(ctx: PluginContext, incident: Incident) {}
    suspend fun onIncidentResolved(ctx: PluginContext, incident: Incident) {}
}
