# Hướng Dẫn Triển Khai Backend: User Profile & User Settings (FinTrack API)

Tài liệu này hướng dẫn chi tiết cách triển khai phân hệ **User Profile** và **User Settings (Preferences & Notifications)** cho dự án [zaabinh/finance-manager-android](https://github.com/zaabinh/finance-manager-android/blob/develop/README.md), bám sát kiến trúc, quy ước mã nguồn và schema cơ sở dữ liệu đã được định nghĩa trong nhánh `develop`.

## 1. Tổng Quan Kiến Trúc & Quy Chuẩn Dự Án

Dựa trên [backend/README.md](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/README.md), [docs/README.md](https://github.com/zaabinh/finance-manager-android/blob/develop/docs/README.md), [docs/api/CONTRACT.md](https://github.com/zaabinh/finance-manager-android/blob/develop/docs/api/CONTRACT.md) và [docs/product/PRODUCT_CONTEXT_VI.md](https://github.com/zaabinh/finance-manager-android/blob/develop/docs/product/PRODUCT_CONTEXT_VI.md), việc phát triển backend cho phần User Profile & Settings phải tuân thủ các nguyên tắc cốt lõi sau:

| Thành phần | Quy chuẩn bắt buộc trong dự án |
| :--- | :--- |
| **Tech Stack** | Java 21, Spring Boot 3.5.16, Spring Data JPA (Hibernate), Spring Security (JWT Bearer), Bean Validation, PostgreSQL 17, Flyway (schema `fintrack`), Springdoc OpenAPI 2.8.17, Lombok. |
| **Kiến trúc phân tầng** | `Controller` (mỏng, chỉ nhận/trả DTO) -> `Service` / `ServiceImpl` (xử lý nghiệp vụ, kiểm tra quyền sở hữu) -> `Repository` (JPA) -> `Entity`. |
| **Quy chuẩn Response** | Trả trực tiếp DTO JSON cho các phản hồi thành công (không bọc trong wrapper `ApiResponse`). Các thao tác không trả dữ liệu sử dụng HTTP `204 No Content`. Thời gian trả về theo chuẩn ISO 8601 UTC (`Instant`). |
| **Quy chuẩn Lỗi** | Mọi lỗi nghiệp vụ và validation đều được `GlobalExceptionHandler` chuẩn hóa về đối tượng [ApiErrorResponse.java](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/src/main/java/com/fintrack/api/dto/response/ApiErrorResponse.java) (`timestamp`, `status`, `errorCode`, `message`, `path`, `fieldErrors`). |
| **Bảo mật & Quyền riêng tư** | Xác thực qua header `Authorization: Bearer <access_token>`. Người dùng có role `USER` chỉ được truy cập/chỉnh sửa hồ sơ và cài đặt của chính mình (lấy `userId` từ `SecurityContext`, tuyệt đối không nhận `userId` từ query/body của client). |
| **Trạng thái hiện tại của Repo** | Các bảng cơ sở dữ liệu đã được khởi tạo trong [V1__init_schema.sql](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/src/main/resources/db/migration/V1__init_schema.sql) và [V2__create_core_tables.sql](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/src/main/resources/db/migration/V2__create_core_tables.sql), nhưng các package [entity](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/src/main/java/com/fintrack/api/entity/package-info.java), [dto/request](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/src/main/java/com/fintrack/api/dto/request/package-info.java), [repository](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/src/main/java/com/fintrack/api/repository/package-info.java), [service](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/src/main/java/com/fintrack/api/service/package-info.java) và [controller](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/src/main/java/com/fintrack/api/controller/package-info.java) mới dừng ở khung thư mục và cần được hiện thực hóa. |

## 2. Thiết Kế API Endpoints (REST Contract)

Trong [docs/api/CONTRACT.md](https://github.com/zaabinh/finance-manager-android/blob/develop/docs/api/CONTRACT.md), các tài liệu hiện tại mới định nghĩa nhóm Wallets, Categories, Transactions và Budgets. Dưới đây là đặc tả API endpoints chuẩn hóa cho **User Profile** và **User Settings**, đồng bộ với thiết kế giao diện trên [Figma Finance Manager Native Android](https://www.figma.com/design/MkLZjSwfzQNYKvfksOwjD0/Finance-Manager-Native-Android?node-id=0-1&p=f&t=Q5Mgw5e4GhCGOtPI-0) và schema trong [V2__create_core_tables.sql](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/src/main/resources/db/migration/V2__create_core_tables.sql):

| HTTP Method | Endpoint | Mô tả chức năng | Request Body | Response |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/users/me` | Lấy thông tin hồ sơ cá nhân và cài đặt hiển thị của user đang đăng nhập | Không có | `200 OK` (`UserProfileResponse`) |
| `PATCH` | `/api/v1/users/me` | Cập nhật thông tin hồ sơ (`displayName`, `username`, `email`) | `UpdateProfileRequest` | `200 OK` (`UserProfileResponse`) |
| `GET` | `/api/v1/users/me/preferences` | Lấy cấu hình tùy chọn cá nhân (`currencyCode`, `timeZone`, `weekStartsOn`, `notificationsEnabled`) | Không có | `200 OK` (`UserPreferencesResponse`) |
| `PUT` | `/api/v1/users/me/preferences` | Cập nhật toàn bộ cấu hình tùy chọn cá nhân | `UpdatePreferencesRequest` | `200 OK` (`UserPreferencesResponse`) |
| `GET` | `/api/v1/users/me/notification-settings` | Lấy thiết lập thông báo chi tiết (nhắc nhở hàng ngày, cảnh báo vượt ngân sách) | Không có | `200 OK` (`NotificationSettingsResponse`) |
| `PUT` | `/api/v1/users/me/notification-settings` | Cập nhật thiết lập thông báo chi tiết | `UpdateNotificationSettingsRequest` | `200 OK` (`NotificationSettingsResponse`) |
| `POST` | `/api/v1/users/me/change-password` | Đổi mật khẩu của người dùng hiện tại và thu hồi các phiên đăng nhập cũ | `ChangePasswordRequest` | `204 No Content` |
| `DELETE` | `/api/v1/users/me` | Vô hiệu hóa tài khoản hiện tại (`status = DISABLED`) và đăng xuất mọi thiết bị | `DeactivateAccountRequest` | `204 No Content` |

### Ví dụ Payload JSON

**1. `GET /api/v1/users/me` (Response `200 OK`):**
```json
{
  "id": "b3d7f6a2-49e0-4c9a-9a3f-12c9a8d7e6f5",
  "username": "nguyenvana",
  "email": "nguyenvana@example.com",
  "displayName": "Nguyễn Văn A",
  "role": "USER",
  "status": "ACTIVE",
  "currencyCode": "VND",
  "timeZone": "Asia/Ho_Chi_Minh",
  "weekStartsOn": 1,
  "notificationsEnabled": true,
  "createdAt": "2026-11-01T08:30:00Z",
  "updatedAt": "2026-11-03T10:00:00Z"
}
```

**2. `PUT /api/v1/users/me/preferences` (Request Body):**
```json
{
  "displayName": "Nguyễn Văn A",
  "currencyCode": "VND",
  "timeZone": "Asia/Ho_Chi_Minh",
  "weekStartsOn": 1,
  "notificationsEnabled": true
}
```

**3. `PUT /api/v1/users/me/notification-settings` (Request & Response `200 OK`):**
```json
{
  "dailyReminderEnabled": true,
  "dailyReminderTime": "20:00:00",
  "budgetAlertEnabled": true,
  "budgetAlertThresholdPercent": 80,
  "updatedAt": "2026-11-03T10:05:00Z"
}
```

## 3. Hướng Dẫn Triển Khai Tầng Entity (`com.fintrack.api.entity`)

Dựa trên file migration [V2__create_core_tables.sql](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/src/main/resources/db/migration/V2__create_core_tables.sql), schema `fintrack` sử dụng các bảng `users`, `user_preferences` và `notification_settings`.

### 3.1. Các Enum trạng thái và phân quyền (`entity/enums`)

**File: `src/main/java/com/fintrack/api/entity/enums/UserRole.java`**
```java
package com.fintrack.api.entity.enums;

public enum UserRole {
    USER,
    ADMIN
}
```

**File: `src/main/java/com/fintrack/api/entity/enums/UserStatus.java`**
```java
package com.fintrack.api.entity.enums;

public enum UserStatus {
    ACTIVE,
    LOCKED,
    DISABLED
}
```

### 3.2. Entity `User` (Bảng `fintrack.users`)

Bảng `fintrack.users` sử dụng kiểu `CITEXT` cho `username` và `email` để không phân biệt hoa thường trong PostgreSQL.

**File: `src/main/java/com/fintrack/api/entity/User.java`**
```java
package com.fintrack.api.entity;

import com.fintrack.api.entity.enums.UserRole;
import com.fintrack.api.entity.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "fintrack")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, columnDefinition = "citext")
    private String username;

    @Column(name = "email", nullable = false, unique = true, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private UserPreferences preferences;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private NotificationSettings notificationSettings;
}
```

### 3.3. Entity `UserPreferences` (Bảng `fintrack.user_preferences`)

Bảng `fintrack.user_preferences` dùng `user_id` làm cả khóa chính (PK) lẫn khóa ngoại (FK) tham chiếu tới `fintrack.users(id)` với các ràng buộc:
- `currency_code`: mặc định `'VND'` (hiện tại dự án khóa cố định `'VND'` theo [V2__create_core_tables.sql](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/src/main/resources/db/migration/V2__create_core_tables.sql)).
- `time_zone`: mặc định `'Asia/Ho_Chi_Minh'`.
- `week_starts_on`: từ `1` (Thứ Hai) đến `7` (Chủ Nhật), mặc định `1`.
- `notifications_enabled`: mặc định `false`.

**File: `src/main/java/com/fintrack/api/entity/UserPreferences.java`**
```java
package com.fintrack.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preferences", schema = "fintrack")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferences {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "currency_code", length = 3, nullable = false, columnDefinition = "bpchar(3)")
    @Builder.Default
    private String currencyCode = "VND";

    @Column(name = "time_zone", nullable = false)
    @Builder.Default
    private String timeZone = "Asia/Ho_Chi_Minh";

    @Column(name = "week_starts_on", nullable = false)
    @Builder.Default
    private Short weekStartsOn = 1;

    @Column(name = "notifications_enabled", nullable = false)
    @Builder.Default
    private Boolean notificationsEnabled = false;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

### 3.4. Entity `NotificationSettings` (Bảng `fintrack.notification_settings`)

**File: `src/main/java/com/fintrack/api/entity/NotificationSettings.java`**
```java
package com.fintrack.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "notification_settings", schema = "fintrack")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettings {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "daily_reminder_enabled", nullable = false)
    @Builder.Default
    private Boolean dailyReminderEnabled = false;

    @Column(name = "daily_reminder_time")
    @Builder.Default
    private LocalTime dailyReminderTime = LocalTime.of(20, 0);

    @Column(name = "budget_alert_enabled", nullable = false)
    @Builder.Default
    private Boolean budgetAlertEnabled = true;

    @Column(name = "budget_alert_threshold_percent", nullable = false)
    @Builder.Default
    private Short budgetAlertThresholdPercent = 80;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

## 4. Hướng Dẫn Triển Khai Tầng Repository (`com.fintrack.api.repository`)

Các repository kế thừa `JpaRepository` và cung cấp các phương thức truy vấn theo `userId` hoặc kiểm tra trùng lặp `username`/`email`.

**File: `src/main/java/com/fintrack/api/repository/UserRepository.java`**
```java
package com.fintrack.api.repository;

import com.fintrack.api.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = {"preferences"})
    Optional<User> findWithPreferencesById(UUID id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, UUID id);
}
```

**File: `src/main/java/com/fintrack/api/repository/UserPreferencesRepository.java`**
```java
package com.fintrack.api.repository;

