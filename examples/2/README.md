# Пример 2: Разделённая архитектура — importer + processor через Kafka

Тот же сценарий, что и в `examples/1`, но `app` запускается в двух ролях,
которые общаются через Kafka:

- **app-importer** — крутит `TrivialImporter`: опрашивает Marquez и Postgres,
  публикует события в Kafka. Масштабируется горизонтально по числу источников.
- **app-processor** — обрабатывает события из Kafka, владеет графом lineage
  (`PostgresStateService`, БД `state_storage`), вызывает `observability-admin` через HTTP.
  Обычно достаточно одной реплики.

Профиль выбирается переменной окружения `OBSERVABILITY_MODE`
(`monolith` | `processor` | `importer`). Образ Docker один и тот же —
`examples/1/Dockerfile.app`; различаются только переменные окружения.

## Архитектура

```
[ app-importer (Nx) ]  ──►  observability.lineage   (key=lineage.id)
TrivialImporter         ──►  observability.incidents (key=incident.id)
   ↑ proxies реализуют LineageProcessor / IncidentProcessor
                                 │
                                 ▼
                            [ Kafka ]  (single-node KRaft)
                                 │
                                 ▼
[ app-processor (1x) ]
LineageConsumerLoop  → StdLineageProcessor   ─┐
IncidentConsumerLoop → StdIncedentProcessor ─┤  state: PostgresStateService (state_storage DB)
                                              ▼
                                      HttpNotifyService → observability-admin
```

| Сервис            | URL                      | Логин / Пароль    |
|-------------------|--------------------------|-------------------|
| Airflow           | http://localhost:8080    | admin / admin     |
| Marquez UI        | http://localhost:3000    | —                 |
| Admin Panel       | http://localhost:8090    | admin / admin     |
| PostgreSQL        | localhost:**15432**      | postgres / secret |
| Kafka (PLAINTEXT) | localhost:**9092**       | —                 |

## Запуск

```bash
docker compose -f examples/2/docker-compose.yaml up --build
```

После старта:

```
app-importer    | OBSERVABILITY_MODE=importer
app-importer    | importer: started; publishing to kafka:9092 ...
app-processor   | OBSERVABILITY_MODE=processor
app-processor   | NotifyService: HttpNotifyService ...
app-processor   | processor: started; consuming from kafka:9092 ...
```

Дальше — точно как в `examples/1`: запустите DAG `copy_order_to_dwh` в Airflow,
вставьте «битый» статус в Postgres и посмотрите карточку инцидента в
admin-панели на http://localhost:8090.

```sql
-- psql -h localhost -p 15432 -U postgres -d postgres-demo  (пароль: secret)
INSERT INTO "order" (customer_name, amount, status)
VALUES ('Evil Corp', 9999.99, 'unknown');
```

## Проверка событий в Kafka

```bash
docker compose -f examples/2/docker-compose.yaml exec kafka \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic observability.lineage \
  --from-beginning --max-messages 5 --property print.key=true
```

То же для `observability.incidents`. Сообщения — JSON; ключ — UUID события.

## Горизонтальное масштабирование importer'а

```bash
docker compose -f examples/2/docker-compose.yaml up -d --scale app-importer=3
```

Все три реплики опрашивают одни и те же источники (Marquez/Postgres) — это
учебный пример, поэтому события будут дублироваться. На практике
дедупликация делается либо шардированием источников по репликам, либо
дедупом на стороне процессора по `lineage.id` / `incident.id`.

## Что НЕ изменилось по сравнению с `examples/1`

- Образ `app` собирается из того же `examples/1/Dockerfile.app`.
- Образы Airflow и observability-admin — те же `examples/1/Dockerfile.*`.
- DAG'и Airflow и init-скрипты Postgres скопированы в `examples/2/airflow` и
  `examples/2/postgres` (compose монтирует их через bind-mount).
- `importer-config.yaml` идентичен примеру 1.

## Топики

Создаются автоматически (`KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`). На проде
лучше создавать заранее:

```bash
kafka-topics.sh --bootstrap-server kafka:9092 --create \
  --topic observability.lineage --partitions 3 --replication-factor 1
kafka-topics.sh --bootstrap-server kafka:9092 --create \
  --topic observability.incidents --partitions 3 --replication-factor 1
```
