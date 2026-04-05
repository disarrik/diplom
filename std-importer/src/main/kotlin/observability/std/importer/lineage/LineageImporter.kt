package observability.std.importer.lineage

import observability.common.model.Lineage

interface LineageImporter {
    fun import(): List<Lineage>
}