import com.fintrack.api.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {
}
```

**File: `src/main/java/com/fintrack/api/repository/NotificationSettingsRepository.java`**
```java
package com.fintrack.api.repository;

import com.fintrack.api.entity.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, UUID> {
}
```

## 5. Hướng Dẫn Triển Khai Tầng DTO & Mapper (`dto` & `mapper`)

Sử dụng `record` của Java 21 kết hợp Jakarta Validation để đảm bảo dữ liệu đầu vào hợp lệ trước khi xuống tầng Service.

### 5.1. Request DTOs (`com.fintrack.api.dto.request`)

**File: `src/main/java/com/fintrack/api/dto/request/UpdateProfileRequest.java`**
```java
package com.fintrack.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 3, max = 50, message = "Username phải từ 3 đến 50 ký tự")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username chỉ gồm chữ cái, số và các ký tự . _ -")
        String username,

        @Email(message = "Định dạng email không hợp lệ")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,

        @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự")
        String displayName
) {}
```

**File: `src/main/java/com/fintrack/api/dto/request/UpdatePreferencesRequest.java`**
```java
package com.fintrack.api.dto.request;

import jakarta.validation.constraints.*;

public record UpdatePreferencesRequest(
        @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự")
        String displayName,

        @NotBlank(message = "Mã tiền tệ không được để trống")
        @Pattern(regexp = "^VND$", message = "Hệ thống hiện tại chỉ hỗ trợ mã tiền tệ VND")
        String currencyCode,

        @NotBlank(message = "Múi giờ không được để trống")
        String timeZone,

        @NotNull(message = "Ngày bắt đầu tuần không được để trống")
        @Min(value = 1, message = "weekStartsOn phải từ 1 (Thứ Hai) đến 7 (Chủ Nhật)")
        @Max(value = 7, message = "weekStartsOn phải từ 1 (Thứ Hai) đến 7 (Chủ Nhật)")
        Short weekStartsOn,

        @NotNull(message = "Trạng thái thông báo không được để trống")
        Boolean notificationsEnabled
) {}
```

**File: `src/main/java/com/fintrack/api/dto/request/UpdateNotificationSettingsRequest.java`**
```java
package com.fintrack.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record UpdateNotificationSettingsRequest(
        @NotNull(message = "Trạng thái nhắc nhở hàng ngày không được để trống")
        Boolean dailyReminderEnabled,

        LocalTime dailyReminderTime,

        @NotNull(message = "Trạng thái cảnh báo ngân sách không được để trống")
        Boolean budgetAlertEnabled,

        @NotNull(message = "Ngưỡng cảnh báo ngân sách không được để trống")
        @Min(value = 1, message = "Ngưỡng cảnh báo phải từ 1% đến 100%")
        @Max(value = 100, message = "Ngưỡng cảnh báo phải từ 1% đến 100%")
        Short budgetAlertThresholdPercent
) {}
```

**File: `src/main/java/com/fintrack/api/dto/request/ChangePasswordRequest.java`**
```java
package com.fintrack.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Mật khẩu hiện tại không được để trống")
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu mới phải từ 8 đến 72 ký tự")
        String newPassword
) {}
```

**File: `src/main/java/com/fintrack/api/dto/request/DeactivateAccountRequest.java`**
```java
package com.fintrack.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeactivateAccountRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu xác nhận để vô hiệu hóa tài khoản")
        String currentPassword
) {}
```

### 5.2. Response DTOs (`com.fintrack.api.dto.response`)

**File: `src/main/java/com/fintrack/api/dto/response/UserProfileResponse.java`**
```java
package com.fintrack.api.dto.response;

