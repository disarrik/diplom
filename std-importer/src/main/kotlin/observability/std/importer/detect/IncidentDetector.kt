package observability.std.importer.detect

import observability.common.model.StorageEntity

interface IncidentDetector {
    fun entity(): StorageEntity
    fun detect(stats: DetectorStatService): DetectResult
}
