package observability.std.importer.detect

import observability.common.model.DataIncident

sealed class DetectResult<T> {
    abstract val incidentDetected: Boolean
    abstract val newStat: T

    data class IncidentDetected<T>(
        override val newStat: T,
        val incident: DataIncident,
    ) : DetectResult<T>() {
        override val incidentDetected: Boolean = true
    }

    data class NotDetected<T>(
        override val newStat: T,
    ) : DetectResult<T>() {
        override val incidentDetected: Boolean = false
    }
}
