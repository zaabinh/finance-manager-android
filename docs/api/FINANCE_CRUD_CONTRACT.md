# Contract Wallet, Category, Budget và Transaction

Chốt phạm vi theo trao đổi ngày 07/11/2026. Đây là contract đích để triển khai, **không xác nhận API đã tồn tại hoặc đã qua kiểm thử**. Các tài liệu requirements/use-cases/roadmap cũ chỉ dùng tham khảo nghiệp vụ; nếu khác với tài liệu này thì dùng quyết định tại đây.

Kế hoạch thực hiện: [Wallet](../development/wallet-crud-plan.md), [Category](../development/category-crud-plan.md), [Budget](../development/budget-crud-plan.md), [Transaction](../development/transaction-implementation-plan.md).

## 1. Quy ước chung

- Domain dùng Wallet/Ví; `/api/v1/wallets`, `walletId`, `walletType`. Không tạo route alias `/accounts`.
- Chỉ VND nguyên. Các trường tiền trong JSON là số nguyên, không phải chuỗi; backend từ chối fractional JSON numbers thay vì ép/cắt phần lẻ. Dùng Java `long`/PostgreSQL `BIGINT` cho giá trị riêng lẻ; tổng dùng phép toán chính xác và kiểm tra overflow, không dùng float/double.
- Hằng `MAX_MONEY = 999999999999`: opening balance từ 0 đến MAX; amount/limit từ 1 đến MAX. Số dư/tổng dẫn xuất có thể lớn hơn MAX nhưng phải nằm trong signed 64-bit; vượt khả năng biểu diễn trả 422 `FINANCE_AMOUNT_OVERFLOW` và rollback mutation.
- Mọi timestamp trả về là ISO-8601 UTC (`Z`). User chọn IANA timezone qua preferences, mặc định `Asia/Ho_Chi_Minh`; không lấy timezone của thiết bị/server làm nguồn quyết định ngày/tháng.
- UUID từ client phải đúng định dạng. Bearer authentication bắt buộc; user lấy từ `AuthenticatedUser`, không từ body/query.
- Business response của bốn module là DTO trực tiếp; DELETE thành công trả 204 không body. Auth và User/Profile đang dùng envelope riêng, giữ nguyên contract của chúng.
- JSON sai cấu trúc, UUID/query/sort sai định dạng: 400 `BAD_REQUEST`; dữ liệu hợp lệ về cú pháp nhưng sai quy tắc: 422; tài nguyên không tồn tại/khác owner: 404 `NOT_FOUND`; chưa xác thực: 401; conflict trạng thái/ràng buộc: 409.
- Error body theo `ApiErrorResponse`: `timestamp`, `status`, `errorCode`, `message`, `path`, `fieldErrors`. Thông báo không tiết lộ tài nguyên thuộc user khác.
- POST tạo mới trả 201 và `Location`; PUT thay toàn bộ các trường được phép sửa, không sửa ID/owner/lifecycle. Các trường tùy chọn bỏ qua trong PUT dùng default/null đã ghi bên dưới. Không hiểu PUT là partial update.
- Field không được hỗ trợ hoặc field chỉ đọc xuất hiện trong request trả 400 `BAD_REQUEST`, không âm thầm bỏ qua rồi báo đã lưu. Ngoại lệ tương thích đã nêu rõ như clientRequestId trong Transaction PUT được xử lý theo mục 5.
- POST Wallet/Category/Budget không có bảo đảm idempotency key: không tự retry mù khi timeout; UI tải lại để kiểm tra. Transaction có idempotency riêng ở mục 5.
- Tên trim hai đầu, 1–100 ký tự, chuẩn Unicode NFC; kiểm tra trùng không phân biệt hoa/thường, vẫn phân biệt dấu. `sortOrder`: integer 0–2147483647, mặc định 0. List Wallet/Category/Budget trả array, chưa phân trang; thứ tự ổn định như từng module.

## 2. Wallet CRUD

