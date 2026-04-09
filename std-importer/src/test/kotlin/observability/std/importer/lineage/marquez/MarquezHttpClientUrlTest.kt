package observability.std.importer.lineage.marquez

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class MarquezHttpClientUrlTest {

    @Test
    fun nodeIdEncodingMatchesHttpClientQueryConvention() {
        val nodeId = "dataset:food_delivery:public.orders"
        val encoded = URLEncoder.encode(nodeId, StandardCharsets.UTF_8)
        assertEquals("dataset%3Afood_delivery%3Apublic.orders", encoded)
    }
}
