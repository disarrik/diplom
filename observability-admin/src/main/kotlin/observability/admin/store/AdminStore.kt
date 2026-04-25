package observability.admin.store

import observability.admin.domain.Datasource
import observability.admin.domain.Incident
import observability.admin.domain.Member
import observability.admin.domain.Team

interface AdminStore {
    suspend fun listMembers(): List<Member>
    suspend fun getMember(id: String): Member?
    suspend fun upsertMember(m: Member): Member
    suspend fun deleteMember(id: String): Boolean

    suspend fun listTeams(): List<Team>
    suspend fun getTeam(id: String): Team?
    suspend fun upsertTeam(t: Team): Team
    suspend fun deleteTeam(id: String): Boolean

    suspend fun listDatasources(): List<Datasource>
    suspend fun getDatasource(id: String): Datasource?
    suspend fun findDatasourceByFq(namespace: String, name: String): Datasource?
    suspend fun upsertDatasource(d: Datasource): Datasource
    suspend fun deleteDatasource(id: String): Boolean

    suspend fun listIncidents(): List<Incident>
    suspend fun getIncident(id: String): Incident?
    suspend fun saveIncident(incident: Incident): Incident
    suspend fun deleteIncident(id: String): Boolean
}
