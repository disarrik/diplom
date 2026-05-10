package observability.std.importer

import observability.common.importer.Importer
import observability.common.processor.IncidentProcessor
import observability.common.processor.LineageProcessor
import observability.common.stat.StatStore
import observability.std.importer.detect.BackboneDetectorStatService
import observability.std.importer.detect.DetectResult
import observability.std.importer.detect.IncidentDetector
import observability.std.importer.lineage.LineageImporter
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Paths

const val IMPORTER_CONFIG_PATH_PROPERTY = "observability.importer.config.path"

class TrivialImporter : Importer<Unit> {

    private val lineageImporters: List<LineageImporter>
    private val incidentDetectors: List<IncidentDetector>
    private val pollIntervalMs: Long
    private val statStore: StatStore

    @Volatile
    private var lineageProcessor: LineageProcessor? = null

    @Volatile
    private var incidentProcessor: IncidentProcessor? = null

    constructor() {
        val yaml = Yaml()
        val config: Map<String, Any> = openImporterConfigStream().use { stream ->
            yaml.load(stream)
        }

        pollIntervalMs = (config["pollIntervalMs"] as? Number)?.toLong() ?: 5000L

        lineageImporters = parseEntries(config["lineageImporters"]).map { (className, params) ->
            instantiate(className, params) as LineageImporter
        }

        incidentDetectors = parseEntries(config["incidentDetectors"]).map { (className, params) ->
            instantiate(className, params) as IncidentDetector
        }

        val (statStoreClass, statStoreParams) = parseEntry(config["statStore"])
            ?: error("`statStore` entry is required in importer config")
        statStore = instantiate(statStoreClass, statStoreParams) as StatStore

        val thread = Thread(::pollLoop)
        thread.isDaemon = false
        thread.name = "trivial-importer-poll"
        thread.start()
    }

    override fun setLineageProcessor(lineageProcessor: LineageProcessor) {
        this.lineageProcessor = lineageProcessor
    }

    override fun setIncidentProcessor(incidentProcessor: IncidentProcessor) {
        this.incidentProcessor = incidentProcessor
    }

    private fun pollLoop() {
        while (true) {
            try {
                lineageProcessor?.let { processor ->
                    for (importer in lineageImporters) {
                        val lineages = importer.import()
                        lineages.forEach { processor.process(it) }
                    }
                }

                incidentProcessor?.let { processor ->
                    for (detector in incidentDetectors) {
                        runDetector(detector, processor)
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Thread.sleep(pollIntervalMs)
        }
    }

    private fun runDetector(detector: IncidentDetector, processor: IncidentProcessor) {
        val detectorId = detector::class.qualifiedName ?: detector::class.java.name
        val stats = BackboneDetectorStatService(statStore, detectorId)
        when (val result = detector.detect(stats)) {
            is DetectResult.IncidentDetected -> result.incidents.forEach(processor::process)
            DetectResult.NotDetected -> Unit
        }
    }

    companion object {
        private fun openImporterConfigStream() =
            System.getProperty(IMPORTER_CONFIG_PATH_PROPERTY)?.trim().orEmpty().takeIf { it.isNotEmpty() }
                ?.let { pathProp ->
                    val path = Paths.get(pathProp)
                    if (!Files.isRegularFile(path)) {
                        error(
                            "Importer config file not found: '$pathProp' " +
                                "(system property $IMPORTER_CONFIG_PATH_PROPERTY)",
                        )
                    }
                    Files.newInputStream(path)
                }
                ?: error(
                    "Set -D$IMPORTER_CONFIG_PATH_PROPERTY=/absolute/path/to/importer-config.yaml"
                )

        @Suppress("UNCHECKED_CAST")
        private fun parseEntry(raw: Any?): Pair<String, Map<String, String>>? {
            val map = raw as? Map<String, Any> ?: return null
            val className = map["className"] as String
            val params = (map["params"] as? Map<String, Any>)
                ?.mapValues { it.value.toString() }
                ?: emptyMap()
            return className to params
        }

        @Suppress("UNCHECKED_CAST")
        private fun parseEntries(raw: Any?): List<Pair<String, Map<String, String>>> {
            if (raw == null) return emptyList()
            val list = raw as? List<Map<String, Any>> ?: return emptyList()
            return list.mapNotNull { parseEntry(it) }
        }

        private fun instantiate(className: String, params: Map<String, String>): Any {
            val clazz = Class.forName(className)
            if (params.isNotEmpty()) {
                try {
                    val ctor = clazz.getConstructor(Map::class.java)
                    return ctor.newInstance(params)
                } catch (_: NoSuchMethodException) {
                    // fall through to no-arg
                }
            }
            return clazz.getDeclaredConstructor().newInstance()
        }
    }
}
