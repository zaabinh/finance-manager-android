# Hướng dẫn chuyển RateLimitingService từ in-memory sang Redis

Migration này đã được áp dụng vào backend. Các bước dưới đây mô tả implementation và cách cấu hình/kiểm thử.
Mục tiêu là các backend instance dùng chung bộ đếm, giữ nguyên API và giới hạn authentication.
Không thay đổi schema PostgreSQL, password policy, OTP challenge hoặc refresh token rotation.

## 1. Những phần cần thay đổi

Đường dẫn bên dưới tính từ root repository:

| File | Thay đổi |
| --- | --- |
| `backend/pom.xml` | Thêm Spring Data Redis starter |
| `backend/src/main/resources/application.yml` | Connection, timeout, namespace Redis và readiness |
| `backend/src/main/java/com/fintrack/api/service/auth/RateLimitingService.java` | Thay Map/Clock bằng StringRedisTemplate và Lua |
| `backend/src/main/java/com/fintrack/api/exception/ErrorCode.java` | Thêm mã lỗi khi Redis không khả dụng |
| `docker-compose.yml` | Thêm Redis, volume, healthcheck và cấu hình backend |
| `.env.example`, `backend/.env.example` | Thêm biến Redis |
| `backend/src/test/…` | Sửa constructor, tách mock/unit test khỏi integration Redis |
| `docs/api/AUTHENTICATION_IMPLEMENTATION.md`, `backend/README.md` | Cập nhật mô tả sau khi triển khai |

Giữ các public method `checkLoginAllowed`, `recordLoginFailure`, `clearLoginFailures`,
`checkAndRecord`. `AuthController` và `AuthService` tiếp tục gọi chúng như hiện tại.
Không cần tạo thêm interface/store abstraction hoặc thêm database migration.

## 2. Giữ nguyên hành vi

| Hành động | Giới hạn hiện tại |
| --- | --- |
| Login thất bại | identifier + IP: 5/15 phút; IP tổng: 50/15 phút |
| Login requests | IP: 100/15 phút |
| Register | IP: 10/giờ |
| Refresh | IP: 20/phút |
| Logout | IP: 60/phút |
| Resend / forgot password | Mỗi purpose: IP 20/giờ; email 3/giờ; cooldown email 60 giây |
| Verify / reset | Mỗi purpose: IP 60/15 phút; email 20/15 phút |

OTP vẫn có tối đa 5 lần nhập sai/challenge, lưu trong PostgreSQL. Đây là bộ đếm riêng,
không chuyển `attempt_count` của challenge sang Redis.

Window bắt đầu từ lần ghi đầu tiên và kết thúc bằng TTL. Request tiếp theo không gia hạn TTL.
Đây là fixed window theo key, không phải sliding window hoặc token bucket.
Login thành công chỉ xóa key identifier + IP, không xóa IP tổng.

## 3. Thêm dependency và connection

Trong `backend/pom.xml`, thêm vào `<dependencies>`; dùng version do Spring Boot quản lý:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Trong `backend/src/main/resources/application.yml`, **merge vào các block đang có**,
không tạo hai block `spring`, `app` hoặc `management` trùng nhau:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      username: ${REDIS_USERNAME:}
      password: ${REDIS_PASSWORD:}
      connect-timeout: 2s
      timeout: 2s
      ssl:
        enabled: ${REDIS_SSL_ENABLED:false}
app:
  auth:
    rate-limit-key-prefix: ${AUTH_RATE_LIMIT_KEY_PREFIX:fintrack:dev:auth:rate-limit:}
management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState,db,redis
```

Spring Boot tạo `StringRedisTemplate`; không cần tự tạo Redis client hay connection factory.
Liveness giữ độc lập với Redis; readiness phản ánh dependency Redis.

Thêm vào hai file `.env.example`:

```dotenv
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_USERNAME=
REDIS_PASSWORD=
REDIS_SSL_ENABLED=false
AUTH_RATE_LIMIT_KEY_PREFIX=fintrack:dev:auth:rate-limit:
```

Mọi instance cùng môi trường phải trỏ cùng Redis và cùng prefix. Staging/prod dùng prefix riêng,
ví dụ `fintrack:prod:auth:rate-limit:`. Khi dùng Redis có authentication/TLS, cấu hình credentials
và bật TLS theo provider. Không commit secret.

## 4. Redis trong Docker Compose cho local development

Thêm service vào `docker-compose.yml` hiện có:

```yaml
services:
  redis:
    image: redis:7.4-alpine
    command: ["redis-server", "--appendonly", "yes", "--maxmemory", "128mb", "--maxmemory-policy", "noeviction"]
    ports:
      - "127.0.0.1:6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5
    restart: unless-stopped

  backend:
    environment:
      REDIS_HOST: redis
      REDIS_PORT: "6379"
      AUTH_RATE_LIMIT_KEY_PREFIX: ${AUTH_RATE_LIMIT_KEY_PREFIX:-fintrack:dev:auth:rate-limit:}
    depends_on:
      redis:
        condition: service_healthy

