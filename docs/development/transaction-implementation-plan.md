# Kế hoạch triển khai Transaction

Cập nhật ngày 07/11/2026 theo quyết định đã chốt. Đây là kế hoạch, chưa triển khai chức năng. Contract hiện hành: [FINANCE_CRUD_CONTRACT.md](../api/FINANCE_CRUD_CONTRACT.md), đặc biệt mục 5 về không âm và reverse khi xóa. Requirements/use-cases/roadmap cũ đã được đọc ở lần lập kế hoạch trước để trace FR-TXN-001–005 và UC-005–008; hiện không còn trong workspace và không ghi đè contract mới.

## 1. Phạm vi đã chốt

- Wallet thay Account; chỉ integer VND, không float/double/chuyển đổi tiền tệ.
- Persist UTC; hiển thị/lọc theo IANA timezone user, mặc định Asia/Ho_Chi_Minh.
- Category luôn có owner, kể cả seeded; không có danh mục toàn cục.
- Đợt này chỉ EXPENSE/INCOME. TRANSFER, OCR/import mới, recurring, debt, adjustment làm sau.
- Không cho phép số dư hiện tại của bất kỳ ví nào âm sau create/update/delete hoặc sửa opening.
- Xóa mềm POSTED → VOIDED, giữ entries để audit; số dư loại contribution cũ đúng một lần. Không thêm deletedAt song song hoặc vừa void vừa ghi entry đảo.
- Số dư dẫn xuất từ wallet_balances, không cần cache. Android production không tính số dư từ một trang transaction.

## 2. Hiện trạng và điều kiện đầu vào

Android đã có TransactionForm/List/Detail, FinanceRepository/ViewModel/Action, Retrofit DTO và mock ledger. Backend chưa có Transaction API; DB đã có transactions, entries, balance view, deferred constraints và unique idempotency key. Android hiện tải wallets/categories/budgets/tất cả trang transactions trong một snapshot nên cần tách đường read theo feature.

**Hoàn thành đầy đủ [Wallet CRUD](wallet-crud-plan.md) và [Category CRUD](category-crud-plan.md) trước Transaction**: list/detail/create/update/archive/restore, ownership, per-user seed, integer money, active/history metadata và kiểm tra không âm khi sửa opening. Không dừng ở API list/create tối thiểu.

[Budget CRUD](budget-crud-plan.md) làm sau Category, có thể test usage bằng ledger fixtures trước khi Transaction API xong. Không để transaction bị chặn vì budget API chưa có; đối soát usage sau khi cả hai sẵn sàng. Notifications thuộc đợt sau.

Android management Wallet/Category đang dùng singleton sample riêng với finance API; phải dùng cùng server trong flavor thật, bỏ fallback ID giả/record đầu tiên. Không xây lại UI hoặc tổ chức lại toàn bộ backend.

## 3. Ràng buộc số dư và xóa mềm

| Lệnh | Candidate theo từng ví | Điều kiện |
|---|---|---|
| Create | balance + contribution mới | EXPENSE âm, INCOME dương |
| Update | balance - contribution cũ + contribution mới | Kiểm tra cả ví cũ/mới, kể cả sửa thu |
| Delete | balance - contribution cũ | Xóa chi hoàn tiền; xóa thu rút lại tiền |
| Sửa opening | balance - oldOpening + newOpening | Dùng chung lock với transaction writer |

Nếu bất kỳ candidate < 0 trả 409 WALLET_INSUFFICIENT_FUNDS và rollback toàn bộ, không thay amount hoặc tạo adjustment bù. Kiểm tra overflow riêng. Quy tắc áp dụng số dư hiện tại sau commit, không kiểm tra lại mọi số dư lũy kế quá khứ với backdate.

Ví dụ reverse chi: opening 1000000 → chi100000 = 900000 → sửa chi150000 = 850000 → xóa = 1000000 → xóa lại vẫn 1000000.

Ví dụ reverse thu: opening0 → thu100000 → chi60000 = 40000. Xóa thu bị 409, balance vẫn40000 và thu vẫn POSTED. Xóa chi trước khôi phục100000, sau đó xóa thu được và trở về0.

