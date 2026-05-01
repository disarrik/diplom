package observability.admin.plugins.slack

import observability.admin.domain.Incident
import observability.admin.domain.IncidentEvent
import observability.admin.domain.Integration
import observability.admin.plugins.Plugin
import observability.admin.plugins.PluginCardKind
import observability.admin.plugins.PluginContext
import observability.admin.plugins.PluginDisplayMeta
import observability.admin.plugins.PluginFieldSpec
import observability.admin.plugins.PluginFieldType
import java.time.Instant

class SlackPlugin : Plugin {
    override val id = "slack"
    override val label = "Slack"
    override val displayMeta = PluginDisplayMeta(color = "#4A154B", iconText = "#", cardTitle = "Slack")
    override val cardKind = PluginCardKind.link

    override val teamFields = listOf(
        PluginFieldSpec("channel", "Default Slack channel", PluginFieldType.channel, "#data-team"),
    )

    override suspend fun onIncidentOpened(ctx: PluginContext, incident: Incident) {
        val team = incident.teamId?.let { ctx.getTeam(it) }
        val channel = team?.extensions?.get(id)?.get("channel")
            ?: "#incident-${incident.id}"
        ctx.kv.put("incident:${incident.id}:channel", channel)
        val slug = channel.trimStart('#')
        ctx.attachIntegration(
            incident.id,
            Integration(
                type = id,
                pluginId = id,
                label = channel,
                url = "https://app.slack.com/client/T000/$slug",
                extra = mapOf("channel" to channel),
            ),
        )
    }

    override suspend fun onIncidentResolved(ctx: PluginContext, incident: Incident) {
        ctx.appendEvent(
            incident.id,
            IncidentEvent(
                type = "integration",
                at = Instant.now().toString(),
                actor = "slack-bot",
                text = "Posted resolution notice to Slack",
            ),
        )
    }
}
