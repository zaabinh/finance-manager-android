# Báo cáo refactor Android

Nhánh: `feature/android-refactor` · Gốc: `bb0bf71` trên `develop` · Ngày hoàn tất: 2026-11-14
Kế hoạch: [`android_refactor.md`](../../android_refactor.md) · Nhật ký thực thi: [`android_refactor_record.md`](../../android_refactor_record.md) · Tách `FinanceBook`: [`finance-book.md`](finance-book.md)

## 1. Tóm tắt

Cả bảy phase của kế hoạch đã được thực hiện, tích hợp và kiểm tra bằng build và unit test. Ứng dụng vẫn là Java + XML, một module `:app`, không thêm thư viện nào và không nâng phiên bản thư viện nào.

| Chỉ số | Trước (`bb0bf71`) | Sau |
|---|---|---|
| Unit test mỗi flavor (mock, dev) | 106 | 271 |
| Lint `mockDebug` | 0 lỗi, 396 cảnh báo | 0 lỗi, 388 cảnh báo |
| `FinanceBook.java` | 748 dòng | 459 dòng |
| Singleton tĩnh (`ApiClient`, `SessionManager.getInstance`, `FinanceRepository.IO`) | có | không còn |
| CI | chỉ `mockDebug` build + test | mock (kèm lint), dev, staging, prod release (R8) |

Phạm vi thay đổi so với gốc: 214 file trong `android/` và `.github/`, +8634/−3864 dòng.

**Chưa kiểm chứng được:** instrumentation trên thiết bị không dùng làm cổng kiểm được (mục 5), và chưa có lượt thử tay nào trên thiết bị thật cho các luồng đăng nhập, xoay màn hình, tạo nhanh ví/danh mục.

## 2. Các commit trên nhánh

| Commit | Nội dung |
|---|---|
| `0dd5172` | Phase 1–5: container DI, phiên có generation, lỗi có kiểu, model tài chính bất biến, phân trang và vòng đời lưu |
| `6ed65cd` | Merge `origin/develop` (8 commit) vào kiến trúc mới |
| `280b1e5` | Phase 6 – nền móng: báo hết phiên, provider demo chỉ cho mock, bật ViewBinding |
| `3b9a4cd` | Phase 6 – ViewBinding cho toàn bộ màn hình, tách validator form và phép tính Home |
| `81f3932` | Phase 7 – xoá code chết, sửa tên, hằng Intent, test hành vi, CI |
| `0414a89`, `624992f` | Tách `FinanceBook` (nhánh `refactor/finance-book`) và merge |

## 3. Thay đổi theo phase

### Phase 1 — Dựng phụ thuộc
- `FintrackApplication` giữ một `AppContainer`. Đây là nơi duy nhất tạo kho phiên, OkHttp/Retrofit, các `*ApiService`, repository và `AppViewModelFactory`.
- `AppExecutors` có bốn executor: `io()` cho gọi mạng, `serial()` cho mọi truy cập file mock (để file không bị đọc khi đang ghi), `computation()` cho OCR, `main()` để trả kết quả về luồng chính.
- Mọi ViewModel là `ViewModel` thường, nhận phụ thuộc qua constructor nên test JVM truyền được fake.

### Phase 2 — Thao tác bất đồng bộ an toàn theo phiên
- `SessionStore` + `SessionSnapshot` bất biến có `generation`. Đăng nhập và đăng xuất đổi generation; làm mới token thì không. Đăng nhập lại cùng tài khoản vẫn là phiên mới.
- Chỉ còn các thao tác ghi có điều kiện: `updateTokens`, `updateProfile`, `completeSetup`, `clear(expected)`. Kiểm tra và ghi nằm trong cùng một khoá.
- Mỗi request được gắn snapshot của phiên phát ra nó (`SessionCallFactory`). `SessionAuthInterceptor` chặn request của phiên đã kết thúc trước khi gửi; `TokenAuthenticator` không bao giờ thử lại request cũ bằng token của phiên mới.
- Làm mới token: các 401 đồng thời cho cùng một token dùng chung một lần refresh; lỗi mạng, 429 và 5xx giữ nguyên phiên; refresh bị từ chối (401/403) chỉ xoá đúng phiên đó.
- Mọi thao tác ghi phiên (đăng nhập, hồ sơ, đăng xuất, vô hiệu hoá tài khoản, hoàn tất setup) chuyển từ Activity vào `AuthViewModel` / `UserViewModel`. Điều hướng sau đăng nhập là sự kiện một lần (`Event<T>`).

