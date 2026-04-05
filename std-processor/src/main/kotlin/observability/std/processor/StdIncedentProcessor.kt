package observability.std.processor

import observability.common.StateService
import observability.common.notify.NotifyService
import observability.common.model.DataIncident
import observability.common.processor.IncidentProcessor

class StdIncedentProcessor(
    private val stateService: StateService,
    private val notifyService: NotifyService,
) : IncidentProcessor {
    override fun process(dataIncident: DataIncident) {
        val affected = stateService.getChildrenRecursively(dataIncident.data)
        notifyService.notify(dataIncident.data, dataIncident, false)
        affected.forEach {
            notifyService.notify(it, dataIncident, false)
        }
    }
}
