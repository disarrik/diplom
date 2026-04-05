package observability.processor

import observability.common.StateService
import observability.common.notify.NotifyService
import observability.common.model.DataIncident

class IncedentProcessor(
    private val stateService: StateService,
    private val notifyService: NotifyService,
) {
    fun process(dataIncident: DataIncident) {
        val affected = stateService.getChildrenRecursively(dataIncident.data)
        affected.forEach {
            notifyService.notify(it, dataIncident, false)
        }
    }
}