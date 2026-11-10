# Kế hoạch CRUD Budget

Ngày 07/11/2026. Requirement và kế hoạch theo [contract tài chính](../api/FINANCE_CRUD_CONTRACT.md), mục 1 và 4. [Category CRUD](category-crud-plan.md) là phụ thuộc tạo ngân sách. Usage đọc ledger hiện có; không cần đợi API transaction hoàn thành để viết test hoặc trả 0 khi user chưa có giao dịch. Chỉ nghiệm thu usage dữ liệu thật đầy đủ sau khi [Transaction](transaction-implementation-plan.md) tích hợp xong.

## 1. Hiện trạng và phạm vi

- DB budgets có owner/category, period_start/end dạng DATE, limit_minor, archived_at và exclusion constraint chống kỳ chồng lấn.
- Android đã có BudgetList/Detail/Form, API list/create/update/delete và tính usage local từ FinanceBook; backend chưa có Budget CRUD.
- Scope: ngân sách tháng theo danh mục chi, create/list/detail/update/archive, usage/remaining/status từ server. Không giữ tiền trong budget, không rollover, không ngân sách nhiều tháng/tuần, không tạo notification delivery trong đợt CRUD.

## 2. Requirements

| ID | Yêu cầu | Nghiệm thu |
|---|---|---|
| BUD-01 | Tạo ngân sách tháng | Category EXPENSE active cùng owner, month YYYY-MM, limit VND nguyên dương; 201 DTO |
| BUD-02 | Không trùng kỳ | Một budget active/user/category/month; create/update đồng thời vẫn 409 khi trùng |
| BUD-03 | List/detail theo owner | Lọc month/category/lifecycle; archived detail đọc được; category khác owner hoặc budget khác owner không leak |
| BUD-04 | Update | Đổi category/month/limit với validation tạo mới; giữ nguyên category archived được sửa limit/month; budget archived không sửa được |
| BUD-05 | Delete | Archive idempotent 204; không đụng transaction/entries/balance; có thể tạo budget mới cùng kỳ sau archive |
| BUD-06 | Usage đúng ledger | Chỉ EXPENSE POSTED cùng category/user/tháng theo timezone user; mọi ví kể cả archived/excluded assets; VOIDED/INCOME không tính |
| BUD-07 | Remaining/status chính xác | remaining có thể âm; threshold lấy settings user; EXCEEDED tại spent >= limit; không dùng phần trăm đã làm tròn để xét status |
| BUD-08 | CRUD độc lập, dữ liệu nhất quán | Form/list budget không phụ thuộc tải tất cả transaction pages; response usage được aggregate ở server trên snapshot nhất quán |
| BUD-09 | Không chặn chi vì budget | Vượt limit vẫn ghi expense nếu ví đủ tiền; wallet nonnegative là quy tắc khác |

Threshold dùng `notification_settings.budget_alert_threshold_percent` (default 80), không thêm threshold riêng trên mỗi budget. Tắt alert không ẩn usage/status. Cảnh báo tự động và dedup được làm ở đợt Notifications sau.

## 3. API, dữ liệu và công thức

| Thao tác | Endpoint | Response |
|---|---|---|
| List | GET `/api/v1/budgets?month=2026-11&status=ACTIVE` | 200 array; month/categoryId optional, status default ACTIVE |
| Detail | GET `/api/v1/budgets/{budgetId}` | 200 DTO và usage |
| Create | POST `/api/v1/budgets` | 201 DTO và usage có sẵn nếu đã có expense |
| Edit | PUT `/api/v1/budgets/{budgetId}` | 200 DTO/usage đã tính lại |
| Delete | DELETE `/api/v1/budgets/{budgetId}` | 204 archive |

POST/PUT yêu cầu categoryId, month, limitMinor. Response giữ budgetId/categoryId/month/limitMinor và thêm spentMinor, remainingMinor, usagePercent, warningThresholdPercent, status, archivedAt, createdAt, updatedAt. Không restore budget ở scope này; archive rồi tạo mới khi cần.