### Phase 3 — Lỗi có kiểu và kết quả repository
- `AppError` gồm loại lỗi, mã HTTP, mã backend, lỗi theo trường, `shortfallMinor` và nguyên nhân kỹ thuật.
- `HttpErrorParser` đọc thân lỗi đúng một lần và không phụ thuộc `Context`. `ErrorMapper` chỉ lo dịch sang chuỗi tài nguyên.
- Repository trả `RequestHandle` (huỷ được) và giao `Resource<T>` qua callback; không còn nhận hay sửa `MutableLiveData` của ViewModel.
- Bỏ điều khiển luồng bằng chuỗi hiển thị: `AUTH_EMAIL_NOT_VERIFIED` giờ là mã lỗi, không còn `valueOf(resource.getMessage())`.

### Phase 4 — Model chuẩn và tách trách nhiệm domain
- Một bộ thực thể bất biến trong `finance.domain`: `Wallet`, `Category`, `Transaction`, `Budget`, `MonthlyBudget`, `FinanceSnapshot`. Các bản trùng trong `*/model` đã xoá.
- `FinanceBook` vẫn là ranh giới ghi sổ duy nhất và không còn lộ trạng thái khả biến. Luật được tách ra: `LedgerCalculations`, `BudgetPolicy`, `TransactionQueryEngine`, `TransactionPolicy`, `LedgerRestorer`.
- File mock có DTO riêng (`FinanceFileDto`, `FinanceFileCodec`): giữ tên file theo người dùng, tên thuộc tính JSON, phiên bản 1, nâng cấp `includeInAssets` và ngân sách tháng cũ, ghi bằng `AtomicFile`. File hỏng báo lỗi lưu trữ chứ không bị thay bằng dữ liệu demo.

### Phase 5 — Snapshot, phân trang và vòng đời lưu
- `TransactionListViewModel` giữ truy vấn, generation của request, các dòng, trang và lỗi tải thêm. Phản hồi của bộ lọc cũ bị bỏ; trang chỉ tăng khi tải thành công; tải thêm lỗi thì giữ nguyên các dòng và thử lại được.
- `FinanceViewModel` nhận scope và id chi tiết ngay khi tạo, có trạng thái tải riêng cho đọc và lưu.
- `MutationReceipt` mang id của thực thể vừa tạo; tạo nhanh ví/danh mục chọn đúng id đó thay vì so hai danh sách.
- Lưu thành công được báo một lần, tách khỏi lần làm mới sau đó. Làm mới lỗi là lỗi đọc, không biến lần lưu thành thất bại. Thao tác ghi không tự thử lại.

### Merge `origin/develop`
`develop` có thêm 8 commit sau gốc. Chúng được chuyển sang kiến trúc mới:
- Phản hồi 201/202 rỗng của đăng ký, gửi lại mã, quên mật khẩu là thành công (`ApiCalls.enqueueBody`).
- Tóm tắt Home từ `GET /api/v1/dashboard` nằm sau `DashboardRepository` và `DashboardViewModel`.
- "Ghi nhớ tôi" chuyển từ `SessionManager` (đã xoá) sang `SessionSnapshot.rememberMe`; giữ khoá lưu trữ `remember_me`. Phiên không được ghi nhớ kết thúc ở lần mở ứng dụng kế tiếp.
- Ví trong bước setup được tạo trên server.

