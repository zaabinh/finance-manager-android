# FinTrack - Finance Manager Application

A personal finance management system for tracking income and expenses, managing multiple wallets, categorizing transactions, planning monthly budgets, and analyzing spending, all in one platform.

The system consists of:

- **Android app**: used by end users to manage their personal finances, with on-device OCR for fast transaction entry.
- **Web Admin**: a React app for system administrators.
- **Backend**: a Spring Boot REST API.
- **Database**: PostgreSQL, versioned with Flyway.

---

## Table of Contents

- [FinTrack - Finance Manager Application](#fintrack---finance-manager-application)
  - [Table of Contents](#table-of-contents)
  - [Architecture](#architecture)
  - [Tech Stack](#tech-stack)
  - [Repository Structure](#repository-structure)
  - [Features](#features)
  - [User Roles](#user-roles)
  - [Database Entities](#database-entities)
  - [OCR Pipeline](#ocr-pipeline)
  - [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [1. Database and backend](#1-database-and-backend)
    - [2. Android app](#2-android-app)
    - [3. Web Admin](#3-web-admin)
  - [Development Principles](#development-principles)
  - [Roadmap](#roadmap)
  - [Contributing](#contributing)

---

## Architecture

```
┌──────────────────────┐        ┌──────────────────────┐
│   Android App (USER) │        │ Web Admin (ADMIN)    │
│  Java · MVVM · OCR   │        │ React                │
└──────────┬───────────┘        └──────────┬───────────┘
           │   HTTPS / JSON (JWT)          │
           └───────────────┬───────────────┘
                           ▼
              ┌─────────────────────────┐
              │  Backend REST API       │
              │  Spring Boot · Java 21  │
              │  Controller → Service   │
              │  → Repository (JPA)     │
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │  PostgreSQL + Flyway    │
              └─────────────────────────┘
```

OCR runs **entirely on the Android device**. The backend only receives transaction data after the user has confirmed it, never the original image.

---

## Tech Stack

| Layer          | Technologies |
|----------------|--------------|
| Android        | Java, XML layouts, Material Components, MVVM (ViewModel + LiveData), Retrofit + OkHttp, Gson, Room (optional cache), CameraX, Google ML Kit Text Recognition v2 |
| Web Admin      | React, REST API client |
| Backend        | Java 21, Spring Boot, Spring Web, Spring Security, JWT (access + refresh token), Spring Data JPA / Hibernate, Bean Validation |
| Database       | PostgreSQL, Flyway migrations |
| API tooling    | Swagger / OpenAPI, Postman |
| Infrastructure | Docker Compose (local), VPS (deployment, TBD) |

---

## Repository Structure

```
finance-manager-android/
├── .github/
│   └── workflows/
│       ├── android-ci.yml             # Build + unit tests for the Android app
│       ├── backend-ci.yml             # Build + tests for the backend
│       └── web-admin-ci.yml           # Lint + build for the web admin
│
├── android/                           # Native Android app (Java, MVVM)
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/fintrack/app/
│   │   │   │   │   ├── FinTrackApplication.java
│   │   │   │   │   ├── core/                  # Shared, feature-agnostic code
│   │   │   │   │   │   ├── network/           # Retrofit client, AuthInterceptor, TokenAuthenticator (auto refresh)
│   │   │   │   │   │   ├── database/          # Room database, DAOs, entities (optional cache)
│   │   │   │   │   │   ├── session/           # Secure token storage, session manager
│   │   │   │   │   │   ├── model/             # Shared models (Resource<T>, ApiError, enums)
│   │   │   │   │   │   ├── ui/                # Base activities/fragments, common adapters, custom views
│   │   │   │   │   │   └── util/              # Currency/date formatters, validators
│   │   │   │   │   ├── auth/                  # Login, register, verify email, forgot/reset/change password
│   │   │   │   │   ├── home/                  # Dashboard: balance, monthly income/expense/savings, recent tx
│   │   │   │   │   ├── wallet/
│   │   │   │   │   ├── category/
│   │   │   │   │   ├── transaction/
│   │   │   │   │   ├── budget/
│   │   │   │   │   ├── analytics/
│   │   │   │   │   ├── notification/
│   │   │   │   │   ├── settings/              # Profile, currency, timezone, account deletion
│   │   │   │   │   └── ocr/
│   │   │   │   │       ├── capture/           # CameraX / gallery picker
│   │   │   │   │       ├── recognition/       # ML Kit text recognition (layer 1: OCR)
│   │   │   │   │       ├── extraction/        # Merchant, total, date, line items (layer 2)
│   │   │   │   │       ├── prediction/        # Rule-based / ML category classifier (layer 3)
│   │   │   │   │       └── review/            # OCR review & confirm screen
│   │   │   │   ├── res/
│   │   │   │   │   ├── layout/
│   │   │   │   │   ├── drawable/
│   │   │   │   │   ├── navigation/
│   │   │   │   │   ├── menu/
│   │   │   │   │   ├── values/                # strings, colors, themes, dimens
│   │   │   │   │   └── values-vi/             # Vietnamese translations
│   │   │   │   ├── assets/
│   │   │   │   │   └── merchant_dictionary.json   # Merchant → category keywords
│   │   │   │   └── AndroidManifest.xml
│   │   │   ├── test/                          # JVM unit tests (extraction, prediction, ViewModels)
│   │   │   └── androidTest/                   # Instrumented / UI tests
│   │   ├── build.gradle
│   │   └── proguard-rules.pro
│   ├── gradle/
│   ├── build.gradle
│   ├── settings.gradle
│   └── gradle.properties
│
├── backend/                           # Spring Boot REST API (Java 21)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/fintrack/api/
│   │   │   │   ├── FinTrackApiApplication.java
│   │   │   │   ├── config/                    # Security, CORS, OpenAPI, Jackson, scheduling
│   │   │   │   ├── security/                  # JWT provider, auth filter, UserDetails, entry points
│   │   │   │   ├── controller/                # REST controllers (thin, no business logic)
│   │   │   │   │   └── admin/                 # /api/admin/** endpoints
│   │   │   │   ├── service/                   # Business rules, ownership checks, calculations
│   │   │   │   │   └── impl/
│   │   │   │   ├── repository/                # Spring Data JPA repositories
│   │   │   │   ├── entity/                    # JPA entities
│   │   │   │   │   └── enums/                 # WalletType, CategoryType, TransactionType, InputMethod, ...
│   │   │   │   ├── dto/
│   │   │   │   │   ├── request/
│   │   │   │   │   └── response/
│   │   │   │   ├── mapper/                    # Entity ↔ DTO mappers
│   │   │   │   ├── exception/                 # Custom exceptions + GlobalExceptionHandler
│   │   │   │   ├── scheduler/                 # Daily reminders, budget checks
│   │   │   │   └── util/
│   │   │   └── resources/
│   │   │       ├── db/migration/              # Flyway scripts: V1__init_schema.sql, V2__seed_default_categories.sql, ...
│   │   │       ├── application.yml
│   │   │       ├── application-dev.yml
│   │   │       └── application-prod.yml
│   │   └── test/
│   │       └── java/com/fintrack/api/         # Unit + integration tests (Testcontainers PostgreSQL)
│   ├── Dockerfile
│   ├── pom.xml
│   └── .env.example
│
├── web-admin/                         # React admin dashboard
│   ├── public/
│   ├── src/
│   │   ├── api/                       # Axios instance, interceptors, endpoint modules
│   │   ├── auth/                      # Admin login, token handling, protected routes
│   │   ├── components/                # Reusable UI components
│   │   ├── layouts/                   # Admin shell (sidebar, header)
│   │   ├── pages/
│   │   │   ├── dashboard/             # User/transaction/wallet/category counts, OCR usage
│   │   │   ├── users/                 # User list, detail, disable
│   │   │   ├── categories/            # Default category management
│   │   │   └── notifications/         # Create, send, history
│   │   ├── hooks/
│   │   ├── routes/
│   │   ├── utils/
│   │   ├── App.jsx
│   │   └── main.jsx
│   ├── package.json
│   ├── vite.config.js
│   └── .env.example
│
├── docs/
│   ├── api/                           # OpenAPI export, Postman collection
│   ├── database/                      # ERD, schema notes
│   ├── architecture/                  # Diagrams, design decisions
│   ├── ocr/                           # OCR pipeline, extraction rules, classifier notes
│   └── ui/                            # Wireframes, screen flows
│
├── docker-compose.yml                 # PostgreSQL + backend for local development
├── .gitignore
├── CONTRIBUTING.md
└── README.md
```

---

## Features

| Module | Backend | Android / Web |
|--------|---------|---------------|
| **Authentication** | Register, login, access + refresh tokens, refresh, logout (current device / all devices), change password, verify email | Register, login, email verification, forgot/reset password, change password, automatic token refresh |
| **User** | Get/update profile, deactivate account, currency and timezone preferences | Profile, edit profile, settings, currency, timezone, account deletion |
| **Wallet** | CRUD, deactivate, balance calculation. Types: `CASH`, `BANK`, `SAVINGS` | Wallet list, detail, create/edit, balance |
| **Category** | CRUD, system defaults + user-created. Types: `INCOME`, `EXPENSE` | Income/expense category lists, create/edit/deactivate |
| **Transaction** | CRUD, search, filter by date range / wallet / category / type. Input methods: `MANUAL`, `RECEIPT_OCR`, `BANK_SCREENSHOT_OCR` | List, add, edit, detail, search, filter |
| **Budget** | Monthly budget per expense category; limit, spent, remaining, usage %, status (`SAFE` / `WARNING` / `EXCEEDED`) | Budget list, progress, create/edit, detail, warning visuals |
| **Dashboard** | Total balance, monthly income/expense/savings, recent transactions, budget summary (computed, no table) | Home screen |
| **Analytics** | Expense/income by category, income vs expense by month, date range, daily/weekly/monthly | Pie/donut and bar/line charts, date filters |
| **Notifications** | List, mark read, unread count, delete, settings, budget notifications. Types: `DAILY_REMINDER`, `BUDGET_WARNING`, `BUDGET_EXCEEDED`, `SYSTEM` | Notification center, unread badge, notification settings |
| **OCR Import** | Receives only confirmed transactions | Capture/select image, OCR, review, edit, confirm |
| **Admin** | Stats, user management, default categories, system notifications | React Web Admin |

> Monthly Savings = Monthly Income − Monthly Expense

---

## User Roles

| Role | Client | Access |
|------|--------|--------|
| `USER` | Android app | Only their own financial data |
| `ADMIN` | Web Admin | System-level data (users, default categories, system notifications, aggregate statistics). Admins **cannot** see users' detailed transactions, wallet balances or financial history. |

---

## Database Entities

```
users ─┬─< wallets ──────< transactions >── categories ──< budgets
       ├─< transactions
       ├─< budgets
       ├─< notifications
       ├── notification_settings (1:1)
       └─< refresh_tokens
```

| Group | Tables |
|-------|--------|
| Core | `users`, `wallets`, `categories`, `transactions`, `budgets` |
| Notification | `notifications`, `notification_settings` |
| Security | `refresh_tokens` |

- Dashboard and analytics are **derived** and have no dedicated tables.
- OCR images are **not stored** by default.
- Soft deletion is used where historical financial consistency matters (wallets, categories).

---

## OCR Pipeline

```
Image (CameraX / Gallery)
   → [1] OCR: ML Kit Text Recognition v2 → raw text
   → [2] Information Extraction: merchant, total, date, line items
   → [3] Category Prediction: category + confidence
   → User Review & Edit
   → Transaction API → PostgreSQL
```

**Category prediction**

- **Baseline:** rule-based keyword matching and a merchant dictionary
  (e.g. Highlands, KFC, Starbucks → Food; Grab, Be, Xanh SM → Transportation; Shopee, Lazada → Shopping).
- **Optional ML:** text preprocessing → TF-IDF → Logistic Regression / Linear SVM → category + confidence.
- If confidence is low, the user must choose the category manually.

---

## Getting Started

### Prerequisites

- JDK 21
- Android Studio (latest stable), Android SDK
- Node.js 20+
- Docker & Docker Compose

### 1. Database and backend

```bash
cp backend/.env.example backend/.env    # fill in DB credentials and JWT secrets
docker compose up -d                    # starts PostgreSQL (and backend, if configured)

cd backend
./mvnw spring-boot:run                  # Flyway migrations run automatically on startup
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

### 2. Android app

1. Open the `android/` folder in Android Studio.
2. Set the API base URL (e.g. `http://10.0.2.2:8080/` for the emulator) in `local.properties` or `gradle.properties`.
3. Run the `app` configuration.

### 3. Web Admin

```bash
cd web-admin
cp .env.example .env                    # VITE_API_BASE_URL=http://localhost:8080
npm install
npm run dev
```

---

## Development Principles

- RESTful APIs; expose **DTOs**, never JPA entities.
- Validate all backend input with Bean Validation.
- Enforce **resource ownership**: users must never access another user's financial data.
- Hash passwords (BCrypt); never log passwords or tokens.
- Put business logic in the **Service layer**; keep controllers thin.
- Do all financial calculations on the **backend**, in one consistent way.
- Don't store derived values unless performance requires caching.
- Keep OCR images on the device; users must **confirm** OCR results before a transaction is created.
- Keep the Android UI and backend business logic clearly separated.

---

## Roadmap

| Priority | Scope |
|----------|-------|
| **P0** | Authentication · User · Wallet · Category · Transaction · Dashboard · Budget |
| **P1** | Analytics · Notifications · Receipt OCR · Category prediction |
| **P2** | Admin dashboard · Advanced OCR · ML-based category classifier |

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
