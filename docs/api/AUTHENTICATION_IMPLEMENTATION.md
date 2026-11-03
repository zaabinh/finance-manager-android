# Authentication backend

Backend đã triển khai register, xác thực email, gửi lại OTP, login, refresh token rotation,
logout, quên/đặt lại mật khẩu và `GET /api/v1/users/me`. Android integration là công việc riêng;
đợt triển khai này chỉ cập nhật backend và tài liệu.

## Kiến trúc và quyết định

- `AuthController` validate JSON, áp dụng rate limit theo IP/email và trả HTTP response.
- `AuthService` quản lý business rules, transaction, password policy và challenge.
- Spring Security `AuthenticationManager` → `DaoAuthenticationProvider` → `CustomUserDetailsService`
  kiểm tra username/email và BCrypt password; không tự so sánh password để thay thế authentication provider.
- `AuthenticatedUser` là application principal có UUID, có thể được OAuth provider tái sử dụng sau này.
  OAuth login chưa được bật và cần identity linking riêng khi bổ sung.
- `TokenService` dùng Spring Security Nimbus JOSE để ký/verify RS256, quản lý opaque refresh tokens.
- Persistence dùng JDBC repositories theo cấu trúc backend hiện có; JPA entity mapping khớp schema PostgreSQL.
- `EmailSenderService` có implementation `ResendEmailSenderService`, gọi REST API qua Spring `RestClient`.
- `RateLimitingService` dùng Redis với fixed windows, Lua script kiểm tra/tăng counter nguyên tử trên từng key
  và TTL tự hết hạn. Các instance dùng chung Redis/prefix chia sẻ quota. Không dùng database để lưu failed login/locked-until.

## Database: V4

V4 chưa áp dụng tại thời điểm triển khai, nên được hoàn thiện trực tiếp. Không sửa V1–V3.
Sau khi V4 đã được áp dụng, mọi thay đổi schema tiếp theo phải dùng migration mới.

`fintrack.users` bổ sung:

```sql
email_verified BOOLEAN NOT NULL DEFAULT false,
onboarding_completed BOOLEAN NOT NULL DEFAULT false
```

Không có `email_verified_at`, `failed_login_attempts`, `locked_until`, `password_changed_at`.
`status` ACTIVE/LOCKED/DISABLED hiện có được giữ cho trạng thái tài khoản quản trị,
không dùng làm cơ chế khóa tạm do đăng nhập sai. User cũ mặc định chưa verify và cần xác thực email.
Unique email/username dùng CITEXT và constraints đã có từ V2, không tạo index trùng.

`verification_challenges` lưu user/email, purpose (`EMAIL_VERIFICATION` hoặc `PASSWORD_RESET`),
BCrypt `code_hash`, expiry, consumed time và attempt count 0–5. BCrypt có salt bảo vệ OTP 6 chữ số
trước việc dò toàn bộ không gian OTP chỉ bằng SHA-256 khi database bị lộ. Không lưu OTP thô.

`refresh_sessions` bổ sung `replaced_by_session_id`, `user_agent` (300 ký tự), `ip_address` (INET).
Refresh token chỉ được lưu dưới dạng SHA-256; không có raw access/refresh token trong database.

## Password policy

`PasswordPolicy` là nguồn kiểm tra dùng chung cho register và reset, kể cả khi gọi service trực tiếp.
DTO dùng `@ValidPassword` và `PasswordPolicyValidator` để trả field error cho password không hợp lệ.

- 8–64 ký tự; tối đa 72 UTF-8 bytes để tránh giới hạn/truncation của BCrypt.
- Có chữ thường ASCII, chữ hoa ASCII, chữ số ASCII và ký tự đặc biệt không phải whitespace.
- Không chấp nhận control characters. Không trim/normalize password.
- BCrypt strength 12. OTP cũng dùng PasswordEncoder, có salt ngẫu nhiên.
- Username được trim, dài 3–50 ký tự, chỉ gồm chữ/số ASCII, dấu chấm, gạch dưới và gạch ngang.
  Không cho username chứa `@` để tránh nhập nhằng username/email. Email trim/lowercase ở service.

