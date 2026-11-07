# Execution plan: Wallet, Category, Budget, Transaction

Ngày 07/11/2026, feature branch `feature/orchestrator`. Đọc đầy đủ bốn CRUD plans và [contract](../api/FINANCE_CRUD_CONTRACT.md) trước implementation. Không merge/push develop.

## Kết quả kiểm tra consistency

| Vấn đề | Quyết định chung |
|---|---|
| Hai nguồn Android (management singleton/sample và finance API) | Màn management từng module chuyển về Repository/ViewModel gọi cùng Retrofit backend; mock chỉ ở mock flavor. Không fallback record đầu tiên/ID giả trong flavor thật |
| Data model/API | Giữ DTO trực tiếp của 4 module, integer VND, UUID/UTC/user timezone, archivedAt cho Wallet/Category/Budget, POSTED/VOIDED cho Transaction; không đổi contract |
| Cross-module references | Budget/Transaction dùng một FinanceSupport backend để lookup/lock scoped wallet/category, balance view và user timezone/threshold. Không service nào sửa dữ liệu thuộc module khác |
| Lock order | Existing transaction/budget row → wallets UUID tăng dần → categories UUID tăng dần; đọc balance bằng statement mới sau wallet lock. Không gọi ngược Wallet/Category service để ghi |
| Repository/service interfaces | Mỗi module có controller/service/repository/DTO riêng theo package hiện có. Shared FinanceSupport chỉ đọc reference/balance/preferences và cung cấp validation/locks; interfaces ghi vẫn ở repository module |
| Navigation | Giữ Activity/routes/layout đã có; management list → add/detail/edit → archive/restore, form dùng ID thật. History/detail đọc archived names; create picker chỉ active. Không khai báo màn mới trùng chức năng |
| Shared UI | Tiếp tục dùng FinanceActivity, FinanceUi, Material dialogs, Resource và LiveData. Root sở hữu thay đổi shared files; subagents không tự fork base class/picker/error handler |
| Loading dependencies | FinanceActivity cung cấp scope REFERENCES/BUDGETS/ALL; form transaction dùng REFERENCES, Budget dùng BUDGETS; history Transaction dùng server query/page riêng. Không bắt Wallet/Category CRUD chờ budgets hoặc transaction API |
| Partial ledger | Domain có serverBalanceMinor cho Wallet, serverSpentMinor/threshold cho Budget; khi server trả số liệu thì không cộng transaction page để thay thế. Mock vẫn tính trên ledger đầy đủ |
| Soft delete/no-negative | Expense void hoàn tiền một lần; income void có thể bị từ chối409. Mọi writer financial/opening dùng cùng wallet lock; archive Wallet/Category/Budget không reverse transaction |
| Seed | Category agent sở hữu seed service/marker migration; root nối seed vào AuthService registration sau khi interface ổn định. Không tạo category trên mỗi GET |
| Deferred features | TRANSFER/cache/push/notification delivery/field không có contract không được lén triển khai hoặc báo đã lưu. Scope hiện tại là CRUD thu/chi và Budget usage |

Không còn mâu thuẫn kiến trúc chưa được giải quyết trước khi giao implementation. Các thiếu hụt hiện tại là implementation gaps phải sửa, không phải API còn cần quyết định.

## Shared interfaces cố định trước khi giao việc

Backend `com.fintrack.api.service.finance.FinanceSupport` do root tạo và sở hữu:

```java
record WalletRef(UUID id, long openingBalanceMinor, boolean active) {}
record CategoryRef(UUID id, String categoryType, boolean active) {}
WalletRef wallet(UUID userId, UUID walletId, boolean lock);
CategoryRef category(UUID userId, UUID categoryId, boolean lock);
long balance(UUID userId, UUID walletId);
ZoneId zone(UUID userId);
int warningThreshold(UUID userId);
```

Lookup khác owner/absent trả404. Validation helpers và deserializers tiền/sort strict ở package finance; module không sửa ObjectMapper toàn ứng dụng. Root thêm finance error codes và scope error handling; không thay Auth/Profile response conventions.

Android shared model additions do root sở hữu:

