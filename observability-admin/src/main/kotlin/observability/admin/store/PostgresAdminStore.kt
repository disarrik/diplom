package observability.admin.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import observability.admin.domain.Datasource
import observability.admin.domain.Incident
import observability.admin.domain.IncidentEvent
import observability.admin.domain.IncidentStatus
import observability.admin.domain.Integration
import observability.admin.domain.Member
import observability.admin.domain.Team
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

class PostgresAdminStore(private val dataSource: DataSource) : AdminStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun listMembers(): List<Member> = io {
        dataSource.connection.use { conn ->
            val teamsByMember = loadAllMemberTeams(conn)
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT id, name, email, role, extensions FROM member ORDER BY id",
                ).use { rs ->
                    val out = mutableListOf<Member>()
                    while (rs.next()) out += readMember(rs, teamsByMember[rs.getString("id")] ?: emptyList())
                    out
                }
            }
        }
    }

    override suspend fun getMember(id: String): Member? = io {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, name, email, role, extensions FROM member WHERE id = ?",
            ).use { ps ->
                ps.setString(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) readMember(rs, loadMemberTeams(conn, id)) else null
                }
            }
        }
    }

    override suspend fun upsertMember(m: Member): Member = io {
        inTransaction { conn -> upsertMember(conn, m) }
        m
    }

    override suspend fun deleteMember(id: String): Boolean = io {
        update("DELETE FROM member WHERE id = ?", id) > 0
    }

    override suspend fun listTeams(): List<Team> = io {
        query("SELECT id, name, handle, extensions FROM team ORDER BY id") { rs -> readTeam(rs) }
    }

    override suspend fun getTeam(id: String): Team? = io {
        single("SELECT id, name, handle, extensions FROM team WHERE id = ?", id) { rs -> readTeam(rs) }
    }

    override suspend fun upsertTeam(t: Team): Team = io {
        dataSource.connection.use { conn -> upsertTeam(conn, t) }
        t
    }

    override suspend fun deleteTeam(id: String): Boolean = io {
        update("DELETE FROM team WHERE id = ?", id) > 0
    }

    override suspend fun listDatasources(): List<Datasource> = io {
        dataSource.connection.use { conn ->
            val teamsByDs = loadAllDatasourceTeams(conn)
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT id, namespace, name, type, host FROM datasource ORDER BY id",
                ).use { rs ->
                    val out = mutableListOf<Datasource>()
                    while (rs.next()) out += readDatasource(rs, teamsByDs[rs.getString("id")] ?: emptyList())
                    out
                }
            }
        }
    }

    override suspend fun getDatasource(id: String): Datasource? = io {
        dataSource.connection.use { conn -> loadDatasourceById(conn, id) }
    }

    override suspend fun findDatasourceByFq(namespace: String, name: String): Datasource? = io {
        dataSource.connection.use { conn ->
            val id = conn.prepareStatement(
                "SELECT id FROM datasource WHERE namespace = ? AND name = ?",
            ).use { ps ->
                ps.setString(1, namespace)
                ps.setString(2, name)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            } ?: return@use null
            loadDatasourceById(conn, id)
        }
    }

    override suspend fun upsertDatasource(d: Datasource): Datasource = io {
        inTransaction { conn -> upsertDatasource(conn, d) }
        d
    }

    override suspend fun deleteDatasource(id: String): Boolean = io {
        update("DELETE FROM datasource WHERE id = ?", id) > 0
    }

    override suspend fun listIncidents(): List<Incident> = io {
        dataSource.connection.use { conn ->
            val incidents = conn.createStatement().use { st ->
                st.executeQuery(
                    """
                    SELECT id, title, incident_type, root_ds_id, team_id, assignee_id,
                           status, opened_at, resolved_at
                    FROM incident
                    ORDER BY opened_at DESC
                    """.trimIndent(),
                ).use { rs ->
                    val out = mutableListOf<IncidentRow>()
                    while (rs.next()) out += readIncidentRow(rs)
                    out
                }
            }
            if (incidents.isEmpty()) return@use emptyList()
            val ids = incidents.map { it.id }
            val affected = loadAffectedDs(conn, ids)
            val integrations = loadIntegrations(conn, ids)
            val events = loadEvents(conn, ids)
            incidents.map { row ->
                row.toIncident(
                    affected[row.id] ?: emptyList(),
                    integrations[row.id] ?: emptyList(),
                    events[row.id] ?: emptyList(),
                )
            }
        }
    }

    override suspend fun getIncident(id: String): Incident? = io {
        dataSource.connection.use { conn -> loadIncidentById(conn, id) }
    }

    override suspend fun saveIncident(incident: Incident): Incident = io {
        inTransaction { conn -> upsertIncident(conn, incident) }
        incident
    }

    override suspend fun mutateIncident(id: String, fn: (Incident) -> Incident): Incident? = io {
        dataSource.connection.use { conn ->
            val previous = conn.autoCommit
            conn.autoCommit = false
            try {
                val locked = conn.prepareStatement(
                    "SELECT id FROM incident WHERE id = ? FOR UPDATE",
                ).use { ps ->
                    ps.setString(1, id)
                    ps.executeQuery().use { rs -> rs.next() }
                }
                if (!locked) {
                    conn.commit()
                    return@io null
                }
                val current = loadIncidentById(conn, id) ?: run {
                    conn.commit()
                    return@io null
                }
                val updated = fn(current)
                upsertIncident(conn, updated)
                conn.commit()
                updated
            } catch (t: Throwable) {
                runCatching { conn.rollback() }
                throw t
            } finally {
                runCatching { conn.autoCommit = previous }
            }
        }
    }

    override suspend fun deleteIncident(id: String): Boolean = io {
        update("DELETE FROM incident WHERE id = ?", id) > 0
    }

    override suspend fun pluginKvGet(pluginId: String, key: String): String? = io {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT value FROM plugin_kv WHERE plugin_id = ? AND key = ?").use { ps ->
                ps.setString(1, pluginId)
                ps.setString(2, key)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }
    }

    override suspend fun pluginKvPut(pluginId: String, key: String, value: String) = io {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO plugin_kv (plugin_id, key, value) VALUES (?, ?, ?)
                ON CONFLICT (plugin_id, key) DO UPDATE SET value = EXCLUDED.value
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, pluginId)
                ps.setString(2, key)
                ps.setString(3, value)
                ps.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun pluginKvDelete(pluginId: String, key: String): Boolean = io {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM plugin_kv WHERE plugin_id = ? AND key = ?").use { ps ->
                ps.setString(1, pluginId)
                ps.setString(2, key)
                ps.executeUpdate() > 0
            }
        }
    }

    override suspend fun pluginKvList(pluginId: String, prefix: String): Map<String, String> = io {
        dataSource.connection.use { conn ->
            val sql = if (prefix.isEmpty())
                "SELECT key, value FROM plugin_kv WHERE plugin_id = ? ORDER BY key"
            else
                "SELECT key, value FROM plugin_kv WHERE plugin_id = ? AND key LIKE ? ORDER BY key"
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, pluginId)
                if (prefix.isNotEmpty()) ps.setString(2, "${escapeLike(prefix)}%")
                ps.executeQuery().use { rs ->
                    val out = linkedMapOf<String, String>()
                    while (rs.next()) out[rs.getString(1)] = rs.getString(2)
                    out
                }
            }
        }
    }

    private fun upsertMember(conn: Connection, m: Member) {
        conn.prepareStatement(
            """
            INSERT INTO member (id, name, email, role, extensions) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                email = EXCLUDED.email,
                role = EXCLUDED.role,
                extensions = EXCLUDED.extensions
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, m.id)
            ps.setString(2, m.name)
            ps.setString(3, m.email)
            ps.setString(4, m.role)
            ps.setObject(5, jsonb(json.encodeToString(m.extensions)))
            ps.executeUpdate()
        }
        replaceMemberTeams(conn, m.id, m.teamIds)
    }

    private fun upsertTeam(conn: Connection, t: Team) {
        conn.prepareStatement(
            """
            INSERT INTO team (id, name, handle, extensions) VALUES (?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                handle = EXCLUDED.handle,
                extensions = EXCLUDED.extensions
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, t.id)
            ps.setString(2, t.name)
            ps.setString(3, t.handle)
            ps.setObject(4, jsonb(json.encodeToString(t.extensions)))
            ps.executeUpdate()
        }
    }

    private fun upsertDatasource(conn: Connection, d: Datasource) {
        conn.prepareStatement(
            """
            INSERT INTO datasource (id, namespace, name, type, host)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                namespace = EXCLUDED.namespace,
                name = EXCLUDED.name,
                type = EXCLUDED.type,
                host = EXCLUDED.host
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, d.id)
            ps.setString(2, d.namespace)
            ps.setString(3, d.name)
            ps.setString(4, d.type)
            ps.setString(5, d.host)
            ps.executeUpdate()
        }
        replaceDatasourceTeams(conn, d.id, d.teamIds)
    }

    private fun upsertIncident(conn: Connection, incident: Incident) {
        conn.prepareStatement(
            """
            INSERT INTO incident (
                id, title, incident_type, root_ds_id, team_id, assignee_id,
                status, opened_at, resolved_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                title = EXCLUDED.title,
                incident_type = EXCLUDED.incident_type,
                root_ds_id = EXCLUDED.root_ds_id,
                team_id = EXCLUDED.team_id,
                assignee_id = EXCLUDED.assignee_id,
                status = EXCLUDED.status,
                opened_at = EXCLUDED.opened_at,
                resolved_at = EXCLUDED.resolved_at
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, incident.id)
            ps.setString(2, incident.title)
            ps.setString(3, incident.incidentType)
            ps.setString(4, incident.rootDsId)
            ps.setString(5, incident.teamId)
            ps.setString(6, incident.assigneeId)
            ps.setString(7, incident.status.name)
            ps.setString(8, incident.openedAt)
            ps.setString(9, incident.resolvedAt)
            ps.executeUpdate()
        }
        replaceIncidentChildren(conn, incident)
    }

    private fun replaceMemberTeams(conn: Connection, memberId: String, teamIds: List<String>) {
        conn.prepareStatement("DELETE FROM member_team WHERE member_id = ?").use { ps ->
            ps.setString(1, memberId)
            ps.executeUpdate()
        }
        if (teamIds.isEmpty()) return
        conn.prepareStatement(
            "INSERT INTO member_team (member_id, team_id) VALUES (?, ?)",
        ).use { ps ->
            for (teamId in teamIds) {
                ps.setString(1, memberId)
                ps.setString(2, teamId)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun replaceDatasourceTeams(conn: Connection, dsId: String, teamIds: List<String>) {
        conn.prepareStatement("DELETE FROM datasource_team WHERE datasource_id = ?").use { ps ->
            ps.setString(1, dsId)
            ps.executeUpdate()
        }
        if (teamIds.isEmpty()) return
        conn.prepareStatement(
            "INSERT INTO datasource_team (datasource_id, team_id) VALUES (?, ?)",
        ).use { ps ->
            for (teamId in teamIds) {
                ps.setString(1, dsId)
                ps.setString(2, teamId)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun replaceIncidentChildren(conn: Connection, incident: Incident) {
        conn.prepareStatement("DELETE FROM incident_affected_ds WHERE incident_id = ?").use { ps ->
            ps.setString(1, incident.id)
            ps.executeUpdate()
        }
        conn.prepareStatement("DELETE FROM incident_integration WHERE incident_id = ?").use { ps ->
            ps.setString(1, incident.id)
            ps.executeUpdate()
        }
        conn.prepareStatement("DELETE FROM incident_event WHERE incident_id = ?").use { ps ->
            ps.setString(1, incident.id)
            ps.executeUpdate()
        }

        if (incident.affectedDsIds.isNotEmpty()) {
            conn.prepareStatement(
                "INSERT INTO incident_affected_ds (incident_id, datasource_id, position) VALUES (?, ?, ?)",
            ).use { ps ->
                for ((idx, dsId) in incident.affectedDsIds.withIndex()) {
                    ps.setString(1, incident.id)
                    ps.setString(2, dsId)
                    ps.setInt(3, idx)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }

        if (incident.integrations.isNotEmpty()) {
            conn.prepareStatement(
                """
                INSERT INTO incident_integration
                    (incident_id, position, type, label, url, plugin_id, extra)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { ps ->
                for ((idx, ig) in incident.integrations.withIndex()) {
                    ps.setString(1, incident.id)
                    ps.setInt(2, idx)
                    ps.setString(3, ig.type)
                    ps.setString(4, ig.label)
                    ps.setString(5, ig.url)
                    ps.setString(6, ig.pluginId)
                    ps.setObject(7, jsonb(json.encodeToString(ig.extra)))
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }

        if (incident.events.isNotEmpty()) {
            conn.prepareStatement(
                """
                INSERT INTO incident_event
                    (incident_id, position, type, at, actor, body, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { ps ->
                for ((idx, ev) in incident.events.withIndex()) {
                    ps.setString(1, incident.id)
                    ps.setInt(2, idx)
                    ps.setString(3, ev.type)
                    ps.setString(4, ev.at)
                    ps.setString(5, ev.actor)
                    ps.setString(6, ev.text)
                    ps.setString(7, ev.detail)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }
    }

    private fun loadDatasourceById(conn: Connection, id: String): Datasource? =
        conn.prepareStatement(
            "SELECT id, namespace, name, type, host FROM datasource WHERE id = ?",
        ).use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null else readDatasource(rs, loadDatasourceTeams(conn, id))
            }
        }

    private fun loadIncidentById(conn: Connection, id: String): Incident? {
        val row = conn.prepareStatement(
            """
            SELECT id, title, incident_type, root_ds_id, team_id, assignee_id,
                   status, opened_at, resolved_at
            FROM incident
            WHERE id = ?
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) readIncidentRow(rs) else null }
        } ?: return null
        val affected = loadAffectedDs(conn, listOf(id))[id] ?: emptyList()
        val integrations = loadIntegrations(conn, listOf(id))[id] ?: emptyList()
        val events = loadEvents(conn, listOf(id))[id] ?: emptyList()
        return row.toIncident(affected, integrations, events)
    }

    private fun loadMemberTeams(conn: Connection, memberId: String): List<String> =
        conn.prepareStatement(
            "SELECT team_id FROM member_team WHERE member_id = ? ORDER BY team_id",
        ).use { ps ->
            ps.setString(1, memberId)
            ps.executeQuery().use { rs ->
                val out = mutableListOf<String>()
                while (rs.next()) out += rs.getString(1)
                out
            }
        }

    private fun loadAllMemberTeams(conn: Connection): Map<String, List<String>> {
        val out = mutableMapOf<String, MutableList<String>>()
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT member_id, team_id FROM member_team ORDER BY member_id, team_id",
            ).use { rs ->
                while (rs.next()) {
                    out.getOrPut(rs.getString(1)) { mutableListOf() } += rs.getString(2)
                }
            }
        }
        return out
    }

    private fun loadDatasourceTeams(conn: Connection, dsId: String): List<String> =
        conn.prepareStatement(
            "SELECT team_id FROM datasource_team WHERE datasource_id = ? ORDER BY team_id",
        ).use { ps ->
            ps.setString(1, dsId)
            ps.executeQuery().use { rs ->
                val out = mutableListOf<String>()
                while (rs.next()) out += rs.getString(1)
                out
            }
        }

    private fun loadAllDatasourceTeams(conn: Connection): Map<String, List<String>> {
        val out = mutableMapOf<String, MutableList<String>>()
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT datasource_id, team_id FROM datasource_team ORDER BY datasource_id, team_id",
            ).use { rs ->
                while (rs.next()) {
                    out.getOrPut(rs.getString(1)) { mutableListOf() } += rs.getString(2)
                }
            }
        }
        return out
    }

    private fun loadAffectedDs(conn: Connection, incidentIds: List<String>): Map<String, List<String>> =
        loadChildren(
            conn,
            incidentIds,
            "SELECT incident_id, datasource_id FROM incident_affected_ds WHERE incident_id = ANY(?) ORDER BY incident_id, position",
        ) { rs -> rs.getString(1) to rs.getString(2) }

    private fun loadIntegrations(conn: Connection, incidentIds: List<String>): Map<String, List<Integration>> =
        loadChildren(
            conn,
            incidentIds,
            """
            SELECT incident_id, type, label, url, plugin_id, extra
            FROM incident_integration
            WHERE incident_id = ANY(?)
            ORDER BY incident_id, position
            """.trimIndent(),
        ) { rs ->
            val extra = decodeStringMap(rs.getString("extra"))
            rs.getString("incident_id") to Integration(
                type = rs.getString("type"),
                label = rs.getString("label"),
                url = rs.getString("url"),
                pluginId = rs.getString("plugin_id"),
                extra = extra,
            )
        }

    private fun loadEvents(conn: Connection, incidentIds: List<String>): Map<String, List<IncidentEvent>> =
        loadChildren(
            conn,
            incidentIds,
            """
            SELECT incident_id, type, at, actor, body, detail
            FROM incident_event
            WHERE incident_id = ANY(?)
            ORDER BY incident_id, position
            """.trimIndent(),
        ) { rs ->
            rs.getString("incident_id") to IncidentEvent(
                type = rs.getString("type"),
                at = rs.getString("at"),
                actor = rs.getString("actor"),
                text = rs.getString("body"),
                detail = rs.getString("detail"),
            )
        }

    private inline fun <T> loadChildren(
        conn: Connection,
        incidentIds: List<String>,
        sql: String,
        mapper: (ResultSet) -> Pair<String, T>,
    ): Map<String, List<T>> {
        if (incidentIds.isEmpty()) return emptyMap()
        val array = conn.createArrayOf("text", incidentIds.toTypedArray())
        val out = mutableMapOf<String, MutableList<T>>()
        try {
            conn.prepareStatement(sql).use { ps ->
                ps.setArray(1, array)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val (incidentId, value) = mapper(rs)
                        out.getOrPut(incidentId) { mutableListOf() } += value
                    }
                }
            }
        } finally {
            runCatching { array.free() }
        }
        return out
    }

    private fun readMember(rs: ResultSet, teamIds: List<String>): Member = Member(
        id = rs.getString("id"),
        name = rs.getString("name"),
        email = rs.getString("email"),
        role = rs.getString("role"),
        teamIds = teamIds,
        extensions = decodeNestedStringMap(rs.getString("extensions")),
    )

    private fun readTeam(rs: ResultSet): Team = Team(
        id = rs.getString("id"),
        name = rs.getString("name"),
        handle = rs.getString("handle"),
        extensions = decodeNestedStringMap(rs.getString("extensions")),
    )

    private fun readDatasource(rs: ResultSet, teamIds: List<String>): Datasource = Datasource(
        id = rs.getString("id"),
        namespace = rs.getString("namespace"),
        name = rs.getString("name"),
        type = rs.getString("type"),
        host = rs.getString("host"),
        teamIds = teamIds,
    )

    private fun readIncidentRow(rs: ResultSet): IncidentRow = IncidentRow(
        id = rs.getString("id"),
        title = rs.getString("title"),
        incidentType = rs.getString("incident_type"),
        rootDsId = rs.getString("root_ds_id"),
        teamId = rs.getString("team_id"),
        assigneeId = rs.getString("assignee_id"),
        status = IncidentStatus.valueOf(rs.getString("status")),
        openedAt = rs.getString("opened_at"),
        resolvedAt = rs.getString("resolved_at"),
    )

    private data class IncidentRow(
        val id: String,
        val title: String,
        val incidentType: String,
        val rootDsId: String,
        val teamId: String?,
        val assigneeId: String?,
        val status: IncidentStatus,
        val openedAt: String,
        val resolvedAt: String?,
    ) {
        fun toIncident(
            affected: List<String>,
            integrations: List<Integration>,
            events: List<IncidentEvent>,
        ): Incident = Incident(
            id = id,
            title = title,
            incidentType = incidentType,
            rootDsId = rootDsId,
            affectedDsIds = affected,
            teamId = teamId,
            assigneeId = assigneeId,
            status = status,
            openedAt = openedAt,
            resolvedAt = resolvedAt,
            integrations = integrations,
            events = events,
        )
    }

    private inline fun <T> query(sql: String, mapper: (ResultSet) -> T): List<T> =
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(sql).use { rs ->
                    val out = mutableListOf<T>()
                    while (rs.next()) out += mapper(rs)
                    out
                }
            }
        }

    private inline fun <T> single(sql: String, key: String, mapper: (ResultSet) -> T): T? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, key)
                ps.executeQuery().use { rs -> if (rs.next()) mapper(rs) else null }
            }
        }

    private fun update(sql: String, key: String): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, key)
                ps.executeUpdate()
            }
        }

    private inline fun <T> inTransaction(block: (Connection) -> T): T =
        dataSource.connection.use { conn ->
            val previous = conn.autoCommit
            conn.autoCommit = false
            try {
                val result = block(conn)
                conn.commit()
                result
            } catch (t: Throwable) {
                runCatching { conn.rollback() }
                throw t
            } finally {
                runCatching { conn.autoCommit = previous }
            }
        }

    private fun decodeNestedStringMap(text: String?): Map<String, Map<String, String>> {
        if (text.isNullOrBlank()) return emptyMap()
        return json.decodeFromString(text)
    }

    private fun decodeStringMap(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        return json.decodeFromString(text)
    }

    private fun jsonb(text: String): PGobject = PGobject().apply {
        type = "jsonb"
        value = text
    }

    private fun escapeLike(prefix: String): String =
        prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private suspend inline fun <T> io(crossinline block: suspend () -> T): T =
        withContext(Dispatchers.IO) { block() }
}