| Method | Route | Kết quả |
|---|---|---|
| GET | `/api/v1/wallets?status=ALL` | 200 `WalletResponse[]` |
| POST | `/api/v1/wallets` | 201 `WalletResponse` |
| GET | `/api/v1/wallets/{walletId}` | 200 `WalletResponse`, kể cả archived |
| PUT | `/api/v1/wallets/{walletId}` | 200 `WalletResponse`; chỉ ví active |
| PATCH | `/api/v1/wallets/{walletId}/status` | Body `{"active":true}` hoặc `false`; 200 `WalletResponse` |
| DELETE | `/api/v1/wallets/{walletId}` | 204; archive idempotent, không hard-delete |

List `status=ACTIVE|ARCHIVED|ALL`, mặc định ALL để không làm mất tham chiếu lịch sử. Picker tạo mới gọi ACTIVE; sort `sortOrder ASC, name ASC, walletId ASC`.

POST/PUT body:

```json
{"name":"Tiền mặt","walletType":"CASH","openingBalanceMinor":2000000,"includeInAssets":true,"sortOrder":0}
```

- `name`, `walletType` bắt buộc. POST opening mặc định 0; PUT opening bắt buộc. `includeInAssets` mặc định true. `walletType=CASH|BANK|SAVINGS`; không có E_WALLET, provider hoặc note trong scope CRUD này.
- Response gồm `walletId`, `name`, `walletType`, `currencyCode` luôn VND, `openingBalanceMinor`, `balanceMinor`, `includeInAssets`, `sortOrder`, `archivedAt` (null nếu active), `createdAt`, `updatedAt`.
- Tên ví active duy nhất theo user, không phân biệt hoa/thường. Trùng khi create/update/reactivate trả 409 `WALLET_NAME_CONFLICT`.
- `balanceMinor = openingBalanceMinor + SUM(delta_minor của transaction POSTED)`. Chỉ đọc từ ledger/view `wallet_balances`; không có request ghi trực tiếp `balanceMinor` và chưa thêm cache số dư.
- Đổi opening phải kiểm tra số dư cuối cùng vẫn không âm, có khóa cùng cơ chế transaction. Ví archived không sửa metadata/opening được, trả 409 `WALLET_ARCHIVED`; có thể reactivate rồi sửa.
- Archive không xóa transaction, không đảo entry, không thay đổi số dư; ví có tiền vẫn archive được. Tổng tài sản chỉ cộng ví active có `includeInAssets=true`; số dư riêng của ví archived vẫn đọc được.
- Ví archived không nhận transaction mới hoặc làm ví thay thế khi sửa transaction. Xóa transaction cũ vẫn được xét độc lập theo mục 5.

## 3. Category CRUD

| Method | Route | Kết quả |
|---|---|---|
| GET | `/api/v1/categories?status=ALL&categoryType=EXPENSE` | 200 `CategoryResponse[]`; type là optional |
| POST | `/api/v1/categories` | 201 `CategoryResponse` |
| GET | `/api/v1/categories/{categoryId}` | 200 `CategoryResponse`, kể cả archived |
| PUT | `/api/v1/categories/{categoryId}` | 200 `CategoryResponse`; chỉ active |
| PATCH | `/api/v1/categories/{categoryId}/status` | Body `{"active":true}` hoặc `false`; 200 `CategoryResponse` |
| DELETE | `/api/v1/categories/{categoryId}` | 204; archive idempotent |

List `status=ACTIVE|ARCHIVED|ALL`, default ALL; sort `sortOrder ASC, name ASC, categoryId ASC`. `categoryType=EXPENSE|INCOME`.

POST/PUT body:

```json
{"name":"Ăn uống","categoryType":"EXPENSE","iconKey":"coffee","colorHex":"#F59E0B","sortOrder":0}
```

