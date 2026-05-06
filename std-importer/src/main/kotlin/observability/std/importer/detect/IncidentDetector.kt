package observability.std.importer.detect

import observability.common.model.StorageEntity
import observability.common.stat.Stat
import observability.common.stat.StatType

interface IncidentDetector {
    fun supports(): StatType
    fun entity(): StorageEntity
    fun detect(previous: Stat<*>?): DetectResult
}
