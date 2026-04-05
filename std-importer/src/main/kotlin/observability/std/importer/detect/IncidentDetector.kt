package observability.std.importer.detect

import observability.std.importer.stat.Stat
import observability.std.importer.stat.StatType

interface IncidentDetector<T> {
    fun detect(stats: List<Stat<T, *>>): DetectResult<T>
    fun supports(): StatType<T>
}