- `name`, `categoryType` bắt buộc; icon/color nullable, mặc định null. `iconKey` là key do app quy ước, không phải Android resource integer; 1–80 ký tự khi có giá trị. `colorHex` đúng `#RRGGBB` và normalize uppercase.
- Response gồm `categoryId`, `name`, `categoryType`, `iconKey`, `colorHex`, `sortOrder`, `archivedAt`, `createdAt`, `updatedAt`. Không có global/default-ownerless category hoặc `isDefault` dùng để chặn sửa.
- Tất cả category có `user_id NOT NULL`, kể cả danh mục tạo sẵn. Seed ban đầu: EXPENSE = Ăn uống, Đi lại, Mua sắm, Hóa đơn, Khác; INCOME = Lương, Thưởng, Thu nhập khác. Mỗi user có UUID riêng, được sửa/archive như danh mục tự tạo.
- Seed một lần, trong transaction khi đăng ký. Backfill user cũ chưa từng có category; nếu đã có bất kỳ category kể cả archived thì coi là đã khởi tạo, giữ nguyên lựa chọn cũ. Không seed lại khi list rỗng hoặc user đã archive/đổi tên danh mục. Dùng dấu khởi tạo nội bộ để phân biệt user chưa seed với user chủ động xóa/ẩn.
- Tên active duy nhất theo `(user, categoryType, normalized name)`: 409 `CATEGORY_NAME_CONFLICT`, kể cả reactivate.
- Đổi type chỉ khi không có bất kỳ transaction (kể cả VOIDED) hoặc budget (kể cả archived) tham chiếu; nếu có trả 409 `CATEGORY_TYPE_IN_USE`. Cùng type vẫn sửa tên/icon/color được.
- Category archived không sửa metadata được (409 `CATEGORY_ARCHIVED`); vẫn đọc lịch sử. Archive không cascade archive budget/transaction, không tác động số dư hoặc usage đã ghi nhận. Không dùng category archived để tạo transaction/budget hoặc đổi sang nó.
- Budget đang có tham chiếu category archived vẫn được xem/xóa và sửa hạn mức nếu giữ nguyên category; xem mục 4.

## 4. Budget CRUD và usage

| Method | Route | Kết quả |
|---|---|---|
| GET | `/api/v1/budgets?month=2026-11&status=ACTIVE` | 200 `BudgetResponse[]` |
| POST | `/api/v1/budgets` | 201 `BudgetResponse` |
| GET | `/api/v1/budgets/{budgetId}` | 200 `BudgetResponse`, kể cả archived |
| PUT | `/api/v1/budgets/{budgetId}` | 200 `BudgetResponse`; chỉ active |
| DELETE | `/api/v1/budgets/{budgetId}` | 204; archive idempotent |

List `month=YYYY-MM` và `categoryId` optional, `status=ACTIVE|ARCHIVED|ALL` default ACTIVE; sort `month DESC, categoryId ASC, budgetId ASC`. Không có restore endpoint trong scope này; sau archive được tạo budget mới cùng category/month.

POST/PUT body, cả ba trường bắt buộc:

```json
{"categoryId":"6d2786bb-e8e8-47eb-bc5a-6b06818cfe68","month":"2026-11","limitMinor":3000000}
```

