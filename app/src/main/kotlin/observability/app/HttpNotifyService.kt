package observability.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import observability.common.model.DataIncident
import observability.common.model.FieldStorageEntity
import observability.common.model.StorageEntity
import observability.common.model.TableStorageEntity
import observability.common.notify.NotifyService
import java.time.Instant
import java.util.logging.Level
import java.util.logging.Logger

class HttpNotifyService(
    private val baseUrl: String,
    private val client: HttpClient = defaultClient(),
) : NotifyService {

    private val log = Logger.getLogger(HttpNotifyService::class.java.name)

    override fun notify(storageEntity: StorageEntity, dataIncident: DataIncident, finished: Boolean) {
        val payload = buildPayload(storageEntity, dataIncident, finished)
        try {
            runBlocking {
                val response = client.post("$baseUrl/api/events/incident") {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
                if (response.status != HttpStatusCode.Accepted && response.status != HttpStatusCode.OK) {
                    log.warning("admin /events/incident returned ${response.status}: ${response.bodyAsText()}")
                }
            }
        } catch (t: Throwable) {
            log.log(Level.WARNING, "failed to deliver incident event to admin at $baseUrl", t)
        }
    }

    private fun buildPayload(entity: StorageEntity, incident: DataIncident, finished: Boolean): JsonObject =
        buildJsonObject {
            put("incidentId", incident.id.toString())
            put("incidentType", incident.incidentType)
            put("rootEntity", storageEntityJson(incident.data))
            put("affectedEntity", storageEntityJson(entity))
            put("finished", JsonPrimitive(finished))
            put("at", Instant.now().toString())
        }

    private fun storageEntityJson(entity: StorageEntity): JsonObject = buildJsonObject {
        when (entity) {
            is TableStorageEntity -> {
                put("kind", "table")
                put("namespace", entity.namespace)
                put("name", entity.name)
            }
            is FieldStorageEntity -> {
                put("kind", "field")
                put("namespace", entity.namespace)
                put("name", entity.name)
                put("field", entity.field)
            }
        }
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
