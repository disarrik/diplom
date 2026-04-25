package observability.admin.http.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import observability.admin.domain.IncidentEvent
import observability.admin.domain.IncidentStatus
import observability.admin.ingest.IncidentEventDto
import observability.admin.integrations.IntegrationRegistry
import observability.admin.ingest.IncidentAggregator
import observability.admin.store.AdminStore
import java.time.Instant

@Serializable
private data class AttachIntegrationInput(
    val providerId: String,
    val params: Map<String, String> = emptyMap(),
)

fun Route.incidentRoutes(
    store: AdminStore,
    aggregator: IncidentAggregator,
    integrations: IntegrationRegistry,
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
            call.respond(store.saveIncident(updated))
        }
        post("{id}/integrations") {
            val id = call.parameters["id"]!!
            val incident = store.getIncident(id) ?: return@post call.respond(HttpStatusCode.NotFound)
            val input = call.receive<AttachIntegrationInput>()
            val provider = integrations.get(input.providerId)
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "no provider with id ${input.providerId}"),
                )
            val integration = provider.create(incident, input.params)
            val now = Instant.now().toString()
            val updated = incident.copy(
                integrations = incident.integrations + integration,
                events = incident.events + IncidentEvent(
                    type = "integration",
                    at = now,
                    actor = "Maya Chen",
                    text = "Attached ${provider.label} ${integration.label}",
                ),
            )
            call.respond(store.saveIncident(updated))
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
