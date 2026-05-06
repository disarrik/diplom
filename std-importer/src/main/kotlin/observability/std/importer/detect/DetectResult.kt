package observability.std.importer.detect

import observability.common.model.DataIncident
import java.math.BigDecimal

sealed class DetectResult {
    abstract val incidentDetected: Boolean
    abstract val newStat: BigDecimal

    data class IncidentDetected(
        override val newStat: BigDecimal,
        val incident: DataIncident,
    ) : DetectResult() {
        override val incidentDetected: Boolean = true
    }

    data class NotDetected(
        override val newStat: BigDecimal,
    ) : DetectResult() {
        override val incidentDetected: Boolean = false
    }
}
