# AI Journal

A record of how I used an AI assistant (Claude) to build this project: what I asked, what it
produced, the decisions I made, and what I still have to verify myself.

- **Project:** Insurance Platform (Spring Boot, Kafka, PostgreSQL, Docker)
- **AI tool:** Claude (Anthropic)
- **Goal:** a realistic project with the current Java stack, everything in **one Git repository**
  that is easy to understand, with complete setup documentation.

---

## Timeline

### 2026-11-14: First attempt (discarded)
- I asked for an insurance project with Spring Boot, Kafka and Docker, plus documentation and
  this journal. The AI proposed an event-driven policy and claims platform and asked about the
  structure.
- I explored **three separate services in a monorepo** (aggregator POM, a shared `common-events`
  module) and **a single application**, and went back and forth between them.
- I chose three services, and the AI generated the whole thing in one go (about 70 files).
- **Why I dropped it:** it was too much to read and understand at once, and none of it had been
  built or run. I wanted to understand the codebase first and add logic step by step.

### 2026-11-15, 23:49: Restart with a single application
- **I asked:** start from scratch, one Git repo that explains the whole codebase, keep it simple,
  begin with the structure of **one application** (including Kafka, producer and consumer
  configuration), then implement the logic step by step.
- **AI did:**
  1. Re-checked versions instead of reusing the old ones. Spring Boot **4.1.2** is the latest
     stable release (4.2 is only at milestone stage); springdoc **3.1.0** targets Boot 4.1.
  2. Wrote a skeleton: POM, main class, `application.yml`, Kafka topic / producer / consumer
     configuration, an `EventPublisher`, `package-info.java` files describing what each empty
     package will hold, Dockerfile, `docker-compose.yml` (Postgres, Kafka in KRaft mode, Kafka UI,
     the app), README and this journal.
  3. Checked what it could (see "Verification").

---

## Design decisions

| Decision | Reason |
|---|---|
| **One application, one repo** | Simplest thing to read, run and explain. |
| **Packages by feature** (`policy`, `claims`, `notification`) with layers inside | A reader can find everything about one business area in one place. |
| **Features only communicate through Kafka events** | Keeps the three parts decoupled, so they can be split into services later. |
| **Kafka config written in Java** (`config/Kafka*Config.java`) | Producer, consumer and topic settings are visible in code, which is the point of the exercise. |
| **String keys and JSON-string values** | Kafka does not depend on our Java classes; `EventPublisher` does the JSON conversion. |
| **Publish after DB commit** | A rolled-back transaction must not produce an event. |
| **Kafka in KRaft mode with two listeners** (`kafka:19092`, `localhost:9092`) | Works for the app in Docker and for the app run from my IDE. |
| **Flyway owns the schema, `ddl-auto: none`** | Versioned, repeatable database changes. |
| **No Lombok** | Fewer moving parts with a very new Java / Spring Boot combination. |
| **Retry 3 times, then skip** for failing consumers | Simple default; a dead-letter topic comes later. |

---

## Verification

Done by the AI: XML and YAML syntax checks, and cross-checking names and ports between files.

**Not done (to do on my machine):**

- [ ] `mvn clean package` succeeds with JDK 25
- [ ] `docker compose up --build` starts all four containers
- [ ] `http://localhost:8080/actuator/health` returns `UP`
- [ ] Swagger UI opens
- [ ] Kafka UI shows the topics `policy.issued`, `policy.cancelled`, `claim.status-changed`

**Problems found and fixed by hand:**

| Problem | Cause | Fix |
|---|---|---|
| | | |

---

## Lessons so far

- Generating everything at once gave me code I did not understand yet. A skeleton first, then
  one feature at a time, is easier to follow and to verify.
- Versions of new frameworks change quickly, so they were looked up again instead of reused.

## Next

See "Next steps" in the README.

### 2026-11-16: Claims workflow foundation
- **I asked:** build the backend for the Chubb APAC claims process, emphasizing the data model,
  controller/service/repository boundaries, Kafka usage, README, journal, Docker setup, and an
  explicit application startup guide. Broader testing is deferred until the workflow is complete.
- **AI did:** added a Flyway/PostgreSQL claims schema; claim intake, token-protected tracking,
  staff queue/assignment/review/decision and claimant information request/response layers; a
  PostgreSQL transactional outbox with a Kafka relay; REST validation/error mapping; and
  `STARTUP.md` with prerequisites, start commands, checks, and an example API request.
- **Design choices:** keep the claim and event in one DB transaction; publish at least once from
  the outbox, using claim number as Kafka key and event ID for consumer deduplication; store only
  a hash of the claimant tracking token; store document metadata in PostgreSQL and document bytes
  in an eventual object store. Market codes are not hardcoded to an assumed six-market list.
- **Not implemented:** authentication/authorization, policy verification, document upload,
  payment execution, notification consumers, market/currency configuration, and staff performance
  aggregates. Do not deploy the staff API publicly without access control.
- **Verification:** not run yet. The available local runtime reports Java 23 while this project
  targets Java 25; tests are deferred by request until the workflow scope is complete.
