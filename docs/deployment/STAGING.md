# Staging đầu tiên — Phase 2 / đầu Phase 3

Tài liệu này là hướng dẫn triển khai thủ công, không phải xác nhận đã có staging server.
Chưa cần ứng dụng hoàn chỉnh hoặc CD production. Backend Spring Boot, PostgreSQL, Flyway,
health endpoint và CI cơ bản là đủ để dựng staging đầu tiên.
CI admin hiện được hoãn; Android/admin integration chỉ được đánh dấu đạt sau khi chạy thật.

## 1. Phân biệt môi trường

| Môi trường | Profile | Dữ liệu | Mục đích |
| --- | --- | --- | --- |
| Development | `dev` | Local riêng từng developer | Code/debug, Swagger |
| Staging | `staging` | DB, user/password và volume riêng | Tích hợp Android/backend/admin |
| Production | `prod` | DB và secrets riêng | Chưa triển khai ở giai đoạn này |

Không dùng database production cho staging. Không dùng profile `dev` trên server staging.
Staging/production không tự import `.env` trong ứng dụng; Docker/deployment inject biến môi trường.
Swagger, SQL logging và health component details đều tắt trong staging.

## 2. Điều kiện trước khi bắt đầu

- Có server Linux được phép triển khai, SSH và Docker Engine + Compose.
- Có domain API staging trỏ DNS về IP server, ví dụ `api-staging.example.com` (phải thay bằng domain thật).
- Có reverse proxy HTTPS; chỉ mở SSH cần thiết và cổng HTTP/HTTPS ra ngoài.
- Có commit đã qua Backend CI và được review; lưu lại SHA để biết server đang chạy bản nào.
- Có mật khẩu staging riêng, đủ mạnh; không đặt trong Git, Dockerfile hoặc image.

Không có server/domain/credentials thì dừng ở chuẩn bị cấu hình; không tuyên bố deploy thành công.

## 3. Lấy source đã qua CI

Trên server, checkout đúng commit của `develop` đã pass CI trong thư mục deployment riêng.
Không checkout tùy tiện bản mới nhất rồi coi như bản đó đã được kiểm chứng.
Các lệnh tiếp theo chạy từ root của checkout này.

Tạo `.env.staging` trên server, không commit, giới hạn quyền đọc:

```sh
touch .env.staging
chmod 600 .env.staging
```

Điền cấu hình bằng editor/secret manager:

```properties
STAGING_IMAGE_TAG=replace-with-ci-passed-commit-sha
POSTGRES_DB=finance_manager_staging
POSTGRES_USER=finance_staging
POSTGRES_PASSWORD=
CORS_ALLOWED_ORIGINS=https://admin-staging.example.com
```

Phải điền mật khẩu và domain thật. CORS origin không có trailing slash; không dùng wildcard.
Nếu chưa có admin domain, để CORS trống đến khi tích hợp browser client.
`.env.staging` là file cho Compose interpolation; ứng dụng nhận các biến `DB_*` từ service dưới đây.

## 4. PostgreSQL staging và backend

Tạo `compose.staging.yml` **trên server**, không sửa file Compose development:

```yaml
name: finance-manager-staging
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: ${POSTGRES_DB:?Set staging database}
      POSTGRES_USER: ${POSTGRES_USER:?Set staging database user}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?Set staging database password}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U \"$$POSTGRES_USER\" -d \"$$POSTGRES_DB\""]
      interval: 5s
      timeout: 5s
      retries: 10
    restart: unless-stopped

  backend:
    image: finance-manager-backend:${STAGING_IMAGE_TAG:?Set verified commit SHA}
    build:
      context: ./backend
      dockerfile: Dockerfile
    environment:
      SPRING_PROFILES_ACTIVE: staging
      SERVER_PORT: "8080"
      DB_HOST: postgres
      DB_PORT: "5432"
      DB_NAME: ${POSTGRES_DB:?Set staging database}
      DB_USERNAME: ${POSTGRES_USER:?Set staging database user}
      DB_PASSWORD: ${POSTGRES_PASSWORD:?Set staging database password}
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:-}
    ports:
      - "127.0.0.1:18080:8080"
    depends_on:
      postgres:
        condition: service_healthy
    restart: unless-stopped

volumes:
  postgres_data:
```

Không có `container_name`, không publish PostgreSQL ra Internet và không mount SQL init scripts.
Project riêng tạo volume `finance-manager-staging_postgres_data`, tách khỏi local/production.
Role tạo từ image PostgreSQL là bootstrap role; trước khi mở rộng triển khai, tách migration role và
runtime role với quyền tối thiểu. Image ban đầu được build thủ công từ commit đã pass CI; đây không phải CD.

```sh
docker compose --env-file .env.staging -f compose.staging.yml config --quiet
docker compose --env-file .env.staging -f compose.staging.yml up -d --build
docker compose --env-file .env.staging -f compose.staging.yml ps
docker compose --env-file .env.staging -f compose.staging.yml logs --tail=100 backend
curl --fail http://127.0.0.1:18080/actuator/health
```

Chờ backend khởi động xong trước khi kết luận health. Log phải xác nhận profile `staging`.
Flyway chạy khi ứng dụng khởi động, trước Hibernate `validate`.

## 5. Kiểm chứng Flyway và restart

Mở psql trong PostgreSQL container:

