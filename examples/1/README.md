# Пример 1: Обнаружение порчи данных через Data Lineage

Демонстрация полного цикла: Airflow копирует данные в DWH, система мониторинга
отслеживает аномалии в таблице `order` и отображает инциденты в панели администратора.

## Архитектура

```
Postgres (postgres-demo)  ──▶  Airflow DAG  ──▶  order_dwh
      │                    │
      │              OpenLineage / Marquez
      │
   app (детектор)  ──▶  observability-admin
```

| Сервис            | URL                      | Логин / Пароль  |
|-------------------|--------------------------|-----------------|
| Airflow           | http://localhost:8080    | admin / admin   |
| Marquez UI        | http://localhost:3000    | —               |
| Admin Panel       | http://localhost:8090    | admin / admin   |
| PostgreSQL        | localhost:**15432**       | postgres / secret |

## Запуск кластера

```bash
docker-compose -f examples/1/docker-compose.yaml up --build
```

Дождитесь, пока все сервисы поднимутся. Готовность можно проверить по логам:

```
airflow-webserver  | [INFO] Listening at: http://0.0.0.0:8080
observability-admin | Started — listening on port 8080
app                | Polling started
```

> Postgres выставлен на порт **15432** (а не 5432), чтобы не конфликтовать с локальной
> установкой PostgreSQL.

## Запуск Airflow DAG

1. Откройте http://localhost:8080 и войдите под `admin / admin`.
2. Найдите DAG **`copy_order_to_dwh`** и включите его (тоглом слева).
3. Запустите вручную кнопкой ▶ **Trigger DAG**.
4. DAG скопирует строки из таблицы `order` в `order_dwh` и отправит lineage-события
   в Marquez.

После успешного прогона граф зависимостей можно посмотреть в Marquez UI:
http://localhost:3000.

## Нормальное состояние системы

Откройте панель администратора: http://localhost:8090.

Детектор `UniqueValuesDetector` каждые 5 секунд выполняет запрос:

```sql
SELECT COUNT(DISTINCT "status") FROM "order";
```

В базе три заказа с допустимыми статусами (`pending`, `processing`, `completed`),
поэтому счётчик уникальных значений стабилен — инцидентов нет.

## Имитация порчи данных

Подключитесь к базе данных:

```bash
psql -h localhost -p 15432 -U postgres -d postgres-demo
# пароль: secret
```

Вставьте заказ с **неизвестным** статусом:

```sql
INSERT INTO "order" (customer_name, amount, status)
VALUES ('Evil Corp', 9999.99, 'unknown');
```

Это увеличивает число уникальных значений `status` с 3 до 4. Детектор обнаружит
изменение в следующем цикле опроса (≤ 5 секунд) и зарегистрирует инцидент типа
`UNIQUE_VALUES_INCREASE`.

### Что происходит дальше

1. **app** отправляет инцидент в `observability-admin` по HTTP.
2. `IncidentAggregator` определяет владельца датасорса через lineage-граф из Marquez.
3. Команде автоматически назначается инцидент.
4. В панели администратора появляется карточка инцидента.

## Просмотр инцидента в панели администратора

1. Обновите страницу http://localhost:8090 (или подождите авто-обновления).
2. В списке инцидентов появится новый со статусом **Open**.
3. Кликните на инцидент для просмотра деталей:
   - тип: `UNIQUE_VALUES_INCREASE`
   - источник: `postgres / order`
   - затронутые датасорсы (транзитивно через lineage)
   - назначенная команда и ответственный
4. Нажмите **Mark resolved**, чтобы закрыть инцидент вручную.

