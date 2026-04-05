package observability.common.processor

import observability.common.model.Lineage

interface LineageProcessor {
    fun process(lineage: Lineage)
}
