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

private const val RELATIONAL_SCHEMA_DDL = """
DROP TABLE IF EXISTS incident CASCADE;
DROP TABLE IF EXISTS datasource CASCADE;
DROP TABLE IF EXISTS member CASCADE;
DROP TABLE IF EXISTS team CASCADE;

CREATE TABLE team (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    handle      TEXT NOT NULL,
    extensions  JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE member (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    email       TEXT NOT NULL,
    role        TEXT NOT NULL,
    extensions  JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE member_team (
    member_id TEXT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    team_id   TEXT NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    PRIMARY KEY (member_id, team_id)
);

CREATE TABLE datasource (
    id        TEXT PRIMARY KEY,
    namespace TEXT NOT NULL,
    name      TEXT NOT NULL,
    type      TEXT NOT NULL,
    host      TEXT NOT NULL,
    UNIQUE (namespace, name)
);

CREATE TABLE datasource_team (
    datasource_id TEXT NOT NULL REFERENCES datasource(id) ON DELETE CASCADE,
    team_id       TEXT NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    PRIMARY KEY (datasource_id, team_id)
);

CREATE TABLE incident (
    id            TEXT PRIMARY KEY,
    title         TEXT NOT NULL,
    incident_type TEXT NOT NULL,
    root_ds_id    TEXT NOT NULL,
    team_id       TEXT,
    assignee_id   TEXT,
    status        TEXT NOT NULL,
    opened_at     TEXT NOT NULL,
    resolved_at   TEXT
);
CREATE INDEX incident_opened_at_idx ON incident (opened_at DESC);

CREATE TABLE incident_affected_ds (
    incident_id   TEXT NOT NULL REFERENCES incident(id) ON DELETE CASCADE,
    datasource_id TEXT NOT NULL,
    position      INT NOT NULL,
    PRIMARY KEY (incident_id, datasource_id)
);

CREATE TABLE incident_integration (
    incident_id TEXT NOT NULL REFERENCES incident(id) ON DELETE CASCADE,
    position    INT NOT NULL,
    type        TEXT NOT NULL,
    label       TEXT NOT NULL,
    url         TEXT NOT NULL,
    plugin_id   TEXT,
    extra       JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (incident_id, position)
);

CREATE TABLE incident_event (
    incident_id TEXT NOT NULL REFERENCES incident(id) ON DELETE CASCADE,
    position    INT NOT NULL,
    type        TEXT NOT NULL,
    at          TEXT NOT NULL,
    actor       TEXT NOT NULL,
    body        TEXT NOT NULL,
    detail      TEXT,
    PRIMARY KEY (incident_id, position)
);
"""

private val migrationJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal data class Migration(val id: String, val apply: (Connection) -> Unit)

private val ADMIN_MIGRATIONS: List<Migration> = listOf(
    Migration("0001_initial_schema") { conn ->
        conn.createStatement().use { it.execute(INITIAL_SCHEMA_DDL) }
    },
    Migration("0002_seed_demo_data") { conn ->
        seedDemoDataJsonb(conn)
    },
    Migration("0003_relational_schema") { conn ->
        conn.createStatement().use { it.execute(RELATIONAL_SCHEMA_DDL) }
    },
    Migration("0004_seed_demo_data_relational") { conn ->
        seedDemoDataRelational(conn)
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

private fun seedDemoDataJsonb(conn: Connection) {
    val teams = demoTeams()
    val members = demoMembers()
    val datasources = demoDatasources()

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

private fun seedDemoDataRelational(conn: Connection) {
    val teams = demoTeams()
    val members = demoMembers()
    val datasources = demoDatasources()

    conn.prepareStatement(
        "INSERT INTO team (id, name, handle, extensions) VALUES (?, ?, ?, ?)",
    ).use { ps ->
        for (t in teams) {
            ps.setString(1, t.id)
            ps.setString(2, t.name)
            ps.setString(3, t.handle)
            ps.setObject(4, jsonb(migrationJson.encodeToString(t.extensions)))
            ps.addBatch()
        }
        ps.executeBatch()
    }

    conn.prepareStatement(
        "INSERT INTO member (id, name, email, role, extensions) VALUES (?, ?, ?, ?, ?)",
    ).use { ps ->
        for (m in members) {
            ps.setString(1, m.id)
            ps.setString(2, m.name)
            ps.setString(3, m.email)
            ps.setString(4, m.role)
            ps.setObject(5, jsonb(migrationJson.encodeToString(m.extensions)))
            ps.addBatch()
        }
        ps.executeBatch()
    }

    conn.prepareStatement(
        "INSERT INTO member_team (member_id, team_id) VALUES (?, ?)",
    ).use { ps ->
        for (m in members) {
            for (teamId in m.teamIds) {
                ps.setString(1, m.id)
                ps.setString(2, teamId)
                ps.addBatch()
            }
        }
        ps.executeBatch()
    }

    conn.prepareStatement(
        "INSERT INTO datasource (id, namespace, name, type, host) VALUES (?, ?, ?, ?, ?)",
    ).use { ps ->
        for (d in datasources) {
            ps.setString(1, d.id)
            ps.setString(2, d.namespace)
            ps.setString(3, d.name)
            ps.setString(4, d.type)
            ps.setString(5, d.host)
            ps.addBatch()
        }
        ps.executeBatch()
    }

    conn.prepareStatement(
        "INSERT INTO datasource_team (datasource_id, team_id) VALUES (?, ?)",
    ).use { ps ->
        for (d in datasources) {
            for (teamId in d.teamIds) {
                ps.setString(1, d.id)
                ps.setString(2, teamId)
                ps.addBatch()
            }
        }
        ps.executeBatch()
    }
}

private fun demoTeams() = listOf(
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

private fun demoMembers() = listOf(
    Member("u_dwh_01", "Lina Vasquez", "lina@acme.io", "Data Engineer", listOf("t_dwh")),
    Member("u_dwh_02", "Theo Albright", "theo@acme.io", "Analytics Eng.", listOf("t_dwh")),
    Member("u_prod_01", "Alex Park", "alex@acme.io", "Data Engineer", listOf("t_product")),
    Member("u_prod_02", "Maya Chen", "maya@acme.io", "SRE", listOf("t_product")),
    Member("u_prod_03", "Ren Tanaka", "ren@acme.io", "On-call Eng.", listOf("t_product")),
)

private fun demoDatasources() = listOf(
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

private fun jsonb(text: String): PGobject = PGobject().apply {
    type = "jsonb"
    value = text
}