- Month lưu DATE `[ngày đầu tháng, ngày đầu tháng sau)`. Usage chuyển biên tháng sang UTC bằng timezone preferences **hiện tại**; đổi timezone có thể làm usage lịch sử thay đổi quanh ranh giới ngày/tháng.
- `spent = sum(EXPENSE POSTED amount)`, `remaining = limit-spent`, `usagePercent = spent*100/limit` làm tròn 2 chữ số, không cap ở 100.
- Status: spent >= limit → EXCEEDED; nếu chưa đạt limit nhưng spent*100 >= limit*threshold → WARNING; còn lại SAFE. Tính với integer/BigDecimal chính xác; intermediate tránh long overflow.
- Archive chỉ thay archived_at; không tạo bút toán hoàn tiền. Response status là usage, lifecycle dựa archivedAt; query status là ACTIVE/ARCHIVED/ALL.

## 4. Kế hoạch triển khai cụ thể

1. **Schema và test persistence.** Rà dữ liệu kỳ cũ có phải tròn tháng, category type đúng và không chồng lấn; báo dữ liệu lệch trước cutover. Tận dụng FK/exclusion constraint, chỉ thêm migration nếu cần enforce kỳ tháng sau khi dữ liệu được đối chiếu. Không sửa migration đã áp dụng.
2. **Repository.** Scoped CRUD với JDBC và lookup category; query month từ period_start; aggregate expense theo category/UTC range, không đọc từng transaction trong Java, không tạo N+1 cho list budget. Dùng query chung/snapshot nhất quán cho limit/usage/settings của một response.
3. **Service.** Parse YearMonth strict, validate VND/limit/category, khóa budget khi sửa/xóa và category khi kiểm tra trạng thái/type. Constraint conflict map 409 BUDGET_PERIOD_CONFLICT; hỗ trợ update loại trừ chính ID hiện tại. Archive idempotent, PUT archived 409; không gọi transaction delete.
4. **Usage và preferences.** Dùng IANA timezone, hỗ trợ DST; threshold từ notification settings. Có ledger fixture để test usage trước khi Transaction API xong; user không có transaction trả zero thật từ DB, không hardcode zero để che API thiếu.
5. **Controller/OpenAPI.** Thêm list/detail/create/update/delete, direct DTO, Location/204/error codes. Query category khác owner trả 404. Không nhận arbitrary periodStart/periodEnd, threshold hay userId từ client.
6. **Android.** Thêm API detail/filter/response usage; BudgetRepository/ViewModel hoặc đường read riêng trong cấu trúc hiện có để không tải tất cả pages transaction. Reuse UI; hiện remaining/status server, không overwrite bằng phép tính FinanceBook thiếu dữ liệu. Category picker chỉ EXPENSE active; vẫn hiển thị tên archived ở chi tiết. Giữ form khi conflict/network/rotation, refresh sau save/delete.
7. **Tích hợp Transaction.** Sau khi có mutation thật, xác minh create/edit/category-month move/delete expense thay usage ngay; income không đổi usage. Refresh budget khi quay lại màn, không dùng cache usage làm nguồn sự thật. Không cần chờ notification delivery để nghiệm thu CRUD, nhưng ghi rõ chưa hoàn thành bước alert của UC-005.

## 5. Test bắt buộc

- Limit 0/âm/phần lẻ/vượt max; month sai format, leap February, December sang January, năm ngoài range; category INCOME/archived/khác owner.
- Concurrent create/update cùng category/month chỉ một budget active; thay đổi chính budget hiện tại không tự conflict; khác category/tháng hoặc sau archive tạo mới được.
- Limit 100000, threshold80: spent 0/79999 → SAFE; 80000 → WARNING; 100000/150000 → EXCEEDED; remaining 0/-50000. Không dùng rounded usage để quyết định threshold; threshold100 và notification disabled đúng quy tắc.
- Expense cuối tháng theo user timezone, DST, đổi timezone, VOIDED và INCOME bị loại; archived wallet/category vẫn tính expense lịch sử. Read không trộn hai user.
- Sửa limit dưới spent được phép; xóa budget không tăng balance hay xóa expense; delete lặp lại không thay dữ liệu tài chính.
- Android list/detail và server cùng số liệu khi history có nhiều trang; không phụ thuộc tải toàn bộ ledger; giữ input khi 409/422 và session đổi giữa request.

## 6. Definition of done

BUD-01–09 đạt; PostgreSQL constraint/aggregate/ownership tests pass; Android CRUD dùng backend thật. Đối soát usage với ledger fixture trước, rồi expense CRUD thật sau. Test report phân biệt rõ CRUD/usage hoàn thành với Notifications còn làm sau; không báo hoàn thành budget alerts khi chỉ có status UI.
