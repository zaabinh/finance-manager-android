# Hướng dẫn hoàn thiện Authentication

Tài liệu này là kế hoạch triển khai Authentication end-to-end cho Finance Manager, bao gồm đăng ký,
xác thực email, đăng nhập, access token, refresh token, đăng xuất và quên mật khẩu. Phạm vi không bao gồm
phân quyền nghiệp vụ chi tiết, social login, MFA hoặc quản trị người dùng.

## 1. Trạng thái hiện tại

Backend hiện mới có hạ tầng Spring Security stateless, CORS, error response và hai bảng liên quan:
`fintrack.users`, `fintrack.refresh_sessions`. Chưa có controller/service/repository Authentication.

Android đã có màn hình và DTO tạm cho:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/verify-email`
- `POST /api/v1/auth/resend-otp`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`

Login dùng `ApiResponse<LoginResponse>` thống nhất giữa backend và Android. Register thành công chỉ dùng HTTP
`201 Created` với body rỗng. Lỗi vẫn dùng `ApiErrorResponse`; `UserResponse` chỉ dành cho `GET /api/v1/users/me`.

## 2. Kiến trúc đề xuất

```text
AuthController
    -> AuthService
        -> UserRepository
        -> RefreshSessionRepository
        -> VerificationChallengeRepository
        -> PasswordEncoder
        -> TokenService
        -> EmailService
```

Package backend đề xuất:

```text
controller/AuthController.java
service/AuthService.java
service/TokenService.java
service/EmailService.java
repository/UserRepository.java
repository/RefreshSessionRepository.java
repository/VerificationChallengeRepository.java
entity/User.java
entity/RefreshSession.java
entity/VerificationChallenge.java
dto/request/auth/
dto/response/auth/
security/JwtAuthenticationFilter.java
security/AuthenticatedUser.java
config/AuthProperties.java
config/MailProperties.java
exception/ErrorCode.java
```

Giữ controller mỏng, transaction và business rules trong service, token logic trong `TokenService`, gửi email
qua interface riêng để test không cần SMTP thật.

## 3. Quyết định bảo mật

### Password

- Hash bằng `PasswordEncoder`; ưu tiên BCrypt strength 12 hoặc Argon2 nếu runtime đã có provider phù hợp.
- Không lưu, log hoặc trả password/OTP/token trong response lỗi.
- Quy tắc MVP đồng bộ Android: tối thiểu 8 ký tự, có chữ hoa, chữ thường, chữ số và ký tự đặc biệt.
- Backend luôn là nơi enforce cuối cùng; validation phía Android chỉ cải thiện UX.
- Khi reset password thành công, revoke toàn bộ refresh session của user.

### Access token

- Dùng JWT ký bất đối xứng RS256/ES256; private key chỉ ở backend, public key dùng để verify.
- Thời hạn đề xuất: 15 phút.
- Claims tối thiểu: `iss`, `sub` = user UUID, `iat`, `exp`, `jti`, `role`.
- Không đưa email, password state hoặc dữ liệu cá nhân không cần thiết vào JWT.
- API nhận `Authorization: Bearer <accessToken>`.
- Access token không lưu trong database và không dùng làm refresh token.

### Refresh token

- Dùng chuỗi opaque ngẫu nhiên ít nhất 256 bit, không dùng JWT.
- Thời hạn đề xuất: 30 ngày; có thể cấu hình theo môi trường.
- Chỉ lưu SHA-256 hash trong `refresh_sessions.token_hash`; raw token chỉ trả cho client một lần.
- Rotate token mỗi lần refresh: revoke session cũ và tạo session mới trong cùng transaction.
- Refresh token đã revoke/hết hạn/tái sử dụng trả `401 AUTH_REFRESH_TOKEN_INVALID`.
- Android lưu access/refresh token trong `EncryptedSharedPreferences`, không log token và không backup token.

### OTP/challenge

- OTP 6 chữ số, thời hạn 10 phút, dùng một lần.
- Chỉ lưu hash OTP; không lưu plain OTP.
- Phân biệt purpose: `EMAIL_VERIFICATION`, `PASSWORD_RESET`.
- Tối đa 5 lần nhập sai cho mỗi challenge.
- Resend có cooldown 60 giây và giới hạn theo email + IP.
- Response forgot-password/resend không tiết lộ email có tồn tại hay không.

## 4. Database migration tiếp theo

Không sửa V1/V2/V3 đã áp dụng. Tạo migration mới, ví dụ
`V4__add_authentication_support.sql`.

Thay đổi đề xuất:

