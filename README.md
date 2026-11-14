# Settle — Split Expenses with Friends

**Splitwise-style app for sharing expenses in a group, tracking who owes whom, and settling up with the fewest transfers.**

![CI](https://github.com/Stasiek99/settle/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-ed8b00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6db33f?logo=springboot&logoColor=white)
![Angular](https://img.shields.io/badge/Angular-21-dd0031?logo=angular&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178c6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169e1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ed?logo=docker&logoColor=white)
![Deployed on Vercel](https://img.shields.io/badge/Frontend-Vercel-000?logo=vercel&logoColor=white)
![Deployed on Railway](https://img.shields.io/badge/Backend-Railway-0b0d0e?logo=railway&logoColor=white)

**Live site:** [settle-coral-seven.vercel.app](https://settle-coral-seven.vercel.app)

🇬🇧 English | [🇵🇱 Polski](README.pl.md)

---

## What it is

A full-stack app: a Spring Boot REST API with PostgreSQL and an Angular frontend. Create a group, add expenses split equally, by percentage or by exact amounts, and see balances plus a minimal set of transfers that settles everyone up.

---

## Screenshots

### Expenses

![Expenses](images/expenses.png)

### Balances per currency

![Balances](images/balances.png)

### Suggested transfers

![Settle up](images/settle-up.png)

### New expense

![New expense](images/new-expense.png)

---

## Features

- **Groups** with members (up to 16), owner and member roles
- **Expenses** split equally, by percentage or exact amounts; money stored in minor units with fair rounding of leftover cents
- **Multiple currencies** — balances and transfers are computed per currency, no exchange rates
- **Settle up** — suggested transfers that clear all debts with the fewest payments
- **Auth** — httpOnly cookie sessions, refresh token rotation, CSRF protection, rate limiting of sign-in, registration and refresh
- **Reliability** — optimistic locking with ETags, pessimistic locks for concurrent writes, Caffeine caches, paginated lists
- **Quality** — Flyway migrations, integration tests on real PostgreSQL (Testcontainers), GitHub Actions CI including a Docker smoke test

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1 (Web MVC, Data JPA, Security) |
| Database | PostgreSQL 17, Flyway |
| Frontend | Angular 21 (standalone, signals, zoneless), PrimeNG |
| Tests | JUnit, Testcontainers, Vitest |
| CI / containers | GitHub Actions, Docker, nginx |
| Hosting | Vercel (frontend), Railway (backend + database) |

---

## Run locally

Needs JDK 21, Node and Docker. Copy `.env.example` to `.env` and set `JWT_SECRET` (32+ characters).

```bash
./mvnw spring-boot:run          # API on :8080, starts Postgres from compose.yaml
cd frontend && npm ci && npm start   # app on :4200
```

Everything in Docker (app on `:8081`): `docker compose -f compose.yaml -f compose.app.yaml up --build`

Tests: `./mvnw verify` (Docker must be running).
