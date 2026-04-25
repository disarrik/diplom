package observability.admin.http.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import observability.admin.domain.Team
import observability.admin.store.AdminStore
import java.util.UUID

@Serializable
private data class TeamInput(
    val name: String,
    val handle: String,
    val slack: String,
)

fun Route.teamRoutes(store: AdminStore) {
    route("/teams") {
        get {
            call.respond(store.listTeams())
        }
        post {
            val input = call.receive<TeamInput>()
            val team = Team(
                id = "t_" + UUID.randomUUID().toString().take(6),
                name = input.name,
                handle = input.handle,
                slack = input.slack,
            )
            call.respond(HttpStatusCode.Created, store.upsertTeam(team))
        }
        put("{id}") {
            val id = call.parameters["id"]!!
            val existing = store.getTeam(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            val input = call.receive<Team>()
            call.respond(store.upsertTeam(input.copy(id = existing.id)))
        }
        delete("{id}") {
            val id = call.parameters["id"]!!
            if (store.deleteTeam(id)) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound)
        }
    }
}
