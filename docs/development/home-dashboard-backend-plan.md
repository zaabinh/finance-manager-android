# Kế hoạch backend Home Dashboard

Ngày 08/11/2026. Vai trò Explorer theo [AGENTS.md](../../AGENTS.md). Đây là kế hoạch và API **đề xuất**, chưa triển khai dashboard endpoint. Không thay đổi contract CRUD đang thực hiện trong [execution plan](finance-execution-plan.md).

## 1. Căn cứ, hiện trạng và phạm vi

Đã đối chiếu [Wallet](wallet-crud-plan.md), [Category](category-crud-plan.md), [Budget](budget-crud-plan.md), [Transaction](transaction-implementation-plan.md), [contract tài chính](../api/FINANCE_CRUD_CONTRACT.md), `HomeDashboardActivity`, `FinanceRepository`, `BudgetRepository` và `NotificationActivity`. Những câu “backend chưa có CRUD” trong các plan gốc là hiện trạng lúc lập tài liệu; dùng code và implementation report để xác nhận tiến độ hiện nay.

- `HomeDashboardActivity` đang lấy profile và FinanceBook riêng; tài sản dùng số dư server, nhưng trạng thái budget trên Home suy ra từ tổng spent/limit rồi cap phần trăm ở 100. Cách đó có thể che một danh mục vượt hạn mức khi các danh mục khác chi ít, không thể hiện WARNING đúng threshold.
- Layout hiện có tài sản/khả dụng/tiết kiệm, budget và thao tác nhanh. Chưa có API dashboard, tổng thu/chi tháng, giao dịch gần đây hoặc unread count server. Các section mới cần Android developer nối dữ liệu, không giả định đã có view tương ứng.
- Phạm vi backend: một read API tổng hợp theo user, số dư hiện tại, dòng tiền tháng được chọn, budget summary và giao dịch gần đây. Unread count tích hợp sau Notifications; không chặn nghiệm thu phần tài chính.
- Không thêm cache số dư, bảng dashboard materialized, sửa ledger, chuyển ví, dự báo, OCR, recurring hoặc analytics nhiều kỳ. Quick actions vẫn điều hướng màn hiện có.

## 2. Requirements và quy tắc tính

| ID | Yêu cầu | Điều kiện nghiệm thu |
|---|---|---|
| HOME-01 | Ownership và session | Tất cả query có user_id từ principal; không nhận owner từ client; không lộ user khác |
| HOME-02 | Một snapshot có thẩm quyền | Các số tổng, tên references, budget và recent transactions thuộc cùng DB snapshot; không cộng từ transaction pages trên Android |
| HOME-03 | Tách tiền hiện tại và dòng tiền tháng | Đổi month chỉ đổi monthlyFlow/budget/recent; không biến assets thành “số dư cuối tháng” |
| HOME-04 | Tài sản đúng Wallet contract | Chỉ wallet active, includeInAssets=true; số dư từ wallet_balances, không cache |
| HOME-05 | Dòng tiền đủ ledger | Chỉ POSTED INCOME/EXPENSE trong tháng theo timezone user; gồm ví archived và excluded assets; VOIDED không tính |
| HOME-06 | Budget không che rủi ro | Trạng thái summary lấy mức nghiêm trọng nhất từng budget; tổng progress chỉ để vẽ, không quyết định status |
| HOME-07 | Dữ liệu rỗng thật | User chưa có ví/giao dịch/ngân sách nhận số 0 và array rỗng; no-budget phân biệt SAFE |
| HOME-08 | Đọc hiệu quả | Không N+1, không gọi HTTP vòng về CRUD, không tải toàn ledger vào Java; giới hạn recent cố định tối đa 5 |
| HOME-09 | Notifications độc lập | Khi module inbox chưa triển khai, unreadCount=null với availability rõ ràng, không giả số 0 |

Các công thức bắt buộc:

