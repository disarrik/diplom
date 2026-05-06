package observability.std.importer.storage

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import observability.common.model.FieldStorageEntity
import observability.common.model.TableStorageEntity
import observability.std.importer.stat.StatType
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun `lastValue parses success response with table entity`() {
        handler.responseBody = """
            {
              "status": "success",
              "data": {
                "resultType": "vector",
                "result": [
                  {
                    "metric": {
                      "__name__": "std_importer_stat_value",
                      "stat_type_id": "UNIQUE_VALUES_COUNT:public.users.email",
                      "entity_kind": "table",
                      "namespace": "public",
                      "name": "users",
                      "field": ""
                    },
                    "value": [1714000000.5, "42"]
                  }
                ]
              }
            }
        """.trimIndent()

        val store = PrometheusStatStore(baseUrl)
        val statType = StatType("UNIQUE_VALUES_COUNT:public.users.email")

        val stat = store.lastValue(statType)

        assertEquals(BigDecimal("42"), stat?.value)
        assertEquals(1714000000L, stat?.unixTimestamp)
        assertEquals(statType, stat?.statType)
        assertEquals(TableStorageEntity("public", "users"), stat?.storageEntity)

        val expectedQuery = "last_over_time(std_importer_stat_value{stat_type_id=\"${statType.statTypeId}\"}[10m])"
        assertEquals(expectedQuery, handler.lastQuery)
    }

    @Test
    fun `lastValue parses field entity`() {
        handler.responseBody = """
            {
              "status": "success",
              "data": {
                "resultType": "vector",
                "result": [
                  {
                    "metric": {
                      "entity_kind": "field",
                      "namespace": "ns",
                      "name": "t",
                      "field": "c"
                    },
                    "value": [1.0, "5"]
                  }
                ]
              }
            }
        """.trimIndent()

        val store = PrometheusStatStore(baseUrl)
        val stat = store.lastValue(StatType("X"))

        assertEquals(FieldStorageEntity("ns", "t", "c"), stat?.storageEntity)
    }

    @Test
    fun `lastValue returns null when result array is empty`() {
        handler.responseBody = """
            {"status":"success","data":{"resultType":"vector","result":[]}}
        """.trimIndent()

        val store = PrometheusStatStore(baseUrl)
        assertNull(store.lastValue(StatType("X")))
    }

    @Test
    fun `lastValue returns null on non-success status`() {
        handler.responseBody = """
            {"status":"error","errorType":"bad_data","error":"boom"}
        """.trimIndent()

        val store = PrometheusStatStore(baseUrl)
        assertNull(store.lastValue(StatType("X")))
    }

    @Test
    fun `lastValue returns null on http 500`() {
        handler.responseStatus = 500
        handler.responseBody = "internal error"

        val store = PrometheusStatStore(baseUrl)
        assertNull(store.lastValue(StatType("X")))
    }

    @Test
    fun `lastValue escapes label values with quotes`() {
        handler.responseBody = """{"status":"success","data":{"resultType":"vector","result":[]}}"""

        val store = PrometheusStatStore(baseUrl)
        store.lastValue(StatType("weird\"id"))

        assertTrue(handler.lastQuery!!.contains("stat_type_id=\"weird\\\"id\""))
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