- Chỉ category EXPENSE của user. Create/đổi category cần category active; PUT giữ nguyên category archived được phép đổi limit/month, vẫn kiểm tra uniqueness.
- `month` chính xác YYYY-MM, năm 0001–9998. Persist `period_start=ngày 1 tháng`, `period_end=ngày 1 tháng sau`, không nhận kỳ tùy ý từ client; không rollover.
- Một budget active cho mỗi user/category/month. Duplicate/overlap, kể cả concurrent create/update, trả 409 `BUDGET_PERIOD_CONFLICT`. Giữ PostgreSQL exclusion constraint hiện có.
- Response gồm `budgetId`, `categoryId`, `month`, `limitMinor`, `spentMinor`, `remainingMinor`, `usagePercent`, `warningThresholdPercent`, `status`, `archivedAt`, `createdAt`, `updatedAt`. `status` trong response là usage `SAFE|WARNING|EXCEEDED`; lifecycle xem `archivedAt`, không nhầm với query `status`.
- `spentMinor`: tổng amount EXPENSE, POSTED, cùng owner/category, mọi ví (kể cả archived hoặc excluded from assets), trong tháng theo timezone preferences hiện tại. Không gồm thu/chuyển ví/VOIDED; chưa có transaction thì bằng 0.
- `remainingMinor=limitMinor-spentMinor` có thể âm khi vượt budget. Đây không phải số dư ví; budget không giữ tiền và không chặn chi khi vẫn đủ số dư ví.
- `usagePercent` là JSON decimal làm tròn 2 chữ số HALF_UP, không cap 100. Tiền vẫn integer. Status so sánh chính xác trước khi làm tròn: spent >= limit → EXCEEDED; còn lại spent*100 >= limit*warningThresholdPercent → WARNING; còn lại SAFE.
- `warningThresholdPercent` lấy từ notification settings của user, mặc định 80; không có field threshold riêng trong request Budget. Tắt cảnh báo không đổi công thức status. Notification delivery/dedup làm sau CRUD.
- Đổi timezone không đổi month đã lưu nhưng tính lại biên UTC và usage theo timezone mới; đây là hành vi đã chọn. Khoảng thời gian là `[đầu tháng local, đầu tháng kế tiếp local)`; không cộng cố định số giờ/ngày.
- Limit nhỏ hơn spent vẫn được lưu. Delete budget không xóa/reverse transaction, không tăng số dư ví; chỉ ngừng xuất hiện trong list active và cảnh báo tương lai. PUT archived trả 409 `BUDGET_ARCHIVED`.
- Các giá trị usage/timestamps trong một response phải lấy trên snapshot DB nhất quán; không ghép các tổng từ các thời điểm khác nhau.

## 5. Transaction thu/chi/chuyển ví và ràng buộc số dư

Nhận `EXPENSE|INCOME|TRANSFER` (TRANSFER theo [spec 2026-11-09](../superpowers/specs/2026-11-09-budget-hierarchy-and-transfer-design.md) §2); loại khác (ADJUSTMENT, DEBT_*) trả 422 `TRANSACTION_TYPE_UNSUPPORTED`.

| Method | Route | Kết quả |
|---|---|---|
| POST | `/api/v1/transactions` | 201 tạo mới, 200 replay cùng request |
| GET | `/api/v1/transactions` | 200 `{"items":[],"page":0,"totalPages":0}` |
| GET | `/api/v1/transactions/{transactionId}` | 200 DTO hiện tại, kể cả VOIDED để đọc audit/detail |
| PUT | `/api/v1/transactions/{transactionId}` | 200 DTO; chỉ POSTED |
| DELETE | `/api/v1/transactions/{transactionId}` | 204, void idempotent khi cùng owner |

POST body:

```json
{"transactionType":"EXPENSE","amountMinor":95000,"categoryId":"6d2786bb-e8e8-47eb-bc5a-6b06818cfe68","walletId":"47aa3a03-023b-4e1a-a0f1-a710d1122179","occurredAt":"2026-11-07T08:45:00Z","note":"Cà phê","payee":"Quán Đồng","clientRequestId":"3bef66f9-8969-447e-b0ea-aa94dd3422f3"}
```

