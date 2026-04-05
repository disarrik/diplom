package observability.std.processor

import observability.common.StateService
import observability.common.notify.NotifyService
import observability.common.model.DataIncident
import observability.common.processor.IncedentProcessor

class StdIncedentProcessor(
    private val stateService: StateService,
    private val notifyService: NotifyService,
) : IncedentProcessor {
    override fun process(dataIncident: DataIncident) {
        val affected = stateService.getChildrenRecursively(dataIncident.data)
        affected.forEach {
            notifyService.notify(it, dataIncident, false)
        }
    }
}
