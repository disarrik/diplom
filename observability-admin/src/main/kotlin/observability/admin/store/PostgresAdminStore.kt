package observability.admin.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import observability.admin.domain.Datasource
import observability.admin.domain.Incident
import observability.admin.domain.Member
import observability.admin.domain.Team
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

class PostgresAdminStore(private val dataSource: DataSource) : AdminStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun listMembers(): List<Member> = io {
        query("SELECT payload FROM member ORDER BY id") { rs -> decode<Member>(rs.getString(1)) }
    }

    override suspend fun getMember(id: String): Member? = io {
        single("SELECT payload FROM member WHERE id = ?", id) { rs -> decode<Member>(rs.getString(1)) }
    }

    override suspend fun upsertMember(m: Member): Member = io {
        dataSource.connection.use { conn -> upsertMember(conn, m) }
        m
    }

    override suspend fun deleteMember(id: String): Boolean = io {
        update("DELETE FROM member WHERE id = ?", id) > 0
    }

    override suspend fun listTeams(): List<Team> = io {
        query("SELECT payload FROM team ORDER BY id") { rs -> decode<Team>(rs.getString(1)) }
    }

    override suspend fun getTeam(id: String): Team? = io {
        single("SELECT payload FROM team WHERE id = ?", id) { rs -> decode<Team>(rs.getString(1)) }
    }

    override suspend fun upsertTeam(t: Team): Team = io {
        dataSource.connection.use { conn -> upsertTeam(conn, t) }
        t
    }

    override suspend fun deleteTeam(id: String): Boolean = io {
        update("DELETE FROM team WHERE id = ?", id) > 0
    }

    override suspend fun listDatasources(): List<Datasource> = io {
        query("SELECT payload FROM datasource ORDER BY id") { rs -> decode<Datasource>(rs.getString(1)) }
    }

    override suspend fun getDatasource(id: String): Datasource? = io {
        single("SELECT payload FROM datasource WHERE id = ?", id) { rs -> decode<Datasource>(rs.getString(1)) }
    }

    override suspend fun findDatasourceByFq(namespace: String, name: String): Datasource? = io {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT payload FROM datasource WHERE namespace = ? AND name = ?",
            ).use { ps ->
                ps.setString(1, namespace)
                ps.setString(2, name)
                ps.executeQuery().use { rs ->
                    if (rs.next()) decode<Datasource>(rs.getString(1)) else null
                }
            }
        }
    }

    override suspend fun upsertDatasource(d: Datasource): Datasource = io {
        dataSource.connection.use { conn -> upsertDatasource(conn, d) }
        d
    }

    override suspend fun deleteDatasource(id: String): Boolean = io {
        update("DELETE FROM datasource WHERE id = ?", id) > 0
    }

    override suspend fun listIncidents(): List<Incident> = io {
        query("SELECT payload FROM incident ORDER BY opened_at DESC") { rs -> decode<Incident>(rs.getString(1)) }
    }

    override suspend fun getIncident(id: String): Incident? = io {
        single("SELECT payload FROM incident WHERE id = ?", id) { rs -> decode<Incident>(rs.getString(1)) }
    }

    override suspend fun saveIncident(incident: Incident): Incident = io {
        dataSource.connection.use { conn -> upsertIncident(conn, incident) }
        incident
    }

    override suspend fun mutateIncident(id: String, fn: (Incident) -> Incident): Incident? = io {
        dataSource.connection.use { conn ->
            val previous = conn.autoCommit
            conn.autoCommit = false
            try {
                val current = conn.prepareStatement(
                    "SELECT payload FROM incident WHERE id = ? FOR UPDATE",
                ).use { ps ->
                    ps.setString(1, id)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) decode<Incident>(rs.getString(1)) else null
                    }
                } ?: run {
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
            "INSERT INTO member (id, payload) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload",
        ).use { ps ->
            ps.setString(1, m.id)
            ps.setObject(2, jsonb(json.encodeToString(m)))
            ps.executeUpdate()
        }
    }

    private fun upsertTeam(conn: Connection, t: Team) {
        conn.prepareStatement(
            "INSERT INTO team (id, payload) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload",
        ).use { ps ->
            ps.setString(1, t.id)
            ps.setObject(2, jsonb(json.encodeToString(t)))
            ps.executeUpdate()
        }
    }

    private fun upsertDatasource(conn: Connection, d: Datasource) {
        conn.prepareStatement(
            """
            INSERT INTO datasource (id, namespace, name, payload)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                namespace = EXCLUDED.namespace,
                name = EXCLUDED.name,
                payload = EXCLUDED.payload
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, d.id)
            ps.setString(2, d.namespace)
            ps.setString(3, d.name)
            ps.setObject(4, jsonb(json.encodeToString(d)))
            ps.executeUpdate()
        }
    }

    private fun upsertIncident(conn: Connection, incident: Incident) {
        conn.prepareStatement(
            """
            INSERT INTO incident (id, opened_at, payload)
            VALUES (?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                opened_at = EXCLUDED.opened_at,
                payload = EXCLUDED.payload
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, incident.id)
            ps.setString(2, incident.openedAt)
            ps.setObject(3, jsonb(json.encodeToString(incident)))
            ps.executeUpdate()
        }
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

    private inline fun <reified T> decode(text: String): T = json.decodeFromString(text)

    private fun jsonb(text: String): PGobject = PGobject().apply {
        type = "jsonb"
        value = text
    }

    private fun escapeLike(prefix: String): String =
        prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private suspend inline fun <T> io(crossinline block: suspend () -> T): T =
        withContext(Dispatchers.IO) { block() }
}