## API contract

Base `/api/v1/auth`, JSON camelCase. Lỗi dùng `ApiErrorResponse` hiện có, không trả secret/stack trace.
Các endpoint bên dưới là public cho phương thức POST; API nghiệp vụ vẫn yêu cầu bearer access token.

| Endpoint | Request | Thành công |
| --- | --- | --- |
| `/register` | `username`, `email`, `password` | 201, body rỗng |
| `/verify-email` | `email`, `otp` (6 chữ số) | 204 |
| `/resend-verification` | `email` | 202, body rỗng |
| `/login` | `usernameOrEmail`, `password` | 200, ApiResponse<LoginResponse> |
| `/refresh` | `refreshToken` | 200, ApiResponse<TokenResponse> |
| `/logout` | `refreshToken` | 204, kể cả token không tồn tại/đã revoke |
| `/forgot-password` | `email` | 202, body rỗng |
| `/reset-password` | `email`, `otp`, `newPassword` | 204 |
| `/change-password` (cần Bearer token) | `currentPassword`, `newPassword` | 200, ApiResponse<TokenResponse> |

`confirmPassword` chỉ là validation phía client. Không dùng endpoint tạm `/resend-otp`.
Resend/forgot có cùng response cho email tồn tại và không tồn tại. Rate limit được áp dụng cho cả hai,
trả 429 khi vượt giới hạn mà không phụ thuộc vào email có trong database hay không.

