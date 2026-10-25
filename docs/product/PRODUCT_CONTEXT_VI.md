# PRODUCT_CONTEXT_VI.md
# Đồng — Tầm nhìn sản phẩm và định hướng triển khai

> Phiên bản 0.1 · 25/10/2026 · Tài liệu thống nhất định hướng, **không phải báo cáo hoàn thành code**.  
> Figma: https://www.figma.com/design/MkLZjSwfzQNYKvfksOwjD0  
> Bản tiếng Anh cho Coding Agent: `PRODUCT_CONTEXT.md`

## 1. Tầm nhìn

**Đồng** là ứng dụng quản lý tài chính cá nhân giúp người dùng **hiểu từng đồng và chủ động trong chi tiêu mỗi ngày**, mà **không phải dành nhiều thời gian để quản lý tiền**.

**Đã chốt:** tên thương hiệu **Đồng**, khái niệm trải nghiệm **Khoản**, và hướng **code nghiệp vụ tài chính cốt lõi trước, bổ sung lớp trải nghiệm Khoản sau**.

**Thông điệp đề xuất:** *Hiểu từng đồng. Chủ động mỗi ngày.*

Đồng không hướng đến việc bắt người dùng hiểu các thuật ngữ ví, ngân sách, quỹ, mục tiêu tiết kiệm mới có thể ghi thu/chi. Ứng dụng phải dễ dùng ngay từ ngày đầu, nhưng vẫn có chiều sâu khi người dùng cần.

### Cam kết sản phẩm

- Ghi thu/chi nhanh, ít bước, ít trường.
- Thấy rõ tiền đang có, tiền có thể chi (khi tính được chính xác), việc cần chú ý.
- Kiểm soát chi tiêu không cần bắt buộc lập ngân sách.
- Dễ duy trì động lực tiết kiệm.
- Theo dõi các khoản định kỳ, nợ, chia tiền và các mục tiêu khi cần.
- Báo cáo đưa ra kết luận và hành động, không chỉ là biểu đồ.

## 2. Người dùng mục tiêu

| Nhóm | Nhu cầu |
|---|---|
| Học sinh/sinh viên | Tiền sinh hoạt, ăn uống, đi lại, học phí, nhiều khoản nhỏ |
| Người trẻ đi làm | Lương, trọ, thuê bao, mua sắm, mục tiêu tích lũy |
| Người đi làm bận rộn | Nhiều nguồn tiền, chi phí chung, nợ, khoản định kỳ |
| Người quản lý tài chính kỹ | Hạn mức, phân bổ, báo cáo sâu, nhưng không muốn giao diện rối |

**Trọng tâm:** người trẻ và người ít thời gian, nhưng không giới hạn đối tượng sử dụng.

**Vấn đề cốt lõi:** ghi chép tốn thời gian; quá nhiều khái niệm; biết số dư nhưng không biết có thể chi bao nhiêu; dễ quên khoản định kỳ; thiếu động lực tích lũy; nhiều biểu đồ nhưng ít chỉ dẫn cụ thể.

## 3. Nguyên tắc thiết kế

1. **Ít thao tác nhất có thể**, nhưng luôn có cách sửa thông tin.
2. **Đơn giản hóa giao diện, không đơn giản hóa sai nghiệp vụ.**
3. **Ngôn ngữ tiếng Việt tự nhiên**, không ép học thuật ngữ tài chính.
4. **Mặc định thông minh:** gợi ý danh mục/nguồn tiền gần đây, có thể đổi.
5. **Home trả lời câu hỏi quan trọng**, không phải bảng điều khiển dày đặc.
6. **Tính năng nâng cao mở khi cần**, không cản trở người dùng cơ bản.
7. **Khuyến khích không phán xét:** không dùng cảnh báo gây áp lực.
8. **Dễ tiếp cận:** màn hình nhỏ, số tiền dài, bàn phím, chữ tiếng Việt, trạng thái lỗi/trống.
9. **An toàn dữ liệu:** dữ liệu tài chính riêng tư, kiểm tra quyền truy cập.
10. **Không bắt buộc ngân sách khi ghi giao dịch** là mặc định đề xuất, chờ chốt cuối.

## 4. “Khoản” là gì?

**Khoản** là một việc hoặc phần tiền bạc mà người dùng muốn **theo dõi, kiểm soát hoặc lên kế hoạch**.

| Khoản | Người dùng quan tâm | Dữ liệu bên dưới |
|---|---|---|
| Ăn uống | Còn được chi bao nhiêu? | Danh mục, giao dịch, hạn mức tùy chọn |
| Tiền trọ | Khi nào cần trả? | Lịch định kỳ, giao dịch |
| Mua laptop | Đã để dành được bao nhiêu? | Mục tiêu, phân bổ |
| Minh còn nợ | Còn bao nhiêu chưa nhận? | Khoản phải thu, thanh toán |
| Du lịch | Đã chuẩn bị được bao nhiêu? | Mục tiêu/kế hoạch chi |

