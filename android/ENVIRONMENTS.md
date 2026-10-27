# Android Environments Configuration

Tài liệu hướng dẫn cấu hình và sử dụng các môi trường (Environments) trong ứng dụng Android Fintrack.

---

## 1. Khái niệm Product Flavor vs Build Type

Trong Android Gradle Plugin, một **Build Variant** được kết hợp từ **Product Flavor** và **Build Type**:

$$\text{Build Variant} = \text{Product Flavor} + \text{Build Type}$$

### Product Flavor (Môi trường)
Định nghĩa **môi trường vận hành / backend API** mà ứng dụng kết nối tới, bao gồm cấu hình Application ID riêng biệt để có thể cài đặt song song trên cùng thiết bị:

| Product Flavor | Application ID | `BuildConfig.BASE_URL` | Mục đích |
|---|---|---|---|
| **`dev`** | `com.example.fintrack.dev` | `http://10.0.2.2:8080/` | Phát triển local với Android Emulator |
| **`staging`** | `com.example.fintrack.staging` | `https://api-staging.example.com/` *(placeholder)* | Kiểm thử tích hợp môi trường Staging |
| **`production`** | `com.example.fintrack` | `https://api.example.com/` *(placeholder)* | Bản phát hành chính thức người dùng cuối |

> [!NOTE]
> Các domain API của `staging` và `production` hiện tại là **placeholder** và sẽ được cập nhật khi các môi trường backend chính thức được bàn giao.

### Build Type (Cấu hình biên dịch)
Định nghĩa **chế độ đóng gói / biên dịch** ứng dụng:

- **`debug`**: Bật HTTP logging, hỗ trợ đính kèm debugger, không mã hóa/tối ưu hóa bytecode.
- **`release`**: Tắt HTTP logging, tối ưu hiệu năng và kích thước ứng dụng.

---

## 2. Các Variant khả dụng

Hệ thống hỗ trợ 6 Build Variants chính:

1. **`devDebug`**: Dùng cho lập trình viên phát triển tính năng local với emulator (`http://10.0.2.2:8080/`).
2. **`devRelease`**: Kiểm tra bản build release với backend local.
3. **`stagingDebug`**: Dùng để kiểm thử ứng dụng kết nối tới server Staging có bật log debug.
4. **`stagingRelease`**: Kiểm thử bản build gần giống Production kết nối tới server Staging.
5. **`productionDebug`**: Debug trên môi trường Production (nếu cần thiết).
6. **`productionRelease`**: Bản phát hành chính thức tới người dùng.

---

## 3. Network Security Configuration & HTTPS

- **Môi trường `dev`**: Cho phép giao tiếp HTTP cleartext duy nhất tới địa chỉ Android Emulator (`10.0.2.2`) thông qua Resource Overlay (`src/dev/res/xml/network_security_config.xml`).
- **Môi trường `staging` & `production`**: Bắt buộc sử dụng HTTPS mã hóa SSL/TLS, tuân thủ `src/main/res/xml/network_security_config.xml`. Không cho phép cleartext HTTP và không chấp nhận User-installed CA certificate theo mặc định.
- **Toàn bộ ứng dụng**: Không bật `android:usesCleartextTraffic="true"` ở cấp độ ứng dụng.

---

## 4. Hướng dẫn chọn Build Variant trong Android Studio

1. Mở cửa sổ **Build Variants** trong Android Studio (Menu: **View** -> **Tool Windows** -> **Build Variants** hoặc click tab **Build Variants** ở góc dưới bên trái).
2. Tại dòng của module `:app`, chọn variant mong muốn (ví dụ: `devDebug` hoặc `stagingDebug`).
3. Android Studio sẽ tự động Sync context, cập nhật `BuildConfig.BASE_URL` và Application ID tương ứng.

---

## 5. Hướng dẫn build ứng dụng bằng Gradle CLI

Mở terminal tại thư mục gốc `android/`:

### Linux / macOS
```bash
# Build devDebug APK
./gradlew assembleDevDebug

# Build stagingDebug APK
./gradlew assembleStagingDebug

# Build stagingRelease APK
./gradlew assembleStagingRelease

# Build productionRelease APK
./gradlew assembleProductionRelease
```

### Windows (PowerShell / CMD)
```powershell
# Build devDebug APK
.\gradlew.bat assembleDevDebug

# Build stagingDebug APK
.\gradlew.bat assembleStagingDebug

# Build stagingRelease APK
.\gradlew.bat assembleStagingRelease

# Build productionRelease APK
.\gradlew.bat assembleProductionRelease
```

File APK sau khi build thành công sẽ nằm trong thư mục:
`app/build/outputs/apk/<flavor>/<buildType>/`

---

## 6. Lưu ý về Release Signing

Hiện tại cấu hình `release` sử dụng cài đặt mặc định của Android Studio. Để tạo bản release APK/AAB chính thức sẵn sàng tải lên Google Play:
- Cần bổ sung khối `signingConfigs` trong `app/build.gradle.kts`.
- Tuyệt đối **không commit keystore file (`.jks`/`.keystore`) hoặc secret passwords** vào Git repository.
