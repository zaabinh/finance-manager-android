# Local development và ba môi trường

## Quy ước

| Môi trường | Spring profile | Database | API URL | Swagger |
| --- | --- | --- | --- | --- |
| Development | `dev` (mặc định) | `finance_manager_local`, Docker localhost:5433 | `http://localhost:8080` | Bật |
| Staging | `staging` | PostgreSQL staging riêng, không dùng volume local | HTTPS staging domain | Tắt |
| Production | `prod` | PostgreSQL production riêng | HTTPS production domain | Tắt |

Chọn đúng một profile bằng biến môi trường process `SPRING_PROFILES_ACTIVE`.
`application.yml` chứa cấu hình chung; `application-dev.yml`, `application-staging.yml`,
`application-prod.yml` chứa cấu hình từng môi trường. CI dùng profile `test` chỉ có trong test resources;
đây là môi trường kiểm thử cô lập, không phải môi trường triển khai thứ tư.

Development đọc root `.env` và tùy chọn `backend/.env`. Staging/production không tự đọc các file này:
deployment phải inject biến môi trường. Staging không bật SQL/bind logging, Swagger hoặc health details.
Flyway và `ddl-auto=validate` áp dụng cho cả ba môi trường.

## 1. Kiểm tra công cụ

PowerShell từ root repository:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
docker version
docker compose version
node --version
npm.cmd --version
```

Backend cần JDK 21, không cần cài Maven toàn máy vì có Maven Wrapper.
Android hiện dùng Gradle daemon JDK 25 theo `gradle-daemon-jvm.properties`; Java source/target là 11.
Không đổi cấu hình Android sang Java 21 chỉ vì backend dùng Java 21.
Android SDK/AVD được quản lý bằng Android Studio.

Nếu `npm` bị PowerShell chặn do execution policy, dùng `npm.cmd`; không cần thay đổi policy toàn máy.
Admin hiện chỉ có khung thư mục, chưa có `package.json`; chưa chạy `npm install`, lint/test/build hay CI admin.

## 2. PostgreSQL và cấu hình

Copy root `.env.example` thành `.env` và đặt mật khẩu thật ở file local được Git ignore:

```properties
POSTGRES_DB=finance_manager_local
POSTGRES_USER=finance_user
POSTGRES_PASSWORD=
POSTGRES_PORT=5433
SPRING_PROFILES_ACTIVE=dev
```

Không để trống mật khẩu khi chạy. Không chia sẻ file này và không dùng credentials local cho staging.
Nếu có `backend/.env`, đảm bảo các biến `DB_*` khớp root `.env`, hoặc bỏ override không cần thiết.

```powershell
docker compose -f docker-compose.yml config --quiet
docker compose -f docker-compose.yml up -d --wait postgres
docker compose -f docker-compose.yml ps
```

Không dùng `docker compose config` không có `--quiet` để gửi cấu hình ra log/chat vì có thể lộ secrets.

## 3. Chạy backend local

Chọn một trong hai cách để tránh chiếm cùng cổng 8080.

Maven Wrapper, từ `backend/`:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
.\mvnw.cmd spring-boot:run
```

Hoặc Docker từ root:

```powershell
docker compose -f docker-compose.yml up -d --build backend
Invoke-RestMethod http://localhost:8080/actuator/health
```

Local Compose mặc định inject `dev`; root `.env`/process environment có thể override.
Kiểm tra log để xác nhận profile thực tế, không suy đoán theo tên container.
Swagger development: `http://localhost:8080/swagger-ui/index.html`.
Flyway history phải có V1, V2, V3 thành công; schema hiện tại dùng `wallets` và `wallet_balances`.

## 4. Kiểm thử tương đương CI

Từ `backend/`:

```powershell
.\mvnw.cmd -B -ntp test
.\mvnw.cmd -B -ntp -DskipTests package
```

`test` chạy context, health, security/CORS và exception tests, không cần DB cá nhân/secrets.
`package` chỉ bắt đầu sau khi test pass; không chạy lại cùng bộ test.
Kiểm chứng PostgreSQL/Flyway thực tế là bước bổ sung, cần Docker:

```powershell
.\mvnw.cmd -Ppostgres-it verify
```

Từ `android/`:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

CI Android cơ bản chưa chạy emulator/instrumentation và chưa chứng minh app gọi được backend.
Emulator dùng `http://10.0.2.2:8080/`; điện thoại USB có thể dùng `adb reverse tcp:8080 tcp:8080`
với BASE_URL `http://127.0.0.1:8080/`.

## 5. Khi chuyển sang staging

Làm theo [STAGING.md](../deployment/STAGING.md). Chưa triển khai production/CD trong Phase 2.
Luồng tiếp theo: feature → PR vào develop → CI pass → triển khai staging thủ công → kiểm thử Android/backend.

## Kết quả kiểm chứng local cho thay đổi này

- Có JDK 21.0.11; `java` trên PATH ban đầu trỏ JDK 17, nên cần chọn `JAVA_HOME`/PATH trong session như trên.
- Node 24.11.0 và `npm.cmd` 11.6.1 hoạt động; lệnh `npm` qua PowerShell bị execution policy chặn.
- Pipeline Maven `test` rồi `package` thành công: 13 smoke/unit tests, gồm security của staging.
- Chạy thêm PostgreSQL integration tests: hai tests pass, kiểm chứng Flyway V1/V2/V3 và upgrade giữ dữ liệu.
- Local Docker dùng `dev`, PostgreSQL healthy, health `UP`, Swagger/OpenAPI HTTP 200.
- Jar khởi động riêng với profile `staging` và `prod` trên database local để kiểm tra cấu hình:
  health `UP`, Swagger/OpenAPI HTTP 401. Đây không phải triển khai staging/production.
- Pipeline Android debug/unit tests pass trên snapshot `feature/frontend-auth@ac71557`; Gradle tái sử dụng
  kết quả build/tests hợp lệ của cùng snapshot (9 tests đã pass), không thay đổi working tree Android.
- Hai workflow qua actionlint; ví dụ Compose staging qua `config --quiet` mà không tạo tài nguyên staging.
- Chưa commit/push workflow lên GitHub, chưa cấu hình required checks, chưa cấp server staging.
