package observability.common.importer

import observability.common.processor.IncidentProcessor
import observability.common.processor.LineageProcessor

interface Importer<T> {
    fun setLineageProcessor(lineageProcessor: LineageProcessor)
    fun setIncidentProcessor(incidentProcessor: IncidentProcessor)
}