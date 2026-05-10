package observability.std.importer.detect.impls

import observability.common.model.DataIncident
import observability.common.model.StorageEntity
import observability.common.model.TableStorageEntity
import observability.std.importer.detect.DetectResult
import observability.std.importer.detect.DetectorStatService
import observability.std.importer.detect.IncidentDetector
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

    override fun entity(): StorageEntity = TableStorageEntity(
        namespace = namespace,
        name = tableName,
    )

    override fun detect(stats: DetectorStatService): DetectResult {
        val tags = mapOf(
            "namespace" to namespace,
            "table" to tableName,
            "column" to columnName,
        )

        val previous = stats.lastValue(SERIES_UNIQUE_VALUES_COUNT, tags)
        val currentCount = queryUniqueCount()
        stats.publish(SERIES_UNIQUE_VALUES_COUNT, BigDecimal.valueOf(currentCount), tags)

        val previousCount = previous?.value?.toLong()
        if (previousCount != null && currentCount > previousCount) {
            return DetectResult.IncidentDetected(
                listOf(
                    DataIncident(
                        id = UUID.randomUUID(),
                        data = TableStorageEntity(namespace = namespace, name = tableName),
                        incidentType = "UNIQUE_VALUES_INCREASE",
                    )
                )
            )
        }

        return DetectResult.NotDetected
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

    companion object {
        private const val SERIES_UNIQUE_VALUES_COUNT = "unique_values_count"
    }
}