import com.fintrack.api.entity.enums.UserRole;
import com.fintrack.api.entity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String username,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        String currencyCode,
        String timeZone,
        Short weekStartsOn,
        Boolean notificationsEnabled,
        Instant createdAt,
        Instant updatedAt
) {}
```

**File: `src/main/java/com/fintrack/api/dto/response/UserPreferencesResponse.java`**
```java
package com.fintrack.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserPreferencesResponse(
        UUID userId,
        String displayName,
        String currencyCode,
        String timeZone,
        Short weekStartsOn,
        Boolean notificationsEnabled,
        Instant updatedAt
) {}
```

**File: `src/main/java/com/fintrack/api/dto/response/NotificationSettingsResponse.java`**
```java
package com.fintrack.api.dto.response;

import java.time.Instant;
import java.time.LocalTime;

public record NotificationSettingsResponse(
        Boolean dailyReminderEnabled,
        LocalTime dailyReminderTime,
        Boolean budgetAlertEnabled,
        Short budgetAlertThresholdPercent,
        Instant updatedAt
) {}
```

### 5.3. Mapper (`com.fintrack.api.mapper.UserMapper`)

**File: `src/main/java/com/fintrack/api/mapper/UserMapper.java`**
```java
package com.fintrack.api.mapper;

import com.fintrack.api.dto.response.NotificationSettingsResponse;
import com.fintrack.api.dto.response.UserPreferencesResponse;
import com.fintrack.api.dto.response.UserProfileResponse;
import com.fintrack.api.entity.NotificationSettings;
import com.fintrack.api.entity.User;
import com.fintrack.api.entity.UserPreferences;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserProfileResponse toProfileResponse(User user, UserPreferences prefs) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                prefs != null ? prefs.getDisplayName() : null,
                user.getRole(),
                user.getStatus(),
                prefs != null ? prefs.getCurrencyCode() : "VND",
                prefs != null ? prefs.getTimeZone() : "Asia/Ho_Chi_Minh",
                prefs != null ? prefs.getWeekStartsOn() : (short) 1,
                prefs != null ? prefs.getNotificationsEnabled() : false,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public UserPreferencesResponse toPreferencesResponse(UserPreferences prefs) {
        return new UserPreferencesResponse(
                prefs.getUserId(),
                prefs.getDisplayName(),
                prefs.getCurrencyCode(),
                prefs.getTimeZone(),
                prefs.getWeekStartsOn(),
                prefs.getNotificationsEnabled(),
                prefs.getUpdatedAt()
        );
    }

    public NotificationSettingsResponse toNotificationSettingsResponse(NotificationSettings settings) {
        return new NotificationSettingsResponse(
                settings.getDailyReminderEnabled(),
                settings.getDailyReminderTime(),
                settings.getBudgetAlertEnabled(),
                settings.getBudgetAlertThresholdPercent(),
                settings.getUpdatedAt()
        );
    }
}
```

## 6. Hướng Dẫn Triển Khai Tầng Service (`service` & `service/impl`)

Tầng Service đảm nhận toàn bộ các quy tắc nghiệp vụ:
1. Kiểm tra sự tồn tại và trạng thái `ACTIVE` của tài khoản.
2. Tự động khởi tạo bản ghi `UserPreferences` hoặc `NotificationSettings` mặc định nếu người dùng cũ chưa có bản ghi.
3. Kiểm tra tính hợp lệ của `TimeZone` (dùng `ZoneId.of(timeZone)`) và kiểm tra trùng `email`/`username` khi cập nhật hồ sơ.
4. Xác thực mật khẩu hiện tại bằng `PasswordEncoder` trước khi đổi mật khẩu hoặc vô hiệu hóa tài khoản.

### 6.1. Interface `UserService`

**File: `src/main/java/com/fintrack/api/service/UserService.java`**
```java
package com.fintrack.api.service;