```sql
ALTER TABLE fintrack.users
    ADD COLUMN email_verified_at TIMESTAMPTZ,
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN password_changed_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE fintrack.verification_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES fintrack.users(id) ON DELETE CASCADE,
    email public.CITEXT NOT NULL,
    purpose TEXT NOT NULL CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    code_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (expires_at > created_at),
    CHECK (attempt_count BETWEEN 0 AND 5)
);

CREATE INDEX ix_verification_challenges_lookup
    ON fintrack.verification_challenges(email, purpose, expires_at)
    WHERE consumed_at IS NULL;
```

Có thể bổ sung vào `refresh_sessions`:

```sql
ALTER TABLE fintrack.refresh_sessions
    ADD COLUMN replaced_by_session_id UUID REFERENCES fintrack.refresh_sessions(id),
    ADD COLUMN user_agent VARCHAR(300),
    ADD COLUMN ip_address INET;
```

Không lưu OTP/password reset token trong `users`. Job dọn challenge/session hết hạn có thể bổ sung sau; truy vấn
runtime vẫn phải luôn kiểm tra `expires_at`, `consumed_at`, `revoked_at`.

## 5. API contract

Base path: `/api/v1/auth`. Request/response dùng JSON camelCase. Thời gian dùng ISO 8601 UTC.

### 5.1 Đăng ký

`POST /api/v1/auth/register`

```json
{
  "username": "an.nguyen",
  "email": "an@example.com",
  "password": "Strong@123"
}
```

Response `201 Created`, body rỗng. Android giữ email từ form đăng ký để chuyển sang màn hình xác thực; backend
không trả `UserResponse` hoặc DTO đăng ký riêng. `confirmPassword` chỉ được kiểm tra ở frontend và không gửi lên API.

Xử lý:

1. Normalize email/username bằng `trim`; tính unique dựa trên CITEXT.
2. Validate password, hash password và tạo user chưa verify.
3. Tạo challenge `EMAIL_VERIFICATION` và gửi OTP sau khi transaction commit.
4. Nếu gửi mail thất bại, không trả OTP; user dùng resend sau.

Lỗi chính: `409 AUTH_EMAIL_ALREADY_EXISTS`, `409 AUTH_USERNAME_ALREADY_EXISTS`,
`400 VALIDATION_ERROR`.

### 5.2 Xác thực email

`POST /api/v1/auth/verify-email`

```json
{
  "email": "an@example.com",
  "otp": "482913"
}
```

Response `204 No Content`. Đánh dấu challenge consumed và set `users.email_verified_at` trong cùng transaction.
Request lặp lại cho user đã verify có thể trả `204` để idempotent.

Lỗi chính: `400 AUTH_OTP_INVALID`, `410 AUTH_OTP_EXPIRED`, `429 AUTH_OTP_ATTEMPTS_EXCEEDED`.

### 5.3 Gửi lại OTP xác thực email

`POST /api/v1/auth/resend-verification`

```json
{
  "email": "an@example.com"
}
```

Response luôn `202 Accepted` với email có hoặc không tồn tại. Nếu hợp lệ, invalidate challenge xác thực cũ,
tạo challenge mới và gửi email. Dùng endpoint rõ purpose thay cho tên tạm Android `/resend-otp`.

### 5.4 Đăng nhập

`POST /api/v1/auth/login`

```json
{
  "usernameOrEmail": "an@example.com",
  "password": "Strong@123"
}
```

