package observability.std.importer.detect

import observability.common.model.DataIncident

sealed class DetectResult {
    data object NotDetected : DetectResult()
    data class IncidentDetected(val incidents: List<DataIncident>) : DetectResult()
}