### Phase 6 — Dọn UI và cô lập dữ liệu demo
- 15 màn hình auth/setup/settings/OCR, toàn bộ màn hình, sheet và adapter của ví/danh mục/ngân sách/giao dịch, cùng Home/Analytics/Thông báo đã chuyển sang ViewBinding. `FinanceActivity` nhận view đã bind (`contentView()`) và bỏ các helper theo id.
- Logic rời Activity sang Java thuần có test: `TransactionFormValidator`, `BudgetFormValidator`, `HomeSummary`.
- `AnalyticsProvider` và `NotificationProvider` do `AppContainer` cấp: dữ liệu demo chỉ có ở flavor mock; dev, staging và prod nhận `null` và hiển thị trạng thái "chưa kết nối". Nút "đánh dấu đã đọc" bị ẩn ở flavor thật; lối vào cài đặt thông báo vẫn còn.
- Trạng thái đã đọc của thông báo demo lưu theo từng tài khoản và từng id. Cờ toàn cục cũ không được chuyển sang.
- `SessionExpiry` là helper chung để chuyển về màn hình đăng nhập khi hết phiên.
- Bản nháp và lựa chọn sống qua xoay màn hình ở setup, settings, Home và Analytics. Mật khẩu không được lưu xuống đĩa.

### Phase 7 — Dọn dẹp và CI
- Xoá: `changePassword` và `resendOtp` không ai gọi, bản chuyển đổi `FinanceFailure` trùng lặp, `FakeCall`, overload `login` chỉ test dùng, `FinanceViewModel.busy()`, lần kiểm tra file mock thứ hai mỗi lần đọc, năm chuỗi mẫu không còn dùng.
- Sửa tên: tham số `phone` thành `timeZone`; `FinanceUi.money(long amount)`; mỗi Intent extra và khoá saved-state chỉ định nghĩa một lần (giá trị không đổi).
- Chuỗi hiển thị còn viết cứng trong Java chuyển vào `strings_*.xml`.
- Flexbox vào version catalog, vẫn phiên bản 3.0.0.
- Test giữ chỗ được thay bằng test hành vi: `BudgetCalculationTest`, `ErrorMapperTest`, `FinanceErrorsTest`.
- CI chạy: mock (build, unit test, lint), dev (build, unit test), staging (build), prod release với R8. Bản release không có `signingConfig` nên không cần secret.

## 4. Thay đổi hành vi cần biết

| Thay đổi | Lý do |
|---|---|
| Đăng nhập lại cùng tài khoản là phiên mới; ViewModel, request và 401 của phiên cũ không còn tác dụng | Yêu cầu Phase 2 |
| Hết phiên khi đang ở màn hình tài chính hoặc Home sẽ chuyển ngay về đăng nhập | Sửa hồi quy phát hiện khi review Phase 1–5: kết quả 401 bị bỏ nên màn hình treo ở trạng thái tải |
| Cờ "đã hoàn tất setup" được ghi khi server xác nhận, không chỉ khi bấm nút cuối | Ghi phiên rời Activity |
| Kết quả đăng xuất, vô hiệu hoá, gửi lại mã, quên mật khẩu là sự kiện một lần | Xoay màn hình không lặp toast, không chạy lại bộ đếm 60 giây |
| Analytics và Thông báo ở dev/staging/prod hiện "chưa kết nối" thay cho số liệu demo | Không hiển thị số liệu bịa ở flavor thật |
| Home hiện "–" cho đến khi có dữ liệu; tiêu đề ngân sách là "Ngân sách tháng này"; chấm đỏ ở chuông chỉ có ở mock | Layout cũ có số liệu mẫu viết cứng hiện ra trước khi dữ liệu về |
| Ở flavor mock, thông báo demo trở lại trạng thái chưa đọc một lần | Trạng thái đã đọc giờ lưu theo tài khoản |
| Thông báo lỗi dùng chuỗi tiếng Việt theo mã lỗi, không còn hiện nguyên văn message của server | Yêu cầu Phase 3 |