- Required: type, amount, walletId, occurredAt; categoryId bắt buộc với EXPENSE/INCOME; POST cần UUID clientRequestId. PUT dùng cùng field nghiệp vụ, không cần clientRequestId và không dùng nó để tạo mới.
- `destinationWalletId`: EXPENSE/INCOME phải absent/null (422). note nullable tối đa 2000, payee nullable tối đa 160, normalize null thành chuỗi rỗng ở response. FR description được ánh xạ sang note/payee, không thêm field description.
- `occurredAt` là UTC timestamp bắt buộc; UI mặc định thời điểm hiện tại, cho đổi ngày/giờ. Không ngầm suy ra thời điểm từ một ngày thiếu timezone. Date dùng cho lọc được chuyển theo timezone user.
- Response gồm `transactionId`, các field nghiệp vụ, `status=POSTED|VOIDED`, `voidedAt`, `createdAt`, `updatedAt`. `clientRequestId` có thể trả lại để đối chiếu; owner không lấy từ client.
- **TRANSFER**: `walletId` = ví nguồn, `destinationWalletId` = ví đích, bắt buộc, khác nhau (422), cả hai active (409 `WALLET_ARCHIVED`) và cùng owner (404); `categoryId` phải null (422). Ghi hai entry: −amount ở ví nguồn, +amount ở ví đích. Response trả `destinationWalletId`, `categoryId=null`. Chuyển ví không phải thu/chi: không tính vào budget spent, tổng thu/chi hay báo cáo; tổng tài sản không đổi.
  ```json
  {"transactionType":"TRANSFER","amountMinor":2000000,"walletId":"<ví tiết kiệm>","destinationWalletId":"<ví chi tiêu>","occurredAt":"2026-11-09T02:00:00Z","note":"","payee":"","clientRequestId":"<uuid>"}
  ```
- POST/PUT yêu cầu ví active, category active cùng owner và type. PUT giao dịch có tham chiếu archived yêu cầu chọn lại tham chiếu active hoặc reactivate trước. DELETE giao dịch cũ không yêu cầu wallet/category còn active, nhưng vẫn kiểm tra số dư.
- Query: page default 0 >=0, size default 50 từ 1–200; optional dateFrom/dateTo (YYYY-MM-DD, inclusive), walletId (khớp ví nguồn hoặc ví đích của TRANSFER), categoryId, type (`EXPENSE|INCOME|TRANSFER`), q (trim, tối đa 200 ký tự). Date năm 0001–9998; dateFrom <= dateTo. Filter ID không thuộc user trả 404, archived hợp lệ để tra lịch sử.
- Sort allowlist: `occurredAt,desc` (default), `occurredAt,asc`, `amountMinor,desc`, `amountMinor,asc`; tie-breaker transactionId cùng chiều. q tìm substring literal không phân biệt hoa/thường trong note/payee, escape `%`/`_`; có thể mở rộng search sau nhưng không ngầm coi pattern SQL từ user.
- List chỉ POSTED; page ngoài phạm vi trả items rỗng và totalPages thực. Filter/count/page trong một request có snapshot nhất quán. Không hứa snapshot cố định giữa nhiều page requests có concurrent writes; Android dedup theo ID, refresh từ page 0 sau mutation.
- POST cùng user/key/cùng payload tạo ban đầu trả 200 bản ghi hiện tại (kể cả đã VOIDED), không repost; cùng key/khác payload trả 409 `TRANSACTION_REQUEST_CONFLICT`. Fingerprint canonical của payload tạo ban đầu (gồm `destinationWalletId` với TRANSFER) bất biến qua edit/delete; retry một lệnh đã thành công ưu tiên replay trước kiểm tra active hiện tại. Android không coi replay VOIDED là giao dịch active mới.
- PUT giao dịch VOIDED trả 409 `TRANSACTION_VOIDED`; DELETE lại cùng owner trả 204, giữ voidedAt ban đầu. Tài nguyên không tồn tại/khác owner luôn 404.

### Không cho phép số dư âm

Invariant áp dụng cho **số dư hiện tại sau commit của từng ví**, không phải chỉ kiểm tra lúc tạo expense, và không áp đặt lại số dư lũy kế từng thời điểm quá khứ cho giao dịch backdate.

```text
balance(wallet) = openingBalanceMinor + sum(delta của transaction POSTED)
create: candidate = balance + newContribution
update: candidate = balance - oldContribution + newContribution (theo từng ví)
delete: candidate = balance - oldContribution
opening update: candidate = balance - oldOpening + newOpening
```