Response `200 OK`:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "displayName": "An Nguyen",
    "onboardingCompleted": false,
    "token": {
      "accessToken": "<jwt>",
      "refreshToken": "<opaque-token>",
      "tokenType": "Bearer",
      "expiresIn": 900
    }
  },
  "code": 200
}
```

`displayName` lấy từ `user_preferences.display_name`; nếu null hoặc blank, backend fallback về `users.username`.
Không đưa `UserResponse`, user ID hoặc email vào login response. `expiresIn` là số giây access token còn hiệu lực.

Login chỉ thành công khi user `ACTIVE` và email đã verify. Sai username/email/password trả cùng một lỗi
`401 AUTH_INVALID_CREDENTIALS`. User chưa verify trả `403 AUTH_EMAIL_NOT_VERIFIED`; locked/disabled trả mã
403 riêng nhưng không tiết lộ thông tin nhạy cảm.

### 5.5 Refresh token

`POST /api/v1/auth/refresh`

```json
{
  "refreshToken": "<opaque-token>"
}
```

Response `200 OK` có cùng token fields như login nhưng có thể bỏ `user`. Backend hash token, lock row/session,
kiểm tra trạng thái user, rotate token atomically và cập nhật `last_used_at`.

Không gửi refresh token trong header Authorization. Không tự refresh endpoint refresh để tránh loop.

### 5.6 Đăng xuất

`POST /api/v1/auth/logout`

```json
{
  "refreshToken": "<opaque-token>"
}
```

Response `204 No Content` kể cả token đã revoke. Revoke đúng refresh session; Android luôn xóa local session
dù request logout lỗi mạng.

Tùy chọn sau MVP: `POST /api/v1/auth/logout-all` để revoke toàn bộ session của user hiện tại.

### 5.7 Quên mật khẩu

`POST /api/v1/auth/forgot-password`

```json
{
  "email": "an@example.com"
}
```

Response luôn `202 Accepted`. Nếu email thuộc user hợp lệ, invalidate challenge reset cũ, tạo challenge
`PASSWORD_RESET` và gửi OTP. Không cho client biết email có tồn tại.

### 5.8 Đặt lại mật khẩu

`POST /api/v1/auth/reset-password`

```json
{
  "email": "an@example.com",
  "otp": "482913",
  "newPassword": "NewStrong@456"
}
```

Response `204 No Content`. Trong cùng transaction:

1. Validate challenge reset chưa dùng và chưa hết hạn.
2. Hash và cập nhật password, `password_changed_at`.
3. Consume challenge.
4. Revoke toàn bộ refresh sessions của user.

## 6. Error response

Mọi lỗi dùng `ApiErrorResponse` hiện có:

```json
{
  "timestamp": "2026-10-27T06:00:00Z",
  "status": 401,
  "errorCode": "AUTH_INVALID_CREDENTIALS",
  "message": "Invalid credentials",
  "path": "/api/v1/auth/login",
  "fieldErrors": []
}
```

Mã lỗi Authentication tối thiểu:

| Error code | HTTP | Ý nghĩa |
| --- | ---: | --- |
| `AUTH_INVALID_CREDENTIALS` | 401 | Username/email hoặc password không đúng |
| `AUTH_ACCESS_TOKEN_INVALID` | 401 | Access token sai/hết hạn |
| `AUTH_REFRESH_TOKEN_INVALID` | 401 | Refresh token sai/hết hạn/revoked |
| `AUTH_EMAIL_NOT_VERIFIED` | 403 | Email chưa xác thực |
| `AUTH_USER_LOCKED` | 403 | User đang bị khóa |
| `AUTH_USER_DISABLED` | 403 | User bị vô hiệu hóa |
| `AUTH_EMAIL_ALREADY_EXISTS` | 409 | Email đã đăng ký |
| `AUTH_USERNAME_ALREADY_EXISTS` | 409 | Username đã tồn tại |
| `AUTH_OTP_INVALID` | 400 | OTP không đúng |
| `AUTH_OTP_EXPIRED` | 410 | OTP hết hạn |
| `AUTH_OTP_ATTEMPTS_EXCEEDED` | 429 | Quá số lần thử |
| `AUTH_RATE_LIMITED` | 429 | Quá giới hạn request |

Security entry point phải trả JSON này thay vì response mặc định của Spring Security.

## 7. Email verification sender

Tạo interface:

```java
public interface EmailService {
    void sendVerificationCode(String recipient, String code, Instant expiresAt);
    void sendPasswordResetCode(String recipient, String code, Instant expiresAt);
}
```

Production implementation dùng Spring Mail/SMTP provider. Test dùng fake implementation ghi nhận message trong
memory; không gửi email thật.

Environment variables đề xuất:

```text
MAIL_HOST
MAIL_PORT
MAIL_USERNAME
MAIL_PASSWORD
MAIL_FROM
MAIL_SMTP_AUTH
MAIL_SMTP_STARTTLS_ENABLE
AUTH_OTP_TTL
AUTH_ACCESS_TOKEN_TTL
AUTH_REFRESH_TOKEN_TTL
AUTH_JWT_ISSUER
AUTH_JWT_PRIVATE_KEY
AUTH_JWT_PUBLIC_KEY
```

Không commit key hoặc SMTP credentials. Với multiline PEM, ưu tiên secret file mount hoặc secret manager thay vì
đặt trực tiếp trong `.env`. Dev có thể dùng Mailpit/MailHog; staging dùng mailbox/provider riêng và verified sender.

Email template phải có purpose rõ ràng, thời gian hết hạn, cảnh báo bỏ qua nếu không yêu cầu và không chứa
password/token. Không nhúng raw exception của provider vào API response.

## 8. Spring Security integration

- Cho phép anonymous cho các endpoint trong `/api/v1/auth/**` được liệt kê ở trên.
- Giữ mọi endpoint nghiệp vụ authenticated mặc định.
- JWT filter chỉ xử lý access token; refresh token không đi qua filter.
- Validate signature, issuer, expiry, subject và trạng thái user phù hợp.
- Không tạo session HTTP; giữ `SessionCreationPolicy.STATELESS`.
- Không bật form login, HTTP Basic hoặc generated password.
- Có thể dùng Spring Security OAuth2 Resource Server/Jose theo dependency management của Spring Boot để verify JWT;
  không tự viết thuật toán ký/verify.

## 9. Android integration

Các thay đổi cần thực hiện sau khi backend contract được merge:

1. Login dùng `Call<ApiResponse<LoginResponse>>`; register dùng `Call<Void>` và chỉ thành công khi HTTP 201.
2. Thêm `refresh`, `logout`; đổi `/resend-otp` thành `/resend-verification`.
3. Đọc token từ `loginResponse.token`, cache `displayName`, và route bằng `onboardingCompleted`.
4. Thêm `Authenticator` hoặc refresh coordinator đồng bộ để chỉ một request refresh token tại một thời điểm.
5. Khi nhận 401 từ business API: refresh một lần, retry request một lần; thất bại thì clear session và về login.
6. Không refresh cho login/register/verify/forgot/reset/refresh requests.
7. Lưu token bằng `EncryptedSharedPreferences`; không dùng plaintext fallback và không backup.
8. Clear token khi reset password thành công, logout hoặc refresh bị từ chối.
9. Lấy user ID/email/profile đầy đủ qua `GET /api/v1/users/me`, không từ login/register.
10. Redact `Authorization`, password, OTP và token khỏi logging; release build không log body auth.

Không dùng boolean `is_logged_in` làm nguồn sự thật duy nhất. Session hợp lệ cần refresh token tồn tại; access token
có thể đã hết hạn và được refresh khi cần.

## 10. Rate limiting và chống abuse

Tối thiểu cần giới hạn:

- Login theo IP và identifier.
- Register theo IP.
- Forgot password/resend theo IP và email hash.
- Verify/reset theo challenge và IP.

Rate-limit store nên dùng Redis khi chạy nhiều instance. MVP một instance có thể dùng in-memory limiter nhưng phải
ghi rõ không phù hợp cho horizontal scaling. Không log raw email làm key; dùng normalized hash nếu log/metric cần.

## 11. Test matrix

### Unit tests

- Password validation và password hashing.
- JWT issue/verify/expired/wrong issuer/wrong signature.
- Refresh token hash, rotation, revoked và expired cases.
- OTP create/hash/expiry/attempt/consume logic.
- Register duplicate email/username.
- Login unverified/locked/disabled/invalid credentials.
- Forgot-password không phân biệt email tồn tại.
- Reset password revoke toàn bộ refresh sessions.

### Integration tests với PostgreSQL Testcontainers

- Migration V4 chạy trên database mới và database V1-V3.
- Toàn bộ flow register -> verify -> login -> refresh -> logout.
- Refresh đồng thời: chỉ một rotation thành công.
- OTP chỉ dùng được một lần.
- Reset password làm password cũ và refresh token cũ mất hiệu lực.
- Restart app không phá JWT verification hoặc Flyway history.

### API/security tests

- Auth public endpoints truy cập anonymous; business endpoint vẫn 401.
- Error JSON đúng contract, không có stack trace/token/password/OTP.
- CORS chỉ cho configured origins.
- Health/Swagger behavior theo dev/staging/prod không thay đổi.

### Email tests

- Unit test fake sender.
- Integration dev với Mailpit/MailHog kiểm tra recipient, subject, OTP và expiry.
- Không phụ thuộc SMTP thật trong CI cơ bản.

## 12. Thứ tự triển khai

1. Chốt API contract và error codes.
2. Thêm dependency JWT/Jose và mail, cấu hình typed properties.
3. Viết Flyway V4 và migration integration test.
4. Implement entities/repositories và password/token/challenge services.
5. Implement register + verification email + resend.
6. Implement login + access/refresh token rotation + logout.
7. Implement forgot/reset password.
8. Tích hợp Security filter/entry point và authorization tests.
9. Cập nhật Android DTO/Retrofit/session refresh flow.
10. Chạy Testcontainers, Mailpit integration, backend CI và Android CI.

## 13. Definition of Done

- [ ] V4 migration chạy thành công và restart không chạy lại migration.
- [ ] Password/OTP/refresh token chỉ lưu dạng hash phù hợp.
- [ ] Register, verify, resend, login, refresh, logout, forgot và reset đúng contract.
- [ ] Access JWT ngắn hạn; refresh opaque rotation hoạt động atomically.
- [ ] Reset password revoke mọi session cũ.
- [ ] Enumeration và brute-force được hạn chế.
- [ ] Email sender cấu hình bằng environment/secret manager, không có secret trong Git.
- [ ] Android tự refresh một lần an toàn và logout khi refresh thất bại.
- [ ] Backend unit/security/integration tests pass.
- [ ] Android unit/build CI pass.
- [ ] Swagger mô tả đầy đủ endpoint và response lỗi trong development.
- [ ] Staging kiểm tra email thật, login, refresh, reset và logout end-to-end.

