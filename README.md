# Data observability

Gradle multi-project (`data-observability`) for observing data quality, lineage, and impact when upstream data is bad.

| Module | Role |
|--------|------|
| **common** | Shared models and utilities (events, stats, storage-related types). |
| **state-storage** | Graph store: tables, columns, metadata, and links between entities. |
| **importer** | Polls DBs for metrics and detects issues → publishes to a topic; polls lineage tools (e.g. Airflow, dbt) → publishes lineage updates to the topic. |
| **processor** | Consumes the topic, updates state-storage, opens incidents on all downstream assets affected by source-side corruption. |

For AI-assisted work in Cursor, see `.cursor/rules/project-context.mdc` for the same architecture summary as project rules.
