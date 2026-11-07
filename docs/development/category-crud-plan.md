# Kế hoạch CRUD Category

Ngày 07/11/2026. Requirement và kế hoạch triển khai theo [contract tài chính](../api/FINANCE_CRUD_CONTRACT.md), mục 1 và 3. Là phụ thuộc bắt buộc của [Transaction](transaction-implementation-plan.md) và [Budget](budget-crud-plan.md); có thể hoàn thiện cùng đợt với [Wallet](wallet-crud-plan.md).

## 1. Hiện trạng và phạm vi

- DB có categories với user_id NOT NULL, unique active name theo user/type, icon_key, color_hex, sort_order, archived_at và FK từ transactions/budgets.
- Backend chưa có Category CRUD. Android CategoryRepository/ViewModel quản lý sample singleton in-memory, trong khi picker finance dùng API riêng. Không coi UI hiện có là backend đã xong.
- Scope: danh mục INCOME/EXPENSE, CRUD bằng archive/restore, seed ban đầu theo user và metadata hiển thị. Không có global category, ownerless record, read-only default category hoặc phân cấp category.

## 2. Requirements

| ID | Yêu cầu | Nghiệm thu |
|---|---|---|
| CAT-01 | Mỗi category thuộc đúng một user | Cả seeded và tự tạo có owner; API không đọc/sửa/xóa/restore category user khác |
| CAT-02 | Seed một lần | User mới/user cũ chưa từng có category có bộ danh mục trong contract; user cũ có dữ liệu giữ nguyên; rename/archive không bị seed lại khi login hoặc GET |
| CAT-03 | Create/list/detail | Tên/type/icon/color hợp lệ; lọc type/lifecycle; detail archived vẫn đọc được |
| CAT-04 | Update | Sửa tên/icon/color/order; chỉ đổi type nếu chưa có transaction/budget nào tham chiếu, kể cả VOIDED/archived |
| CAT-05 | Archive/restore | Không hard-delete, không cascade history; archive lặp lại 204; restore kiểm tra active-name uniqueness |
| CAT-06 | Uniqueness | Active name duy nhất theo user/type/case-insensitive; cùng tên ở INCOME và EXPENSE được phép |
| CAT-07 | Picker đúng trạng thái | Tạo transaction/budget chỉ chọn category active cùng type; chi tiết lịch sử vẫn hiện tên archived |
| CAT-08 | Android cùng nguồn | Màn list/add/edit và transaction/budget picker thấy cùng dữ liệu server; icon dùng string key, không gửi Android resId lên API |

Seeded categories được chỉnh sửa/archive như category khác. Không cần `isDefault` để phân quyền. Archive category không làm thay đổi balance, không loại các transaction POSTED khỏi budget usage và không tự archive budget đang tham chiếu.

## 3. API và payload

| Thao tác | Endpoint | Response |
|---|---|---|
| List | GET `/api/v1/categories?status=ALL&categoryType=EXPENSE` | 200 array; cả hai query optional |
| Detail | GET `/api/v1/categories/{categoryId}` | 200 DTO |
| Create | POST `/api/v1/categories` | 201 DTO |
| Edit | PUT `/api/v1/categories/{categoryId}` | 200 DTO |
| Status | PATCH `/api/v1/categories/{categoryId}/status` | Body active boolean; 200 DTO |
| Delete | DELETE `/api/v1/categories/{categoryId}` | 204 archive |

POST/PUT: name, categoryType, iconKey nullable, colorHex nullable, sortOrder. Response bổ sung categoryId, archivedAt, createdAt, updatedAt. Không có description trong scope; ẩn field chưa có backend thay vì báo đã lưu. Chi tiết quy tắc field/default/error ở contract mục 3.

## 4. Kế hoạch triển khai cụ thể

