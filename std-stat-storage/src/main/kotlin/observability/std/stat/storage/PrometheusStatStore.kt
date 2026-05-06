package observability.std.stat.storage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import observability.common.model.FieldStorageEntity
import observability.common.model.StorageEntity
import observability.common.model.TableStorageEntity
import observability.common.stat.Stat
import observability.common.stat.StatStore
import observability.common.stat.StatType
import org.xerial.snappy.Snappy
import prometheus.Label
import prometheus.Sample
import prometheus.TimeSeries
import prometheus.WriteRequest
import java.math.BigDecimal
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class PrometheusStatStore(
    private val prometheusUrl: String,
) : StatStore {

    constructor(params: Map<String, String>) : this(
        prometheusUrl = params["prometheusUrl"] ?: "http://prometheus:9090",
    )

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val objectMapper = ObjectMapper()

    override fun append(stat: Stat<*>) {
        val timeseries = TimeSeries.newBuilder()
            .addAllLabels(buildLabels(stat))
            .addSamples(
                Sample.newBuilder()
                    .setValue(stat.value.toDouble())
                    .setTimestamp(stat.unixTimestamp * 1000)
                    .build()
            )
            .build()

        val writeRequest = WriteRequest.newBuilder()
            .addTimeseries(timeseries)
            .build()

        val compressed = Snappy.compress(writeRequest.toByteArray())

        val request = HttpRequest.newBuilder(URI.create("$prometheusUrl/api/v1/write"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/x-protobuf")
            .header("Content-Encoding", "snappy")
            .header("X-Prometheus-Remote-Write-Version", "0.1.0")
            .POST(HttpRequest.BodyPublishers.ofByteArray(compressed))
            .build()

        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() / 100 != 2) {
                System.err.println("PrometheusStatStore: remote-write returned ${response.statusCode()}")
            }
        } catch (e: Exception) {
            System.err.println("PrometheusStatStore: remote-write failed: ${e.message}")
        }
    }

    override fun lastValue(statType: StatType): Stat<*>? {
        val promql = "last_over_time(${METRIC_NAME}{stat_type_id=\"${escapeLabel(statType.statTypeId)}\"}[10m])"
        val url = URI.create("$prometheusUrl/api/v1/query?query=${URLEncoder.encode(promql, StandardCharsets.UTF_8)}")
        val request = HttpRequest.newBuilder(url)
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            return null
        }
        if (response.statusCode() / 100 != 2) return null

        val root = objectMapper.readTree(response.body())
        if (root.path("status").asText() != "success") return null

        val firstResult = root.path("data").path("result").firstOrNull() ?: return null
        return parseSample(statType, firstResult)
    }

    private fun parseSample(statType: StatType, sample: JsonNode): Stat<*>? {
        val value = sample.path("value")
        if (!value.isArray || value.size() < 2) return null

        val timestamp = value[0].asDouble().toLong()
        val raw = value[1].asText() ?: return null
        val numeric = try {
            BigDecimal(raw)
        } catch (e: NumberFormatException) {
            return null
        }

        val metric = sample.path("metric")
        val entity = parseEntity(metric) ?: return null

        @Suppress("UNCHECKED_CAST")
        return Stat(
            value = numeric,
            statType = statType,
            storageEntity = entity,
            unixTimestamp = timestamp,
        ) as Stat<*>
    }

    private fun parseEntity(metric: JsonNode): StorageEntity? {
        val kind = metric.path("entity_kind").asText().ifEmpty { return null }
        val namespace = metric.path("namespace").asText()
        val name = metric.path("name").asText()
        val field = metric.path("field").asText()
        return when (kind) {
            ENTITY_KIND_TABLE -> TableStorageEntity(namespace = namespace, name = name)
            ENTITY_KIND_FIELD -> FieldStorageEntity(namespace = namespace, name = name, field = field)
            else -> null
        }
    }

    private fun buildLabels(stat: Stat<*>): List<Label> {
        val labels = mutableListOf(
            label("__name__", METRIC_NAME),
            label("stat_type_id", stat.statType.statTypeId),
        )
        when (val entity = stat.storageEntity) {
            is TableStorageEntity -> {
                labels += label("entity_kind", ENTITY_KIND_TABLE)
                labels += label("namespace", entity.namespace)
                labels += label("name", entity.name)
                labels += label("field", "")
            }
            is FieldStorageEntity -> {
                labels += label("entity_kind", ENTITY_KIND_FIELD)
                labels += label("namespace", entity.namespace)
                labels += label("name", entity.name)
                labels += label("field", entity.field)
            }
        }
        return labels
    }

    private fun label(name: String, value: String): Label =
        Label.newBuilder().setName(name).setValue(value).build()

    private fun escapeLabel(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        const val METRIC_NAME = "std_importer_stat_value"
        private const val ENTITY_KIND_TABLE = "table"
        private const val ENTITY_KIND_FIELD = "field"
    }
}
