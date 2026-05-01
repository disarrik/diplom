package observability.admin

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import observability.admin.http.appModule
import observability.admin.ingest.IncidentAggregator
import observability.admin.ingest.IncidentRouter
import observability.admin.plugins.PluginRegistry
import observability.admin.plugins.PluginRuntime
import observability.admin.plugins.jira.JiraPlugin
import observability.admin.plugins.mockmsg.MockMessengerPlugin
import observability.admin.plugins.slack.SlackPlugin
import observability.admin.store.InMemoryAdminStore
import observability.admin.store.SeedData

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    val store = InMemoryAdminStore().apply {
        seed(seedTeams = SeedData.teams, seedMembers = SeedData.members, seedDatasources = SeedData.datasources)
    }
    val plugins = PluginRegistry().apply {
        register(SlackPlugin())
        register(JiraPlugin())
        register(MockMessengerPlugin())
    }
    val runtime = PluginRuntime(store, plugins)
    val router = IncidentRouter(store)
    val aggregator = IncidentAggregator(store, router, runtime)

    embeddedServer(Netty, port = port) {
        appModule(store, aggregator, plugins, runtime)
    }.start(wait = true)
}
