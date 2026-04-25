package observability.admin.integrations

import observability.admin.domain.Incident
import observability.admin.domain.Integration

/**
 * Extension point for 3rd-party conversation tools (Slack, Jira, PagerDuty, ...).
 *
 * The admin holds only a reference (`Integration`) — the conversation lives in the
 * provider's own system. A provider is asked to materialize a resource for an
 * incident (create a channel, open a ticket, ...) and to return the reference.
 *
 * Worked example (not shipped): a Jira provider with `id = "jira"`,
 * `label = "Jira"` would, given `params = { "project" -> "DATA" }`, create a new
 * issue in that project and return:
 *   `Integration(type = "jira", label = "DATA-3812", url = "https://acme.atlassian.net/browse/DATA-3812")`
 */
interface IntegrationProvider {
    val id: String
    val label: String
    fun create(incident: Incident, params: Map<String, String>): Integration
}
