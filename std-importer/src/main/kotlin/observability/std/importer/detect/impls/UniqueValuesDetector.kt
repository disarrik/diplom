package observability.std.importer.detect.impls

import observability.common.model.DataIncident
import observability.common.model.TableStorageEntity
import observability.std.importer.detect.DetectResult
import observability.std.importer.detect.IncidentDetector
import observability.std.importer.stat.Stat
import observability.std.importer.stat.StatType
import java.sql.DriverManager
import java.util.UUID

class UniqueValuesDetector(
    private val config: Map<String, String>,
) : IncidentDetector<Long> {

    private val connectionString: String get() = config.getValue("connectionString")
    private val password: String get() = config.getValue("password")
    private val namespace: String get() = config.getValue("namespace")
    private val tableName: String get() = config.getValue("tableName")
    private val columnName: String get() = config.getValue("columnName")

    override fun supports(): StatType<Long> = StatType(
        Long::class.java,
        "UNIQUE_VALUES_COUNT:${namespace}.${tableName}.${columnName}",
    )

    override fun detect(stats: List<Stat<Long, *>>): DetectResult<Long> {
        val currentCount = queryUniqueCount()
        val previousCount = stats.lastOrNull()?.value

        if (previousCount != null && currentCount > previousCount) {
            return DetectResult.IncidentDetected(
                newStat = currentCount,
                incident = DataIncident(
                    id = UUID.randomUUID(),
                    data = TableStorageEntity(
                        namespace = namespace,
                        name = tableName,
                    ),
                    incidentType = "UNIQUE_VALUES_INCREASE",
                ),
            )
        }

        return DetectResult.NotDetected(newStat = currentCount)
    }

    private fun queryUniqueCount(): Long {
        val sql = "SELECT COUNT(DISTINCT $columnName) FROM $tableName"

        DriverManager.getConnection(connectionString, /* user extracted from URL */ null, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { resultSet ->
                    resultSet.next()
                    return resultSet.getLong(1)
                }
            }
        }
    }
}
