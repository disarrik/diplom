package observability.std.importer.lineage

import observability.common.model.Lineage
import observability.std.importer.lineage.marquez.MarquezHttpClient
import observability.std.importer.lineage.marquez.MarquezLineageGraphMapper
import observability.std.importer.lineage.marquez.DatasetsResponse
import observability.std.importer.lineage.marquez.LineageGraphResponse
import observability.std.importer.lineage.marquez.NamespacesResponse
import observability.std.importer.lineage.marquez.parseJson
import java.util.UUID
import java.util.logging.Logger

/**
 * Imports **dataset-level** lineage from a [Marquez](https://marquezproject.ai/) OpenLineage backend.
 * Typical flow: **Airflow** (OpenLineage provider) → events → **Marquez** → this importer (REST).
 *
 * Configure in the importer YAML file (path: system property `observability.importer.config.path`) under `lineageImporters` with a `params` map:
 * - [KEY_BASE_URL] — Marquez API root (no trailing slash), e.g. `http://localhost:5000`
 * - [KEY_DEPTH] — optional lineage depth (default `20`)
 * - [KEY_DRY_RUN_MAX_DATASETS] — optional cap on lineage API calls per poll (seed datasets)
 * - [KEY_BEARER_TOKEN], [KEY_USERNAME]/[KEY_PASSWORD], [KEY_API_KEY] — optional auth
 */
class MarquezLineageImporter(
    private val params: Map<String, String>,
) : LineageImporter {

    private val log: Logger = Logger.getLogger(MarquezLineageImporter::class.java.name)

    private val baseUrl: String = params[KEY_BASE_URL]?.trim()?.trimEnd('/')
        ?: error("MarquezLineageImporter: missing required param '$KEY_BASE_URL'")

    private val depth: Int = params[KEY_DEPTH]?.toIntOrNull() ?: 20

    private val maxLineageFetches: Int? = params[KEY_DRY_RUN_MAX_DATASETS]?.toIntOrNull()

    private val http = MarquezHttpClient(
        baseUrl = baseUrl,
        bearerToken = params[KEY_BEARER_TOKEN]?.takeIf { it.isNotBlank() },
        username = params[KEY_USERNAME]?.takeIf { it.isNotBlank() },
        password = params[KEY_PASSWORD]?.takeIf { it.isNotBlank() },
        apiKey = params[KEY_API_KEY]?.takeIf { it.isNotBlank() },
    )

    override fun import(): List<Lineage> {
        val namespaces = resolveNamespaces()
        val datasetNodeIds = mutableListOf<String>()
        for (ns in namespaces) {
            val body = http.getDatasets(ns)
            val parsed = parseJson<DatasetsResponse>(body)
            for (ds in parsed.datasets.orEmpty()) {
                datasetNodeIds.add(datasetNodeId(ns, ds.name))
            }
        }

        val limit = maxLineageFetches ?: datasetNodeIds.size
        val seeds = datasetNodeIds.take(limit)

        val byJobId = LinkedHashMap<UUID, Lineage>()
        for (nodeId in seeds) {
            try {
                val graphBody = http.getLineage(nodeId, depth)
                val graphResponse = parseJson<LineageGraphResponse>(graphBody)
                val lineages = MarquezLineageGraphMapper.toLineages(graphResponse.graph.orEmpty())
                for (ln in lineages) {
                    byJobId[ln.id] = ln
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val result = byJobId.values.toList()
        for (ln in result) {
            val srcs = ln.sources.joinToString { "${it.namespace}:${it.name}" }
            val tgts = ln.targets.joinToString { "${it.namespace}:${it.name}" }
            log.info("imported lineage: [$srcs] -> [$tgts]")
        }
        return result
    }

    private fun resolveNamespaces(): List<String> {
        val body = http.getNamespaces()
        val parsed = parseJson<NamespacesResponse>(body)
        return parsed.namespaces.orEmpty().map { it.name }
    }

    private fun datasetNodeId(namespace: String, datasetName: String): String =
        "dataset:$namespace:$datasetName"

    companion object {
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_DEPTH = "depth"
        const val KEY_DRY_RUN_MAX_DATASETS = "dryRunMaxDatasets"
        const val KEY_BEARER_TOKEN = "bearerToken"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_API_KEY = "apiKey"
    }
}