## 5. Kiểm thử

### Đã chạy và qua (cây cuối `624992f`, từ `android/`)
`./gradlew :app:assembleMockDebug :app:testMockDebugUnitTest :app:lintMockDebug :app:assembleDevDebug :app:testDevDebugUnitTest :app:assembleStagingDebug :app:assembleProdRelease :app:assembleMockDebugAndroidTest`

| Hạng mục | Kết quả |
|---|---|
| `testMockDebugUnitTest`, `testDevDebugUnitTest` | 271 test mỗi flavor, 0 lỗi, 0 bỏ qua |
| `assembleMockDebug`, `assembleDevDebug`, `assembleStagingDebug` | qua |
| `assembleProdRelease` (R8) | qua |
| `lintMockDebug` | 0 lỗi, 388 cảnh báo |
| `assembleMockDebugAndroidTest` | qua (chỉ đóng gói APK test) |

Kiểm tra đột biến ở Phase 2: bỏ phép so sánh generation trong `TokenAuthenticator` làm 2 test hỏng; cho `login` dùng lại generation làm 5 test hỏng.

### Kịch bản hồi quy bắt buộc và test tương ứng

| Kịch bản | Test |
|---|---|
| Callback refresh/hồ sơ cũ sau đăng xuất hoặc đổi tài khoản | `TokenAuthenticatorTest`, `AuthViewModelTest`, `UserViewModelTest` |
| Đăng nhập lại cùng tài khoản khi request cũ đang chạy | `SessionStoreTest`, `TokenAuthenticatorTest` |
| 401 đồng thời; refresh lỗi mạng, 429, 5xx | `TokenAuthenticatorTest` |
| Thu, chi, chuyển khoản, lưu trữ, huỷ | `FinanceBookTest`, `FinanceBookRefactorContractTest` |
| Hạn mức đúng bằng, vượt, thiếu hụt, giới hạn kỳ | `BudgetCalculationTest`, `MonthlyBudgetBookTest` |
| Tràn số và ranh giới múi giờ | `FinanceBookTest`, `FinanceBookRefactorContractTest`, `HomeSummaryTest` |
| Nạp JSON mock cũ, ghi lỗi không làm hỏng file | test trong `finance/data` |
| Đổi bộ lọc khi request đang chạy; tải thêm lỗi rồi thử lại | `TransactionListViewModelTest` |
| Lưu thành công rồi làm mới lỗi; sự kiện một lần | `FinanceViewModelTest` |
| Id tạo nhanh chính xác | `FinanceViewModelTest` (receipt), source review ở Activity |
| Cô lập demo theo flavor | `DemoProvidersTest` (chạy ở cả mock và dev), `DemoNotificationProviderTest` |
| Hết phiên khi đang đọc/lưu | `FinanceViewModelTest`, `TransactionListViewModelTest`, `DashboardViewModelTest` |

### Instrumentation
Chạy `:app:connectedMockDebugAndroidTest` trên emulator duy nhất của máy (`Pixel_8`, API 37) với cây cuối: 26 test, 13 qua, 13 hỏng.

Bộ test này không dùng làm cổng kiểm được vì nó đã hỏng trên commit gốc trước khi refactor: 11/18 test hỏng ở lượt chạy đầy đủ, và 4/5 test đăng nhập hỏng ở lượt chạy riêng. 13 test hỏng ở cây cuối đều nằm trong số đã từng hỏng trên commit gốc, cùng loại nguyên nhân:
- emulator không lấy được window focus (`RootViewWithoutFocusException`);
- test tham chiếu view hoặc văn bản không còn khớp với giao diện hiện tại;
- `LayoutRenderTest` hỏng vì layout `activity_receipt_import` không còn tồn tại.

