package observability.std.importer.lineage.marquez

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

internal val marquezObjectMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class NamespacesResponse(
    val namespaces: List<NamespaceDto>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class NamespaceDto(
    val name: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DatasetsResponse(
    val datasets: List<DatasetDto>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DatasetDto(
    val name: String,
    val namespace: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class LineageGraphResponse(
    val graph: List<GraphNodeDto>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class GraphNodeDto(
    val id: String,
    val type: String,
    val data: JsonNode? = null,
    val inEdges: List<EdgeDto>? = null,
    val outEdges: List<EdgeDto>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EdgeDto(
    val origin: String,
    val destination: String,
)

internal inline fun <reified T> parseJson(body: String): T = marquezObjectMapper.readValue(body)
