package observability.admin.http.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import observability.admin.integrations.IntegrationRegistry

@Serializable
data class IntegrationProviderDto(val id: String, val label: String)

fun Route.integrationRoutes(integrations: IntegrationRegistry) {
    route("/integrations") {
        get("/providers") {
            call.respond(integrations.all().map { IntegrationProviderDto(it.id, it.label) })
        }
    }
}
