package observability.admin.http

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import observability.admin.auth.ADMIN_AUTH
import observability.admin.auth.installAuth
import observability.admin.http.routes.authRoutes
import observability.admin.http.routes.datasourceReadRoutes
import observability.admin.http.routes.datasourceWriteRoutes
import observability.admin.http.routes.incidentRoutes
import observability.admin.http.routes.integrationRoutes
import observability.admin.http.routes.memberReadRoutes
import observability.admin.http.routes.memberWriteRoutes
import observability.admin.http.routes.teamReadRoutes
import observability.admin.http.routes.teamWriteRoutes
import observability.admin.ingest.IncidentAggregator
import observability.admin.integrations.IntegrationRegistry
import observability.admin.store.AdminStore

fun Application.appModule(
    store: AdminStore,
    aggregator: IncidentAggregator,
    integrations: IntegrationRegistry,
) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "internal error")))
        }
    }
    installAuth()

    routing {
        get("/healthz") { call.respondText("ok") }
        route("/api") {
            apiRoutes(store, aggregator, integrations)
        }
        singlePageApplication {
            useResources = true
            filesPath = "static"
            defaultPage = "index.html"
        }
    }
}

private fun Route.apiRoutes(
    store: AdminStore,
    aggregator: IncidentAggregator,
    integrations: IntegrationRegistry,
) {
    memberReadRoutes(store)
    teamReadRoutes(store)
    datasourceReadRoutes(store)
    incidentRoutes(store, aggregator, integrations)
    integrationRoutes(integrations)

    authenticate(ADMIN_AUTH) {
        authRoutes()
        memberWriteRoutes(store)
        teamWriteRoutes(store)
        datasourceWriteRoutes(store)
    }
}