volumes:
  redis_data:
```

Đây là các phần **bổ sung**, không thay toàn bộ Compose: giữ postgres, postgres_data, backend environment
và `depends_on.postgres` hiện tại. Backend chạy trong Compose dùng hostname `redis`; backend chạy
trực tiếp trên máy dùng `localhost`.

Compose trên dành cho local, Redis không bật password và chỉ publish ra loopback. Nếu cấu hình Redis
production từ provider, truyền thêm REDIS_USERNAME/PASSWORD/SSL_ENABLED vào backend environment,
không dùng service local này làm cấu hình production mặc định.

`noeviction` tránh tự xóa key rate limit chưa hết hạn khi đầy bộ nhớ. Khi đầy, Redis có thể từ chối
ghi và backend phải trả 503. Giới hạn 10.000 key trong Map cũ không được giữ bằng cách đếm toàn bộ
Redis key; dùng memory limit, TTL và theo dõi memory thay thế. AOF/volume giảm mất bộ đếm khi restart
Redis nhưng không bảo đảm không mất lần ghi mới nhất trong mọi tình huống crash/failover.

## 5. Thay implementation RateLimitingService

Thêm `AUTH_RATE_LIMIT_UNAVAILABLE` vào enum `ErrorCode`. Sau đó thay nội dung class hiện tại bằng
implementation dưới đây. `TokenHash`, `ClientMetadata`, `AppException` đã có trong dự án.

```java
package com.fintrack.api.service.auth;