```text
includedWallets = wallets archived_at IS NULL AND include_in_assets = true
assets.totalMinor     = SUM(balance_minor của includedWallets)
assets.savingsMinor   = SUM(balance_minor của includedWallets có wallet_type=SAVINGS)
assets.availableMinor = totalMinor - savingsMinor
monthlyFlow.incomeMinor  = SUM(amount_minor INCOME POSTED trong tháng)
monthlyFlow.expenseMinor = SUM(amount_minor EXPENSE POSTED trong tháng)
monthlyFlow.netSavingsMinor = incomeMinor - expenseMinor
```

`availableMinor` nghĩa là số dư các ví CASH/BANK được tính vào tài sản, không trừ ngân sách còn lại. `netSavingsMinor` là chênh lệch thu/chi tháng, **có thể âm**; khác số dư ví SAVINGS hiện tại và không vi phạm quy tắc ví không âm. Opening balance không phải khoản thu trong tháng. TRANSFER/ADJUSTMENT và các loại legacy khác không tính vào thu/chi, nhưng phần đóng góp POSTED hợp lệ của chúng vẫn nằm trong view số dư ví. Không double count transaction bằng cách join các entries rồi SUM amount.

Month chuẩn YYYY-MM, năm 0001–9998 như Budget; mặc định tháng hiện tại tại IANA timezone preferences của user. Chuyển `[đầu tháng local, đầu tháng kế tiếp local)` sang UTC; hỗ trợ DST. Tất cả timestamp response UTC, tiền integer VND. Tổng dùng NUMERIC/BigInteger và kiểm tra signed64, không dùng double.

Budget summary chỉ gồm budget active của selectedMonth, kể cả category đã archived vì lifecycle đó không xóa usage lịch sử. `spentMinor` summary cộng usage các budget được chọn; không đồng nhất với toàn bộ expense tháng nếu một số category chưa có budget. `remainingMinor=sum(limit)-sum(spent)` có thể âm. Phải dùng cùng công thức/threshold với BudgetResponse; `worstStatus=EXCEEDED > WARNING > SAFE`; không có budget thì `NONE`, không dùng SAFE. `usagePercent=sum(spent)*100/sum(limit)` HALF_UP 2 chữ số, không cap; chỉ `progressPercent` cho thanh hiển thị clamp 0–100. Ví dụ budget A 100/100, B 0/900: aggregate 10% nhưng worstStatus vẫn EXCEEDED.

Recent transactions: tối đa 5 POSTED INCOME/EXPENSE của **tháng được chọn**, sort occurredAt DESC, transactionId DESC. Tên wallet/category hiện tại, kể cả archived; không bịa snapshot tên lịch sử chưa được schema lưu. DTO chứa archive flags cho UI. Không bao gồm note/payee vào summary nếu UI chưa cần. Click xem chi tiết dùng ID thật và endpoint detail hiện có.

## 3. API/DTO đề xuất

`GET /api/v1/dashboard?month=2026-11` → 200 direct `DashboardResponse`; Bearer bắt buộc. Endpoint chỉ đọc, không tự seed/create dữ liệu. Query khác allowlist hoặc month sai trả 400 BAD_REQUEST. Không nhận walletId, userId, limit tùy ý trong phiên bản đầu.

```json
{
  "month": "2026-11",
  "timeZone": "Asia/Ho_Chi_Minh",
  "currencyCode": "VND",
  "snapshotAt": "2026-11-08T08:00:00Z",
  "user": {"displayName": "An"},
  "assets": {"totalMinor": 5000000, "availableMinor": 3000000, "savingsMinor": 2000000, "walletCount": 2},
  "monthlyFlow": {"incomeMinor": 2000000, "expenseMinor": 900000, "netSavingsMinor": 1100000},
  "budget": {
    "count": 2, "limitMinor": 2000000, "spentMinor": 900000, "remainingMinor": 1100000,
    "usagePercent": 45.00, "progressPercent": 45, "worstStatus": "WARNING",
    "warningCount": 1, "exceededCount": 0
  },
  "recentTransactions": [
    {"transactionId": "11111111-1111-4111-8111-111111111111", "transactionType": "EXPENSE", "amountMinor": 900000,
     "occurredAt": "2026-11-08T07:00:00Z",
     "wallet": {"walletId": "22222222-2222-4222-8222-222222222222", "name": "Tiền mặt", "archived": false},
     "category": {"categoryId": "33333333-3333-4333-8333-333333333333", "name": "Ăn uống", "iconKey": "coffee", "colorHex": "#F59E0B", "archived": false}}
  ],
  "notifications": {"available": false, "unreadCount": null}
}
```