13 test qua là các test không cần thao tác trên giao diện: `EncryptedSessionStorageTest` (4, gồm lưu/đọc "Ghi nhớ tôi" trên kho mã hoá thật), `MockFinanceStoreTest` (4), `FinancePersistenceTest` (3), `ReceiptRecognitionTest` (1) và `LoginVerificationTest.resendErrorStaysOnLogin` (1). Không có test nào từng qua ở commit gốc mà nay hỏng.

Hệ quả: các luồng giao diện (`FinanceFlowTest` 0/6, `HomeDashboardActivityTest` 0/2, `LoginActivityTest` 0/2) không có bằng chứng tự động nào trên thiết bị, cả trước lẫn sau refactor.

### Chưa chạy
- Instrumentation trên API 24 và API 36: máy không có system image tương ứng.
- Thử tay trên thiết bị: đăng nhập → đăng xuất → đăng nhập lại cùng tài khoản; xoay màn hình ở form giao dịch và màn hình xác thực email; tạo nhanh ví/danh mục; "Ghi nhớ tôi".
- Smoke test với backend staging.

## 6. Việc còn lại và rủi ro

- **`findViewById` còn 48 chỗ trong 15 file.** Lý do: view nằm trong thẻ `<include>` không có `android:id` (ViewBinding không truy cập được), `FabSpeedDialHelper` (layout speed-dial được include vào bốn layout khác nhau), `FinanceUi.navigation(Activity, int)`, `android.R.id.content`, và `SheetDialog`. Bỏ hết cần thêm id cho sáu nhóm include, tức là sửa layout.
- **Sheet tạo nhanh ví/danh mục đóng khi xoay màn hình** trong khi cờ chờ vẫn được giữ. Có từ trước refactor, chưa sửa.
- **`LoginVerificationTest.resendSuccessOpensVerificationWithEmail`** đã hỏng trên commit gốc và có thể vẫn hỏng.
- **Bản sao layout trong `androidTest/assets/layouts/`** đã lỗi thời từ trước (`LayoutRenderTest` hỏng ở commit gốc vì `activity_receipt_import`).
- **`WalletCalculationTest.testWalletCreation`** chủ yếu đọc lại trường của constructor; giữ nguyên vì là test domain.
- **Đường nối `AppContainer`** (thứ tự interceptor, gắn authenticator) chưa có test đầu-cuối; từng thành phần được test riêng.
- **`develop` ở máy** đang có commit `0dd5172` chưa push; nhánh `feature/android-refactor` đã chứa commit này.

## 7. Thay đổi có sẵn của người dùng

Mọi việc từ lúc merge `origin/develop` được làm trong worktree `.worktrees/android-refactor`, nên checkout chính không bị đụng tới. Các thay đổi chưa commit ở đó vẫn nguyên: `.gitignore` (thêm `secret.txt`), `android/app/build.gradle.kts` (hai `BASE_URL`, trùng với nội dung đã có trên `origin/develop`), và các file chưa track (`AGENTS.md`, `ORCHESTRATOR.md`, `UI_FIXING.md`, `Fable52.md`, `final_report.md`, `docs/deployment/*`). Không file nào trong số này nằm trong commit của nhánh.

## 8. Phân công

| Việc | Thực hiện |
|---|---|
| Điều phối, chốt contract, review, cổng kiểm tra, commit | Phiên chính (Fable) |
| Phase 1, Phase 2, merge `origin/develop` | Subagent tích hợp |
| Phase 3–5 và tách `FinanceBook` | Các phiên khác của người dùng (xem nhật ký thực thi) |
| Nền móng Phase 6, Phase 7 | Subagent Opus |
| Ba nhóm ViewBinding của Phase 6 | Ba subagent Sonnet chạy song song, mỗi nhóm một tập file riêng |
