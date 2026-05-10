package observability.std.stat.storage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import observability.common.stat.Metric
import observability.common.stat.MetricSample
import observability.common.stat.StatStore
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

    override fun append(metric: Metric) {
        require(METRIC_NAME_REGEX.matches(metric.name)) {
            "Metric name '${metric.name}' must match $METRIC_NAME_REGEX"
        }

        val labels = mutableListOf(label("__name__", metric.name))
        for ((name, value) in metric.labels) {
            require(name != "__name__") { "Label '__name__' is reserved" }
            require(LABEL_NAME_REGEX.matches(name)) { "Label name '$name' must match $LABEL_NAME_REGEX" }
            labels += label(name, value)
        }

        val timeseries = TimeSeries.newBuilder()
            .addAllLabels(labels)
            .addSamples(
                Sample.newBuilder()
                    .setValue(metric.value.toDouble())
                    .setTimestamp(metric.timestampSeconds * 1000)
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

    override fun query(
        metricName: String,
        labelFilters: Map<String, String>,
        lookbackSeconds: Long,
    ): List<MetricSample> {
        require(METRIC_NAME_REGEX.matches(metricName)) {
            "Metric name '$metricName' must match $METRIC_NAME_REGEX"
        }
        require(lookbackSeconds > 0) { "lookbackSeconds must be > 0" }

        val filters = labelFilters.entries.joinToString(",") { (k, v) ->
            require(LABEL_NAME_REGEX.matches(k)) { "Label name '$k' must match $LABEL_NAME_REGEX" }
            "$k=\"${escapeLabel(v)}\""
        }
        val selector = if (filters.isEmpty()) metricName else "$metricName{$filters}"
        val promql = "last_over_time($selector[${lookbackSeconds}s])"
        val url = URI.create("$prometheusUrl/api/v1/query?query=${URLEncoder.encode(promql, StandardCharsets.UTF_8)}")
        val request = HttpRequest.newBuilder(url)
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            return emptyList()
        }
        if (response.statusCode() / 100 != 2) return emptyList()

        val root = objectMapper.readTree(response.body())
        if (root.path("status").asText() != "success") return emptyList()

        val results = root.path("data").path("result")
        if (!results.isArray) return emptyList()
        return results.mapNotNull(::parseSample)
    }

    private fun parseSample(sample: JsonNode): MetricSample? {
        val value = sample.path("value")
        if (!value.isArray || value.size() < 2) return null

        val timestamp = value[0].asDouble().toLong()
        val raw = value[1].asText() ?: return null
        val numeric = try {
            BigDecimal(raw)
        } catch (e: NumberFormatException) {
            return null
        }

        val metricNode = sample.path("metric")
        val labels = buildMap {
            metricNode.fields().forEach { (k, v) ->
                if (k != "__name__") put(k, v.asText())
            }
        }
        return MetricSample(
            labels = labels,
            value = numeric,
            timestampSeconds = timestamp,
        )
    }

    private fun label(name: String, value: String): Label =
        Label.newBuilder().setName(name).setValue(value).build()

    private fun escapeLabel(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private val METRIC_NAME_REGEX = Regex("[a-zA-Z_:][a-zA-Z0-9_:]*")
        private val LABEL_NAME_REGEX = Regex("[a-zA-Z_][a-zA-Z0-9_]*")
    }
}