Khóa row transaction khi update/delete; khóa wallet cũ/mới theo UUID tăng dần, sau đó categories. Tính aggregate bằng statement/snapshot mới sau khi đã lấy wallet lock; validate/ghi/void trong cùng database transaction. Archive/type changes và opening edit dùng cùng protocol ở contract. Hai expense70000 trên ví100000 đồng thời chỉ một lệnh được thành công.

Vượt budget không đồng nghĩa thiếu tiền ví: đủ balance thì expense vượt budget vẫn hợp lệ.

## 4. API đã chốt

| Method | Route | Kết quả |
|---|---|---|
| POST | /api/v1/transactions | 201 mới; 200 replay cùng key/payload |
| GET | /api/v1/transactions | 200 items/page/totalPages; chỉ POSTED |
| GET | /api/v1/transactions/{transactionId} | 200 detail, kể cả VOIDED cho audit; unknown/unowned404 |
| PUT | /api/v1/transactions/{transactionId} | 200, chỉ POSTED; VOIDED409 |
| DELETE | /api/v1/transactions/{transactionId} | 204 void/repeated void; thiếu tiền409 |

Payload: transactionType, amountMinor, categoryId, walletId, occurredAt UTC, note, payee; POST cần clientRequestId. destinationWalletId absent/null. Response thêm status/voidedAt/createdAt/updatedAt. POST/PUT yêu cầu references active; DELETE transaction cũ không bắt ví/category còn active nhưng vẫn kiểm tra balance.

Query page/size/dateFrom/dateTo/walletId/categoryId/type/q/sort, giới hạn và allowlist theo contract mục5. Date inclusive theo timezone user chuyển thành half-open UTC range. Search literal case-insensitive trên note/payee; không thêm description riêng.

Idempotency theo user/key và fingerprint payload tạo ban đầu, không đổi fingerprint khi edit/delete. Retry nguyên request sau edit/void trả bản ghi hiện tại, không post lại; nội dung khác cùng key409. Android không coi replay VOIDED là vừa tạo transaction active. PUT chưa có optimistic version: writer sau lock thay payload nếu còn hợp lệ, không hứa phát hiện stale form.

## 5. Kế hoạch triển khai

### Bước 1 — Prerequisites và acceptance tests

- Nghiệm thu Wallet/Category CRUD, seed/lifecycle, shared wallet locking và integer money.
- Tách transaction read khỏi budgets; ẩn/vô hiệu chuyển ví trên backend chưa hỗ trợ.
- Viết acceptance tests create/detail/update/delete/ownership/không âm với hai ví, hai user và fixture ledger.

### Bước 2 — Backend create/detail/delete

- Repository JDBC scoped user_id, DTO riêng; @Transactional cho transaction+entries. Income một entry dương, expense một entry âm; giữ deferred constraints khi commit.
- Validate active/type/owner, amount integer, UTC timestamp, note/payee; reject reserved types chưa hỗ trợ.
- Migration mới cho fingerprint tạo ban đầu; giữ unique(user_id,client_request_id). Xử lý concurrent retry không double-post và không tiếp tục query trong DB transaction đã aborted vì constraint.
- Sau wallet lock, check candidate/overflow rồi commit. DELETE kiểm tra trước VOIDED; giữ entries và voidedAt lần đầu, không reverse lặp lại.
- Detail query theo ID, không tìm trong trang history đã tải; audit VOIDED readonly.

### Bước 3 — Backend update, query và concurrency

- Khóa transaction, đọc lại state, khóa old/new wallets/categories theo protocol; thay metadata/entries nguyên tử. Giữ fingerprint gốc; rollback nếu ví mới thiếu tiền hoặc commit constraint lỗi.
- Test đổi amount/type/wallet/category/month, sửa/xóa thu đã tiêu, delete/update/opening/archive/type-change đồng thời.
- Implement filters/search/sort/page; count/page cùng snapshot trong một request, không hứa snapshot cố định giữa nhiều request. Page ngoài range rỗng, IDs/filter khác owner404.
- Không cộng trang hiện tại thành balance/tổng tháng/usage. Wallet balance và budget usage dùng server; tổng tháng UI nếu cần phải có aggregate endpoint được bổ sung contract trước khi hiển thị.
- Dùng index hiện có; thêm sau EXPLAIN/đo dữ liệu đại diện, không sửa migrations lịch sử.

