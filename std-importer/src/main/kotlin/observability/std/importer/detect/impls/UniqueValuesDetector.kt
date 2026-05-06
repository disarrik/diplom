package observability.std.importer.detect.impls

import observability.common.model.DataIncident
import observability.common.model.StorageEntity
import observability.common.model.TableStorageEntity
import observability.std.importer.detect.DetectResult
import observability.std.importer.detect.IncidentDetector
import observability.common.stat.Stat
import observability.common.stat.StatType
import java.math.BigDecimal
import java.sql.DriverManager
import java.util.UUID

class UniqueValuesDetector(
    private val config: Map<String, String>,
) : IncidentDetector {

    private val connectionString: String get() = config.getValue("connectionString")
    private val password: String get() = config.getValue("password")
    private val namespace: String get() = config.getValue("namespace")
    private val tableName: String get() = config.getValue("tableName")
    private val columnName: String get() = config.getValue("columnName")

    override fun supports(): StatType = StatType(
        "UNIQUE_VALUES_COUNT:${namespace}.${tableName}.${columnName}",
    )

    override fun entity(): StorageEntity = TableStorageEntity(
        namespace = namespace,
        name = tableName,
    )

    override fun detect(previous: Stat<*>?): DetectResult {
        val currentCount = queryUniqueCount()
        val previousCount = previous?.value?.toLong()

        if (previousCount != null && currentCount > previousCount) {
            return DetectResult.IncidentDetected(
                newStat = BigDecimal.valueOf(currentCount),
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

        return DetectResult.NotDetected(newStat = BigDecimal.valueOf(currentCount))
    }

    private fun queryUniqueCount(): Long {
        val quotedColumn = quotePgIdentifier(columnName)
        val quotedTable = quotePgIdentifier(tableName)
        val sql = "SELECT COUNT(DISTINCT $quotedColumn) FROM $quotedTable"

        DriverManager.getConnection(connectionString, /* user extracted from URL */ null, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { resultSet ->
                    resultSet.next()
                    return resultSet.getLong(1)
                }
            }
        }
    }

    private fun quotePgIdentifier(qualified: String): String =
        qualified.split('.').joinToString(".") { segment ->
            "\"" + segment.replace("\"", "\"\"") + "\""
        }
}
