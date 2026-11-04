# Android App

Native Android app for end users (Java, XML layouts, MVVM).

**Stack:** Material Components · ViewModel + LiveData · Retrofit + OkHttp + Gson · Room (optional cache) · CameraX · ML Kit Text Recognition v2

## Structure

| Path | Purpose |
|------|---------|
| `app/src/main/java/com/example/fintrack/app/core/` | Shared, feature-agnostic code (network, session, database, utils) |
| `app/src/main/java/com/example/fintrack/app/<feature>/` | One package per feature: `auth`, `home`, `wallet`, `category`, `transaction`, `budget`, `analytics`, `notification`, `settings`, `ocr` |
| `app/src/main/res/` | Layouts, drawables, navigation graphs, strings (`values-vi/` for Vietnamese) |
| `app/src/main/assets/` | Static data, e.g. `merchant_dictionary.json` for category prediction |
| `app/src/test/` | JVM unit tests (OCR extraction, prediction, ViewModels) |
| `app/src/androidTest/` | Instrumented / UI tests |

## Feature package layout

Each feature package follows MVVM:

```
<feature>/
├── ui/            Activities, fragments, adapters
├── viewmodel/     ViewModels (LiveData)
├── repository/    Single source of data for the feature
├── remote/        Retrofit API interface + request/response DTOs
└── local/         Room DAO + entity (optional)
```

## Build Environments & Flavors

App hỗ trợ 3 Product Flavors độc lập (`dev`, `staging`, `production`) tương ứng với 3 môi trường backend:

- **`dev`**: Application ID `com.example.fintrack.dev`, API: `http://10.0.2.2:8080/`
- **`staging`**: Application ID `com.example.fintrack.staging`, API: `https://api-staging.example.com/` *(placeholder)*
- **`production`**: Application ID `com.example.fintrack`, API: `https://api.example.com/` *(placeholder)*

Chi tiết cấu hình, phân biệt Product Flavor vs Build Type và hướng dẫn chạy bằng Gradle CLI xem tại [ENVIRONMENTS.md](./ENVIRONMENTS.md).

## Quick Setup

1. Open this `android/` folder in Android Studio.
2. Select your desired Build Variant in the **Build Variants** tool window (e.g. `devDebug` for local emulator backend).
3. Run the `app` configuration.
