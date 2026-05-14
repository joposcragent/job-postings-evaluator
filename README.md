# job-postings-evaluator

Сервис оценки вакансий: HTTP API, Kafka (`async-job.job-posting-evaluate`), jOOQ к `job_postings.postings`, OpenFeign к settings-manager и sentence-transformer.

## jOOQ и схема БД

1. Поднимите PostgreSQL и примените миграции. Удобный вариант — compose из [specifications/database-schema/docker-compose.yaml][db-compose] (образ Flyway: `joposcragent/flyway:${FLYWAY_IMAGE_TAG:-latest}`; при ручном pull можно использовать `FLYWAY_TAG` / `latest`).
2. Задайте `JOOQ_DB_URL`, `JOOQ_DB_USER`, `JOOQ_DB_PASSWORD` и выполните `./gradlew generateJooq`.
3. Сборка: `./gradlew check`.

По умолчанию для генерации: `jdbc:postgresql://localhost:5432/joposcragent`.

## Локальный профиль

Профиль `local` в [application.yaml][app-yaml].

[db-compose]: ../../specifications/database-schema/docker-compose.yaml
[app-yaml]: src/main/resources/application.yaml
