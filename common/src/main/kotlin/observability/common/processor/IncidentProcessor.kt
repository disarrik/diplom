package observability.common.processor

import observability.common.model.DataIncident

interface IncidentProcessor {
    fun process(dataIncident: DataIncident)
}