- Nếu bất kỳ candidate < 0: 409 `WALLET_INSUFFICIENT_FUNDS`, toàn bộ mutation rollback. Không xóa trước rồi báo lỗi, không tự đổi amount hoặc tạo adjustment bù.
- TRANSFER: create/update kiểm tra ví nguồn (candidate ≥ 0); update được đổi amount/ví nguồn/ví đích, khóa mọi ví cũ và mới theo thứ tự UUID tăng dần và kiểm tra từng ví; DELETE (void) trả tiền về ví nguồn đúng một lần và kiểm tra **ví đích** không âm (409 `WALLET_INSUFFICIENT_FUNDS`).
- Xóa expense 100000 hoàn lại 100000 đúng một lần. Xóa income 100000 làm giảm balance 100000; nếu balance hiện tại chỉ còn 40000 thì bị từ chối. Người dùng phải sửa/xóa chi liên quan hoặc bổ sung thu hợp lệ trước.
- Sửa expense chuyển ví phải trả tiền ví cũ và trừ tiền ví mới trong cùng transaction; sửa/xóa income phải kiểm tra cả tiền đã chi dựa trên income đó.
- Khóa row transaction trước khi update/delete, sau đó khóa các wallet liên quan theo UUID tăng dần (create/opening update khóa wallet trực tiếp). Tất cả writer tài chính, kể cả archive/opening changes, phải theo cùng protocol; không thêm cache để giải quyết race.
- Tính aggregate **sau khi đã lấy wallet lock**, bằng statement/snapshot đọc mới phù hợp isolation READ COMMITTED; không dùng balance đọc trước khi chờ lock. Khóa shared references category khi kiểm tra active/type để phối hợp archive/type update; thứ tự chung transaction → wallets sorted → categories sorted, không đảo chiều ở thao tác khác.
- Ghi metadata/entries/void và kiểm tra invariant trong cùng database transaction. Giữ deferred ledger constraints; số dư dẫn xuất tự loại VOIDED. Không vừa VOIDED vừa ghi entry đảo vì sẽ reverse hai lần.
- Hai expense 70000 đồng thời trên ví 100000: chỉ một lệnh thành công, lệnh còn lại 409. Update/delete/opening update cùng kiểm tra với trạng thái đã commit mới nhất.
- Concurrent PUT không có optimistic version trong đợt này: lần thực thi sau lock ghi đè toàn bộ field nghiệp vụ nếu còn hợp lệ; contract không hứa phát hiện stale form. Android refresh dữ liệu sau lệnh.

## 6. Persistence, migration và nghiệm thu

- Flyway migrations đang áp dụng là lịch sử bất biến. Thêm migration mới sau version hiện có; không sửa V1–V6 để đổi schema.
- Dùng `wallets`, `categories`, `transactions`, `transaction_entries`, `budgets`, `wallet_balances` hiện có. `wallets.id` là PK; `transaction_entries.wallet_id` và `wallet_balances.wallet_id` là tham chiếu. API `walletId` không đồng nghĩa phải đổi PK thành `wallet_id`.
- Bổ sung unique active wallet name, marker seed category và fingerprint idempotency nếu thiếu. Kiểm tra dữ liệu hiện hữu trước khi áp constraint; không tự xóa bản ghi hoặc làm tròn tiền để migration pass.
- Ledger chưa có negative-balance database trigger: invariant phải được service áp dụng cho mọi writer theo locking protocol và được chứng minh bằng PostgreSQL concurrency tests. Phát hiện ví âm từ dữ liệu cũ phải được báo và xử lý trước cutover, không silently tiếp tục.
- Tiêu chí kiểm chứng: CRUD/ownership, archive/history, unique concurrent writes, không âm, rollback, reverse đúng một lần, timezone/month, actual Retrofit JSON, device flows và backend thật. H2/mock/build thành công không thay thế PostgreSQL/device integration.
