package observability.common.stat

import java.math.BigDecimal

data class Metric(
    val name: String,
    val labels: Map<String, String>,
    val value: BigDecimal,
    val timestampSeconds: Long,
)

data class MetricSample(
    val labels: Map<String, String>,
    val value: BigDecimal,
    val timestampSeconds: Long,
)
