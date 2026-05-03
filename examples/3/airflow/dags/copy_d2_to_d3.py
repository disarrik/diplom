"""
Copy public.d2 -> public.d3 in the same Postgres.

This DAG has no schedule (``schedule=None``); it runs only when manually
triggered from the Airflow UI. The point of example 3 is that this lineage
edge appears *after* an incident has already been raised on ``d1`` and
propagated to ``d2`` — triggering this DAG forwards the still-open incident
onto ``d3``.

We use a ``BaseOperator`` subclass (not ``SQLExecuteQueryOperator``) so that
no registered OpenLineage extractor shadows our explicit facets.
"""
from __future__ import annotations

import logging
from datetime import datetime

from airflow.decorators import dag
from airflow.models import BaseOperator
from airflow.providers.openlineage.extractors import OperatorLineage
from airflow.providers.postgres.hooks.postgres import PostgresHook
from openlineage.client.event_v2 import Dataset as OlDataset

_LOG = logging.getLogger(__name__)
_PG_NAMESPACE = "postgres"
_SRC = OlDataset(namespace=_PG_NAMESPACE, name="d2")
_TGT = OlDataset(namespace=_PG_NAMESPACE, name="d3")

COPY_SQL = """
TRUNCATE d3;
INSERT INTO d3 (src_id, customer_name, amount, status)
SELECT src_id, customer_name, amount, status FROM d2;
"""


class CopyD2ToD3Operator(BaseOperator):
    def __init__(self, *, conn_id: str = "postgres_demo", **kwargs) -> None:
        super().__init__(**kwargs)
        self.conn_id = conn_id

    def execute(self, context) -> None:
        PostgresHook(postgres_conn_id=self.conn_id).run(COPY_SQL)

    def get_openlineage_facets_on_complete(self, task_instance):  # type: ignore[override]
        _LOG.info("emitting OpenLineage facets: d2 -> d3")
        return OperatorLineage(inputs=[_SRC], outputs=[_TGT])


@dag(
    dag_id="copy_d2_to_d3",
    schedule=None,
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=["example3", "lineage", "manual"],
    doc_md="## Example 3 — step 3\nManually triggered: copies `d2` -> `d3` after the incident on `d1` is already in flight.",
)
def copy_d2_to_d3():
    CopyD2ToD3Operator(task_id="copy_d2_to_d3")


copy_d2_to_d3()