- FinanceBook.Wallet: archivedAt, includeInAssets (default true), serverBalanceMinor (Long nullable).
- FinanceBook.Category: archivedAt, iconKey, colorHex, sortOrder.
- FinanceBook.Budget: serverSpentMinor (Long nullable), warningThresholdPercent (default80).
- FinanceBook.State: timeZone (default Asia/Ho_Chi_Minh); book.monthAt(timestamp) dùng instance timezone.
- FinanceActivity.readScope() trả FinanceRepository.Scope; default REFERENCES. Budget override BUDGETS; Transaction list/detail có API query riêng khi cần. Existing method signatures được giữ khi còn caller.
- Root giữ FinanceRepository/FinanceViewModel/FinanceAction/FinanceUi/FinanceActivity/FinanceBook/MockFinanceStore/core network/session/resources shared. Module agents chỉ thay package/module UI/DTO/resources riêng và báo nhu cầu thay shared files.

## Phân công và thứ tự

Giới hạn runtime có 3 subagents chạy cùng lúc ngoài root. Dùng 4 agent theo hai đợt; Agent4 bắt đầu khi Agent1/2 đã giao interfaces và core CRUD compile/test. Không cần worktree vì các path sở hữu tách biệt; không agent nào được reset/revert thay đổi của người khác hoặc sửa file shared. Nếu xuất hiện nhu cầu sửa cùng file, root làm phần đó và thông báo interfaces trước.

| Agent | Sở hữu | Dependency/gate |
|---|---|---|
| 1 Wallet | Backend Wallet* controller/repository/service/DTO/tests; migration V7; Android app/wallet và layout/strings_wallet | Shared interface root; ưu tiên đợt1 |
| 2 Category | Backend Category* + CategorySeedService/DTO/tests; migration V8; Android app/category và layout/strings_category | Shared root; ưu tiên đợt1; seed hook do root nối |
| 3 Budget | Backend Budget* controller/repository/service/DTO/tests; Android app/budget và layout/strings_budget | Sau interface Category đã cố định; có thể làm độc lập bằng FinanceSupport và ledger fixtures; nghiệm thu integration sau Category |
| 4 Transaction | Backend Transaction* controller/repository/service/DTO/tests; migration V9 fingerprint; Android app/transaction và layout/strings_transaction | Wallet/Category implementation gate trước khi bắt đầu, shared API đã cố định |
| Root | Shared finance/core files, Auth seed hook, migration/test orchestration, integration tests, diff review, full verification/report | Không thay contract tùy tiện; phối hợp lỗi compile/shared API |

Trước khi sửa, mỗi agent tìm/đọc AGENTS.md áp dụng và plan module + contract + execution plan; nếu không có AGENTS.md thì ghi rõ. Không tìm thấy AGENTS.md trong repo hoặc các thư mục cha đã kiểm tra ở lần phân công này. Không agent nào commit/merge/push; tất cả sửa trên feature branch hiện tại.

## Quy trình giao/nhận

1. Root tạo shared backend interfaces/error codes/strict JSON helpers và ổn định Android field/scope signatures.
2. Wallet/Category triển khai theo path ownership; Budget dùng Category reference contract chung, không sửa Category service. Agents thêm unit/API/DB tests phù hợp, chạy compile/test liên quan; không chạy nhiều build cùng output directory đồng thời, phối hợp root khi cần chạy full build.
3. Root review Wallet/Category prerequisites, nối registration seed, xử lý compile/regression. Khi đạt gate thì chạy Agent4 Transaction.
4. Root tích hợp navigation, picker active/history, server balances/usage/timezone/session, loại budget dependency; review toàn bộ diff, xử lý inconsistency.
5. Chạy backend unit/API suite, PostgreSQL integration/concurrency khi runtime khả dụng, Android dev/mock unit/build/lint và device tests khả thi. Không đánh dấu skipped/environment-blocked là pass.
6. Ghi IMPLEMENTATION_REPORT.md: kết quả thực tế, architecture cuối, file chính, commands/pass/fail/blocked, technical debt và việc Android developer cần tiếp tục. Chưa đạt test nào phải nói rõ, không gọi compile là end-to-end verification.
