package observability.common.processor

import observability.common.model.DataIncident

interface IncedentProcessor {
    fun process(dataIncident: DataIncident)
}
