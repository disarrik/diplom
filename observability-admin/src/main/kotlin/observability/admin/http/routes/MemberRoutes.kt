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
import observability.admin.domain.Member
import observability.admin.store.AdminStore
import java.util.UUID

@Serializable
private data class MemberInput(
    val name: String,
    val email: String,
    val role: String,
    val teamIds: List<String> = emptyList(),
)

fun Route.memberReadRoutes(store: AdminStore) {
    route("/members") {
        get {
            call.respond(store.listMembers())
        }
    }
}

fun Route.memberWriteRoutes(store: AdminStore) {
    route("/members") {
        post {
            val input = call.receive<MemberInput>()
            val member = Member(
                id = "u_" + UUID.randomUUID().toString().take(6),
                name = input.name,
                email = input.email,
                role = input.role,
                teamIds = input.teamIds,
            )
            call.respond(HttpStatusCode.Created, store.upsertMember(member))
        }
        put("{id}") {
            val id = call.parameters["id"]!!
            val existing = store.getMember(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            val input = call.receive<Member>()
            call.respond(store.upsertMember(input.copy(id = existing.id)))
        }
        delete("{id}") {
            val id = call.parameters["id"]!!
            if (store.deleteMember(id)) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound)
        }
    }
}
