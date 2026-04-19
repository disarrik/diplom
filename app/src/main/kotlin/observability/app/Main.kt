package observability.app

import observability.common.model.DataIncident
import observability.common.model.StorageEntity
import observability.common.notify.NotifyService
import observability.std.importer.TrivialImporter
import observability.std.processor.StdIncedentProcessor
import observability.std.processor.StdLineageProcessor
import observability.storage.memory.InMemoryStateService

fun main(args: Array<String>) {
    System.setProperty("observability.importer.config.path",
        "/Users/disarra02/hse/diplom/project/examples/1/importer-config-local.yaml")
    val stateService = InMemoryStateService()
    val notifyService = LoggingNotifyService()

    val incidentProcessor = StdIncedentProcessor(stateService, notifyService)
    val lineageProcessor = StdLineageProcessor(stateService, notifyService)

    val importer = TrivialImporter()
    importer.setIncidentProcessor(incidentProcessor)
    importer.setLineageProcessor(lineageProcessor)

    println("Data observability application started.")
}

private class LoggingNotifyService : NotifyService {
    override fun notify(storageEntity: StorageEntity, dataIncident: DataIncident, finished: Boolean) {
        val status = if (finished) "RESOLVED" else "ACTIVE"
        println("[$status] ${dataIncident.incidentType} on ${storageEntity.namespace}.${storageEntity.name} (incident=${dataIncident.id})")
    }
}
