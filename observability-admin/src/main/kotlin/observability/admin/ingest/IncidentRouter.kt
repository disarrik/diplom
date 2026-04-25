package observability.admin.ingest

import observability.admin.domain.Datasource
import observability.admin.domain.Member
import observability.admin.store.AdminStore

data class Routing(val teamId: String?, val assigneeId: String?)

class IncidentRouter(private val store: AdminStore) {
    suspend fun route(rootDs: Datasource): Routing {
        val teamId = rootDs.teamIds.firstOrNull() ?: return Routing(null, null)
        val assignee: Member? = store.listMembers().firstOrNull { teamId in it.teamIds }
        return Routing(teamId, assignee?.id)
    }
}
