package observability.std.stat.storage

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import observability.common.model.FieldStorageEntity
import observability.common.model.TableStorageEntity
import observability.common.stat.Stat
import observability.common.stat.StatType
import org.xerial.snappy.Snappy
import prometheus.WriteRequest
import java.math.BigDecimal
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PrometheusStatStoreWriteTest {

    private lateinit var server: HttpServer
    private val handler = RecordingWriteHandler()
    private lateinit var baseUrl: String

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/write", handler)
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `append posts remote-write request for table entity`() {
        val store = PrometheusStatStore(baseUrl)
        val statType = StatType("UNIQUE_VALUES_COUNT:public.users.email")
        val entity = TableStorageEntity(namespace = "public", name = "users")

        store.append(Stat(BigDecimal("42"), statType, entity, 1714000000L))

        assertEquals("POST", handler.lastMethod)
        assertEquals("/api/v1/write", handler.lastPath)
        assertEquals("application/x-protobuf", handler.lastHeaders["content-type"])
        assertEquals("snappy", handler.lastHeaders["content-encoding"])
        assertEquals("0.1.0", handler.lastHeaders["x-prometheus-remote-write-version"])

        val request = decodeWriteRequest(assertNotNull(handler.lastBody))
        assertEquals(1, request.timeseriesCount)
        val ts = request.getTimeseries(0)
        assertEquals(
            mapOf(
                "__name__" to "std_importer_stat_value",
                "stat_type_id" to statType.statTypeId,
                "entity_kind" to "table",
                "namespace" to "public",
                "name" to "users",
                "field" to "",
            ),
            ts.labelsList.associate { it.name to it.value },
        )
        assertEquals(1, ts.samplesCount)
        assertEquals(42.0, ts.getSamples(0).value)
        assertEquals(1714000000_000L, ts.getSamples(0).timestamp)
    }

    @Test
    fun `append posts remote-write request for field entity`() {
        val store = PrometheusStatStore(baseUrl)
        val statType = StatType("X")
        val entity = FieldStorageEntity(namespace = "ns", name = "t", field = "c")

        store.append(Stat(BigDecimal("7"), statType, entity, 1L))

        val request = decodeWriteRequest(assertNotNull(handler.lastBody))
        val ts = request.getTimeseries(0)
        assertEquals(
            mapOf(
                "__name__" to "std_importer_stat_value",
                "stat_type_id" to "X",
                "entity_kind" to "field",
                "namespace" to "ns",
                "name" to "t",
                "field" to "c",
            ),
            ts.labelsList.associate { it.name to it.value },
        )
        assertEquals(7.0, ts.getSamples(0).value)
        assertEquals(1_000L, ts.getSamples(0).timestamp)
    }

    @Test
    fun `append sends one write per call`() {
        val store = PrometheusStatStore(baseUrl)
        val statType = StatType("X")
        val entity = TableStorageEntity("ns", "t")

        store.append(Stat(BigDecimal("1"), statType, entity, 1L))
        store.append(Stat(BigDecimal("99"), statType, entity, 2L))

        assertEquals(2, handler.callCount)
        val last = decodeWriteRequest(assertNotNull(handler.lastBody))
        assertEquals(99.0, last.getTimeseries(0).getSamples(0).value)
    }

    @Test
    fun `append swallows non-2xx responses without throwing`() {
        handler.responseStatus = 500
        val store = PrometheusStatStore(baseUrl)

        store.append(
            Stat(BigDecimal("1"), StatType("X"), TableStorageEntity("ns", "t"), 1L),
        )

        assertEquals(1, handler.callCount)
    }

    private fun decodeWriteRequest(snappyBytes: ByteArray): WriteRequest {
        val raw = Snappy.uncompress(snappyBytes)
        return WriteRequest.parseFrom(raw)
    }

    private class RecordingWriteHandler : HttpHandler {
        var responseStatus: Int = 204
        var lastMethod: String? = null
        var lastPath: String? = null
        var lastHeaders: Map<String, String> = emptyMap()
        var lastBody: ByteArray? = null
        var callCount: Int = 0

        override fun handle(exchange: HttpExchange) {
            callCount++
            lastMethod = exchange.requestMethod
            lastPath = exchange.requestURI.path
            lastHeaders = exchange.requestHeaders.entries
                .associate { it.key.lowercase() to (it.value.firstOrNull() ?: "") }
            lastBody = exchange.requestBody.use { it.readAllBytes() }
            exchange.sendResponseHeaders(responseStatus, -1)
            exchange.responseBody.close()
        }
    }
}
