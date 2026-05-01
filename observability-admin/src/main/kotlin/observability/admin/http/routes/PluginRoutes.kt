package observability.admin.http.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import observability.admin.plugins.PluginCardKind
import observability.admin.plugins.PluginDisplayMeta
import observability.admin.plugins.PluginFieldSpec
import observability.admin.plugins.PluginRegistry

@Serializable
data class PluginDescriptorDto(
    val id: String,
    val label: String,
    val displayMeta: PluginDisplayMeta,
    val teamFields: List<PluginFieldSpec>,
    val memberFields: List<PluginFieldSpec>,
    val cardKind: PluginCardKind,
)

fun Route.pluginRoutes(registry: PluginRegistry) {
    route("/plugins") {
        get {
            call.respond(registry.all().map {
                PluginDescriptorDto(
                    id = it.id,
                    label = it.label,
                    displayMeta = it.displayMeta,
                    teamFields = it.teamFields,
                    memberFields = it.memberFields,
                    cardKind = it.cardKind,
                )
            })
        }
    }
}
