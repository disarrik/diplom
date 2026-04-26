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
import observability.admin.domain.Datasource
import observability.admin.store.AdminStore
import java.util.UUID

@Serializable
private data class DatasourceInput(
    val namespace: String,
    val name: String,
    val type: String,
    val host: String,
    val teamIds: List<String> = emptyList(),
)

fun Route.datasourceReadRoutes(store: AdminStore) {
    route("/datasources") {
        get {
            call.respond(store.listDatasources())
        }
    }
}

fun Route.datasourceWriteRoutes(store: AdminStore) {
    route("/datasources") {
        post {
            val input = call.receive<DatasourceInput>()
            val ds = Datasource(
                id = "ds_" + UUID.randomUUID().toString().take(6),
                namespace = input.namespace,
                name = input.name,
                type = input.type,
                host = input.host,
                teamIds = input.teamIds,
            )
            call.respond(HttpStatusCode.Created, store.upsertDatasource(ds))
        }
        put("{id}") {
            val id = call.parameters["id"]!!
            val existing = store.getDatasource(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            val input = call.receive<Datasource>()
            call.respond(store.upsertDatasource(input.copy(id = existing.id)))
        }
        delete("{id}") {
            val id = call.parameters["id"]!!
            if (store.deleteDatasource(id)) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound)
        }
    }
}
