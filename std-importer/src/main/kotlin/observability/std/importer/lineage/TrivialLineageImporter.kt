package observability.std.importer.lineage

import observability.common.model.Lineage
import observability.common.model.LineageType
import observability.common.model.TableStorageEntity
import java.util.UUID

private val trivialLineageId: UUID = UUID.fromString("00000000-0000-4000-8000-000000000001")

private const val DATABASE_NAMESPACE = "default"

class TrivialLineageImporter: LineageImporter {
    override fun import(): List<Lineage> = listOf(
        Lineage(
            id = trivialLineageId,
            sources = listOf(TableStorageEntity(namespace = DATABASE_NAMESPACE, name = "order")),
            targets = listOf(TableStorageEntity(namespace = DATABASE_NAMESPACE, name = "order_dwh")),
            lineageType = LineageType.REWRITE,
        ),
    )
}