**Phân biệt bắt buộc:**
- **Giao dịch:** một sự kiện thu/chi/chuyển tiền.
- **Tài khoản/Ví:** nơi tiền thực sự được giữ.
- **Danh mục:** cách phân loại giao dịch.
- **Ngân sách:** hạn mức/kế hoạch.
- **Khoản:** cách người dùng nhìn và quản lý điều mình quan tâm.

Các ý định tạo Khoản đề xuất: **Kiểm soát chi tiêu; Dành tiền cho mục tiêu; Nhớ khoản định kỳ; Theo dõi tiền nợ**. Chúng không nhất thiết là các lớp nghiệp vụ tách biệt tuyệt đối: tiền trọ vừa là chi tiêu vừa là định kỳ.

**Không được:** đổi tên toàn bộ ví/ngân sách/danh mục thành Khoản; gộp tất cả thành một bảng; lưu số dư thứ hai trong Khoản. Khoản là **lớp trải nghiệm và điều phối**, không phải nguồn dữ liệu tài chính gốc.

## 5. Nghiệp vụ và kiến trúc

### Đối tượng nền

| Đối tượng | Vai trò | Giai đoạn |
|---|---|---|
| User/Auth | Danh tính, phân quyền | Core |
| Account/Wallet | Nguồn tiền: tiền mặt, ngân hàng, tiết kiệm | Core |
| Transaction | Thu, chi, chuyển tiền, điều chỉnh | Core |
| Category | Phân loại | Core |
| Budget/SpendingPlan | Hạn mức theo thời gian/phạm vi | Core cơ bản |
| SavingsGoal/Allocation | Mục tiêu và tiền dành riêng | Sau |
| Debt/Receivable | Nợ phải thu/phải trả | Sau |
| RecurringRule | Khoản định kỳ | Sau |
| MoneyFocus | Tổng hợp hiển thị Khoản | Sau core |

### Quy tắc tài chính không được sai

- Chuyển tiền giữa các tài khoản của chính người dùng không tạo thu nhập/chi tiêu mới.
- **Tổng tiền**, **tiền đã dành**, **tiền có thể chi** là các đại lượng khác nhau.
- Sửa/xóa giao dịch phải cập nhật số dư/báo cáo nhất quán.
- Đổi/xóa danh mục không được làm mất lịch sử.
- Nhận lại tiền cho vay không mặc nhiên là thu nhập mới.
- Đến hạn định kỳ không có nghĩa đã thanh toán.
- Không dùng `float/double` cho tiền; cần chốt độ chính xác, tiền tệ và múi giờ.
- Mọi dữ liệu tài chính phải được kiểm tra quyền sở hữu ở backend.
- Cần quy tắc rõ cho hoàn tiền, chia tiền, số dư ban đầu và thanh toán một phần.

### Công nghệ theo ngữ cảnh đã trao đổi

- **Backend:** Java + Spring Boot + PostgreSQL; JWT access/refresh.
- **Android:** Java + XML; định hướng MVVM + Repository.
- **Web Admin:** một nhánh riêng; framework và quyền quản trị cần kiểm tra repo.
- **Thiết kế:** Figma; viewport tham chiếu khoảng 360×800.
- **Quy trình:** GitHub, feature branch, PR và review.

Đây là thông tin từ quá trình trao đổi, **chưa phải kiểm tra mã nguồn hiện tại**. Mọi agent phải đọc repo trước khi code.

### Hướng triển khai

**Domain-first, Experience-later, Experience-aware:** xây đúng Account, Transaction, Category, Budget trước; Android có thể gọi API nghiệp vụ trực tiếp. Sau khi ổn định, thêm `MoneyFocusService` để tổng hợp và điều phối, không viết lại các service cũ.

Backend nên tổ chức modular monolith, tách Controller/Application Service/Domain/Repository; API trả DTO; thay đổi database qua migration. Android dùng ViewModel + Repository, không đặt phép tính tài chính trong Activity/Fragment.

## 6. Tình trạng Figma đã kiểm kê (25/10/2026)

File có **Android App** và **Web Admin**. Android có **36 frame màn hình**:

| Khu vực | Số frame |
|---|---:|
| Authentication | 6 |
| Home và setup | 6 |
| Transactions | 5 |
| Wallets | 4 |
| Categories | 5 |
| Budgets | 4 |
| Analytics | 1 |
| Notifications | 2 |
| Settings | 3 |

Section Components có **35 component/component set cấp đầu**, gồm Button, Card/Transaction, Card/Wallet, Card/Category, Field/Selector, Control/Segmented, Navigation và BudgetForm.