Ví dụ budget 900000 spent có thể WARNING vì một budget 1000000 đã dùng900000 trong khi budget khác chưa dùng. Khi `count=0`: limit/spent/remaining/usage/progress/counts đều0, worstStatus NONE. `displayName` fallback username rồi “bạn”; không cần trả email/PII profile thừa. Khi Notifications sẵn sàng: available=true, unreadCount integer >=0, count từ cùng snapshot inbox. Trạng thái AVAILABLE là cờ rollout server, không phải server bắt lỗi SQL rồi trả giả count.

Lỗi theo ApiErrorResponse hiện có: 401 authentication; 400 BAD_REQUEST/query; 422 FINANCE_AMOUNT_OVERFLOW; 500 lỗi hạ tầng. Dashboard lỗi tổng hợp tài chính không trả 200 với số 0 thay dữ liệu lỗi. Không có resource path ID nên không thêm404 business. Response không chứa thông tin DB/provider trong message.

## 4. Architecture và dependency

Đề xuất package/module theo backend hiện có:

```java
DashboardController.get(AuthenticatedUser principal, String month);
DashboardService.get(UUID userId, String optionalMonth) -> DashboardResponse;
DashboardRepository.read(UUID userId, YearMonth month, ZoneId zone) -> DashboardSnapshot;
// Shared pure calculation được tái sử dụng, không HTTP/service write vòng qua module khác:
BudgetResponse.of(...); // exact usage status, nếu giữ helper hiện tại phù hợp
```

DashboardService `@Transactional(readOnly=true, isolation=REPEATABLE_READ)` đọc timezone/profile, parse/default month rồi repository aggregates và recent references trên cùng transaction. `snapshotAt` dùng transaction_timestamp của DB snapshot, không ghép nhiều request profile/settings/finance. Nếu chọn một SQL statement có CTE để giảm round trips vẫn phải tránh cross-product giữa wallet, transactions và budgets. Không đổi READ COMMITTED của financial writers hoặc lấy wallet write locks chỉ để hiển thị dashboard. Không gọi service Budget riêng mà tạo snapshots khác.

DashboardRepository có các query set-based có giới hạn: assets view, income/expense aggregate, budget usage batch, recent với scoped joins, unreadCount nếu module bật. Reuse SQL predicates/formula của Budget qua helper/repository projection được review cùng Integration Agent; không copy một công thức gần giống rồi tự xử lý rounding khác. Recent names join bằng `(user_id,id)`; ownership không chỉ dựa UUID. Không thêm repository interfaces dư nếu concrete JDBC class hiện tại đáp ứng.

Dependency:

1. Wallet/Category implementations và seed ổn định → assets/names.
2. Transaction ledger CRUD/void/no-negative hoàn tất và PostgreSQL tests → monthly flow/recent.
3. Budget aggregate/timezone/threshold được nghiệm thu → summary exact/worst status.
4. Notifications inbox/read-state và cùng datasource → unread integration; dashboard finance có thể nghiệm thu trước bằng available=false.

