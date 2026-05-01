package observability.app.kafka

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import observability.common.model.DataIncident
import observability.common.model.FieldStorageEntity
import observability.common.model.Lineage
import observability.common.model.LineageType
import observability.common.model.StorageEntity
import observability.common.model.TableStorageEntity
import java.util.UUID

@Serializable
data class JsonStorageEntity(
    val kind: String,
    val namespace: String,
    val name: String,
    val field: String? = null,
)

@Serializable
data class LineageEnvelope(
    val id: String,
    val lineageType: String,
    val sources: List<JsonStorageEntity>,
    val targets: List<JsonStorageEntity>,
)

@Serializable
data class IncidentEnvelope(
    val id: String,
    val data: JsonStorageEntity,
    val incidentType: String,
)

object EventCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encodeLineage(lineage: Lineage): ByteArray {
        val envelope = LineageEnvelope(
            id = lineage.id.toString(),
            lineageType = lineage.lineageType.name,
            sources = lineage.sources.map(::encodeEntity),
            targets = lineage.targets.map(::encodeEntity),
        )
        return json.encodeToString(LineageEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8)
    }

    fun decodeLineage(bytes: ByteArray): Lineage {
        val envelope = json.decodeFromString(LineageEnvelope.serializer(), bytes.toString(Charsets.UTF_8))
        return Lineage(
            id = UUID.fromString(envelope.id),
            sources = envelope.sources.map(::decodeEntity),
            targets = envelope.targets.map(::decodeEntity),
            lineageType = LineageType.valueOf(envelope.lineageType),
        )
    }

    fun encodeIncident(incident: DataIncident): ByteArray {
        val envelope = IncidentEnvelope(
            id = incident.id.toString(),
            data = encodeEntity(incident.data),
            incidentType = incident.incidentType,
        )
        return json.encodeToString(IncidentEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8)
    }

    fun decodeIncident(bytes: ByteArray): DataIncident {
        val envelope = json.decodeFromString(IncidentEnvelope.serializer(), bytes.toString(Charsets.UTF_8))
        return DataIncident(
            id = UUID.fromString(envelope.id),
            data = decodeEntity(envelope.data),
            incidentType = envelope.incidentType,
        )
    }

    private fun encodeEntity(entity: StorageEntity): JsonStorageEntity = when (entity) {
        is TableStorageEntity -> JsonStorageEntity(
            kind = "table",
            namespace = entity.namespace,
            name = entity.name,
        )
        is FieldStorageEntity -> JsonStorageEntity(
            kind = "field",
            namespace = entity.namespace,
            name = entity.name,
            field = entity.field,
        )
    }

    private fun decodeEntity(json: JsonStorageEntity): StorageEntity = when (json.kind) {
        "table" -> TableStorageEntity(namespace = json.namespace, name = json.name)
        "field" -> FieldStorageEntity(
            namespace = json.namespace,
            name = json.name,
            field = json.field ?: error("field required for kind=field"),
        )
        else -> error("unknown StorageEntity kind: ${json.kind}")
    }
}
