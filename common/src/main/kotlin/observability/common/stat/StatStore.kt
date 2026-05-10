package observability.common.stat

interface StatStore {
    fun append(metric: Metric)

    fun query(
        metricName: String,
        labelFilters: Map<String, String>,
        lookbackSeconds: Long = 600,
    ): List<MetricSample>
}
