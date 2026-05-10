package observability.std.importer.detect

import java.math.BigDecimal

interface DetectorStatService {
    fun publish(
        seriesName: String,
        value: BigDecimal,
        extraTags: Map<String, String> = emptyMap(),
    )

    fun publish(
        seriesName: String,
        value: BigDecimal,
        timestampSeconds: Long,
        extraTags: Map<String, String> = emptyMap(),
    )

    fun lastValue(
        seriesName: String,
        extraTagFilters: Map<String, String> = emptyMap(),
    ): DetectorMetricPoint?

    fun rangeValues(
        seriesName: String,
        extraTagFilters: Map<String, String> = emptyMap(),
        lookbackSeconds: Long = 600,
    ): List<DetectorMetricPoint>
}

data class DetectorMetricPoint(
    val value: BigDecimal,
    val timestampSeconds: Long,
    val tags: Map<String, String>,
)
