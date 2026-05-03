"""
Copy public."order" -> public.order_dwh in the same Postgres.

We use a ``BaseOperator`` subclass (not ``SQLExecuteQueryOperator``) so that
no registered OpenLineage extractor shadows our explicit facets. The
``DefaultExtractor`` picks up ``get_openlineage_facets_on_complete`` and
emits the dataset edge ``order`` -> ``order_dwh`` to Marquez.
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
_SRC = OlDataset(namespace=_PG_NAMESPACE, name="order")
_TGT = OlDataset(namespace=_PG_NAMESPACE, name="order_dwh")

COPY_SQL = """
TRUNCATE order_dwh;
INSERT INTO order_dwh (order_id, customer_name, amount)
SELECT id, customer_name, amount FROM "order";
"""


class CopyOrderToDwhOperator(BaseOperator):
    def __init__(self, *, conn_id: str = "postgres_demo", **kwargs) -> None:
        super().__init__(**kwargs)
        self.conn_id = conn_id

    def execute(self, context) -> None:
        PostgresHook(postgres_conn_id=self.conn_id).run(COPY_SQL)

    def get_openlineage_facets_on_complete(self, task_instance):  # type: ignore[override]
        _LOG.info("emitting OpenLineage facets: order -> order_dwh")
        return OperatorLineage(inputs=[_SRC], outputs=[_TGT])


@dag(
    dag_id="copy_order_to_dwh",
    schedule="@once",
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=["example1", "lineage"],
    doc_md="## Example 1\nCopies `order` -> `order_dwh`; OpenLineage emits lineage to Marquez.",
)
def copy_order_to_dwh():
    CopyOrderToDwhOperator(task_id="copy_order_to_order_dwh")


copy_order_to_dwh()
