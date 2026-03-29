package observability.common.model.importer.check

import observability.common.model.DataIncident

sealed interface CheckResult {
    val isOk: Boolean
}

data class Success(override val isOk: Boolean = true) : CheckResult
data class Incident(
    override val isOk: Boolean = false,
    val dataIncident: DataIncident,
) : CheckResult
