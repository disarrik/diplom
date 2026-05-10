package observability.std.stat.storage

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrometheusStatStoreReadTest {

    private lateinit var server: HttpServer
    private val handler = RecordingHandler()
    private lateinit var baseUrl: String

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/query", handler)
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `query parses success response and exposes labels minus __name__`() {
        handler.responseBody = """
            {
              "status": "success",
              "data": {
                "resultType": "vector",
                "result": [
                  {
                    "metric": {
                      "__name__": "detector_FooDetector_unique_values_count",
                      "detector_id": "com.example.FooDetector",
                      "namespace": "public",
                      "table": "users",
                      "column": "email"
                    },
                    "value": [1714000000.5, "42"]
                  }
                ]
              }
            }
        """.trimIndent()

        val store = PrometheusStatStore(baseUrl)

        val samples = store.query(
            metricName = "detector_FooDetector_unique_values_count",
            labelFilters = mapOf("detector_id" to "com.example.FooDetector"),
            lookbackSeconds = 600,
        )

        assertEquals(1, samples.size)
        val sample = samples.single()
        assertEquals(BigDecimal("42"), sample.value)
        assertEquals(1714000000L, sample.timestampSeconds)
        assertEquals(
            mapOf(
                "detector_id" to "com.example.FooDetector",
                "namespace" to "public",
                "table" to "users",
                "column" to "email",
            ),
            sample.labels,
        )

        val expectedQuery = "last_over_time(detector_FooDetector_unique_values_count" +
            "{detector_id=\"com.example.FooDetector\"}[600s])"
        assertEquals(expectedQuery, handler.lastQuery)
    }

    @Test
    fun `query returns all results in vector`() {
        handler.responseBody = """
            {
              "status": "success",
              "data": {
                "resultType": "vector",
                "result": [
                  {"metric": {"__name__": "m", "k": "a"}, "value": [1.0, "1"]},
                  {"metric": {"__name__": "m", "k": "b"}, "value": [2.0, "2"]}
                ]
              }
            }
        """.trimIndent()

        val store = PrometheusStatStore(baseUrl)
        val samples = store.query("m", emptyMap(), 60)

        assertEquals(2, samples.size)
        assertEquals(setOf("a", "b"), samples.mapNotNull { it.labels["k"] }.toSet())
    }

    @Test
    fun `query returns empty list when result array is empty`() {
        handler.responseBody = """
            {"status":"success","data":{"resultType":"vector","result":[]}}
        """.trimIndent()

        val store = PrometheusStatStore(baseUrl)
        assertEquals(emptyList(), store.query("m", emptyMap(), 60))
    }

    @Test
    fun `query returns empty list on non-success status`() {
        handler.responseBody = """
            {"status":"error","errorType":"bad_data","error":"boom"}
        """.trimIndent()

        val store = PrometheusStatStore(baseUrl)
        assertEquals(emptyList(), store.query("m", emptyMap(), 60))
    }

    @Test
    fun `query returns empty list on http 500`() {
        handler.responseStatus = 500
        handler.responseBody = "internal error"

        val store = PrometheusStatStore(baseUrl)
        assertEquals(emptyList(), store.query("m", emptyMap(), 60))
    }

    @Test
    fun `query escapes label values with quotes and backslashes`() {
        handler.responseBody = """{"status":"success","data":{"resultType":"vector","result":[]}}"""

        val store = PrometheusStatStore(baseUrl)
        store.query("m", mapOf("k" to "weird\"\\id"), 60)

        assertTrue(handler.lastQuery!!.contains("k=\"weird\\\"\\\\id\""))
    }

    @Test
    fun `query omits selector braces when no filters`() {
        handler.responseBody = """{"status":"success","data":{"resultType":"vector","result":[]}}"""

        val store = PrometheusStatStore(baseUrl)
        store.query("m", emptyMap(), 60)

        assertEquals("last_over_time(m[60s])", handler.lastQuery)
    }

    private class RecordingHandler : HttpHandler {
        var responseBody: String = ""
        var responseStatus: Int = 200
        var lastQuery: String? = null

        override fun handle(exchange: HttpExchange) {
            val rawQuery = exchange.requestURI.rawQuery ?: ""
            lastQuery = rawQuery.split("&")
                .firstOrNull { it.startsWith("query=") }
                ?.removePrefix("query=")
                ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
            val bytes = responseBody.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(responseStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
