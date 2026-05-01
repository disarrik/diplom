package observability.admin.plugins.mockmsg

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
import java.util.UUID

class MockMessengerPlugin : Plugin {
    override val id = "mockmsg"
    override val label = "MockMessenger"
    override val displayMeta = PluginDisplayMeta(color = "#1976D2", iconText = "M", cardTitle = "MockMessenger")
    override val cardKind = PluginCardKind.link

    override val memberFields = listOf(
        PluginFieldSpec("handle", "MockMessenger handle", PluginFieldType.text, "@username"),
    )

    override suspend fun onIncidentOpened(ctx: PluginContext, incident: Incident) {
        val chatId = "chat_" + UUID.randomUUID().toString().take(8)
        val invitee = incident.assigneeId?.let { ctx.getMember(it) }
        val handle = invitee?.extensions?.get(id)?.get("handle")

        ctx.kv.put("incident:${incident.id}:chatId", chatId)
        if (handle != null) ctx.kv.put("incident:${incident.id}:invitee", handle)

        val extra = buildMap {
            put("chatId", chatId)
            if (handle != null) put("invitee", handle)
        }
        ctx.attachIntegration(
            incident.id,
            Integration(
                type = id,
                pluginId = id,
                label = "Group chat #${chatId.removePrefix("chat_")}",
                url = "https://mockmessenger.example/chat/$chatId",
                extra = extra,
            ),
        )
    }

    override suspend fun onIncidentResolved(ctx: PluginContext, incident: Incident) {
        val chatId = ctx.kv.get("incident:${incident.id}:chatId") ?: return
        ctx.appendEvent(
            incident.id,
            IncidentEvent(
                type = "integration",
                at = Instant.now().toString(),
                actor = "mockmsg-bot",
                text = "Closed group chat #${chatId.removePrefix("chat_")}",
            ),
        )
    }
}
