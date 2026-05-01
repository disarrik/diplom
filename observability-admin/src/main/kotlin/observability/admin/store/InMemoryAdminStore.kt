package observability.admin.store

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import observability.admin.domain.Datasource
import observability.admin.domain.Incident
import observability.admin.domain.Member
import observability.admin.domain.Team

class InMemoryAdminStore : AdminStore {
    private val mutex = Mutex()

    private val members = linkedMapOf<String, Member>()
    private val teams = linkedMapOf<String, Team>()
    private val datasources = linkedMapOf<String, Datasource>()
    private val incidents = linkedMapOf<String, Incident>()
    private val kv = linkedMapOf<String, MutableMap<String, String>>()

    fun seed(
        seedMembers: List<Member> = emptyList(),
        seedTeams: List<Team> = emptyList(),
        seedDatasources: List<Datasource> = emptyList(),
    ) {
        seedTeams.forEach { teams[it.id] = it }
        seedMembers.forEach { members[it.id] = it }
        seedDatasources.forEach { datasources[it.id] = it }
    }

    override suspend fun listMembers(): List<Member> = mutex.withLock { members.values.toList() }
    override suspend fun getMember(id: String): Member? = mutex.withLock { members[id] }
    override suspend fun upsertMember(m: Member): Member = mutex.withLock {
        members[m.id] = m
        m
    }
    override suspend fun deleteMember(id: String): Boolean = mutex.withLock {
        members.remove(id) != null
    }

    override suspend fun listTeams(): List<Team> = mutex.withLock { teams.values.toList() }
    override suspend fun getTeam(id: String): Team? = mutex.withLock { teams[id] }
    override suspend fun upsertTeam(t: Team): Team = mutex.withLock {
        teams[t.id] = t
        t
    }
    override suspend fun deleteTeam(id: String): Boolean = mutex.withLock {
        teams.remove(id) != null
    }

    override suspend fun listDatasources(): List<Datasource> = mutex.withLock { datasources.values.toList() }
    override suspend fun getDatasource(id: String): Datasource? = mutex.withLock { datasources[id] }
    override suspend fun findDatasourceByFq(namespace: String, name: String): Datasource? = mutex.withLock {
        datasources.values.firstOrNull { it.namespace == namespace && it.name == name }
    }
    override suspend fun upsertDatasource(d: Datasource): Datasource = mutex.withLock {
        datasources[d.id] = d
        d
    }
    override suspend fun deleteDatasource(id: String): Boolean = mutex.withLock {
        datasources.remove(id) != null
    }

    override suspend fun listIncidents(): List<Incident> = mutex.withLock {
        incidents.values.sortedByDescending { it.openedAt }
    }
    override suspend fun getIncident(id: String): Incident? = mutex.withLock { incidents[id] }
    override suspend fun saveIncident(incident: Incident): Incident = mutex.withLock {
        incidents[incident.id] = incident
        incident
    }
    override suspend fun mutateIncident(id: String, fn: (Incident) -> Incident): Incident? = mutex.withLock {
        val cur = incidents[id] ?: return@withLock null
        val updated = fn(cur)
        incidents[id] = updated
        updated
    }
    override suspend fun deleteIncident(id: String): Boolean = mutex.withLock {
        incidents.remove(id) != null
    }

    override suspend fun pluginKvGet(pluginId: String, key: String): String? = mutex.withLock {
        kv[pluginId]?.get(key)
    }
    override suspend fun pluginKvPut(pluginId: String, key: String, value: String): Unit = mutex.withLock {
        kv.getOrPut(pluginId) { linkedMapOf() }[key] = value
    }
    override suspend fun pluginKvDelete(pluginId: String, key: String): Boolean = mutex.withLock {
        kv[pluginId]?.remove(key) != null
    }
    override suspend fun pluginKvList(pluginId: String, prefix: String): Map<String, String> = mutex.withLock {
        val ns = kv[pluginId] ?: return@withLock emptyMap()
        if (prefix.isEmpty()) ns.toMap() else ns.filterKeys { it.startsWith(prefix) }
    }
}