```sh
docker compose --env-file .env.staging -f compose.staging.yml exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Chạy SQL:

```sql
SELECT version, description, success
FROM fintrack.flyway_schema_history
ORDER BY installed_rank;

SELECT table_name, table_type
FROM information_schema.tables
WHERE table_schema = 'fintrack'
ORDER BY table_name;
```

Kỳ vọng V1/V2/V3 thành công, có `wallets`, `transaction_entries`, `wallet_balances` và các bảng core.
Thoát bằng `\q`, restart backend và kiểm tra health/history lần nữa:

```sh
docker compose --env-file .env.staging -f compose.staging.yml restart backend
```

Mỗi migration chỉ có một dòng thành công; restart không chạy lại migration đã áp dụng.
Không sửa migration cũ hoặc bật baseline/repair để che lỗi. Không dùng `down -v` hay xóa volume để sửa staging.

## 6. Public HTTPS health

Đặt reverse proxy trên host đến `127.0.0.1:18080`. Ví dụ Caddyfile sau khi cài Caddy:

```caddyfile
api-staging.example.com {
    reverse_proxy 127.0.0.1:18080
}
```

Thay domain, cấu hình DNS và cổng 80/443, xác nhận chứng chỉ HTTPS hợp lệ.
Xem [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https).

```sh
curl --fail https://api-staging.example.com/actuator/health
curl --fail https://api-staging.example.com/actuator/health/readiness
```

Body phải có `"status":"UP"`. Không công khai health details, database port, Swagger hoặc toàn bộ Actuator.
Không bỏ qua xác minh chứng chỉ để làm cho kiểm thử pass.

## 7. Android gọi staging

Thực hiện trên nhánh Android, không sửa CI/backend để hardcode domain.
Với nhánh `feature/frontend-auth` đã đọc `BuildConfig.BASE_URL`, có thể thêm build type riêng:

```kotlin
staging {
    initWith(getByName("debug"))
    applicationIdSuffix = ".staging"
    matchingFallbacks += listOf("debug")
    buildConfigField("String", "BASE_URL", "\"https://api-staging.example.com/\"")
}
```

Đặt trong `android.buildTypes`, giữ `buildFeatures.buildConfig = true`, thay domain thật.
Chọn variant `staging` trong Android Studio hoặc chạy `:app:assembleStaging` và cài trên thiết bị.
Không đổi URL debug của cả nhóm để tích hợp staging. Staging dùng HTTPS, không thêm cleartext exception.

Thêm Retrofit health call và DTO riêng cho native Actuator payload:

```java
@GET("actuator/health")
Call<HealthResponse> health();
```

`HealthResponse` đọc trường `status`; không dùng envelope `success/data/code`.
Chạy request từ app qua Repository/ViewModel, hiển thị kết quả `UP`, kiểm tra network logs không chứa token.
`curl` trên laptop hoặc server không thay thế kiểm thử từ app. Hiện health API phía Android chưa được triển khai;
bước này là việc tích hợp tiếp theo, không được tự đánh dấu hoàn tất.

## 8. Admin gọi staging khi có source

Hiện admin chưa có `package.json` và CI được hoãn theo yêu cầu.
Khi khởi tạo admin, định nghĩa lockfile cùng scripts lint/test/build trước khi bật CI.
Nếu dùng Vite, cấu hình `VITE_API_BASE_URL=https://api-staging.example.com/` ở build staging.
Biến `VITE_*` đi vào browser bundle, chỉ chứa URL public, không chứa DB password/API secrets.

Deploy admin dưới HTTPS domain staging; thêm đúng origin vào `CORS_ALLOWED_ORIGINS`, recreate backend khi đổi env.
Từ browser admin gọi `GET /actuator/health`, kiểm tra HTTP 200, body `UP`, console không báo CORS/mixed content.
Không dùng Postman làm bằng chứng cho CORS browser. CORS không thay thế authentication/authorization.

## 9. Luồng Phase 3 và rollback

`feature → PR develop → CI pass → merge develop → deploy staging thủ công → Android/backend integration test`.
Đến khi quy trình ổn định mới cân nhắc CD staging; không thêm CD production trong Phase 2.

Mỗi lần deploy lưu commit SHA, image tag và phiên bản Flyway. Backup PostgreSQL trước migration mới;
lưu backup ngoài Git và thử restore. Đổi image về bản cũ chỉ rollback ứng dụng, không tự undo Flyway.
Chỉ rollback image nếu tương thích schema hiện tại; nếu không, chuẩn bị migration sửa tiếp/khôi phục có kiểm soát.

## 10. Acceptance checklist — chỉ đánh dấu sau khi kiểm chứng thật

- [ ] Server/domain/secrets staging đã được cấp và tách khỏi local/production.
- [ ] Commit triển khai đã qua Backend CI.
- [ ] PostgreSQL staging healthy, dữ liệu bền qua restart và không public port.
- [ ] Backend dùng profile `staging`, Flyway V1/V2/V3 thành công.
- [ ] Restart không chạy lại migration.
- [ ] HTTPS health/readiness trả HTTP 200 và `UP`.
- [ ] Android staging build gọi health thành công trên emulator/điện thoại.
- [ ] Admin gọi health từ browser thành công, CORS đúng origin (khi có admin source).
- [ ] Ghi lại SHA/image/Flyway version, backup và phương án rollback.

Việc viết hướng dẫn, chạy profile staging trên laptop hoặc lint Compose không chứng minh server staging đã được deploy.
