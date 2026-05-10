package observability.std.importer.detect

import observability.common.stat.Metric
import observability.common.stat.MetricSample
import observability.common.stat.StatStore
import java.math.BigDecimal

class BackboneDetectorStatService(
    private val statStore: StatStore,
    private val detectorId: String,
) : DetectorStatService {

    private val defaultLabels: Map<String, String> = mapOf(LABEL_DETECTOR_ID to detectorId)
    private val sanitizedDetectorId: String = detectorId.replace(NON_IDENTIFIER_CHAR, "_")

    override fun publish(seriesName: String, value: BigDecimal, extraTags: Map<String, String>) =
        publish(seriesName, value, System.currentTimeMillis() / 1000, extraTags)

    override fun publish(
        seriesName: String,
        value: BigDecimal,
        timestampSeconds: Long,
        extraTags: Map<String, String>,
    ) {
        validateExtraTags(extraTags)
        statStore.append(
            Metric(
                name = composeMetricName(seriesName),
                labels = defaultLabels + extraTags,
                value = value,
                timestampSeconds = timestampSeconds,
            )
        )
    }

    override fun lastValue(
        seriesName: String,
        extraTagFilters: Map<String, String>,
    ): DetectorMetricPoint? {
        validateExtraTags(extraTagFilters)
        return statStore
            .query(composeMetricName(seriesName), defaultLabels + extraTagFilters, 600)
            .maxByOrNull { it.timestampSeconds }
            ?.let(::toPoint)
    }

    override fun rangeValues(
        seriesName: String,
        extraTagFilters: Map<String, String>,
        lookbackSeconds: Long,
    ): List<DetectorMetricPoint> {
        validateExtraTags(extraTagFilters)
        return statStore
            .query(composeMetricName(seriesName), defaultLabels + extraTagFilters, lookbackSeconds)
            .map(::toPoint)
    }

    private fun composeMetricName(seriesName: String): String {
        require(SERIES_NAME_REGEX.matches(seriesName)) {
            "seriesName '$seriesName' must match $SERIES_NAME_REGEX"
        }
        return "detector_${sanitizedDetectorId}_$seriesName"
    }

    private fun validateExtraTags(tags: Map<String, String>) {
        val collisions = tags.keys.intersect(defaultLabels.keys)
        require(collisions.isEmpty()) {
            "Detector may not set reserved tags: $collisions"
        }
    }

    private fun toPoint(sample: MetricSample): DetectorMetricPoint = DetectorMetricPoint(
        value = sample.value,
        timestampSeconds = sample.timestampSeconds,
        tags = sample.labels - defaultLabels.keys,
    )

    companion object {
        const val LABEL_DETECTOR_ID = "detector_id"
        private val SERIES_NAME_REGEX = Regex("[a-zA-Z_][a-zA-Z0-9_]*")
        private val NON_IDENTIFIER_CHAR = Regex("[^A-Za-z0-9_]")
    }
}
