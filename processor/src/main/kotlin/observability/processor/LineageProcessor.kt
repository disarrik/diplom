package observability.processor

import observability.NotifyService
import observability.common.StateService
import observability.common.model.Lineage
import observability.common.model.LineageType
import observability.common.model.StorageEntity

class LineageProcessor(
    private val stateService: StateService,
    private val notifyService: NotifyService,
) {

    fun process(lineage: Lineage) {
        if (lineage.lineageType == LineageType.REWRITE) {
            disableOldIncidents(lineage.targets)
        }
        val activeIncidentsOnSources = lineage.sources
            .flatMap { stateService.getActiveIncidentsRecursively(it) }
        lineage.sources.forEach { source ->
            lineage.targets.forEach { target ->
                stateService.link(source, target)
                stateService.getChildrenRecursively(target).forEach { children ->
                    activeIncidentsOnSources.forEach {
                        notifyService.notify(children, it, false)
                    }
                }
            }
        }
    }

    fun disableOldIncidents(targets: Collection<StorageEntity>) {
        targets.forEach { target ->
            stateService.getActiveIncidentsRecursively(target).forEach {  incident ->
                notifyService.notify(target, incident, true)
            }
        }
    }
}