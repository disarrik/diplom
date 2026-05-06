package observability.std.importer.detect

import observability.common.model.StorageEntity
import observability.std.importer.stat.Stat
import observability.std.importer.stat.StatType

interface IncidentDetector {
    fun supports(): StatType
    fun entity(): StorageEntity
    fun detect(previous: Stat<*>?): DetectResult
}
