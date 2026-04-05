package observability.std.processor

import observability.common.StateService
import observability.common.notify.NotifyService
import observability.common.model.Lineage
import observability.common.model.LineageType
import observability.common.model.StorageEntity
import observability.common.processor.LineageProcessor

class StdLineageProcessor(
    private val stateService: StateService,
    private val notifyService: NotifyService,
) : LineageProcessor {

    override fun process(lineage: Lineage) {
        if (lineage.lineageType == LineageType.REWRITE) {
            disableOldIncidents(lineage.targets)
        }
        val activeIncidentsOnSources = lineage.sources
            .flatMap { stateService.getActiveIncidentsRecursively(it) }
        lineage.sources.forEach { source ->
            lineage.targets.forEach { target ->
                stateService.link(source, target)
                (listOf(target) + stateService.getChildrenRecursively(target)).forEach { child ->
                    activeIncidentsOnSources.forEach {
                        notifyService.notify(child, it, false)
                    }
                }
            }
        }
    }

    private fun disableOldIncidents(targets: Collection<StorageEntity>) {
        targets.forEach { target ->
            stateService.getActiveIncidentsRecursively(target).forEach { incident ->
                notifyService.notify(target, incident, true)
            }
        }
    }
}