**Lưu ý quan trọng:** Đây chỉ là kiểm kê cấu trúc, **không khẳng định các màn đã hoàn thiện**. Có hai frame tên `03_CategoryAdd`; chưa thấy section Khoản riêng; chưa thấy frame Transaction Edit riêng (có thể dùng chung form). Cần kiểm tra sâu prototype, biến thiết kế, auto-layout, accessibility và chất lượng màn hình.

**Quy tắc:** Không xóa/chỉnh hàng loạt thiết kế cũ trước khi audit. Giữ bản tham chiếu, tái sử dụng component, kiểm tra bằng screenshot trước/sau.

## 7. Nhận diện Đồng và UI/UX

Phong cách **Friendly Minimal Finance**: tối giản nhưng không lạnh lùng, gần gũi nhưng không trẻ con, đáng tin nhưng không quá doanh nghiệp.

**Token đề xuất để thử nghiệm, chưa chốt:**
- Brand green: `#246B43`
- Accent: `#4CAF50`
- Background: `#F6F8F5`
- Text: `#1D2922`
- Spacing: bội số 4dp (thường 8/12/16/24)
- Radius: input 12dp, card 16dp
- Icon: Lucide 20–24dp
- Font: Inter/Roboto hỗ trợ tiếng Việt

Ưu tiên phân cấp rõ, khoảng trắng, số tiền dễ đọc, ít shadow, ít card lồng nhau, animation tiết chế. Không tô đỏ mọi khoản chi vì chi tiêu không phải lỗi.

**Logo:** tên đã chốt nhưng artwork cuối chưa được phê duyệt trong tài liệu này.

### Điều hướng đề xuất

`Tổng quan | Lịch sử | + | Khoản | Cài đặt`

- **Tổng quan:** tiền đang có, tiền có thể chi nếu tính đáng tin, tóm tắt tháng, việc cần chú ý.
- **Lịch sử:** tìm, lọc, xem, sửa giao dịch.
- **+:** ghi thu/chi nhanh, chuyển tiền, chụp hóa đơn khi có.
- **Khoản:** ưu tiên các khoản ghim, gần hạn, cần chú ý; có tìm kiếm/lọc.
- **Cài đặt:** hồ sơ, thông báo, bảo mật, nguồn tiền, danh mục.

Nguồn tiền phải truy cập được từ Home, không chỉ nằm trong Settings.

### Luồng ưu tiên

**Ghi giao dịch:** `+` → số tiền → danh mục → lưu; nguồn tiền/ngày có mặc định hợp lý và vẫn sửa được. Cho phép thêm nhanh danh mục/nguồn tiền mà không mất bản nháp.

**Tạo Khoản:** chọn điều muốn quản lý → nhập tối thiểu → thấy trạng thái và hành động tiếp theo.

**Báo cáo Story (sau):** báo cáo tuần/tháng dạng slide tương tác, tập trung insight và hành động; không thay thế lịch sử giao dịch.

## 8. Phạm vi chức năng

**Core:** Auth, nguồn tiền, danh mục, thu/chi/chuyển tiền, lịch sử, sửa/xóa, hạn mức cơ bản, Home.

**Tiết kiệm thời gian:** Quick Add, gợi ý, tạo danh mục/nguồn tiền ngay trong form, ghi một chạm hằng ngày, OCR hóa đơn có bước kiểm tra, chia tiền.

**Nâng cao:** nợ, mục tiêu tiết kiệm, phân bổ, khoản cố định/linh hoạt/phát sinh, định kỳ, báo cáo Story.

**Không làm đồng loạt.** Mỗi tính năng phải có quy tắc nghiệp vụ, API, UI, kiểm thử và xử lý lỗi.

## 9. Lộ trình đề xuất

### P0 — Audit và thống nhất
- Đọc repo Backend/Android/Web Admin.
- Claude Code + Figwright audit Figma **read-only**.
- Kiểm kê màn hình, component, API, database.
- Chốt quy tắc tài chính, glossary, ADR, backlog.
- **Đầu ra:** báo cáo hiện trạng và quyết định còn thiếu.

### P1 — Hoàn thiện nghiệp vụ cốt lõi
- User/Auth, Account/Wallet, Category, Transaction.
- Thu/chi/chuyển tiền/sửa/xóa, hạn mức cơ bản.
- Migration, API, kiểm thử, phân quyền.
- **Đầu ra:** core đáng tin, API ổn định.

### P2 — Hoàn thiện Android cơ bản
- Chuẩn hóa design system Đồng.
- Redesign Home, Transaction Create/Edit/List, Wallet, Category, Budget.
- Rút gọn onboarding, Quick Add, xử lý keyboard/empty/loading/error.
- **Đầu ra:** người dùng ghi và quản lý thu/chi hằng ngày được.

