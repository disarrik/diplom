"""
Copy public.d1 -> public.d2 in the same Postgres.

We use a ``BaseOperator`` subclass (not ``SQLExecuteQueryOperator``) so that
no registered OpenLineage extractor shadows our explicit facets. The
``DefaultExtractor`` picks up ``get_openlineage_facets_on_complete`` and
emits the dataset edge ``d1`` -> ``d2`` to Marquez.
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
_SRC = OlDataset(namespace=_PG_NAMESPACE, name="d1")
_TGT = OlDataset(namespace=_PG_NAMESPACE, name="d2")

COPY_SQL = """
TRUNCATE d2;
INSERT INTO d2 (src_id, customer_name, amount, status)
SELECT id, customer_name, amount, status FROM d1;
"""


class CopyD1ToD2Operator(BaseOperator):
    def __init__(self, *, conn_id: str = "postgres_demo", **kwargs) -> None:
        super().__init__(**kwargs)
        self.conn_id = conn_id

    def execute(self, context) -> None:
        PostgresHook(postgres_conn_id=self.conn_id).run(COPY_SQL)

    def get_openlineage_facets_on_complete(self, task_instance):  # type: ignore[override]
        _LOG.info("emitting OpenLineage facets: d1 -> d2")
        return OperatorLineage(inputs=[_SRC], outputs=[_TGT])


@dag(
    dag_id="copy_d1_to_d2",
    schedule="@once",
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=["example3", "lineage"],
    doc_md="## Example 3 — step 1\nCopies `d1` -> `d2`; OpenLineage emits the first lineage edge to Marquez.",
)
def copy_d1_to_d2():
    CopyD1ToD2Operator(task_id="copy_d1_to_d2")


copy_d1_to_d2()
