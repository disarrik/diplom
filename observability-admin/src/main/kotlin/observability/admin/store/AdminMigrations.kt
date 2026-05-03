package observability.admin.store

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import observability.admin.domain.Datasource
import observability.admin.domain.Member
import observability.admin.domain.Team
import org.postgresql.util.PGobject
import java.sql.Connection
import javax.sql.DataSource

private const val MIGRATION_TABLE_DDL = """
CREATE TABLE IF NOT EXISTS schema_migration (
    id          TEXT PRIMARY KEY,
    applied_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
"""

private const val INITIAL_SCHEMA_DDL = """
CREATE TABLE member (
    id      TEXT PRIMARY KEY,
    payload JSONB NOT NULL
);
CREATE TABLE team (
    id      TEXT PRIMARY KEY,
    payload JSONB NOT NULL
);
CREATE TABLE datasource (
    id        TEXT PRIMARY KEY,
    namespace TEXT NOT NULL,
    name      TEXT NOT NULL,
    payload   JSONB NOT NULL,
    UNIQUE (namespace, name)
);
CREATE TABLE incident (
    id        TEXT PRIMARY KEY,
    opened_at TEXT NOT NULL,
    payload   JSONB NOT NULL
);
CREATE INDEX incident_opened_at_idx ON incident (opened_at DESC);
CREATE TABLE plugin_kv (
    plugin_id TEXT NOT NULL,
    key       TEXT NOT NULL,
    value     TEXT NOT NULL,
    PRIMARY KEY (plugin_id, key)
);
"""

private val migrationJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal data class Migration(val id: String, val apply: (Connection) -> Unit)

private val ADMIN_MIGRATIONS: List<Migration> = listOf(
    Migration("0001_initial_schema") { conn ->
        conn.createStatement().use { it.execute(INITIAL_SCHEMA_DDL) }
    },
    Migration("0002_seed_demo_data") { conn ->
        seedDemoData(conn)
    },
)

fun runAdminMigrations(dataSource: DataSource) {
    dataSource.connection.use { conn ->
        conn.createStatement().use { it.execute(MIGRATION_TABLE_DDL) }

        val applied = mutableSetOf<String>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT id FROM schema_migration").use { rs ->
                while (rs.next()) applied += rs.getString(1)
            }
        }

        val previousAuto = conn.autoCommit
        try {
            for (m in ADMIN_MIGRATIONS) {
                if (m.id in applied) continue
                conn.autoCommit = false
                try {
                    m.apply(conn)
                    conn.prepareStatement("INSERT INTO schema_migration (id) VALUES (?)").use { ps ->
                        ps.setString(1, m.id)
                        ps.executeUpdate()
                    }
                    conn.commit()
                } catch (t: Throwable) {
                    runCatching { conn.rollback() }
                    throw IllegalStateException("admin migration ${m.id} failed", t)
                }
            }
        } finally {
            runCatching { conn.autoCommit = previousAuto }
        }
    }
}

private fun seedDemoData(conn: Connection) {
    val teams = listOf(
        Team(
            id = "t_dwh",
            name = "DWH",
            handle = "@dwh",
            extensions = mapOf("slack" to mapOf("channel" to "#data-dwh")),
        ),
        Team(
            id = "t_product",
            name = "Product",
            handle = "@product",
            extensions = mapOf("slack" to mapOf("channel" to "#data-product")),
        ),
    )

    val members = listOf(
        Member("u_dwh_01", "Lina Vasquez", "lina@acme.io", "Data Engineer", listOf("t_dwh")),
        Member("u_dwh_02", "Theo Albright", "theo@acme.io", "Analytics Eng.", listOf("t_dwh")),
        Member("u_prod_01", "Alex Park", "alex@acme.io", "Data Engineer", listOf("t_product")),
        Member("u_prod_02", "Maya Chen", "maya@acme.io", "SRE", listOf("t_product")),
        Member("u_prod_03", "Ren Tanaka", "ren@acme.io", "On-call Eng.", listOf("t_product")),
    )

    val datasources = listOf(
        Datasource(
            id = "ds_order",
            namespace = "postgres",
            name = "order",
            type = "postgres",
            host = "postgres:5432/postgres-demo",
            teamIds = listOf("t_product"),
        ),
        Datasource(
            id = "ds_order_dwh",
            namespace = "postgres",
            name = "order_dwh",
            type = "postgres",
            host = "postgres:5432/postgres-demo",
            teamIds = listOf("t_dwh"),
        ),
    )

    conn.prepareStatement("INSERT INTO team (id, payload) VALUES (?, ?)").use { ps ->
        for (t in teams) {
            ps.setString(1, t.id)
            ps.setObject(2, jsonb(migrationJson.encodeToString(t)))
            ps.addBatch()
        }
        ps.executeBatch()
    }

    conn.prepareStatement("INSERT INTO member (id, payload) VALUES (?, ?)").use { ps ->
        for (m in members) {
            ps.setString(1, m.id)
            ps.setObject(2, jsonb(migrationJson.encodeToString(m)))
            ps.addBatch()
        }
        ps.executeBatch()
    }

    conn.prepareStatement(
        "INSERT INTO datasource (id, namespace, name, payload) VALUES (?, ?, ?, ?)",
    ).use { ps ->
        for (d in datasources) {
            ps.setString(1, d.id)
            ps.setString(2, d.namespace)
            ps.setString(3, d.name)
            ps.setObject(4, jsonb(migrationJson.encodeToString(d)))
            ps.addBatch()
        }
        ps.executeBatch()
    }
}

private fun jsonb(text: String): PGobject = PGobject().apply {
    type = "jsonb"
    value = text
}
