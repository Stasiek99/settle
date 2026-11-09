# Settle

A small app for splitting expenses with friends and keeping track of who owes what, in the spirit of Splitwise. I'm building it to practice Java and Spring Boot properly, so the code is written with real-world habits in mind: migrations, tests against a real database, clear separation of layers.

**Status:** early days. Right now it's just the project skeleton and a running database.

## Tech stack

- Java 21
- Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator)
- PostgreSQL 17 with Flyway migrations
- Testcontainers for integration tests
- Maven (via the included wrapper)

## Running it locally

You'll need JDK 21 and Docker (Docker Desktop must be running).

1. Create your env file and set a password:

   ```bash
   cp .env.example .env
   ```

2. Start the app. Spring Boot starts the Postgres container from `compose.yaml` on its own:

   ```bash
   ./mvnw spring-boot:run
   ```

   On Windows cmd or PowerShell use `mvnw.cmd spring-boot:run`.

3. Check that it's alive: <http://localhost:8080/actuator/health> should return `{"status":"UP"}`.

## Running the tests

```bash
./mvnw test
```

Tests spin up their own throwaway Postgres container, so Docker needs to be running here too.

## Roadmap

Next up: users and groups, then expenses and splitting, balances, auth, and an Angular frontend.