### Bước 4 — Android với backend thật

- Mở rộng TransactionApiService detail/query/response status; dùng Repository/ViewModel và ba Activity hiện có.
- Server paging, dedup ID; giữ filter qua rotation, refresh page0 sau mutation; bỏ response cũ khi logout/đổi user.
- Picker active/type-compatible, detail hiển thị archived names. PUT references archived yêu cầu chọn lại active hoặc reactivate.
- Giữ input/clientRequestId qua timeout/rotation, chặn bấm Lưu liên tục; phân biệt mutation thành công với refresh lỗi. Replay VOIDED hiển thị đúng state hiện tại.
- Loading/empty/no-results/retry và lỗi401/404/409/422/field errors; xóa thu thiếu tiền giữ màn hình và giải thích lý do.
- Đồng bộ mock no-negative/timezone/soft-delete; không fallback mock khi API thật lỗi; dùng balance server thay tính từ partial snapshot.

### Bước 5 — Budget integration và nghiệm thu

- Khi Budget CRUD sẵn sàng: create/edit/category-month move/delete expense cập nhật spent/remaining/status; income/VOIDED không tính; budget delete không trả tiền ví.
- Đối soát wallet_balances với opening+entries POSTED sau mọi mutation; không thêm cache.
- Device/backend thật: restart/login lại, rotation/back, timeout/retry, session switch và lịch sử nhiều trang. Báo rõ các ca bị môi trường chặn.
- TRANSFER và tự động gửi notification làm sau, không là điều kiện hoàn tất CRUD thu/chi hiện tại.

## 6. Ma trận kiểm thử

| Nhóm | Ca bắt buộc |
|---|---|
| Validation | 0/âm/phần lẻ/MAX/overflow; date/type sai; note/payee dài; wallet/category inactive hoặc type mismatch |
| Ownership | Không list/detail/update/delete/audit của user khác; không dùng reference/filter của người khác |
| Reverse | Xóa chi hoàn tiền; xóa thu trừ tiền; repeated204; failure giữ nguyên state/entries/balance |
| Không âm | Create thiếu tiền; giảm income; tăng expense; chuyển expense sang ví thiếu tiền; xóa thu đã tiêu; giảm opening; đúng bằng balance về0 |
| Concurrency | Hai expense đồng thời; update/delete/opening đua nhau; archive/type change khi tạo reference; rollback khi commit lỗi |
| Retry | Cùng key/payload một posting; khác payload409; replay sau edit/void; user khác độc lập key |
| Query/timezone | Tie-breaker, literal wildcard search, filter kết hợp, cuối tháng/DST/đổi timezone, page rỗng, nhiều trang |
| Android | Input/key/filter sau rotation; archived history/active picker; budgets chưa có; success rồi refresh lỗi; đổi session; balance không lấy từ page |
| Budget | Expense CRUD/category-month move đổi usage; income không đổi; vượt budget được ghi nếu ví đủ tiền |

Thứ tự: backend unit/service → PostgreSQL Testcontainers (Flyway, deferred constraints, rollback, locking) → Retrofit contract → Android unit/build/lint → instrumentation → backend/device smoke. H2/mock/build pass không thay cho test DB/thiết bị thật.

## 7. Definition of done và thứ tự review

1. Wallet/Category CRUD hoàn tất trước; contract được dùng thống nhất, không còn global category/decimal/TRANSFER/cache cản scope.
2. Transaction CRUD thu/chi và ownership pass với backend thật; balance không âm sau mọi mutation, reverse đúng một lần.
3. Android dùng server, dữ liệu đúng qua restart/login; test report tách unit/contract/device/backend.
4. Đối soát balance/usage không sai; đo CRUD p95 dưới500ms loại trừ mạng client trên dữ liệu/mức tải được ghi trong báo cáo.

Các phần review: (1) Wallet CRUD; (2) Category CRUD/seed; (3) Budget CRUD sau Category và transaction ledger/create/detail/delete; (4) transaction update/query/concurrency; (5) Android transaction và budget usage integration. Không triển khai TRANSFER/cache trong các phần này.
