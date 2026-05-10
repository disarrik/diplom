# Пример 3: Поздно появившийся lineage и распространение незакрытого инцидента

Сценарий, который не покрывают примеры 1 и 2: новая lineage-связь между
датасорсами добавляется **уже после** того, как инцидент был зафиксирован,
но **до** того, как его закрыли. Цель примера — увидеть, как открытый инцидент
автоматически распространяется на ещё не существовавший на момент его
регистрации датасорс.

## Сценарий

1. Airflow-DAG `copy_d1_to_d2` создаёт первую lineage-связь: `d1 → d2`.
2. В `d1` вставляют «битый» статус — `UniqueValuesDetector` фиксирует инцидент
   на `d1`, и тот распространяется на `d2` по уже существующему ребру lineage.
3. Пока инцидент **открыт**, оператор вручную запускает второй DAG —
   `copy_d2_to_d3` (без расписания). Это создаёт новое ребро `d2 → d3`.
4. Ожидаемый результат: тот же инцидент автоматически появляется и на `d3`,
   несмотря на то, что данные `d3` сами по себе не «портились».

## Архитектура

```
Postgres (postgres-demo)
   d1  ──[DAG copy_d1_to_d2, @once]──▶  d2  ──[DAG copy_d2_to_d3, manual]──▶  d3
                       │                                  │
                       └──────  OpenLineage / Marquez  ───┘
                                         │
                       app (TrivialImporter + StdLineageProcessor + StdIncedentProcessor)
                                         │
                                         ▼
                              observability-admin (UI 8090)
```

| Сервис            | URL                      | Логин / Пароль    |
|-------------------|--------------------------|-------------------|
| Airflow           | http://localhost:8080    | admin / admin     |
| Marquez UI        | http://localhost:3000    | —                 |
| Admin Panel       | http://localhost:8090    | admin / admin     |
| Grafana           | http://localhost:3001    | admin / admin (или anonymous Viewer) |
| Prometheus        | http://localhost:9090    | —                 |
| PostgreSQL        | localhost:**15432**      | postgres / secret |

## Запуск кластера

```bash
docker compose -f examples/3/docker-compose.yaml up --build
```

Дождитесь, пока все сервисы поднимутся. Готовность можно проверить по логам:

```
airflow-webserver   | [INFO] Listening at: http://0.0.0.0:8080
observability-admin | Started — listening on port 8080
app                 | Polling started
```

> Postgres выставлен на порт **15432** (а не 5432), чтобы не конфликтовать с
> локальной установкой PostgreSQL.

## Шаг 1 — создаём lineage `d1 → d2`

1. Откройте http://localhost:8080 и войдите под `admin / admin`.
2. Найдите DAG **`copy_d1_to_d2`**. Расписание — `@once`, так что после
   включения он выполнится автоматически (можно и руками — кнопкой ▶).
3. Дождитесь зелёного запуска. В Marquez UI (http://localhost:3000) появится
   ребро `d1 → d2` в namespace `postgres`.

На этом шаге система в нормальном состоянии: `UniqueValuesDetector` каждые
5 секунд считает `SELECT COUNT(DISTINCT status) FROM d1` (3 уникальных статуса:
`pending`, `processing`, `completed`), инцидентов нет.

## Шаг 2 — портим `d1`

Подключитесь к базе и вставьте строку с неизвестным статусом:

```bash
psql -h localhost -p 15432 -U postgres -d postgres-demo
# пароль: secret
```

```sql
INSERT INTO d1 (customer_name, amount, status)
VALUES ('Evil Corp', 9999.99, 'unknown');
```

Число уникальных значений `status` вырастает с 3 до 4. В течение ≤ 5 секунд
детектор регистрирует инцидент `UNIQUE_VALUES_INCREASE`. Откройте панель
администратора http://localhost:8090 — инцидент должен появиться сразу на
двух датасорсах: `d1` (источник) и `d2` (потомок по lineage).

> **Не закрывайте инцидент.** Цель примера — увидеть, что происходит, когда
> новая lineage-связь добавляется при ещё открытом инциденте.

## Шаг 3 — вручную запускаем `copy_d2_to_d3`

1. В Airflow найдите DAG **`copy_d2_to_d3`**. Поле «Schedule» отображается
   как `None` — DAG не запускается сам.
2. Нажмите ▶ **Trigger DAG**.
3. После завершения в Marquez появится новое ребро `d2 → d3`.

## Шаг 4 — наблюдаем распространение инцидента на `d3`

Обновите панель администратора (http://localhost:8090). Тот же открытый
инцидент теперь должен числиться и на `d3`, хотя данные `d3` напрямую не
портились — он распространился по только что созданной lineage-связи
`d2 → d3`. Это и есть точка примера.

Логика, которая это обеспечивает, реализована в
`std-processor/src/main/kotlin/observability/std/processor/StdLineageProcessor.kt`:
при обработке нового lineage-события процессор вызывает
`getActiveIncidentsRecursively(source)` и для каждого активного инцидента
уведомляет таргет и всех его потомков.

## Метрики детекторов в Grafana

http://localhost:3001 — дашборд **Detector Metrics** (preset из
`examples/1/grafana/`). На графике видно, как `unique_values_count` для `d1`
растёт с 3 до 4 в момент шага 2; теги `namespace` / `table` / `column`
позволяют фильтровать по конкретному датасорсу.

## Завершение

```bash
docker compose -f examples/3/docker-compose.yaml down -v
```
