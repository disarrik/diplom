package observability.std.importer.lineage.marquez

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Minimal HTTP client for Marquez REST API ([Marquez](https://marquezproject.ai/)).
 * Airflow emits OpenLineage events to Marquez; this client reads lineage graphs only.
 */
internal class MarquezHttpClient(
    baseUrl: String,
    private val bearerToken: String?,
    private val username: String?,
    private val password: String?,
    private val apiKey: String?,
) {
    private val root: String = baseUrl.trimEnd('/')
    private val http: HttpClient = HttpClient.newBuilder().build()

    fun getNamespaces(): String = get("/api/v1/namespaces")

    fun getDatasets(namespace: String): String {
        val ns = encodePathSegment(namespace)
        return get("/api/v1/namespaces/$ns/datasets")
    }

    fun getLineage(nodeId: String, depth: Int): String {
        val encoded = URLEncoder.encode(nodeId, StandardCharsets.UTF_8)
        return get("/api/v1/lineage?nodeId=$encoded&depth=$depth")
    }

    private fun get(pathAndQuery: String): String {
        val uri = URI.create("$root$pathAndQuery")
        val builder = HttpRequest.newBuilder(uri).GET()
        when {
            !bearerToken.isNullOrBlank() ->
                builder.header("Authorization", "Bearer ${bearerToken.trim()}")
            !username.isNullOrBlank() && !password.isNullOrBlank() -> {
                val token = Base64.getEncoder().encodeToString(
                    "$username:$password".toByteArray(StandardCharsets.UTF_8),
                )
                builder.header("Authorization", "Basic $token")
            }
        }
        if (!apiKey.isNullOrBlank()) {
            builder.header("X-API-Key", apiKey.trim())
        }
        val request = builder.build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() in 200..299) {
            return response.body()
        }
        error("Marquez HTTP ${response.statusCode()} for ${uri}: ${response.body().take(500)}")
    }

    private fun encodePathSegment(namespace: String): String =
        URLEncoder.encode(namespace, StandardCharsets.UTF_8).replace("+", "%20")
}