import com.fintrack.api.dto.request.*;
import com.fintrack.api.dto.response.NotificationSettingsResponse;
import com.fintrack.api.dto.response.UserPreferencesResponse;
import com.fintrack.api.dto.response.UserProfileResponse;

import java.util.UUID;

public interface UserService {

    UserProfileResponse getMyProfile(UUID userId);

    UserProfileResponse updateMyProfile(UUID userId, UpdateProfileRequest request);

    UserPreferencesResponse getMyPreferences(UUID userId);

    UserPreferencesResponse updateMyPreferences(UUID userId, UpdatePreferencesRequest request);

    NotificationSettingsResponse getMyNotificationSettings(UUID userId);

    NotificationSettingsResponse updateMyNotificationSettings(UUID userId, UpdateNotificationSettingsRequest request);

    void changeMyPassword(UUID userId, ChangePasswordRequest request);

    void deactivateMyAccount(UUID userId, DeactivateAccountRequest request);
}
```

### 6.2. Implementation `UserServiceImpl`

**File: `src/main/java/com/fintrack/api/service/impl/UserServiceImpl.java`**
```java
package com.fintrack.api.service.impl;

import com.fintrack.api.dto.request.*;
import com.fintrack.api.dto.response.NotificationSettingsResponse;
import com.fintrack.api.dto.response.UserPreferencesResponse;
import com.fintrack.api.dto.response.UserProfileResponse;
import com.fintrack.api.entity.NotificationSettings;
import com.fintrack.api.entity.User;
import com.fintrack.api.entity.UserPreferences;
import com.fintrack.api.entity.enums.UserStatus;
import com.fintrack.api.mapper.UserMapper;
import com.fintrack.api.repository.NotificationSettingsRepository;
import com.fintrack.api.repository.UserPreferencesRepository;
import com.fintrack.api.repository.UserRepository;
import com.fintrack.api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(UUID userId) {
        User user = getActiveUserOrThrow(userId);
        UserPreferences prefs = preferencesRepository.findById(userId).orElse(null);
        return userMapper.toProfileResponse(user, prefs);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(UUID userId, UpdateProfileRequest request) {
        User user = getActiveUserOrThrow(userId);
        UserPreferences prefs = getOrCreatePreferences(user);

        if (request.username() != null && !request.username().isBlank()) {
            String normalizedUsername = request.username().trim();
            if (userRepository.existsByUsernameIgnoreCaseAndIdNot(normalizedUsername, userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username đã được sử dụng");
            }
            user.setUsername(normalizedUsername);
        }

        if (request.email() != null && !request.email().isBlank()) {
            String normalizedEmail = request.email().trim().toLowerCase();
            if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng");
            }
            user.setEmail(normalizedEmail);
        }

        if (request.displayName() != null) {
            prefs.setDisplayName(request.displayName().trim());
            preferencesRepository.save(prefs);
        }

        User savedUser = userRepository.save(user);
        return userMapper.toProfileResponse(savedUser, prefs);
    }

    @Override
    @Transactional
    public UserPreferencesResponse getMyPreferences(UUID userId) {
        User user = getActiveUserOrThrow(userId);
        UserPreferences prefs = getOrCreatePreferences(user);
        return userMapper.toPreferencesResponse(prefs);
    }

    @Override
    @Transactional
    public UserPreferencesResponse updateMyPreferences(UUID userId, UpdatePreferencesRequest request) {
        validateTimeZone(request.timeZone());

        User user = getActiveUserOrThrow(userId);
        UserPreferences prefs = getOrCreatePreferences(user);

        if (request.displayName() != null) {
            prefs.setDisplayName(request.displayName().trim());
        }
        prefs.setCurrencyCode(request.currencyCode().toUpperCase());
        prefs.setTimeZone(request.timeZone().trim());
        prefs.setWeekStartsOn(request.weekStartsOn());
        prefs.setNotificationsEnabled(request.notificationsEnabled());

        UserPreferences savedPrefs = preferencesRepository.save(prefs);
        return userMapper.toPreferencesResponse(savedPrefs);
    }

    @Override
    @Transactional
    public NotificationSettingsResponse getMyNotificationSettings(UUID userId) {
        User user = getActiveUserOrThrow(userId);
        NotificationSettings settings = getOrCreateNotificationSettings(user);
        return userMapper.toNotificationSettingsResponse(settings);
    }

    @Override
    @Transactional
    public NotificationSettingsResponse updateMyNotificationSettings(
            UUID userId,
            UpdateNotificationSettingsRequest request
    ) {
        User user = getActiveUserOrThrow(userId);
        NotificationSettings settings = getOrCreateNotificationSettings(user);

        settings.setDailyReminderEnabled(request.dailyReminderEnabled());
        if (request.dailyReminderTime() != null) {
            settings.setDailyReminderTime(request.dailyReminderTime());
        } else if (Boolean.TRUE.equals(request.dailyReminderEnabled()) && settings.getDailyReminderTime() == null) {
            settings.setDailyReminderTime(LocalTime.of(20, 0));
        }
        settings.setBudgetAlertEnabled(request.budgetAlertEnabled());
        settings.setBudgetAlertThresholdPercent(request.budgetAlertThresholdPercent());

        NotificationSettings saved = notificationSettingsRepository.save(settings);
        return userMapper.toNotificationSettingsResponse(saved);
    }

    @Override
    @Transactional
    public void changeMyPassword(UUID userId, ChangePasswordRequest request) {
        User user = getActiveUserOrThrow(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không chính xác");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới không được trùng với mật khẩu cũ");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // Lưu ý: Gọi thêm RefreshSessionRepository.RevokeAllByUserId(userId) nếu đã triển khai RefreshSessionRepository
    }

    @Override
    @Transactional
    public void deactivateMyAccount(UUID userId, DeactivateAccountRequest request) {
        User user = getActiveUserOrThrow(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không chính xác");
        }

        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);
        // Lưu ý: Thu hồi toàn bộ refresh sessions của userId sau khi vô hiệu hóa
    }

    private User getActiveUserOrThrow(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không ở trạng thái hoạt động");
        }
        return user;
    }

    private UserPreferences getOrCreatePreferences(User user) {
        return preferencesRepository.findById(user.getId())
                .orElseGet(() -> preferencesRepository.save(
                        UserPreferences.builder()
                                .user(user)
                                .displayName(user.getUsername())
                                .currencyCode("VND")
                                .timeZone("Asia/Ho_Chi_Minh")
                                .weekStartsOn((short) 1)
                                .notificationsEnabled(false)
                                .build()
                ));
    }

    private NotificationSettings getOrCreateNotificationSettings(User user) {
        return notificationSettingsRepository.findById(user.getId())
                .orElseGet(() -> notificationSettingsRepository.save(
                        NotificationSettings.builder()
                                .user(user)
                                .dailyReminderEnabled(false)
                                .dailyReminderTime(LocalTime.of(20, 0))
                                .budgetAlertEnabled(true)
                                .budgetAlertThresholdPercent((short) 80)
                                .build()
                ));
    }

    private void validateTimeZone(String timeZone) {
        try {
            ZoneId.of(timeZone.trim());
        } catch (DateTimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Múi giờ không hợp lệ: " + timeZone);
        }
    }
}
```

## 7. Hướng Dẫn Triển Khai Tầng Controller (`com.fintrack.api.controller`)

Theo quy chuẩn trong [backend/README.md](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/README.md) và [docs/api/CONTRACT.md](https://github.com/zaabinh/finance-manager-android/blob/develop/docs/api/CONTRACT.md):
- Controller giữ mỏng (thin controller), không chứa logic nghiệp vụ.
- Lấy `UUID userId` trực tiếp từ `Authentication` / `@AuthenticationPrincipal` của Spring Security.
- Trả về trực tiếp DTO hoặc `ResponseEntity.noContent().build()` (HTTP `204`).

**File: `src/main/java/com/fintrack/api/controller/UserController.java`**
```java
package com.fintrack.api.controller;