Login thành công khi user ACTIVE, email đã verify và password hợp lệ:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "displayName": "an.nguyen",
    "onboardingCompleted": false,
    "token": {
      "accessToken": "<RS256-JWT>",
      "refreshToken": "<opaque-token>",
      "tokenType": "Bearer",
      "expiresIn": 900
    }
  },
  "code": 200
}
```

`displayName` lấy từ user_preferences, fallback username khi null/blank. Không có user profile trong login.
Refresh trả token fields trực tiếp trong `data`. `GET /api/v1/users/me` trả `ApiResponse<UserResponse>`
với id, username, displayName, email và yêu cầu access token hợp lệ.

| Error code | HTTP |
| --- | ---: |
| AUTH_INVALID_CREDENTIALS / AUTH_ACCESS_TOKEN_INVALID / AUTH_REFRESH_TOKEN_INVALID | 401 |
| AUTH_EMAIL_NOT_VERIFIED / AUTH_USER_LOCKED / AUTH_USER_DISABLED | 403 |
| AUTH_EMAIL_ALREADY_EXISTS / AUTH_USERNAME_ALREADY_EXISTS | 409 |
| AUTH_OTP_INVALID | 400 |
| AUTH_OTP_EXPIRED | 410 |
| AUTH_OTP_ATTEMPTS_EXCEEDED / AUTH_RATE_LIMITED | 429 |
| AUTH_RATE_LIMIT_UNAVAILABLE | 503 |
| VALIDATION_ERROR | 400 |

## OTP, reset và transaction

OTP gồm 6 chữ số từ SecureRandom, mặc định hết hạn sau 10 phút. Mỗi purpose chỉ có một challenge chưa consume
hiện hành; resend làm challenge cũ mất hiệu lực. Kiểm tra/consume challenge khóa user rồi khóa challenge.
Nhập sai tăng attempt_count; lần sai thứ 5 và các lần sau trả 429. `ChallengeRejectedException` được cấu hình
`noRollbackFor` để lần thử sai được commit; lỗi database và lỗi ngoài dự kiến vẫn rollback.
Email đã verify có thể xác thực lặp lại và nhận 204.

Reset chỉ dùng challenge PASSWORD_RESET, consume đúng một lần, đổi password và revoke toàn bộ refresh session
trong cùng transaction. Reset không tự đánh dấu email_verified. Access JWT đã cấp còn hiệu lực tối đa đến expiry;
không có denylist JWT hoặc password_changed_at. Logout cũng chỉ revoke refresh session được chỉ định.

Đổi mật khẩu yêu cầu Bearer token và currentPassword hợp lệ. Trong cùng transaction, backend hủy mọi OTP
PASSWORD_RESET đang chờ, cập nhật password, revoke refresh sessions và cấp token family mới cho thiết bị hiện tại.
Nếu transaction thất bại, việc hủy OTP cũng rollback. Password sai hoặc password mới không hợp lệ không hủy OTP.

## Access JWT và refresh rotation

- Access JWT mặc định 15 phút, RS256, claims `iss`, `sub` (UUID), `iat`, `exp`, `jti`, `role`.
- Verify signature/issuer/expiry/subject và đọc trạng thái user hiện tại; disabled/locked/unverified bị từ chối.
- Refresh token là 32 random bytes (256 bit), base64url, mặc định 30 ngày.
- Mỗi refresh khóa user và session, kiểm tra expiry/revoked/user, tạo replacement, revoke token cũ,
  gán replaced_by_session_id và last_used_at trong cùng transaction.
- Hai request cùng refresh token chỉ tạo một successor. Trong grace period (mặc định 20 giây), khi successor
  chưa dùng, request lặp lại nhận cùng refresh successor; ngoài điều kiện đó, replay revoke toàn bộ family.
- Login/reset/refresh đều khóa user trước session, tránh cấp session dựa trên password cũ trong lúc reset.
- Không có HTTP session, form login hay HTTP Basic. JWT filter chỉ đăng ký trong Spring Security chain.
  Chỉ các POST auth public bỏ qua JWT filter để refresh/logout hoạt động dù access token đã hết hạn.
  `/change-password` vẫn được JWT filter xác thực và từ chối token thiếu/sai/hết hạn.

## Rate limits mặc định

| Hành động | Key / giới hạn |
| --- | --- |
| Login thất bại | identifier chuẩn hóa + IP: 5/15 phút; IP: 50/15 phút |
| Login requests | IP: 100/15 phút, bao gồm login hợp lệ và tài khoản chưa verify |
| Register | IP: 10/giờ |
| Refresh | IP: 20/phút |
| Logout | IP: 60/phút |
| Resend verification / forgot password | Mỗi purpose: IP 20/giờ; email 3/giờ; cooldown email 60 giây |
| Verify / reset | Mỗi purpose: IP 60/15 phút; email 20/15 phút; thêm tối đa 5 lần sai/challenge |

Đăng nhập thành công xóa failure counter của identifier + IP, giữ counter IP tổng.
Key identifier/email được SHA-256, không log raw identity. Dùng remote address từ servlet,
không tin `X-Forwarded-For` do client gửi; cần cấu hình trusted proxy rõ ràng nếu deploy sau reverse proxy.
Rate limit không mất khi backend restart và được chia sẻ giữa instance. Redis lỗi trả 503, vượt quota trả 429;
không fallback in-memory. Lua nguyên tử trên từng key; nhiều quota của một endpoint vẫn áp dụng lần lượt.
Redis restart/crash có thể làm mất lần ghi mới nhất tùy persistence; Compose local bật AOF/volume và noeviction.

Cấu hình `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD`, `REDIS_SSL_ENABLED` và
`AUTH_RATE_LIMIT_KEY_PREFIX`. Chạy native dùng localhost; Compose dùng service `redis`. Mọi instance cùng
môi trường dùng cùng prefix, staging/prod dùng prefix riêng. Readiness kiểm tra cả PostgreSQL và Redis;
liveness không phụ thuộc chúng. Xem [hướng dẫn Redis](../development/REDIS_RATE_LIMITING_MIGRATION.md).

## Resend REST API và cấu hình

Sender gọi `POST https://api.resend.com/emails`, `Authorization: Bearer <RESEND_API_KEY>`, JSON
`from`, `to`, `subject`, `text`, kèm `Idempotency-Key` theo delivery UUID.
Timeout connect 5 giây, đọc 10 giây. Email phân biệt verification/reset, có OTP, expiry UTC và hướng dẫn bỏ qua.
Không dùng Spring Mail/SMTP. Tham khảo [Resend Send Email](https://resend.com/docs/api-reference/emails/send-email).

Email được publish trong transaction và gửi đồng bộ sau commit qua `@TransactionalEventListener`.
Lỗi provider không rollback account/challenge và không rò nội dung exception; chỉ log delivery UUID.
Đây là best-effort delivery, chưa có durable outbox/automatic retry; user có thể resend nếu gửi thất bại
hoặc process dừng giữa commit và send. Test thay sender bằng fake/mock, không gửi email thật.

```dotenv
AUTH_JWT_ISSUER=fintrack-api
AUTH_ACCESS_TOKEN_TTL=15m
AUTH_REFRESH_TOKEN_TTL=30d
AUTH_OTP_TTL=10m
AUTH_JWT_PRIVATE_KEY=file:/run/secrets/auth-private.pem
AUTH_JWT_PUBLIC_KEY=file:/run/secrets/auth-public.pem
RESEND_API_KEY=<secret>
MAIL_FROM=FinTrack <auth@your-verified-domain.example>
```

Private key dùng PKCS#8 PEM, public key dùng X.509 PEM, RSA tối thiểu 2048 bit và phải cùng cặp.
Staging/prod fail startup nếu thiếu key hoặc Resend configuration. Không commit secret/key.
Dev cho phép thiếu key và sinh RSA key mới mỗi startup; access JWT cũ sẽ mất hiệu lực sau restart.
Đặt key files cố định nếu muốn giữ JWT qua restart. Dev thiếu Resend configuration vẫn tạo account/challenge
nhưng gửi mail thất bại, không log OTP; cấu hình Resend trước khi kiểm tra email thật.

Spring Security integration dựa trên
[DaoAuthenticationProvider](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/dao-authentication-provider.html)
và [Nimbus JWT support](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

## Kiểm thử

Java 21, từ thư mục backend:

```powershell
./mvnw.cmd test
./mvnw.cmd -Ppostgres-it verify
```

Unit/API tests không cần database: policy/UTF-8 limit, Spring authentication, OTP expiry/attempts,
reset revoke sessions, JWT signature/issuer/expiry, rotation/replay/logout, concurrent rate limits,
Resend HTTP payload và lỗi delivery. MVC contract tests mock AuthOperations; không thay thế integration tests.

`AuthenticationIT` chạy flow thật qua Spring/JDBC/PostgreSQL với sender mock, kiểm tra OTP sai được commit,
one-time reset, old password/session invalidation, refresh đồng thời và migration idempotence.
Mặc định dùng PostgreSQL và Redis Testcontainers; `PostgresMigrationIT` kiểm tra fresh/restart và upgrade schema cũ.
`RedisRateLimitingIT` kiểm tra quota chia sẻ, TTL, xóa đúng key, concurrent requests và fail closed khi Redis từ chối ghi.
Có thể chạy AuthenticationIT trên PostgreSQL **test riêng có thể bỏ đi**, không dùng database ứng dụng.
Redis test vẫn chạy bằng Docker/Testcontainers:

```powershell
./mvnw.cmd '-Dtest=AuthenticationIT' '-Dauth.it.jdbc-url=jdbc:postgresql://127.0.0.1:55489/postgres' '-Dauth.it.username=postgres' test
```

## Android cần tích hợp riêng

Dùng login envelope trên, đổi resend endpoint, thêm refresh/logout và lưu token an toàn.
Serialize refresh request để chỉ có một request refresh tại một thời điểm; retry business API tối đa một lần.
Không refresh tự động cho auth endpoints. Xóa local session khi logout/reset/refresh thất bại.
Không log password/OTP/token hoặc Authorization; lấy profile qua `/users/me`.