1. **Seed và migration.** Dùng danh sách seed cố định trong contract. Thêm `categories_initialized BOOLEAN NOT NULL DEFAULT FALSE` vào user_preferences bằng migration mới; field nội bộ, không cho client sửa. Với user cũ: transaction khóa row preferences, nếu chưa initialized thì kiểm tra mọi category kể cả archived; có dữ liệu thì chỉ đánh dấu true, không thêm seed để tránh khôi phục danh mục người dùng đã ẩn/đổi tên. Chưa từng có category thì insert bộ seed và đánh dấu true trong cùng transaction. Batch backfill có thể chạy lại an toàn; không xóa hoặc đổi owner dữ liệu cũ. User mới seed trong registration transaction và đặt marker ngay. Backfill và Category writer cùng khóa marker/preferences khi khởi tạo lần đầu để không đua tạo danh mục.
2. **Persistence.** CategoryRepository JDBC: scoped list/detail, insert/update/archive/restore; kiểm tra reference ở cả transactions và budgets, không lọc mất VOIDED/archived khi quyết định đổi type. Dùng unique index hiện có để bảo vệ concurrent create/restore; normalize tên trước khi ghi.
3. **Service.** Validate enum/name/icon/color, ownership và lifecycle; map CATEGORY_NAME_CONFLICT/CATEGORY_TYPE_IN_USE/CATEGORY_ARCHIVED. Khóa row category khi đổi type/archive/restore, phối hợp cùng transaction/budget writer: writer phải khóa category trước khi validate và ghi reference. Không check references ngoài transaction rồi cho writer chèn vào giữa.
4. **API/OpenAPI.** DTO riêng create/update/status/response; JSON field tương thích categoryId/categoryType hiện có; nullable icon/color cho phép clear; không nhận userId/isDefault. Bộ icon key có mapping UI và fallback icon nếu nhận key chưa biết.
5. **Android.** Thay CategoryRepository sample bằng server trong flavor thật; tái sử dụng list/add/edit UI, icon mapper và ViewModel. Bỏ ID giả/fallback category đầu tiên. Liên kết switch trạng thái tới status endpoint; delete dùng archive, thêm restore; lỗi conflict giữ form và giải thích field. Picker chọn theo type/ACTIVE, history lookup vẫn ALL.
6. **Kiểm chứng quan hệ.** Seed nhiều user có UUID khác nhau; cập nhật category không đổi owner. Test archive/type change đua với create transaction/budget bằng fixture persistence dù các API sau chưa hoàn thành.
7. **Nghiệm thu.** Backend unit + PostgreSQL constraints/concurrency + Retrofit JSON + device/backend thật. Category CRUD không cần chờ API budget hoặc transaction có thể truy cập từ UI.

## 5. Test bắt buộc

- Tất cả endpoint với owner khác trả 404; không thể tự gắn category của user khác vào transaction/budget.
- Registration atomic: lỗi seed không để lại user/seed nửa chừng; backfill retry không duplicate; GET không tạo lại danh mục đã archive/rename; seed không sửa category tùy chỉnh cùng tên.
- Name Unicode/trim/case; khác type được cùng tên; conflict concurrent POST/reactivate. Icon null/quá dài; color invalid/normalize uppercase.
- Type chưa dùng đổi được; đã có transaction POSTED hoặc VOIDED/budget active hoặc archived thì 409. Race type change và create reference không làm dữ liệu type không hợp lệ.
- Archive không đổi số dư/usage/lịch sử; không chọn archived cho create. Restore không sửa transaction/budget cũ và không vượt qua uniqueness.
- Android tạo category từ picker quay lại form còn amount/date/wallet; list quản lý thấy category vừa tạo; switch trạng thái và icon được lưu qua restart và giữa thiết bị.

## 6. Definition of done

CAT-01–08 đạt, schema không có ownerless category, user mới/cũ đều khởi tạo đúng một lần, CRUD/lifecycle/ownership được test và Android dùng API thật. Không có yêu cầu thiết kế hay migration danh mục toàn cục còn treo trong đường triển khai Transaction/Budget.
