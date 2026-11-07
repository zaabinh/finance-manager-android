# Kế hoạch CRUD Wallet

Ngày 07/11/2026. Đầu ra là kế hoạch và requirement; chưa triển khai API. Dùng [contract tài chính](../api/FINANCE_CRUD_CONTRACT.md), mục 1–2 và invariant mục 5. Thứ tự: hoàn thành Wallet và [Category](category-crud-plan.md) trước khi làm [Transaction](transaction-implementation-plan.md); [Budget](budget-crud-plan.md) CRUD có thể làm sau Category.

## 1. Hiện trạng và phạm vi

- DB có `fintrack.wallets`, `transaction_entries.wallet_id`, view `wallet_balances`, lifecycle `archived_at`; chưa cần bảng số dư cache.
- Backend chưa có WalletController/Service/Repository. Tận dụng `AuthenticatedUser`, `JdbcTemplate`, transaction manager và error handler hiện có.
- Android có hai nguồn: màn quản lý ví dùng `WalletRepository` singleton sample/in-memory với `WalletItem` chứa double; luồng finance dùng `FinanceRepository`/Retrofit và integer `FinanceBook.Wallet`. Cần đồng bộ về cùng backend; không để một màn sửa mẫu còn picker đọc dữ liệu thật.
- Scope: list/detail/create/update/archive/reactivate; số dư dẫn xuất; quyền sở hữu; kiểm tra không âm. Không thêm ví điện tử, provider, chuyển ví hoặc adjustment.

## 2. Requirements

| ID | Yêu cầu | Nghiệm thu |
|---|---|---|
| WAL-01 | Tạo ví CASH/BANK/SAVINGS theo user đăng nhập | Tên hợp lệ, VND nguyên, opening >=0; 201 và UUID thật; không nhận owner từ client |
| WAL-02 | List/detail đúng owner | Có ACTIVE/ARCHIVED/ALL, thứ tự ổn định; UUID sai owner trả 404, không fallback sang ví đầu tiên |
| WAL-03 | Sửa metadata và opening | PUT không sửa balance trực tiếp; opening delta phản ánh đúng vào derived balance; candidate âm trả 409 và rollback |
| WAL-04 | Archive/reactivate | DELETE chỉ archive, lặp lại 204; tiền và lịch sử giữ nguyên; reactivate kiểm tra tên trùng |
| WAL-05 | Tên active duy nhất theo user | Trim/NFC/case-insensitive; create/update/restore đồng thời vẫn không trùng; khác user được dùng cùng tên |
| WAL-06 | Số dư không âm | Mọi transaction writer và sửa opening dùng chung wallet lock; không dùng check-then-write không khóa |
| WAL-07 | Tách opening khỏi current balance | UI ghi rõ “Số dư ban đầu”; không gán current balance vào opening khi mở form edit |
| WAL-08 | Tích hợp Android và session | List/add/edit/detail/picker đều đọc cùng server, dùng long, không sample fallback; user đổi thì bỏ kết quả cũ |

Giới hạn field, default, status codes và response được chốt ở contract; UI không gửi note/provider/E_WALLET vì chưa thuộc contract. `includeInAssets` chỉ điều khiển tổng tài sản; không ảnh hưởng số dư riêng hoặc budget expense.

## 3. API và luồng người dùng

| Thao tác | Endpoint | Response |
|---|---|---|
| List | GET `/api/v1/wallets?status=ALL` | 200 array; picker dùng ACTIVE |
| Detail | GET `/api/v1/wallets/{walletId}` | 200, kể cả archived |
| Create | POST `/api/v1/wallets` | 201 DTO |
| Edit | PUT `/api/v1/wallets/{walletId}` | 200 DTO |
| Status | PATCH `/api/v1/wallets/{walletId}/status` | 200 DTO với body active boolean |
| Delete | DELETE `/api/v1/wallets/{walletId}` | 204 archive |

