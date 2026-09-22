# core

Shared code used by every feature. Nothing here may depend on a feature package.

| Package | Purpose |
|---------|---------|
| `network/` | Retrofit/OkHttp client, `AuthInterceptor` (adds access token), `TokenAuthenticator` (auto refresh on 401) |
| `database/` | Room database, DAOs, entities (optional offline cache) |
| `session/` | Secure token storage (EncryptedSharedPreferences), session manager |
| `model/` | Shared models: `Resource<T>`, `ApiError`, enums |
| `ui/` | Base activities/fragments, common adapters, custom views |
| `util/` | Currency/date formatters, input validators |
