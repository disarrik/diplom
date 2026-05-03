package observability.storage.postgres

import observability.common.StateService
import observability.common.model.DataIncident
import observability.common.model.FieldStorageEntity
import observability.common.model.StorageEntity
import observability.common.model.TableStorageEntity
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

class PostgresStateService(private val dataSource: DataSource) : StateService {

    override fun getActiveIncidentsRecursively(storageEntity: StorageEntity): List<DataIncident> =
        inTransaction { conn ->
            val rootId = lookupEntityId(conn, storageEntity) ?: return@inTransaction emptyList()
            val sql = """
                WITH RECURSIVE ancestors(id) AS (
                    SELECT CAST(? AS BIGINT)
                    UNION
                    SELECT le.source_id
                    FROM lineage_edge le
                    JOIN ancestors a ON le.target_id = a.id
                )
                SELECT i.id, i.change_type, e.kind, e.namespace, e.name, e.field
                FROM incident i
                JOIN state_entity e ON e.id = i.entity_id
                JOIN ancestors a ON a.id = i.entity_id
            """.trimIndent()
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, rootId)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<DataIncident>()
                    while (rs.next()) {
                        out += DataIncident(
                            id = rs.getObject("id", UUID::class.java),
                            data = readEntity(rs.getString("kind"), rs.getString("namespace"), rs.getString("name"), rs.getString("field")),
                            incidentType = rs.getString("change_type"),
                        )
                    }
                    out
                }
            }
        }

    override fun getChildrenRecursively(storageEntity: StorageEntity): List<StorageEntity> =
        inTransaction { conn ->
            val rootId = lookupEntityId(conn, storageEntity) ?: return@inTransaction emptyList()
            childrenOf(conn, rootId)
        }

    override fun link(source: StorageEntity, target: StorageEntity) {
        inTransaction { conn ->
            val s = ensureEntityId(conn, source)
            val t = ensureEntityId(conn, target)
            conn.prepareStatement(
                "INSERT INTO lineage_edge (source_id, target_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
            ).use { ps ->
                ps.setLong(1, s)
                ps.setLong(2, t)
                ps.executeUpdate()
            }
        }
    }

    override fun unlink(source: StorageEntity, target: StorageEntity): List<StorageEntity> =
        inTransaction { conn ->
            val s = lookupEntityId(conn, source)
            val t = lookupEntityId(conn, target) ?: return@inTransaction emptyList()
            if (s != null) {
                conn.prepareStatement(
                    "DELETE FROM lineage_edge WHERE source_id = ? AND target_id = ?"
                ).use { ps ->
                    ps.setLong(1, s)
                    ps.setLong(2, t)
                    ps.executeUpdate()
                }
            }
            childrenOf(conn, t)
        }

    override fun registerChange(storageEntity: StorageEntity, changeType: String): List<StorageEntity> =
        inTransaction { conn ->
            val entityId = ensureEntityId(conn, storageEntity)
            conn.prepareStatement(
                "INSERT INTO incident (id, entity_id, change_type) VALUES (?, ?, ?)"
            ).use { ps ->
                ps.setObject(1, UUID.randomUUID())
                ps.setLong(2, entityId)
                ps.setString(3, changeType)
                ps.executeUpdate()
            }
            childrenOf(conn, entityId)
        }

    override fun unregisterChange(storageEntity: StorageEntity, changeType: String): List<StorageEntity> =
        inTransaction { conn ->
            val entityId = lookupEntityId(conn, storageEntity) ?: return@inTransaction emptyList()
            val deleted = conn.prepareStatement(
                "DELETE FROM incident WHERE entity_id = ? AND change_type = ?"
            ).use { ps ->
                ps.setLong(1, entityId)
                ps.setString(2, changeType)
                ps.executeUpdate()
            }
            if (deleted == 0) emptyList() else childrenOf(conn, entityId)
        }

    private fun childrenOf(conn: Connection, rootId: Long): List<StorageEntity> {
        val sql = """
            WITH RECURSIVE descendants(id) AS (
                SELECT target_id FROM lineage_edge WHERE source_id = ?
                UNION
                SELECT le.target_id
                FROM lineage_edge le
                JOIN descendants d ON le.source_id = d.id
            )
            SELECT e.kind, e.namespace, e.name, e.field
            FROM state_entity e
            JOIN descendants d ON d.id = e.id
        """.trimIndent()
        return conn.prepareStatement(sql).use { ps ->
            ps.setLong(1, rootId)
            ps.executeQuery().use { rs ->
                val out = mutableListOf<StorageEntity>()
                while (rs.next()) {
                    out += readEntity(rs.getString("kind"), rs.getString("namespace"), rs.getString("name"), rs.getString("field"))
                }
                out
            }
        }
    }

    private fun ensureEntityId(conn: Connection, e: StorageEntity): Long {
        val (kind, field) = entityKindAndField(e)
        val sql = """
            INSERT INTO state_entity (kind, namespace, name, field)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (kind, namespace, name, field) DO UPDATE SET field = EXCLUDED.field
            RETURNING id
        """.trimIndent()
        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, kind)
            ps.setString(2, e.namespace)
            ps.setString(3, e.name)
            ps.setString(4, field)
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
    }

    private fun lookupEntityId(conn: Connection, e: StorageEntity): Long? {
        val (kind, field) = entityKindAndField(e)
        return conn.prepareStatement(
            "SELECT id FROM state_entity WHERE kind = ? AND namespace = ? AND name = ? AND field = ?"
        ).use { ps ->
            ps.setString(1, kind)
            ps.setString(2, e.namespace)
            ps.setString(3, e.name)
            ps.setString(4, field)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getLong(1) else null
            }
        }
    }

    private fun entityKindAndField(e: StorageEntity): Pair<String, String> = when (e) {
        is TableStorageEntity -> "TABLE" to ""
        is FieldStorageEntity -> "FIELD" to e.field
    }

    private fun readEntity(kind: String, namespace: String, name: String, field: String): StorageEntity =
        when (kind) {
            "TABLE" -> TableStorageEntity(namespace, name)
            "FIELD" -> FieldStorageEntity(namespace, name, field)
            else -> error("unknown state_entity.kind: $kind")
        }

    private inline fun <T> inTransaction(block: (Connection) -> T): T =
        dataSource.connection.use { conn ->
            val previousAuto = conn.autoCommit
            conn.autoCommit = false
            try {
                val result = block(conn)
                conn.commit()
                result
            } catch (t: Throwable) {
                runCatching { conn.rollback() }
                throw t
            } finally {
                runCatching { conn.autoCommit = previousAuto }
            }
        }
}