import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RateLimitingService {
    private static final DefaultRedisScript<Long> WINDOW = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            local limit = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            local mode = ARGV[3]
            if mode == 'check' then
                if count >= limit then return 0 end
                return 1
            end
            if mode == 'consume' and count >= limit then return 0 end
            if count >= limit then return 1 end
            if count == 0 then
                redis.call('SET', KEYS[1], '1', 'PX', ttl)
            else
                redis.call('INCR', KEYS[1])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String prefix;

    public RateLimitingService(StringRedisTemplate redis,
            @Value("${app.auth.rate-limit-key-prefix}") String prefix) {
        if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("Missing Redis key prefix");
        this.redis = redis;
        this.prefix = prefix;
    }

    public void checkLoginAllowed(String identifier, ClientMetadata client) {
        execute(loginKey(identifier, client), 5, Duration.ofMinutes(15), "check");
        execute(key("login-ip", client.ipAddress()), 50, Duration.ofMinutes(15), "check");
    }

    public void recordLoginFailure(String identifier, ClientMetadata client) {
        execute(loginKey(identifier, client), 5, Duration.ofMinutes(15), "bump");
        execute(key("login-ip", client.ipAddress()), 50, Duration.ofMinutes(15), "bump");
    }

    public void clearLoginFailures(String identifier, ClientMetadata client) {
        try {
            redis.delete(loginKey(identifier, client));
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    public void checkAndRecord(String scope, String identifier, int limit, Duration duration) {
        execute(key(scope, identifier), limit, duration, "consume");
    }

    private void execute(String key, int limit, Duration duration, String mode) {
        if (limit < 1 || duration.toMillis() < 1) throw new IllegalArgumentException("Invalid rate limit");
        Long result;
        try {
            result = redis.execute(WINDOW, List.of(key), Integer.toString(limit),
                    Long.toString(duration.toMillis()), mode);
        } catch (DataAccessException exception) {
            throw unavailable();
        }
        if (result == null) throw unavailable();
        if (result == 0L) throw new AppException(HttpStatus.TOO_MANY_REQUESTS,
                ErrorCode.AUTH_RATE_LIMITED, "Too many requests; try again later");
    }

    private String loginKey(String identifier, ClientMetadata client) {
        return key("login", identifier.trim().toLowerCase(Locale.ROOT) + ':' + client.ipAddress());
    }

    private String key(String scope, String identifier) {
        return prefix + scope + ':' + TokenHash.sha256(identifier.trim().toLowerCase(Locale.ROOT));
    }

    private AppException unavailable() {
        return new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.AUTH_RATE_LIMIT_UNAVAILABLE, "Authentication rate limiting is temporarily unavailable");
    }
}
```

Ba mode có mục đích khác nhau:

- `check`: chỉ đọc giới hạn login failure, không tăng khi kiểm tra.
- `bump`: ghi nhận login sai, chặn giá trị counter ở ngưỡng; lần sai thứ 5 vẫn trả lỗi credentials từ
  AuthService, lần login tiếp theo bị `check` trả 429 như hiện tại.
- `consume`: kiểm tra và tăng quota request trong cùng một Lua script; không tăng khi key đã đủ quota.

Lua thực thi nguyên tử **trên từng key**. Không dùng `GET → kiểm tra Java → INCR → EXPIRE` tách rời
vì nhiều instance có thể vượt quota hoặc tạo counter thiếu TTL. `StringRedisTemplate.execute` hỗ trợ
chạy script và script cache. Xem [Spring Data Redis scripting](https://docs.spring.io/spring-data/data-redis/reference/redis/scripting.html)
và [Redis INCR rate limiter pattern](https://redis.io/docs/latest/commands/incr/).

Không cần Clock/Instant/Map/synchronized trong class mới: Redis quản lý expiry. **Giữ bean Clock**
trong SecurityConfig vì AuthService, TokenService và JWT validation vẫn sử dụng.

Các limit của một endpoint vẫn chạy lần lượt như hiện tại: nếu limit thứ hai từ chối, limit thứ nhất
có thể đã tăng. Hai key login failure cũng được ghi riêng. Ví dụ này không tạo transaction nhiều key
hoặc bảo đảm cả endpoint là một phép atomic; các key đơn không gặp ràng buộc cross-slot của Redis Cluster.
Các login sai đã bắt đầu đồng thời trước khi đạt ngưỡng vẫn có thể hoàn tất; đây là giới hạn của
mô hình check-then-authenticate hiện tại, không phải giới hạn của script `consume`.

## 6. Khi Redis lỗi

Cập nhật 08/11/2026: khi Redis lỗi hoặc không trả kết quả, `RateLimitingService` chuyển sang bộ đếm
in-memory (`ConcurrentHashMap`) với cùng limit/window, nên auth vẫn hoạt động. Giới hạn: đếm theo từng
instance (N instance cho phép tối đa N × limit trong lúc Redis lỗi). Mỗi request vẫn thử Redis trước; khi
Redis trả lời lại, bộ đếm in-memory bị xóa. Map giới hạn `MAX_FALLBACK_KEYS` key; khi đầy (sau khi dọn key
hết hạn) thì key mới trả `503 AUTH_RATE_LIMIT_UNAVAILABLE` để không bị bypass hoặc cạn bộ nhớ.

`429` nghĩa là đã vượt quota; `503` nghĩa là backend không kiểm tra được quota. Client không nên
coi 503 là password sai hoặc tự động retry liên tục. Business API chỉ dùng JWT không tự động cần Redis,
nhưng readiness có Redis có thể khiến load balancer ngừng route cả instance khi Redis down.

Không tự động retry thao tác tăng counter sau timeout: Redis có thể đã ghi nhưng response chưa tới client.
Chạy Redis ngoài transaction PostgreSQL; không bật `setEnableTransactionSupport(true)` trên template.
Rollback database không hoàn tác Redis; các lần thử vẫn bị tính quota, phù hợp mục đích chống abuse.

## 7. Sửa tests theo đúng layer

### Unit/MVC tests không cần Redis thật

1. `AuthServiceTest` đang gọi `new RateLimitingService(clock)`. Đổi sang Mockito mock limiter;
   kiểm tra service gọi check/record/clear đúng lúc. Test login generic-error và test limiter nên tách:
   Mockito mock không tự chặn request thứ 6 như instance Map cũ.
2. Viết lại `RateLimitingServiceTest` với mock `StringRedisTemplate`:
   script trả 1 thì cho qua, 0 thì 429, null hoặc DataAccessException thì 503;
   kiểm tra clear chỉ DELETE login identifier + IP, không xóa key IP tổng.
   Script mock không chứng minh Lua/TTL/concurrency hoạt động, cần integration test bên dưới.
3. Các SpringBootTest dưới đây thêm `@MockitoBean RateLimitingService limits` để controller không gọi Redis:
   `ApplicationSmokeTest`, `AuthApiAcceptanceTest`, `ProductionSecurityTest`, `StagingSecurityTest`.
4. Trong `backend/src/test/resources/application-test.yml`, thêm:

   ```yaml
   management:
     health:
       redis:
         enabled: false
   ```

   Merge vào `management` hiện tại. Việc này tắt Redis health contributor cho profile test; chỉ bỏ Redis
   khỏi readiness chưa đủ vì `/actuator/health` tổng vẫn có thể kiểm tra Redis. Không tắt health Redis
   trong cấu hình dev/staging/prod chỉ để test pass.

### Integration tests có Redis thật

Dùng Testcontainers `GenericContainer` với image `redis:7.4-alpine`, expose 6379, đợi PING sẵn sàng;
inject host/mapped port bằng DynamicPropertySource. Spring test tạo LettuceConnectionFactory và
StringRedisTemplate như production. Dependency Testcontainers hiện có đã cung cấp GenericContainer.
Container của test phải được dừng sau suite.

Tạo `RedisRateLimitingIT` và kiểm tra ít nhất:

- Hai RateLimitingService riêng, cùng Redis/prefix: tổng 100 request đồng thời, limit 20, đúng 20 thành công.
- TTL > 0 sau lần đầu, lần sau không tăng TTL. Dùng window ngắn, chờ key hết hạn rồi thử lại.
- Năm login sai chặn identifier + IP; identifier khác vẫn dùng được cho tới khi IP tổng đạt 50.
- Login thành công xóa key identifier + IP nhưng không xóa IP tổng.
- Tạo lại service/backend trong khi Redis giữ nguyên thì counter vẫn còn.
- Hai prefix khác nhau không dùng chung quota.
- Redis dừng hoặc ghi bị từ chối thì trả 503, không bỏ qua limit.

`AuthenticationIT` và `PostgresMigrationIT` hiện khởi động application thật bằng profile dev.
Sau khi Redis được thêm, chúng cũng cần Redis test: bổ sung container và connection properties;
với PostgresMigrationIT truyền Redis host/port qua SpringApplicationBuilder.
`AuthenticationIT` hiện cho phép dùng PostgreSQL local qua `auth.it.jdbc-url`; tùy chọn này không thay
thế Redis. Nếu giữ cách chạy local không Docker, bổ sung host/port Redis test riêng cho test đó.

Mỗi test/scenario dùng prefix UUID riêng để không ảnh hưởng quota test khác. Không dùng FLUSHALL
hoặc FLUSHDB trên Redis dùng chung. Testcontainers dùng database riêng vẫn nên dọn bằng lifecycle container.

Chạy từ thư mục backend sau khi sửa code/tests:

```powershell
./mvnw.cmd test
./mvnw.cmd -Ppostgres-it verify
```

Profile `postgres-it` hiện đã chạy các class tên `*IT`, nên bao gồm RedisRateLimitingIT khi thêm file đó.
Unit tests phải chạy được khi không có Redis; integration suite cần Docker và các container test.

## 8. Kiểm tra local và rollout

Từ root repo, sau khi sửa Compose và cấu hình `.env`:

```powershell
docker compose config --quiet
docker compose up -d redis
docker compose exec redis redis-cli ping
```

PING phải trả `PONG`. Khởi động/rebuild backend theo cách bạn đang dùng, kiểm tra readiness và thử
login sai 5 lần với một identifier/IP test; lần thứ 6 phải trả 429.

Kiểm tra key bằng SCAN, không dùng KEYS trên production:

```powershell
docker compose exec redis redis-cli --scan --pattern 'fintrack:dev:auth:rate-limit:*'
```

Dùng `GET <key>` và `PTTL <key>` trên key test cụ thể để xem counter và milliseconds còn lại.
Không ghi email/password/OTP/token vào key hoặc log. Giữ cách lấy remote IP hiện tại; triển khai sau
proxy phải cấu hình trusted proxy để tránh mọi client dùng chung một IP hoặc tin header giả mạo.

Không cần migrate counter Map sang Redis. Khi chuyển đổi, quota cũ bắt đầu lại; rollout nên ngắn để
tránh giai đoạn instance cũ dùng Map còn instance mới dùng Redis. Sau rollout, tất cả instance phải dùng
cùng Redis/prefix. Rollback về Map cũng làm mất trạng thái quota Redis đối với code cũ.

Sau khi kiểm thử thành công, cập nhật tài liệu authentication/README để mô tả Redis, 503,
connection variables và hạn chế durability; bỏ mô tả Map 10.000 key và giới hạn một instance.

## 9. Điều kiện hoàn tất

- [x] Không còn Map hoặc Clock dependency trong RateLimitingService; các consumer giữ nguyên API.
- [x] Tất cả counter ghi mới đều có TTL; quota không bị reset khi backend restart.
- [x] Nhiều instance chia sẻ quota, concurrency test không vượt giới hạn `consume`.
- [x] Redis lỗi trả 503; vượt quota trả 429; không fallback in-memory.
- [x] Unit/MVC tests không cần Redis; integration tests chạy với Redis thật.
- [x] Compose/env/readiness và tài liệu vận hành đã cập nhật.

Implementation thực tế nằm trong RateLimitingService. Unit/MVC tests mock Redis hoặc limiter;
RedisRateLimitingIT, AuthenticationIT và PostgresMigrationIT dùng Redis test riêng qua Testcontainers.
Ngay cả khi AuthenticationIT dùng tùy chọn PostgreSQL local, Redis test vẫn yêu cầu Docker.
