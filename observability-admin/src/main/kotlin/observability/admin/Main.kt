package observability.admin

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import observability.admin.http.appModule
import observability.admin.ingest.IncidentAggregator
import observability.admin.ingest.IncidentRouter
import observability.admin.integrations.IntegrationRegistry
import observability.admin.store.InMemoryAdminStore
import observability.admin.store.SeedData

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    val store = InMemoryAdminStore().apply {
        seed(seedTeams = SeedData.teams, seedMembers = SeedData.members, seedDatasources = SeedData.datasources)
    }
    val router = IncidentRouter(store)
    val aggregator = IncidentAggregator(store, router)
    val integrations = IntegrationRegistry()

    embeddedServer(Netty, port = port) {
        appModule(store, aggregator, integrations)
    }.start(wait = true)
}
