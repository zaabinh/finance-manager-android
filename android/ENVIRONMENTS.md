# Android Environments Configuration

Tài liệu hướng dẫn cấu hình và sử dụng các môi trường (Environments) và Product Flavors trong ứng dụng Android FinTrack.

---

## 1. Khái niệm Product Flavor vs Build Type

Trong Android Gradle Plugin, một **Build Variant** được kết hợp từ **Product Flavor** và **Build Type**:

$$\text{Build Variant} = \text{Product Flavor} + \text{Build Type}$$

### Product Flavor (Môi trường & Chế độ dữ liệu)
Định nghĩa **môi trường vận hành / backend API** hoặc **chế độ giả lập (Mock)** mà ứng dụng kết nối tới, bao gồm cấu hình Application ID riêng biệt để có thể cài đặt song song trên cùng thiết bị:

| Product Flavor | Application ID | `BuildConfig.USE_MOCK` | `BuildConfig.BASE_URL` | Mục đích |
|---|---|---|---|---|
| **`mock`** | `com.example.fintrack.mock` | `true` | `http://10.0.2.2:8080/` | Kiểm thử offline với `MockInterceptor` (không cần Backend thật) |
| **`dev`** | `com.example.fintrack.dev` | `false` | `http://10.0.2.2:8080/` | Phát triển local kết nối Android Emulator với Backend dev |
| **`staging`** | `com.example.fintrack.staging` | `false` | `https://api-staging.example.com/` | Kiểm thử tích hợp môi trường Staging |
| **`prod`** | `com.example.fintrack` | `false` | `https://api.example.com/` | Bản phát hành chính thức người dùng cuối |

### Build Type (Cấu hình biên dịch)
Định nghĩa **chế độ đóng gói / biên dịch** ứng dụng:

- **`debug`**: Bật HTTP logging, hỗ trợ đính kèm debugger, không mã hóa/tối ưu hóa bytecode.
- **`release`**: Tắt HTTP logging, tối ưu hiệu năng và kích thước ứng dụng (`isMinifyEnabled = true`).

---

## 2. Các Variant khả dụng

Hệ thống hỗ trợ 8 Build Variants chính:

1. **`mockDebug`**: Dùng cho lập trình viên test toàn bộ luồng app (Auth, Setup, Home, Setting) hoàn toàn offline với dữ liệu giả lập (`USE_MOCK = true`).
2. **`mockRelease`**: Build release với mock data.
3. **`devDebug`**: Phát triển tính năng kết nối backend local emulator (`http://10.0.2.2:8080/`).
4. **`devRelease`**: Kiểm tra bản build release với backend local.
5. **`stagingDebug`**: Kiểm thử kết nối tới server Staging có bật log debug.
6. **`stagingRelease`**: Kiểm thử bản build gần giống Production kết nối tới server Staging.
7. **`prodDebug`**: Debug trên môi trường Production (nếu cần thiết).
8. **`prodRelease`**: Bản phát hành chính thức tới người dùng trên Google Play.

---

## 3. Network Security Configuration & HTTPS

- **Môi trường `mock` & `dev`**: Cho phép giao tiếp HTTP cleartext duy nhất tới địa chỉ Android Emulator (`10.0.2.2`).
- **Môi trường `staging` & `prod`**: Bắt buộc sử dụng HTTPS mã hóa SSL/TLS, tuân thủ chính sách bảo mật mạng. Không cho phép cleartext HTTP.
- **Toàn bộ ứng dụng**: Sử dụng EncryptedSharedPreferences (`SessionManager`) để bảo mật token và dữ liệu phiên làm việc.

---

## 4. Hướng dẫn chọn Build Variant trong Android Studio

1. Mở cửa sổ **Build Variants** trong Android Studio (Menu: **View** -> **Tool Windows** -> **Build Variants** hoặc click tab **Build Variants** ở góc dưới bên trái).
2. Tại dòng của module `:app`, chọn variant mong muốn (ví dụ: `mockDebug` để test nhanh hoặc `devDebug` để kết nối API thật).
3. Android Studio sẽ tự động Sync context, cập nhật `BuildConfig.USE_MOCK`, `BuildConfig.BASE_URL` và Application ID tương ứng.

---

## 5. Hướng dẫn build ứng dụng bằng Gradle CLI

Mở terminal tại thư mục gốc `android/`:

### Linux / macOS
```bash
# Build mockDebug APK (Khuyên dùng để test không cần server)
./gradlew assembleMockDebug

# Build devDebug APK
./gradlew assembleDevDebug

# Build stagingDebug APK
./gradlew assembleStagingDebug

# Build prodRelease APK
./gradlew assembleProdRelease
```

### Windows (PowerShell / CMD)
```powershell
# Build mockDebug APK (Khuyên dùng để test không cần server)
.\gradlew.bat assembleMockDebug

# Build devDebug APK
.\gradlew.bat assembleDevDebug

# Build stagingDebug APK
.\gradlew.bat assembleStagingDebug

# Build prodRelease APK
.\gradlew.bat assembleProdRelease
```

File APK sau khi build thành công sẽ nằm trong thư mục:
`app/build/outputs/apk/<flavor>/<buildType>/`

---

## 6. Lưu ý về Release Signing

Hiện tại cấu hình `release` sử dụng cài đặt mặc định của Android Studio. Để tạo bản release APK/AAB chính thức sẵn sàng tải lên Google Play:
- Cần bổ sung khối `signingConfigs` trong `app/build.gradle.kts`.
- Tuyệt đối **không commit keystore file (`.jks`/`.keystore`) hoặc secret passwords** vào Git repository.
