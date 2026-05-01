package observability.admin.http.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import observability.admin.domain.IncidentEvent
import observability.admin.domain.IncidentStatus
import observability.admin.ingest.IncidentAggregator
import observability.admin.ingest.IncidentEventDto
import observability.admin.plugins.PluginRuntime
import observability.admin.store.AdminStore
import java.time.Instant

fun Route.incidentRoutes(
    store: AdminStore,
    aggregator: IncidentAggregator,
    runtime: PluginRuntime,
) {
    route("/incidents") {
        get {
            call.respond(store.listIncidents())
        }
        get("{id}") {
            val id = call.parameters["id"]!!
            val incident = store.getIncident(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(incident)
        }
        post("{id}/resolve") {
            val id = call.parameters["id"]!!
            val incident = store.getIncident(id) ?: return@post call.respond(HttpStatusCode.NotFound)
            if (incident.status == IncidentStatus.resolved) {
                call.respond(incident); return@post
            }
            val now = Instant.now().toString()
            val updated = incident.copy(
                status = IncidentStatus.resolved,
                resolvedAt = now,
                events = incident.events + IncidentEvent(
                    type = "resolved",
                    at = now,
                    actor = "Maya Chen",
                    text = "Marked resolved",
                ),
            )
            val saved = store.saveIncident(updated)
            runtime.fireResolved(saved)
            call.respond(saved)
        }
    }

    route("/events") {
        post("/incident") {
            val event = call.receive<IncidentEventDto>()
            aggregator.ingest(event)
            call.respond(HttpStatusCode.Accepted)
        }
    }
}
