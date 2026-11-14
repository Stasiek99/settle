# Settle — Dzielenie Wydatków ze Znajomymi

**Aplikacja w stylu Splitwise do wspólnych wydatków w grupie: kto komu ile jest winien i jak się rozliczyć jak najmniejszą liczbą przelewów.**

![CI](https://github.com/Stasiek99/settle/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-ed8b00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6db33f?logo=springboot&logoColor=white)
![Angular](https://img.shields.io/badge/Angular-21-dd0031?logo=angular&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178c6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169e1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ed?logo=docker&logoColor=white)
![Deployed on Vercel](https://img.shields.io/badge/Frontend-Vercel-000?logo=vercel&logoColor=white)
![Deployed on Railway](https://img.shields.io/badge/Backend-Railway-0b0d0e?logo=railway&logoColor=white)

**Strona na żywo:** [settle-coral-seven.vercel.app](https://settle-coral-seven.vercel.app)

[🇬🇧 English](README.md) | 🇵🇱 Polski

---

## Czym jest ten projekt

Aplikacja full-stack: REST API w Spring Boot z PostgreSQL i frontend w Angularze. Tworzysz grupę, dodajesz wydatki dzielone po równo, procentowo lub dokładnymi kwotami i widzisz salda oraz minimalny zestaw przelewów, który wszystkich rozlicza.

---

## Zrzuty ekranu

### Wydatki

![Wydatki](images/expenses.png)

### Salda w każdej walucie

![Salda](images/balances.png)

### Sugerowane przelewy

![Rozliczenia](images/settle-up.png)

### Nowy wydatek

![Nowy wydatek](images/new-expense.png)

---

## Funkcje

- **Grupy** z członkami (do 16), role właściciela i członka
- **Wydatki** dzielone po równo, procentowo lub dokładnymi kwotami; pieniądze w jednostkach podstawowych, groszowe reszty rozdzielane sprawiedliwie
- **Wiele walut** — salda i przelewy liczone osobno dla każdej waluty, bez kursów
- **Rozliczenia** — sugerowane przelewy, które zamykają wszystkie długi najmniejszą liczbą płatności
- **Uwierzytelnianie** — sesje w ciasteczkach httpOnly, rotacja refresh tokenów, ochrona CSRF, limity prób logowania, rejestracji i odświeżania
- **Niezawodność** — blokowanie optymistyczne z ETagami, blokady pesymistyczne przy równoległych zapisach, cache Caffeine, stronicowane listy
- **Jakość** — migracje Flyway, testy integracyjne na prawdziwym PostgreSQL (Testcontainers), CI w GitHub Actions z testem Dockera

---

## Stos technologiczny

| Warstwa | Technologia |
|---|---|
| Backend | Java 21, Spring Boot 4.1 (Web MVC, Data JPA, Security) |
| Baza danych | PostgreSQL 17, Flyway |
| Frontend | Angular 21 (standalone, signals, zoneless), PrimeNG |
| Testy | JUnit, Testcontainers, Vitest |
| CI / kontenery | GitHub Actions, Docker, nginx |
| Hosting | Vercel (frontend), Railway (backend + baza) |

---

## Uruchomienie lokalnie

Potrzebne: JDK 21, Node i Docker. Skopiuj `.env.example` do `.env` i ustaw `JWT_SECRET` (min. 32 znaki).

```bash
./mvnw spring-boot:run          # API na :8080, sam startuje Postgresa z compose.yaml
cd frontend && npm ci && npm start   # aplikacja na :4200
```

Całość w Dockerze (aplikacja na `:8081`): `docker compose -f compose.yaml -f compose.app.yaml up --build`

Testy: `./mvnw verify` (Docker musi działać).