Không schema tài chính mới cho Dashboard. Chỉ cân nhắc index user/status/occurredAt hoặc category/date sau EXPLAIN dữ liệu đại diện; migration mới lấy version kế tiếp lúc triển khai, không sửa migration lịch sử. Kiểm tra legacy budgets kỳ không tròn tháng/ledger bất thường theo các plan CRUD trước cutover; không tự làm tròn hoặc sửa số liệu để dashboard nhìn đúng.

## 5. Các bước triển khai

1. **Contract gate:** review DTO và ý nghĩa từng số với Android developer; ghi OpenAPI/contract mới cho Dashboard sau khi thống nhất, giữ nguyên FINANCE_CRUD_CONTRACT. Chốt expected fixtures gồm ví archived/excluded, budget vượt riêng lẻ và monthly net âm.
2. **Persistence:** implement JDBC projections + exact overflow; snapshot transaction; query-plan/index review. Tái sử dụng Budget math, không thêm cache.
3. **Service/API:** controller auth, strict month/query, default timezone, direct response, lỗi chuẩn; API tests và OpenAPI examples dữ liệu rỗng/đủ dữ liệu.
4. **Android handoff:** `DashboardApiService/Repository/ViewModel` dùng ApiClient/Resource/session guards hiện có; Home bỏ tự suy risk từ aggregate và bỏ gọi finance/profile để dựng cùng một card. Có thể giữ profile fetch cho edit-profile riêng, không dùng để overwrite snapshot card.
5. **Navigation/UI:** assets ghi “hiện tại”, monthly card ghi tháng đã chọn; giữ hide-balance qua rotation; bind WARNING/worstStatus, recent detail và budget list theo selectedMonth. Badge unread=null thì ẩn/chưa hỗ trợ, không hiển thị0 như đã đọc hết. OCR/report shortcuts chỉ bật theo chức năng thật hiện có.
6. **Integration:** refresh khi resume/save/void/archive/read notification; bỏ response khi đổi user; loading/empty/retry không hiển thị sample. Kiểm tra lỗi refresh sau mutation không báo mutation thất bại.

## 6. Test và definition of done

- PostgreSQL thực: asset sum/available/savings đúng với opening + entries POSTED; archive/includeInAssets ảnh hưởng assets nhưng không xóa monthlyFlow; opening update không thành income. Toàn ledger nhiều trang vẫn trả totals đúng.
- INCOME/EXPENSE/VOIDED/legacy TRANSFER; netSavings âm; zero records; MAX và tổng vượt long →422 rõ ràng. Giữ tài sản hiện tại khi chọn tháng cũ/tương lai.
- Tháng leap/December, UTC boundaries, DST, đổi timezone/preferences; mặc định month lấy từ snapshot user timezone, không từ clock Android.
- Budget warning/exceed exact trước rounding; threshold100; notification toggles off không đổi status; một budget exceed nhưng aggregate thấp; no-budget NONE; archived category còn usage.
- Snapshot concurrency: giữa hai SELECT có expense commit/void/rename/settings change vẫn nhận một snapshot đồng nhất; request tiếp theo nhận mới; không test bằng sleep may rủi mà dùng barrier hai connection.
- Ownership A/B cho mọi projection/recent references; JWT401; malformed month400; notes/payee/email không lộ trong payload. Query count/EXPLAIN chứng minh không N+1; đo p95 mục tiêu <500ms backend, ghi rõ số record/concurrency và loại mạng client.
- API JSON/Retrofit contract, Android ViewModel/session/rotation/loading/retry, real-device backend flow. Build không thay thế fixture PostgreSQL hoặc device verification.
- Khi Notifications tích hợp: read/read-all/delete làm badge đổi; email SENT không làm inbox read; available=false khác available=true/count0.

Hoàn thành khi HOME-01–09 đạt, contract/OpenAPI mới đã review, tài sản/flow/budget đối soát bằng fixtures, tests có kết quả thật và Android nhận được DTO qua backend. Báo cáo tách dashboard finance hoàn tất với unread integration còn gate; không tuyên bố endpoint này đã có chỉ vì tài liệu tồn tại.