import com.fintrack.api.dto.request.*;
import com.fintrack.api.dto.response.NotificationSettingsResponse;
import com.fintrack.api.dto.response.UserPreferencesResponse;
import com.fintrack.api.dto.response.UserProfileResponse;
import com.fintrack.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "User Profile & Settings", description = "Quản lý hồ sơ cá nhân và cài đặt người dùng")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Lấy thông tin hồ sơ của người dùng hiện tại")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal(expression = "id") UUID userId
    ) {
        return ResponseEntity.ok(userService.getMyProfile(userId));
    }

    @PatchMapping
    @Operation(summary = "Cập nhật thông tin hồ sơ cá nhân (username, email, displayName)")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(userService.updateMyProfile(userId, request));
    }

    @GetMapping("/preferences")
    @Operation(summary = "Lấy cài đặt tùy chọn của người dùng (tiền tệ, múi giờ, ngày đầu tuần)")
    public ResponseEntity<UserPreferencesResponse> getMyPreferences(
            @AuthenticationPrincipal(expression = "id") UUID userId
    ) {
        return ResponseEntity.ok(userService.getMyPreferences(userId));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Cập nhật cài đặt tùy chọn của người dùng")
    public ResponseEntity<UserPreferencesResponse> updateMyPreferences(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @Valid @RequestBody UpdatePreferencesRequest request
    ) {
        return ResponseEntity.ok(userService.updateMyPreferences(userId, request));
    }

    @GetMapping("/notification-settings")
    @Operation(summary = "Lấy thiết lập thông báo chi tiết (nhắc nhở hàng ngày & cảnh báo ngân sách)")
    public ResponseEntity<NotificationSettingsResponse> getMyNotificationSettings(
            @AuthenticationPrincipal(expression = "id") UUID userId
    ) {
        return ResponseEntity.ok(userService.getMyNotificationSettings(userId));
    }

    @PutMapping("/notification-settings")
    @Operation(summary = "Cập nhật thiết lập thông báo chi tiết")
    public ResponseEntity<NotificationSettingsResponse> updateMyNotificationSettings(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @Valid @RequestBody UpdateNotificationSettingsRequest request
    ) {
        return ResponseEntity.ok(userService.updateMyNotificationSettings(userId, request));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu tài khoản hiện tại")
    public ResponseEntity<Void> changeMyPassword(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changeMyPassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Vô hiệu hóa tài khoản hiện tại")
    public ResponseEntity<Void> deactivateMyAccount(
            @AuthenticationPrincipal(expression = "id") UUID userId,
            @Valid @RequestBody DeactivateAccountRequest request
    ) {
        userService.deactivateMyAccount(userId, request);
        return ResponseEntity.noContent().build();
    }
}
```

> **Lưu ý về `@AuthenticationPrincipal`**: Nếu lớp `UserPrincipal` / `CustomUserDetails` trong package `com.fintrack.api.security` đặt tên phương thức lấy ID là `getUserId()` thay vì `getId()`, bạn chỉ cần đổi biểu thức thành `@AuthenticationPrincipal(expression = "userId") UUID userId` hoặc inject trực tiếp `@AuthenticationPrincipal UserPrincipal principal` rồi gọi `principal.getUserId()`.

## 8. Kiểm Thử & Xác Thực (Testing Checklist)

Theo hướng dẫn tại [backend/README.md](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/README.md), sau khi hoàn tất các class trên, bạn cần kiểm tra:

1. **Chạy Flyway & Build dự án**:
   ```bash
   cd backend
   ./mvnw clean test
   ```
2. **Các kịch bản cần viết Unit/Integration Test (`UserControllerTest` & `UserServiceIntegrationTest`)**:
   - `GET /api/v1/users/me` trả về `200 OK` kèm mặc định `currencyCode = "VND"` và `timeZone = "Asia/Ho_Chi_Minh"` ngay cả khi bảng `user_preferences` chưa có dòng dữ liệu trước đó.
   - `PATCH /api/v1/users/me` trả về `409 Conflict` (`ApiErrorResponse`) khi cập nhật `email` hoặc `username` trùng với người dùng khác (không phân biệt hoa/thường nhờ `CITEXT`).
   - `PUT /api/v1/users/me/preferences` trả về `400 Bad Request` kèm `fieldErrors` nếu `currencyCode != "VND"` hoặc `weekStartsOn` nằm ngoài khoảng `1..7`.
   - `POST /api/v1/users/me/change-password` trả về `204 No Content` khi `currentPassword` đúng và trả về `400 Bad Request` khi `currentPassword` sai.
   - Khi chưa truyền header `Authorization: Bearer <token>`, mọi endpoint `/api/v1/users/me/**` phải trả về `401 Unauthorized` theo đúng định dạng [ApiErrorResponse.java](https://github.com/zaabinh/finance-manager-android/blob/develop/backend/src/main/java/com/fintrack/api/dto/response/ApiErrorResponse.java).
