package observability.app

import observability.app.kafka.IncidentConsumerLoop
import observability.app.kafka.KafkaConfig
import observability.app.kafka.KafkaIncidentProcessorProxy
import observability.app.kafka.KafkaLineageProcessorProxy
import observability.app.kafka.LineageConsumerLoop
import observability.common.model.DataIncident
import observability.common.model.StorageEntity
import observability.common.notify.NotifyService
import observability.std.importer.TrivialImporter
import observability.std.processor.StdIncedentProcessor
import observability.std.processor.StdLineageProcessor
import observability.storage.postgres.PostgresStateServiceFactory
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer

fun main(args: Array<String>) {
    val mode = (System.getenv("OBSERVABILITY_MODE") ?: "monolith").lowercase()
    println("OBSERVABILITY_MODE=$mode")
    when (mode) {
        "monolith" -> runMonolith()
        "processor" -> runProcessor()
        "importer" -> runImporter()
        else -> error("unknown OBSERVABILITY_MODE: $mode (expected monolith|processor|importer)")
    }
}

private fun runMonolith() {
    val stateService = PostgresStateServiceFactory.fromEnv()
    val notifyService = buildNotifyService()
    val incidentProcessor = StdIncedentProcessor(stateService, notifyService)
    val lineageProcessor = StdLineageProcessor(stateService, notifyService)

    val importer = TrivialImporter()
    importer.setIncidentProcessor(incidentProcessor)
    importer.setLineageProcessor(lineageProcessor)

    println("monolith: data observability application started.")
}

private fun runImporter() {
    val producer = KafkaProducer<ByteArray, ByteArray>(KafkaConfig.producerProps())
    Runtime.getRuntime().addShutdownHook(
        Thread {
            runCatching { producer.flush() }
            runCatching { producer.close() }
        },
    )

    val lineageProxy = KafkaLineageProcessorProxy(producer, KafkaConfig.lineageTopic)
    val incidentProxy = KafkaIncidentProcessorProxy(producer, KafkaConfig.incidentTopic)

    val importer = TrivialImporter()
    importer.setLineageProcessor(lineageProxy)
    importer.setIncidentProcessor(incidentProxy)

    println(
        "importer: started; publishing to ${KafkaConfig.bootstrap} " +
            "(lineage=${KafkaConfig.lineageTopic}, incidents=${KafkaConfig.incidentTopic}).",
    )
}

private fun runProcessor() {
    val stateService = PostgresStateServiceFactory.fromEnv()
    val notifyService = buildNotifyService()
    val lineageProcessor = StdLineageProcessor(stateService, notifyService)
    val incidentProcessor = StdIncedentProcessor(stateService, notifyService)

    val lineageLoop = LineageConsumerLoop(
        consumer = KafkaConsumer(KafkaConfig.consumerProps()),
        topic = KafkaConfig.lineageTopic,
        processor = lineageProcessor,
    )
    val incidentLoop = IncidentConsumerLoop(
        consumer = KafkaConsumer(KafkaConfig.consumerProps()),
        topic = KafkaConfig.incidentTopic,
        processor = incidentProcessor,
    )

    val lineageThread = Thread(lineageLoop, "kafka-lineage-consumer").apply {
        isDaemon = false
        start()
    }
    val incidentThread = Thread(incidentLoop, "kafka-incident-consumer").apply {
        isDaemon = false
        start()
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            lineageLoop.stop()
            incidentLoop.stop()
            lineageThread.join(5_000)
            incidentThread.join(5_000)
        },
    )

    println(
        "processor: started; consuming from ${KafkaConfig.bootstrap} as group ${KafkaConfig.consumerGroup}.",
    )
}

private fun buildNotifyService(): NotifyService {
    val adminUrl = System.getenv("OBSERVABILITY_ADMIN_URL")
    val service: NotifyService = when {
        adminUrl.isNullOrBlank() || adminUrl.equals("none", ignoreCase = true) -> LoggingNotifyService()
        else -> HttpNotifyService(adminUrl.trimEnd('/'))
    }
    println("NotifyService: ${service::class.simpleName} (admin url: ${adminUrl ?: "<unset>"})")
    return service
}

private class LoggingNotifyService : NotifyService {
    override fun notify(storageEntity: StorageEntity, dataIncident: DataIncident, finished: Boolean) {
        val status = if (finished) "RESOLVED" else "ACTIVE"
        println("[$status] ${dataIncident.incidentType} on ${storageEntity.namespace}.${storageEntity.name} (incident=${dataIncident.id})")
    }
}
