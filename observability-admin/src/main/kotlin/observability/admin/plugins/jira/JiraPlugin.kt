package observability.admin.plugins.jira

import observability.admin.domain.Incident
import observability.admin.domain.Integration
import observability.admin.plugins.Plugin
import observability.admin.plugins.PluginCardKind
import observability.admin.plugins.PluginContext
import observability.admin.plugins.PluginDisplayMeta
import observability.admin.plugins.PluginFieldSpec
import observability.admin.plugins.PluginFieldType

class JiraPlugin : Plugin {
    override val id = "jira"
    override val label = "Jira"
    override val displayMeta = PluginDisplayMeta(color = "#0052CC", iconText = "J", cardTitle = "Jira")
    override val cardKind = PluginCardKind.link

    override val memberFields = listOf(
        PluginFieldSpec("username", "Jira username", PluginFieldType.text, "alex.park"),
    )

    override suspend fun onIncidentOpened(ctx: PluginContext, incident: Incident) {
        val project = "DATA"
        val seq = (ctx.kv.get("seq:$project")?.toIntOrNull() ?: 1000) + 1
        ctx.kv.put("seq:$project", seq.toString())
        val key = "$project-$seq"
        ctx.attachIntegration(
            incident.id,
            Integration(
                type = id,
                pluginId = id,
                label = key,
                url = "https://acme.atlassian.net/browse/$key",
                extra = mapOf("project" to project, "issueKey" to key),
            ),
        )
    }
}
