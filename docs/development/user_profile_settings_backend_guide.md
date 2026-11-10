# User Profile & Settings — Backend Contract

Tài liệu này mô tả contract đã thống nhất giữa backend và Android cho hồ sơ người dùng, tùy chọn hiển thị và cài đặt thông báo.

## Nguyên tắc

- Backend dùng Spring Security JWT và lấy `userId` từ `AuthenticatedUser`, không nhận `userId` từ client.
- Tầng persistence dùng `JdbcTemplate` và các record trong repository.
- Response thành công có dạng `ApiResponse<T>`; lỗi dùng `ApiErrorResponse`.
- Các thao tác cập nhật dùng `PATCH` và hỗ trợ cập nhật một phần.
- Email và đơn vị tiền tệ chỉ đọc. Profile chỉ cập nhật `displayName` và `timeZone`.
- `displayName` và `timeZone` được lưu trong `fintrack.user_preferences`.
- Toàn bộ trạng thái thông báo được lưu trong `fintrack.notification_settings`.

## Endpoints

| Method | Endpoint | Chức năng |
| --- | --- | --- |
| `GET` | `/api/v1/users/me` | Lấy profile cùng tiền tệ và múi giờ cần cho màn Profile |
| `PATCH` | `/api/v1/users/me` | Cập nhật `displayName` và/hoặc `timeZone` |
| `GET` | `/api/v1/users/me/settings` | Tải profile, preferences và notification settings trong một request khi mở Settings |
| `GET` | `/api/v1/users/me/preferences` | Lấy preferences riêng |
| `PATCH` | `/api/v1/users/me/preferences` | Cập nhật `timeZone` qua endpoint riêng |
| `GET` | `/api/v1/users/me/notification-settings` | Lấy cài đặt thông báo |
| `PATCH` | `/api/v1/users/me/notification-settings` | Cập nhật một phần cài đặt thông báo |
| `DELETE` | `/api/v1/users/me` | Vô hiệu hóa tài khoản sau khi xác nhận mật khẩu |

Endpoint tổng hợp phục vụ UX của màn Settings. Các endpoint riêng vẫn được giữ để Profile và Notifications tải hoặc lưu độc lập.

## Payload

### PATCH `/api/v1/users/me`

```json
{
  "displayName": "Nguyễn Văn A",
  "timeZone": "Asia/Ho_Chi_Minh"
}
```

Ít nhất một trường phải được gửi. `displayName` không được rỗng và dài tối đa 100 ký tự. `timeZone` phải là ID hợp lệ của `java.time.ZoneId`.

### GET `/api/v1/users/me/settings`

```json
{
  "success": true,
  "message": "User settings",
  "data": {
    "profile": {
      "id": "b3d7f6a2-49e0-4c9a-9a3f-12c9a8d7e6f5",
      "username": "nguyenvana",
      "email": "nguyenvana@example.com",
      "displayName": "Nguyễn Văn A",
      "role": "USER",
      "status": "ACTIVE",
      "currencyCode": "VND",
      "timeZone": "Asia/Ho_Chi_Minh",
      "weekStartsOn": 1,
      "createdAt": "2026-11-01T08:30:00Z",
      "updatedAt": "2026-11-03T10:00:00Z"
    },
    "preferences": {
      "userId": "b3d7f6a2-49e0-4c9a-9a3f-12c9a8d7e6f5",
      "displayName": "Nguyễn Văn A",
      "currencyCode": "VND",
      "timeZone": "Asia/Ho_Chi_Minh",
      "weekStartsOn": 1,
      "updatedAt": "2026-11-03T10:00:00Z"
    },
    "notificationSettings": {
      "notificationsEnabled": true,
      "dailyReminderEnabled": true,
      "dailyReminderTime": "20:00:00",
      "budgetAlertEnabled": true,
      "budgetAlertThresholdPercent": 80,
      "budgetExceedAlertEnabled": true,
      "otherNotificationsEnabled": true,
      "updatedAt": "2026-11-03T10:05:00Z"
    }
  },
  "code": 200
}
```

### PATCH `/api/v1/users/me/notification-settings`

```json
{
  "notificationsEnabled": true,
  "dailyReminderEnabled": true,
  "dailyReminderTime": "20:00:00",
  "budgetAlertEnabled": true,
  "budgetAlertThresholdPercent": 80,
  "budgetExceedAlertEnabled": true,
  "otherNotificationsEnabled": true
}
```

Mọi trường đều tùy chọn theo semantics của PATCH. Giá trị không được gửi sẽ được giữ nguyên. `budgetAlertThresholdPercent` phải nằm trong khoảng `1..100`. Khi bật nhắc hằng ngày mà chưa có thời gian, backend dùng `20:00`.

`budgetAlertEnabled` điều khiển cảnh báo sắp chạm ngưỡng. `budgetExceedAlertEnabled` điều khiển cảnh báo đã vượt ngân sách.

## Database migration V6

V6 tạo bảng `fintrack.notification_settings` với các cột:

- `notifications_enabled`
- `daily_reminder_enabled`
- `daily_reminder_time`
- `budget_alert_enabled`
- `budget_alert_threshold_percent`
- `budget_exceed_alert_enabled`
- `other_notifications_enabled`
- `updated_at`

V6 sao chép `notifications_enabled` hiện có từ `user_preferences` sang bảng mới rồi xóa cột cũ, nhờ đó chỉ còn một nguồn dữ liệu cho thông báo. V6 chỉ được sửa trực tiếp vì migration này chưa được áp dụng trên database dùng chung.

## Luồng Android

- Mở màn Settings: gọi `GET /api/v1/users/me/settings` để hiển thị profile và cache dữ liệu cần cho các mục cài đặt.
- Mở Profile: gọi `GET /api/v1/users/me`; nút Lưu gửi một PATCH chứa `displayName` và `timeZone`.
- Email và currency hiển thị read-only.
- Mở Notifications: gọi endpoint notification settings riêng.
- Người dùng chỉnh các công tắc, giờ nhắc và ngưỡng cảnh báo cục bộ trên form; nút Lưu gửi một PATCH duy nhất.
- Không dùng `SharedPreferences` làm nguồn dữ liệu chính cho profile hoặc notification settings.

## Kiểm thử

Backend cần xác nhận:

- endpoint tổng hợp trả đủ ba phần;
- profile PATCH cập nhật đồng thời display name và múi giờ;
- timezone không hợp lệ trả `400 VALIDATION_ERROR`;
- notification PATCH giữ nguyên các trường không được gửi;
- ngưỡng cảnh báo ngoài `1..100` bị validation từ chối;
- người dùng chỉ truy cập dữ liệu của principal hiện tại;
- request không có Bearer token trả `401`.

Lệnh kiểm tra:

```bash
cd backend
mvn test

cd ../android
./gradlew :app:compileDevDebugJavaWithJavac
```
