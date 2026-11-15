# Local startup checklist

## 1. Prerequisites

Choose one run mode:

- **Docker Compose:** Docker Desktop with Compose plugin; about 4 GB RAM and ports 5432, 8080,
  8090, and 9092 available. If 9092 is occupied, set `KAFKA_EXTERNAL_PORT` to an available port
  such as `19092` in `.env`, or prefix the Compose command with `KAFKA_EXTERNAL_PORT=19092`.
- **Run the app on your machine:** JDK 25 and Maven 3.9+, plus Docker Compose for PostgreSQL and
  Kafka. The project currently targets Java 25.

Check Docker tools:

```bash
docker --version
docker compose version
```

Check local Java/Maven (only if not using the all-in-Docker mode):

```bash
java -version
mvn -version
```

## 2. Configure local credentials

Both run modes use the database name `insurancedb` and default to username/password `postgres` /
`postgres`. When running the app on your machine, it connects to your local PostgreSQL at
`jdbc:postgresql://localhost:5432/insurancedb`. Alternatively, set `DB_URL`, `DB_USER`, and
`DB_PASSWORD` as environment variables. To change Docker Compose credentials:

```bash
cp .env.example .env
```

Edit `.env` before startup. These sample credentials are not suitable for a shared or production
environment. Keep `.env` out of source control.

## 3. Start infrastructure and application

### Everything in Docker (recommended first run)

From the repository root:

```bash
docker compose up --build
```

Compose starts PostgreSQL and Kafka, waits for their health checks, then starts the application.
Flyway applies the database migration at application startup. Wait for the Spring Boot
`Started InsuranceApplication` log line.

### Infrastructure in Docker, app on host (development)

In one terminal:

```bash
docker compose up -d postgres kafka kafka-ui
```

In another terminal:

```bash
mvn spring-boot:run
```

The app's default datasource points to `insurancedb` at `localhost:5432`; Kafka points to
`localhost:9092`. In all-in-Docker mode, Compose connects the app to the `insurancedb` PostgreSQL
container using the configured `POSTGRES_USER` / `POSTGRES_PASSWORD`. If you change
`KAFKA_EXTERNAL_PORT` and run the app on your host, set `KAFKA_BOOTSTRAP_SERVERS` to
`localhost:<that-port>` as well.

## 4. Check services

| Service | Address |
|---|---|
| App health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Kafka UI | http://localhost:8090 |
| PostgreSQL | `localhost:5432`, database `insurancedb`, user `postgres` |

The first app start runs Flyway migration `V1__claims_workflow.sql`. Confirm that Kafka UI shows
the `claim.status-changed` topic.

Flyway creates and tracks this application's schema in whichever database URL is configured.
Make sure `insurancedb` is the intended development database (and back it up if it contains data):
a pre-existing table with a migration-created name can cause startup to fail.

## 5. Try claim intake and tracking

In Swagger, call `POST /api/v1/claims` with a request like:

```json
{
  "claimantName": "Alex Example",
  "claimantEmail": "alex@example.test",
  "claimantPhone": "+61123456789",
  "marketCode": "AU",
  "productType": "MOTOR",
  "policyNumber": "POL-10001",
  "incidentDate": "2026-11-15",
  "incidentLocation": "Sydney",
  "incidentDescription": "Vehicle was damaged in a collision.",
  "estimatedLossAmount": 2500.00,
  "currencyCode": "AUD"
}
```

The response contains a `claimNumber` and a one-time `trackingToken`. Save both. Use
`GET /api/v1/claims/{claimNumber}` with header `X-Claim-Tracking-Token: <trackingToken>` to view
the status and history. Requests through staff endpoints are currently unauthenticated and should
only be used on a trusted local development setup.

The staff queue is `GET /api/v1/staff/claims`; `GET /api/v1/staff/claims/workload` returns aggregate
counts/exposure grouped by market, product, currency, status, and assignee. Use the staff endpoints
in Swagger to assign, review, request additional information, record a decision, and record a
confirmed settlement.

## 6. Stop and reset

Stop containers and keep the database volume:

```bash
docker compose down
```

Delete the local database volume as well (this permanently removes local claim data):

```bash
docker compose down -v
```

For startup errors, check `docker compose logs app postgres kafka`, ensure required ports are free,
and verify that a local Java run uses JDK 25.
