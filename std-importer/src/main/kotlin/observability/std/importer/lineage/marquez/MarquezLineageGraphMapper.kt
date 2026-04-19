package observability.std.importer.lineage.marquez

import com.fasterxml.jackson.databind.JsonNode
import observability.common.model.Lineage
import observability.common.model.LineageType
import observability.common.model.TableStorageEntity
import java.util.UUID

/**
 * Converts a Marquez lineage graph ([Get a lineage graph](https://marquezproject.ai/docs/api/get-lineage/))
 * into [Lineage] records (one per JOB with both inputs and outputs).
 */
internal object MarquezLineageGraphMapper {

    fun toLineages(graph: List<GraphNodeDto>): List<Lineage> {
        if (graph.isEmpty()) return emptyList()
        val types = graph.associate { it.id to it.type }
        val edges = collectEdges(graph)
        val nodeById = graph.associateBy { it.id }

        val seenJobs = LinkedHashSet<String>()
        val out = mutableListOf<Lineage>()

        for (node in graph) {
            if (node.type != "JOB") continue
            val jobId = node.id
            if (!seenJobs.add(jobId)) continue

            val inputs = edges
                .filter { it.destination == jobId && types[it.origin] == "DATASET" }
                .map { it.origin }
                .distinct()
            val outputs = edges
                .filter { it.origin == jobId && types[it.destination] == "DATASET" }
                .map { it.destination }
                .distinct()

            if (inputs.isEmpty() || outputs.isEmpty()) continue

            val sources = inputs.mapNotNull { datasetIdToTable(it, nodeById[it]?.data) }
            val targets = outputs.mapNotNull { datasetIdToTable(it, nodeById[it]?.data) }
            if (sources.size != inputs.size || targets.size != outputs.size) continue

            out.add(
                Lineage(
                    id = stableLineageId(jobId),
                    sources = sources,
                    targets = targets,
                    lineageType = LineageType.UPDATE,
                ),
            )
        }
        return out
    }

    private fun collectEdges(graph: List<GraphNodeDto>): Set<DirectedEdge> {
        val set = LinkedHashSet<DirectedEdge>()
        for (n in graph) {
            for (e in n.inEdges.orEmpty()) {
                set.add(DirectedEdge(e.origin, e.destination))
            }
            for (e in n.outEdges.orEmpty()) {
                set.add(DirectedEdge(e.origin, e.destination))
            }
        }
        return set
    }

    private data class DirectedEdge(val origin: String, val destination: String)

    internal fun stableLineageId(jobNodeId: String): UUID =
        UUID.nameUUIDFromBytes("marquez:$jobNodeId".toByteArray(Charsets.UTF_8))

    internal fun datasetIdToTable(datasetNodeId: String, data: JsonNode?): TableStorageEntity? {
        parseDatasetNodeId(datasetNodeId)?.let { return it }
        if (data != null && data.isObject) {
            val ns = data.get("namespace")?.asText()
                ?: data.get("namespaceName")?.asText()
            val name = data.get("name")?.asText()
                ?: data.get("physicalName")?.asText()
            if (!ns.isNullOrBlank() && !name.isNullOrBlank()) {
                return TableStorageEntity(namespace = ns, name = name)
            }
        }
        return null
    }

    /**
     * Parses Marquez dataset node ids of the form `dataset:<namespace>:<name>`.
     * If the name contains colons, only the first segment pair is used unless we add smarter parsing.
     */
    internal fun parseDatasetNodeId(id: String): TableStorageEntity? {
        val prefix = "dataset:"
        if (!id.startsWith(prefix)) return null
        val rest = id.removePrefix(prefix)
        val idx = rest.indexOf(':')
        if (idx <= 0 || idx >= rest.length - 1) return null
        return TableStorageEntity(
            namespace = rest.substring(0, idx),
            name = rest.substring(idx + 1),
        )
    }
}
