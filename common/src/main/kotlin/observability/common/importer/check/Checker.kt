package observability.common.importer.check

import observability.common.model.importer.check.CheckResult

interface Checker {
    fun check(): CheckResult
}