Payload create/update: name, walletType, openingBalanceMinor, includeInAssets, sortOrder. Response bổ sung walletId, currencyCode, balanceMinor, archivedAt, createdAt, updatedAt. Ví archived không edit trực tiếp, phải reactivate trước.

Flow: danh sách → tạo → chi tiết → sửa → archive → xem danh sách archived → reactivate. Tạo ví từ transaction picker xong phải quay lại form với dữ liệu đang nhập còn nguyên.

## 4. Kế hoạch triển khai cụ thể

1. **DB và contract tests.** Rà dữ liệu tên active trùng/vi phạm không âm. Thêm unique index `(user_id, lower(name)) WHERE archived_at IS NULL` bằng migration mới; normalize tên nhất quán từ service. Nếu có dữ liệu trùng phải báo để xử lý, không tự xóa. Kiểm tra view balance trả được numeric tổng chính xác và map có kiểm tra overflow.
2. **Repository.** Thêm `WalletRepository` JDBC backend: list/detail theo user, insert/update/archive/restore, query derived balance và `SELECT FOR UPDATE` scoped theo owner. Tất cả lookup resource phải có user_id trong predicate. Update opening aggregate sau khi đã lấy lock. Không expose entity persistence trực tiếp.
3. **Service.** Validate field/enum, unique name và lifecycle; ánh xạ 404/409/422 theo contract. `@Transactional` cho mutation; chung locking protocol với transaction về sau. Archive không gọi delete transaction hay reverse entries. Ghi UTC timestamps; thao tác status lặp lại không thay timestamp archive ban đầu.
4. **Controller/OpenAPI.** Thêm Create/Update/Status DTO và WalletResponse; direct DTO, Location ở 201, 204 delete. Test JSON số lẻ phải bị reject, không coercion sang long. GET không tự tạo ví mẫu.
5. **Android data.** Mở rộng WalletApiService/DTO, WalletRepository/ViewModel và FinanceRepository dùng chung API semantics; long thay double ở mọi caller/model/adapter liên quan. Bỏ sample fallback và ID giả `w1` ở dev/staging/prod; mock chỉ trong mock flavor, cập nhật mock validation cùng quy tắc.
6. **Android UI.** Map 3 loại ví chính xác; ẩn input chưa hỗ trợ; sửa nhãn số dư ban đầu/current balance; thêm archive/restore và picker active-only. Giữ lịch sử tham chiếu archived, form khi lỗi và rotation; refresh sau success. Budget/Transaction API chưa có không được chặn CRUD ví.
7. **Nghiệm thu.** Backend tests/PostgreSQL → Android unit/contract/build/lint → device với backend thật. Ghi bằng chứng, chưa chuyển sang transaction trên giả định build là đủ.

## 5. Test bắt buộc

- CRUD owner A/B, JWT thiếu/hết hạn, UUID sai; detail ID không có không trả ví mặc định.
- Opening 0, MAX, số âm, phần lẻ, overflow; name blank/100/101 ký tự, tên trùng khi khác hoa thường, create/restore đồng thời.
- Opening 100000 + chi 60000 → balance 40000; sửa opening 80000 → 20000; sửa opening 50000 bị 409 và vẫn giữ 20000/opening 80000.
- Archive ví còn tiền giữ nguyên balance/history; DELETE lại không đảo tiền; archived không được chọn tạo giao dịch; restore trùng tên bị 409.
- Hai writer expense/opening edit đồng thời không thể commit balance âm; kiểm tra với PostgreSQL thật, có fixture ledger để kiểm chứng dù CRUD transaction chưa expose.
- Android wallet list/detail/edit và finance picker thấy cùng dữ liệu; không double rounding, không sample data giữa hai user, giữ form khi timeout/422/409.

## 6. Definition of done

Các WAL-01–08 đạt; migration chạy trên DB mới và DB nâng cấp; API/OpenAPI đúng contract; ownership, archive và balance tests pass; Android CRUD chạy với backend thật và data tồn tại qua restart/login lại. Không cần cache, chuyển ví hoặc budget API để nghiệm thu Wallet.
