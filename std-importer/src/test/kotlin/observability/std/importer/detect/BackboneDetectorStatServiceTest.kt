package observability.std.importer.detect

import observability.common.stat.Metric
import observability.common.stat.MetricSample
import observability.common.stat.StatStore
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BackboneDetectorStatServiceTest {

    private val detectorId = "com.example.FooDetector"
    private val sanitizedId = "com_example_FooDetector"

    @Test
    fun `publish injects detector_id and composes metric name`() {
        val store = RecordingStatStore()
        val service = BackboneDetectorStatService(store, detectorId)

        service.publish(
            seriesName = "unique_values_count",
            value = BigDecimal("42"),
            timestampSeconds = 1000L,
            extraTags = mapOf("namespace" to "ns", "table" to "users"),
        )

        val appended = store.appended.single()
        assertEquals("detector_${sanitizedId}_unique_values_count", appended.name)
        assertEquals(
            mapOf(
                "detector_id" to detectorId,
                "namespace" to "ns",
                "table" to "users",
            ),
            appended.labels,
        )
        assertEquals(BigDecimal("42"), appended.value)
        assertEquals(1000L, appended.timestampSeconds)
    }

    @Test
    fun `publish rejects extra tag named detector_id`() {
        val store = RecordingStatStore()
        val service = BackboneDetectorStatService(store, detectorId)

        assertFailsWith<IllegalArgumentException> {
            service.publish(
                seriesName = "s",
                value = BigDecimal.ONE,
                extraTags = mapOf("detector_id" to "spoof"),
            )
        }
        assertEquals(0, store.appended.size)
    }

    @Test
    fun `publish rejects invalid series name`() {
        val store = RecordingStatStore()
        val service = BackboneDetectorStatService(store, detectorId)

        assertFailsWith<IllegalArgumentException> {
            service.publish(seriesName = "bad-name", value = BigDecimal.ONE)
        }
    }

    @Test
    fun `lastValue forwards composed name + merged filters and strips defaults`() {
        val store = RecordingStatStore().apply {
            queryResponse = listOf(
                MetricSample(
                    labels = mapOf("detector_id" to detectorId, "namespace" to "ns"),
                    value = BigDecimal("7"),
                    timestampSeconds = 100L,
                ),
            )
        }
        val service = BackboneDetectorStatService(store, detectorId)

        val point = service.lastValue("series", mapOf("namespace" to "ns"))

        val q = store.queries.single()
        assertEquals("detector_${sanitizedId}_series", q.name)
        assertEquals(mapOf("detector_id" to detectorId, "namespace" to "ns"), q.filters)

        assertNotNull(point)
        assertEquals(BigDecimal("7"), point.value)
        assertEquals(100L, point.timestampSeconds)
        assertEquals(mapOf("namespace" to "ns"), point.tags)
    }

    @Test
    fun `lastValue picks the most recent sample`() {
        val store = RecordingStatStore().apply {
            queryResponse = listOf(
                MetricSample(emptyMap(), BigDecimal("1"), 100L),
                MetricSample(emptyMap(), BigDecimal("2"), 300L),
                MetricSample(emptyMap(), BigDecimal("3"), 200L),
            )
        }
        val service = BackboneDetectorStatService(store, detectorId)

        val point = service.lastValue("s")

        assertEquals(BigDecimal("2"), point?.value)
        assertEquals(300L, point?.timestampSeconds)
    }

    @Test
    fun `lastValue returns null on empty result`() {
        val store = RecordingStatStore().apply { queryResponse = emptyList() }
        val service = BackboneDetectorStatService(store, detectorId)

        assertNull(service.lastValue("s"))
    }

    @Test
    fun `rangeValues maps every sample and strips default tags`() {
        val store = RecordingStatStore().apply {
            queryResponse = listOf(
                MetricSample(mapOf("detector_id" to detectorId, "k" to "a"), BigDecimal.ONE, 1L),
                MetricSample(mapOf("detector_id" to detectorId, "k" to "b"), BigDecimal.TEN, 2L),
            )
        }
        val service = BackboneDetectorStatService(store, detectorId)

        val points = service.rangeValues("s", lookbackSeconds = 60)

        assertEquals(2, points.size)
        assertEquals(setOf("a", "b"), points.mapNotNull { it.tags["k"] }.toSet())
        points.forEach { assertEquals(setOf("k"), it.tags.keys) }
        assertEquals(60L, store.queries.single().lookbackSeconds)
    }

    private class RecordingStatStore : StatStore {
        val appended = mutableListOf<Metric>()
        val queries = mutableListOf<RecordedQuery>()
        var queryResponse: List<MetricSample> = emptyList()

        override fun append(metric: Metric) {
            appended += metric
        }

        override fun query(
            metricName: String,
            labelFilters: Map<String, String>,
            lookbackSeconds: Long,
        ): List<MetricSample> {
            queries += RecordedQuery(metricName, labelFilters, lookbackSeconds)
            return queryResponse
        }
    }

    private data class RecordedQuery(
        val name: String,
        val filters: Map<String, String>,
        val lookbackSeconds: Long,
    )
}