### P3 — Thêm trải nghiệm Khoản
- `MoneyFocusService`/read model.
- Khoản List/Detail/Create/Edit, **Khoản chi tiêu trước**.
- Tích hợp Home/navigation, tái sử dụng nghiệp vụ cũ.
- **Đầu ra:** trải nghiệm thống nhất, không trùng dữ liệu tiền.

### P4 — Nghiệp vụ nâng cao
Thứ tự gợi ý: **định kỳ → tiết kiệm → nợ → chia tiền → OCR → ghi một chạm → Story**.

### P5 — Hoàn thiện vận hành
Bảo mật, hiệu năng, backup, monitoring, accessibility, kiểm thử người dùng, rollback/migration, chuẩn bị phát hành.

**Phase thể hiện thứ tự phụ thuộc, không phải cam kết số tuần.**

## 10. Trách nhiệm các nhánh

| Nhánh | Trọng tâm |
|---|---|
| Backend | Domain, database, API, auth, tài chính chính xác, test |
| Android | Luồng người dùng, UI, ViewModel/Repository, state, tích hợp |
| Web Admin | Nghiệp vụ quản trị hợp lệ, hỗ trợ vận hành, phân quyền, dashboard |

Để cân bằng công việc, thành viên Web Admin **có thể** đảm nhận thêm CI/CD, API contract tests, QA automation, tài liệu vận hành hoặc observability theo thỏa thuận. Không mặc định admin được xem dữ liệu tài chính riêng tư.

Quy trình: issue → feature branch → PR → review → merge; thay đổi API/schema phải thông báo và cập nhật tài liệu; quyết định ảnh hưởng nhiều nhánh cần ADR.

## 11. Claude Code + Figwright

- **P0:** kiểm kê, screenshot, audit, **không chỉnh sửa**.
- **P1:** chuẩn hóa token/component, giữ bản cũ.
- **P2:** Home → Transaction Form → Transaction List → Wallet/Category/Budget → Onboarding.
- **P3:** Khoản List/Detail/Create/Edit, ưu tiên chi tiêu.
- **P4:** kiểm tra screenshot, prototype, màn hình nhỏ, số tiền dài, trạng thái lỗi/trống, bàn phím.

Không giao một prompt “vẽ lại toàn bộ app”. Chia phase nhỏ, có đầu ra và điểm dừng để nhóm review.

## 12. Tiêu chí nghiệm thu

**Sản phẩm:** người mới ghi giao dịch nhanh; Home rõ; không bắt hiểu thuật ngữ; có thể mở rộng khi cần; không gây áp lực.

**Kỹ thuật:** không tính tiền hai lần; kiểm thử sửa/xóa/chuyển tiền; tiền chính xác; quyền truy cập đúng; không tạo nguồn dữ liệu trùng; API và migration rõ.

**Thiết kế:** component/tokens nhất quán; accessibility; trạng thái đầy đủ; screenshot/prototype; không đánh dấu hoàn thành chỉ vì có frame.

## 13. Quyết định cần chốt tiếp

| Vấn đề | Đề xuất hiện tại |
|---|---|
| Ngân sách bắt buộc? | Không |
| Chọn nguồn tiền khi ghi? | Gợi ý gần nhất/mặc định, có thể đổi |
| Tự tạo Khoản từ danh mục? | Có thể suy ra, không ép thiết lập |
| Khoản có liên kết nhiều nghiệp vụ? | Có, theo quan hệ linh hoạt |
| Tiết kiệm là tài khoản hay phân bổ? | Phân biệt tiền thực và tiền dành riêng |
| Độ chính xác tiền, timezone? | Chốt trước khi lưu |
| Offline? | Chờ audit |
| Logo/token cuối? | Duyệt trên Figma |
| Web Admin được phép làm gì? | Đặc tả quyền tối thiểu |
| OCR/Story? | Sau core/MVP |

Các dòng trên **chưa phải tất cả đã chốt**.

## 14. Cách dùng tài liệu

- **Cả nhóm:** đọc mục 1–4 để thống nhất tầm nhìn; mục 5–9 để hiểu thiết kế và lộ trình.
- **Coding Agent:** đọc `PRODUCT_CONTEXT.md` tiếng Anh, **sau đó đọc repo thật**. Phân biệt **đã kiểm chứng / đã thống nhất / đề xuất / chưa biết**.
- **Designer:** dùng tài liệu để đánh giá luồng, component, ngôn ngữ, và khả năng mở rộng.

> **Nguyên tắc cuối:** Đồng giúp người dùng quản lý tiền tốt hơn mà không khiến họ cảm thấy phải dành quá nhiều thời gian để quản lý tiền. Nếu chỉ có 15 giây, họ vẫn nên ghi được một giao dịch hoặc biết việc tài chính tiếp theo cần làm.
