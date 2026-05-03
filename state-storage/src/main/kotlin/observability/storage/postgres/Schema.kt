package observability.storage.postgres

import javax.sql.DataSource

internal const val DDL = """
CREATE TABLE IF NOT EXISTS state_entity (
    id          BIGSERIAL PRIMARY KEY,
    kind        TEXT NOT NULL,
    namespace   TEXT NOT NULL,
    name        TEXT NOT NULL,
    field       TEXT NOT NULL DEFAULT '',
    UNIQUE (kind, namespace, name, field)
);
CREATE TABLE IF NOT EXISTS lineage_edge (
    source_id BIGINT NOT NULL REFERENCES state_entity(id) ON DELETE CASCADE,
    target_id BIGINT NOT NULL REFERENCES state_entity(id) ON DELETE CASCADE,
    PRIMARY KEY (source_id, target_id)
);
CREATE TABLE IF NOT EXISTS incident (
    id          UUID PRIMARY KEY,
    entity_id   BIGINT NOT NULL REFERENCES state_entity(id) ON DELETE CASCADE,
    change_type TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS incident_by_entity ON incident (entity_id);
"""

fun bootstrapStateStorageSchema(dataSource: DataSource) {
    dataSource.connection.use { conn ->
        conn.createStatement().use { it.execute(DDL) }
    }
}
