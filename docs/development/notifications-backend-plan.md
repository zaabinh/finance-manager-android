# Kế hoạch backend Notifications và email tài chính

Ngày lập 08/11/2026. Backend đã triển khai trên nhánh riêng `feature/notification`, chưa merge/push. Contract cuối cùng: [NOTIFICATIONS.md](../api/NOTIFICATIONS.md); rollout: [NOTIFICATIONS.md](../deployment/NOTIFICATIONS.md); kết quả: [NOTIFICATIONS_IMPLEMENTATION_REPORT.md](../../NOTIFICATIONS_IMPLEMENTATION_REPORT.md). Android, App Links và nối unread vào Dashboard là phần handoff tiếp theo; không thay đổi auth/CRUD contract. Nội dung bên dưới giữ nguyên yêu cầu và quyết định thiết kế ban đầu để đối chiếu.

## 1. Hiện trạng và dependency

Đã đọc [bốn CRUD plans](finance-execution-plan.md), [contract tài chính](../api/FINANCE_CRUD_CONTRACT.md), `NotificationActivity`, `HomeDashboardActivity`, `EmailTemplateRenderer`, `EmailSenderService`, `ResendEmailSenderService`, `EmailVerificationService`, các email templates/tests và migration V6.

- Android notification list hiện tạo n1/n2/n3 cố định; all-read lưu một boolean SharedPreferences trên thiết bị, không theo user, không đồng bộ nhiều máy. Chưa có inbox backend, pagination, persistent per-item read hoặc deep link thật.
- V6 có notification_settings và API User/Profile envelope cho các toggle/time/threshold; chưa có email finance opt-in, notifications table, dedup state, scheduler cursor hoặc outbox.
- Auth đã dùng layout chung, logo, HTML escaping, username fallback “bạn”, và provider `send(recipient,subject,htmlBody,deliveryId)` với idempotency header `email/<deliveryId>`. Listener AFTER_COMMIT của auth hiện best-effort, không phải durable queue. Không sao chép listener đó để coi email tài chính là durable.
- Phụ thuộc Wallet/Category/Transaction/Budget cho usage/target và User/preferences cho timezone/recipient; Notifications không là gate của CRUD. [Home Dashboard](home-dashboard-backend-plan.md) chỉ nối unread khi inbox có sẵn.

Scope: inbox cho DAILY_REMINDER/BUDGET_WARNING/BUDGET_EXCEEDED, read/read-all/unread/delete, daily scheduler, budget risk evaluation, durable email outbox, ba template. Không SMS/push, FCM, queue service mới, marketing, recurring transaction hoặc ghi tiền tự động. Scheduler/worker dùng Spring scheduling + PostgreSQL hiện có; không cần Render worker/Redis queue mới.

## 2. Requirements và chính sách kênh

| ID | Yêu cầu | Nghiệm thu |
|---|---|---|
| NOT-01 | Inbox owner-scoped | List/detail/read/delete không đọc/sửa user khác; UUID unknown/unowned cùng404 |
| NOT-02 | Read-state bền vững | readAt từng item lưu server; read-all không tác động item sinh sau cutoff; count/list snapshot nhất quán |
| NOT-03 | Quyền email rõ ràng | email opt-in mặc định false; user ACTIVE và email_verified=true mới được gửi; không lấy recipient từ client |
| NOT-04 | Budget exact/dedup | Dùng usage/threshold cùng Budget, không xét phần trăm rounded; concurrent evaluator không nhân bản cảnh báo |
| NOT-05 | Daily đúng lịch user | IANA timezone, DST, restart/multi-instance và once-per-user/local-date ở tầng dữ liệu |
| NOT-06 | Durable async delivery | Mutation tài chính không gọi provider; durable outbox, retry cùng deliveryId và lease recovery |
| NOT-07 | Read khác delivery | Email SENT hoặc mở app không tự read inbox; mark read không coi email đã gửi |
| NOT-08 | Template an toàn | Layout/renderer/provider hiện có, escape mọi biến, không OTP/auth secret trong finance emails |
| NOT-09 | Revalidation | Disable/archive/void/settings/recipient đổi trước gửi phải xét lại; stale jobs không gửi mù |
| NOT-10 | Không sai tiền | Notifications chỉ đọc ledger; lỗi email không đảo/mất/chặn transaction đã hợp lệ |

Chính sách đề xuất cho đợt này:

- `notificationsEnabled` là master cho **thông báo tài chính mới** ở cả inbox/email; tắt không xóa lịch sử, không sửa readAt, không thay Budget status.
- DAILY_REMINDER cần dailyReminderEnabled và dailyReminderTime; BUDGET_WARNING cần budgetAlertEnabled; BUDGET_EXCEEDED cần budgetExceedAlertEnabled. `otherNotificationsEnabled` giữ tương thích, chưa phát minh loại thông báo khác sử dụng nó.
- Inbox cần user ACTIVE; email cần thêm emailFinanceEnabled=true và địa chỉ hiện tại verified. Thiết lập email không thay đổi quyền gửi xác minh/reset password: auth emails vẫn theo AuthService riêng, độc lập toàn bộ toggle tài chính.
- Permission POST_NOTIFICATIONS của Android chỉ điều khiển hiển thị OS trên thiết bị; không phải email consent hoặc server inbox gate. UI không tự tắt email vì user từ chối permission Android.
- Khi bật lại master/email/type: chỉ sự kiện mới, không gửi lại lịch sử hoặc phục hồi các outbox đã CANCELLED. Không dùng “chưa đọc” làm điều kiện retry email.

## 3. API và read-state đề xuất

Finance notification APIs dùng direct DTO, error ApiErrorResponse, Bearer/principal theo CRUD. Không nhận userId/recipient/title/body từ client để tự tạo notification. Chưa cung cấp public POST hoặc nút “send test email” production.

| Method | Route/body | Kết quả |
|---|---|---|
| GET | `/api/v1/notifications?page=0&size=20&readStatus=ALL` | 200 items/page/totalPages/unreadCount/snapshotAt |
| GET | `/api/v1/notifications/{id}` | 200 NotificationResponse, không tự mark read |
| GET | `/api/v1/notifications/unread-count` | 200 unreadCount/snapshotAt |
| PATCH | `/api/v1/notifications/{id}/read`, `{"read":true}` | 200 item; idempotent, giữ readAt lần đầu; false chưa hỗ trợ |
| POST | `/api/v1/notifications/read-all`, `{"through":"2026-11-08T08:00:00Z"}` | 200 affectedCount/unreadCount/snapshotAt; cutoff có giới hạn dưới đây |
| DELETE | `/api/v1/notifications/{id}` | 204 soft-delete idempotent; không xóa target hoặc email đã gửi |

page>=0; size1–100 default20; readStatus ALL/READ/UNREAD defaultALL. Sort cố định createdAt DESC, notificationId DESC; page ngoài range rỗng cùng totalPages thực. Không hứa snapshot cố định giữa requests; Android dedup theo ID, refresh page0 sau read/delete. Server ghi createdAt khi INSERT thực hiện bằng statement_timestamp, không dùng thời điểm DB transaction bắt đầu để backdate vào cutoff.

```json
{
  "items": [{
    "notificationId": "11111111-1111-4111-8111-111111111111",
    "type": "BUDGET_WARNING", "title": "Ăn uống đã dùng 85% ngân sách",
    "body": "Tháng 11/2026: đã chi 850.000 đ trên hạn mức 1.000.000 đ.",
    "createdAt": "2026-11-08T07:00:00Z", "readAt": null,
    "amountMinor": 850000,
    "target": {"screen": "BUDGET_DETAIL", "budgetId": "22222222-2222-4222-8222-222222222222", "month": "2026-11"}
  }],
  "page": 0, "totalPages": 1, "unreadCount": 1, "snapshotAt": "2026-11-08T08:00:00Z"
}
```

NotificationResponse gồm id/type/title/body/createdAt/readAt/nullable amountMinor/typed target. Type daily có target `{screen:TRANSACTION_CREATE,localDate:YYYY-MM-DD}` và amountMinor=null. BUDGET_* có BUDGET_DETAIL/budgetId/month; không trả arbitrary URL/HTML/icon resource integer. Nội dung inbox là bản ghi snapshot thông báo lúc phát sinh; detail target vẫn lấy dữ liệu tài chính mới từ API, có thể khác snapshot vì edit/void.

Read-all `through` bắt buộc UTC timestamp lấy từ response snapshotAt gần nhất, không ở tương lai so với DB now; sai cú pháp/future trả400. Cần bảo đảm đồng thời: mọi insertion inbox và read-all cùng lấy advisory transaction lock **theo user** trước insert/cutoff update; read-all transaction READ COMMITTED cập nhật deleted_at IS NULL AND read_at IS NULL AND created_at<=through sau lock bằng statement mới. Insertion commit sau thao tác read-all không bị đọc hộ; insertion commit trước nhưng tạo sau cutoff vẫn unread. Chỉ lock scope inbox, không giữ nó trong financial mutation. Trả count sau update cùng transaction; insert bị chặn đến commit. Không dùng timestamp client tùy ý để đánh dấu notification chưa từng tải ở tương lai.

Soft-delete giữ dedup event key và audit tối thiểu, không sinh lại notification vì user xóa inbox. Lookup deleted owner cho DELETE vẫn204; detail/read deleted404; owner khác404. DELETE hủy outbox chưa gửi gắn item; không thể thu hồi email provider đã nhận. Không có restore/unread toggle trong phiên bản này.

Lỗi:401 auth;400 malformed UUID/query/unknown/read=false/through;404 unknown/unowned/deleted resource;422 dữ liệu ngữ nghĩa hoặc overflow theo shared conventions;500 infrastructure. Failed email không biến list/read thành lỗi provider; delivery status nội bộ không expose địa chỉ/exception. List counts không bao gồm deleted. API client không được thay readAt trực tiếp.

## 4. Email opt-in: đề xuất versioned settings

Không âm thầm thêm email vào DTO hiện có rồi coi đã chốt. Đề xuất tài liệu/OpenAPI **Notifications settings v2**, endpoint mới `GET/PATCH /api/v2/users/me/notification-settings`; giữ envelope User/Profile hiện có. V2 dùng tất cả field V1 và thêm boolean `emailFinanceEnabled`, mặc định false; PATCH ít nhất một field, null giữ nguyên theo semantics V1, strict boolean/type/unknown-field validation. Ví dụ bật email: `{"emailFinanceEnabled":true}`. Response trả tất cả settings và updatedAt.

Hai version cùng một row: V1 PATCH phải chỉ update các cột V1 được cung cấp và **không reset emailFinanceEnabled** đã bật qua V2; không read-merge-write toàn row không khóa làm mất concurrent changes. Dùng row lock và partial merge hoặc UPDATE trường được phép trong cùng transaction. V1 GET không trả field mới, V2 GET có field mới. Bật email khi user chưa verified trả 409 `NOTIFICATION_EMAIL_UNVERIFIED` đề xuất; chủ yếu vẫn revalidate trước send vì recipient có thể đổi sau đó. Không gửi email xác minh ngầm từ settings API. Default/backfill false cho mọi user cũ, không suy consent từ notificationsEnabled hoặc việc từng nhận OTP.

Android cần toggle “Nhận email tài chính” riêng trong Settings/Setup, giải thích địa chỉ tài khoản hiện tại; không gộp với công tắc quyền Android. Contract v2 này cần review/ghi tài liệu trước implementation; không sửa FINANCE_CRUD_CONTRACT của bốn module.

## 5. Persistence và interfaces

Migrations mới chỉ chọn version kế tiếp **khi triển khai**, sau các migration CRUD đang phát triển; không đặt sẵn V10 hoặc sửa V6. Audit existing rows/preferences/timezones trước constraint/backfill. Đề xuất:

1. `notification_settings.email_finance_enabled BOOLEAN NOT NULL DEFAULT FALSE`.
2. `notifications`: UUID id/user_id FK, type allowlist, event_key UNIQUE(user_id,event_key), title/body snapshot text bounded, payload JSONB schema version, target fields/payload typed, amount_minor nullable, created_at/read_at/deleted_at. Index `(user_id,created_at DESC,id DESC)` và partial unread `(user_id) WHERE read_at IS NULL AND deleted_at IS NULL`.
3. `notification_outbox`: delivery_id UUID PK, user_id/notification_id composite owner FK, channel EMAIL, immutable template_name/template_version/recipient binding/subject/escaped-source variables payload, state PENDING/IN_FLIGHT/RETRY/SENT/CANCELLED/DEAD, attempts,next_attempt_at,lease_until,claim_token,sent_at,expires_at, sanitized reason. UNIQUE(notification_id,channel), partial due index. Dữ liệu recipient/payload thuộc dữ liệu nhạy cảm, không đưa vào toString/log.
4. `budget_notification_state`: `(user_id,budget_id)` PK/FK, context category+month, observed_severity, cycle_number, highest_emitted_severity, last_evaluated_at và config fingerprint để stale check. Lưu trạng thái/dedup, **không cache số dư hoặc spent** như nguồn tài chính.
5. `notification_finance_events`: append-only durable event id/user_id, affected category/month scopes hoặc whole-user flag, occurredAt, claim/processed fields. Writer chỉ append metadata cùng financial DB transaction; poller đánh dấu processed, không HTTP/provider call. Event ID mới cho mỗi mutation; không update một dirty-row đang bị evaluator giữ khiến đảo lock order.
6. `daily_notification_schedule`: user_id PK, schedule_version, next_due_at UTC, last_processed_local_date. Unique semantic daily event `(user_id,DAILY_REMINDER,local_date)` nằm ở notifications.event_key và giữ tombstone sau delete để timezone changes không nhân bản cùng ngày.

Outbox/event FK cùng owner, không cascade xóa lịch sử khi archive budget/wallet/category. User deactivation hủy pending; hard delete user nếu có sau này theo retention policy riêng. Retention đề xuất payload/outbox hoàn tất90 ngày, inbox softdeleted90 ngày; chưa purge dedup daily dates trong cửa sổ retry/scheduler catch-up. Muốn xóa dedup lâu dài phải chặn producer sinh lịch quá khứ trước; không claim tuân thủ pháp lý chỉ từ số ngày đề xuất này.

Interfaces dự kiến theo package hiện có, concrete JDBC repository khi đủ:

```java
NotificationRepository.page(UUID user, NotificationQuery query) -> NotificationPage;
NotificationRepository.detail(UUID user, UUID id) -> NotificationResponse;
NotificationService.markRead(UUID user, UUID id) -> NotificationResponse;
NotificationService.markAllRead(UUID user, Instant through) -> ReadAllResult;
NotificationService.delete(UUID user, UUID id);
NotificationInboxWriter.insertOnce(NotificationCandidate event) -> Optional<UUID>;
FinanceNotificationEventRepository.append(UUID user, AffectedScopes scopes); // MANDATORY tx
BudgetAlertEvaluator.evaluate(UUID user, Set<BudgetScope> scopes, Instant now);
DailyReminderScheduler.enqueueDue(Instant now, int batchSize);
NotificationOutboxRepository.claimDue(Instant now, Duration lease, int batchSize) -> List<DeliveryClaim>;
FinanceEmailDeliveryWorker.deliver(DeliveryClaim claim);
// Reuse nguyên interface hiện tại:
EmailTemplateRenderer.render(String template, Map<String,String> values);
EmailSenderService.send(String recipient, String subject, String htmlBody, UUID deliveryId);
```

Inbox insert+unique event key+email outbox INSERT trong cùng transaction Notifications, inbox advisory lock order trước event insert. NotificationService list dùng read-only REPEATABLE READ để count/items/settings timezone cùng snapshot; mutation read/delete khóa row sau inbox user lock, không gọi provider. Budget evaluator serialize theo user bằng advisory lock riêng rồi đọc usage mới; không giữ budget-state/advisory locks trong financial writer. Giữ nguyên lock protocol transaction→wallets→categories của CRUD; writer chỉ append event sau các mutation, không khóa state budget/evaluator/outbox. Đặt giới hạn batch/lease, không tạo thêm generic queue framework.

## 6. Budget alert cycles và concurrency

Usage đọc ledger POSTED cùng timezone/settings và công thức BudgetResponse; các wallet archived/excluded vẫn tính. Chỉ budget active của **tháng local hiện tại** tạo alert mới; sửa giao dịch lịch sử cập nhật usage nhưng không spam email tháng đã đóng. Đây là quyết định đề xuất cho Notifications, không thay query/CRUD lịch sử.

Nguồn durable event: expense create/edit/void (old/new category và old/new local month), budget create/update/archive, thay threshold/toggles/timezone. Wallet opening/archive và income thuần không đổi budget spent nên không phát budget event; edit đổi income↔expense có scopes tương ứng. Timezone change enqueue reevaluate whole user vì tháng transaction có thể dịch; không đổi occurredAt hoặc kỳ budget đã lưu. Event replay/idempotent transaction POST replay không sinh thêm finance event.

| Chuyển trạng thái committed được evaluator quan sát | Hành vi |
|---|---|
| SAFE→WARNING | Tạo warning một lần cho cycle nếu type/master bật |
| SAFE→EXCEEDED | Chỉ exceeded; không gửi warning ngay sau vì cùng chi tiêu |
| WARNING→EXCEEDED | Một exceeded trong cycle; hủy email warning chưa gửi đã bị supersede |
| EXCEEDED→WARNING | Không phát warning mới của cùng cycle |
| WARNING/EXCEEDED→SAFE sau edit/void/tăng limit | Đóng cycle; lần lên ngưỡng sau tạo cycle mới |
| Vẫn WARNING hoặc EXCEEDED | Không gửi lại vì mỗi expense nhỏ/retry/settings event |
| Đổi category/month của budget | Context mới, cycle mới; historical/future month chỉ baseline, không alert |
| Archive budget | Dừng alert, hủy pending email; inbox cũ vẫn xem và readonly target |

Trong mỗi cycle, key gồm budgetId/context/cycle/severity; unique event key và row lock state đảm bảo nhiều evaluator chỉ một item/email cho cùng mức. Bước nhảy trực tiếp EXCEEDED đánh dấu warning đã superseded, kể cả exceeded toggle tắt, không phát warning muộn chỉ vì scheduler lặp. Threshold100 không tạo WARNING khi vừa chạm100 vì EXCEEDED ưu tiên.

Limit/threshold edits cùng context re-evaluate severity như một expense: hạ limit/threshold có thể tạo risk alert nếu baseline trước đóSAFE; tăng limit/void đưa SAFE thì cycle đóng. Thay đổi ngưỡng đơn thuần không reset cycle để gửi lại risk cũ. Bật lại master/type/email chỉ baseline trạng thái hiện tại, không gửi risk đã tồn tại; event mới sau đó có thể chuyển lên severity cao hơn. Khi master/type tắt vẫn cập nhật semantic baseline để tránh catch-up ngoài ý muốn; nếu evaluator không chạy trong khoảng tắt, enable action phải enqueue explicit baseline-reset trước khi nhận event mới.

Evaluator đọc trạng thái committed mới nhất sau serialize lock, không cam kết gửi mọi trạng thái trung gian rất ngắn (chi rồi void ngay trước poll có thể không tạo alert). Đổi lại không gửi email đã lỗi thời. Baseline-reset do enable phải được xử lý trước các reevaluation thông thường cùng user; các mutation đến trong khoảng reset chưa xử lý được coalesce vào baseline mới nhất, có thể không tạo alert cho một bước tăng ngay trong cửa sổ đó. Nếu sản phẩm cần bảo toàn mọi bước chuyển thì phải bổ sung event sequence/snapshot, không tuyên bố mô hình polling này đã bảo đảm. Một reconciler định kỳ rà active current-month budgets để phục hồi missing event sau vận hành/migration; không tự reset dedup. Khi deploy lần đầu, baseline risk hiện tại không gửi hàng loạt email lịch sử; budget mới tạo ở current month đã có expense được xét như event mới.

Outbox trước send kiểm tra budget/context/cycle còn phù hợp, severity hiện tại hỗ trợ mức event, không bị mức cao hơn supersede và chưa hết hạn; không thay số tiền snapshot trong inbox. Nếu stale thì CANCELLED, không tạo deliveryId khác để “retry”. Email budget hết hạn tại min(createdAt+24h, đầu tháng local kế tiếp); inbox snapshot vẫn giữ, có thể gắn chú thích thời điểm.

## 7. Daily scheduler

- Mỗi user có next_due_at UTC tính từ LocalDate + dailyReminderTime tại preferences.time_zone; không so một giờ server cố định với mọi user. Lấy Clock injectable để test; scheduler poll mỗi phút, batch có giới hạn.
- DST gap: chọn thời điểm hợp lệ đầu tiên sau gap; overlap: chọn offset sớm hơn. Khóa/key theo user+localDate đảm bảo chỉ một daily item ngay cả giờ lặp; timezone/schedule change cùng ngày không tạo daily thứ hai.
- Claim schedule rows `FOR UPDATE SKIP LOCKED`, recompute settings/zone/schedule_version trong transaction, insert notification/outbox atomically, advance next_due_at. Hai instance/restart không nhân bản; claim/crash trước commit rollback, sau commit unique key chặn replay.
- Chỉ gửi nhắc ghi chép, không tự suy “chưa có giao dịch hôm nay” khi user đã ghi; nội dung trung tính. Nếu muốn skip khi đã ghi sau này cần requirement riêng.
- Catch-up đề xuất tối đa2 giờ kể từ dueAt và chỉ cùng localDate; quá cửa sổ thì ghi skipped/advance lịch, không dồn email nhiều ngày sau downtime. Retry email không qua localDate tiếp theo. Ghi rõ đây là chính sách sản phẩm đề xuất, không hứa gửi đúng phút khi app/backend ngừng hoạt động.
- Settings time/timezone đổi thì invalidate schedule_version cũ, tính lại due; nếu giờ hôm nay đã qua và trong grace thì một event, ngoài grace hẹn ngày tiếp; vẫn không tạo lại ngày đã phát. Toggle tắt/user inactive cancels schedule pending; bật lại tính lịch mới, không backfill ngày cũ.

## 8. Outbox, provider retry và giới hạn bảo đảm

1. Financial transaction chỉ append finance event; sau commit evaluator tạo inbox+outbox bền vững. Daily producer cũng insert inbox/outbox cùng transaction của chính nó. Provider không chạy trong financial transaction và không rollback tiền vì lỗi mail.
2. Worker claim PENDING/RETRY đến hạn bằng SKIP LOCKED và lease+claim_token, commit claim trước network. Lease phải dài hơn connect/read timeouts cộng safety margin; nếu gia hạn cần fencing token, không giữ DB transaction mở khi gọi provider. Đọc lại user ACTIVE, verified recipient, email opt-in/master/type, item chưa delete, expiry và stale budget/schedule trước render/send. Chốt recipient binding và template payload lần đầu; retry giữ nguyên body/recipient/deliveryId.
3. Nếu email account đã đổi sau khi binding thì cancel delivery cũ; không tái sử dụng idempotency key cũ với recipient/body mới, cũng không tự phát mail mới khi chưa có event mới. Trường hợp chưa bind thì dùng recipient hiện tại đã verified. Settings/deactivation sau pre-send check vẫn có race với provider; không thể hủy email đã được chấp nhận, ghi giới hạn này rõ ràng.
4. Render template allowlist, validate đủ biến, subject không chứa CR/LF, URL từ configured allowlisted origin/path (HTML escaping không thay URL validation). Gọi sender.send(...,deliveryId); thành công mark SENT bằng claim_token còn hiệu lực. Claim token ngăn worker cũ overwrite state worker mới.
5. Network/timeout/429/5xx: retry giữ deliveryId, exponential backoff+jitter, tôn trọng Retry-After hợp lệ, ví dụ1m/5m/15m/1h tối đa5 attempts và expiry event. 4xx recipient/template/config xác định không transient → DEAD/CANCELLED theo reason; template missing variable là lỗi triển khai, không retry vô hạn. Provider quota/auth lỗi cần metric/alert, không log raw response.
6. Crash sau provider accept trước mark SENT: lease recovery có thể gọi lại cùng key. Provider idempotency chỉ giảm duplicate trong phạm vi bảo đảm/cửa sổ của provider; cần xác minh tài liệu provider lúc implementation. Retry horizon phải nhỏ hơn cửa sổ đó; ngoài cửa sổ hoặc trạng thái mơ hồ không tự đổi key/gửi lại, giữ DEAD/needs-review. Không tuyên bố exactly-once end-to-end.
7. Không log recipient/subject/body/note/secret/provider exception chứa request; chỉ deliveryId/eventId, reason allowlist, attempt, latency, counts. Dùng test sender/MockRestServiceServer để kiểm thử; plan này không gửi email thật. Chưa thêm webhook tracking opens/bounces/delivery receipts, SENT chỉ nghĩa provider call được chấp nhận.

## 9. Ba email templates đề xuất

Tạo sau này dưới `backend/src/main/resources/templates/email/`: `daily-reminder.html`, `budget-warning.html`, `budget-exceeded.html`. Bao bởi **layout.html hiện có**, table layout/inline styles/logo/footer cùng forgot-password/otp-verification. Không copy mã OTP, ttlMinutes, lời nhắc xác minh hoặc reset password vào email tài chính. Renderer tiếp tục escape mọi value, greeting username strip/fallback “bạn”; không truyền raw HTML từ DB.

Common exact variables cho **cả ba**: `title`, `preheader`, `username`, `actionUrl`, `settingsUrl`. `logoUrl` do renderer inject, không do event/client ghi. `title` cũng là subject do service tạo; raw subject strip CR/LF/length check, HTML renderer escape bản trong title. Các biến số tiền format integer VND locale vi-VN tại service (`850.000 đ`), phần trăm tối đa 2 chữ số thập phân, bỏ số 0 không cần thiết; snapshot raw integer vẫn giữ riêng trong outbox/inbox.

| Template | Biến bổ sung bắt buộc | Subject/title và preheader mẫu |
|---|---|---|
| daily-reminder | `localDateLabel`, `reminderTimeLabel`, `timeZoneLabel` | “Đồng · Nhắc ghi chép ngày 08/11/2026”; “Dành một phút cập nhật thu chi hôm nay trong Đồng.” |
| budget-warning | `categoryName`, `monthLabel`, `spentAmount`, `limitAmount`, `remainingAmount`, `usagePercent`, `thresholdPercent` | “Đồng · Ngân sách Ăn uống đã dùng 85%”; “Tháng 11/2026: còn 150.000 đ trong ngân sách Ăn uống.” |
| budget-exceeded | `categoryName`, `monthLabel`, `spentAmount`, `limitAmount`, `overAmount`, `usagePercent` | “Đồng · Ngân sách Ăn uống đã chạm hạn mức”; “Tháng 11/2026: đã chi 1.200.000 đ trên hạn mức 1.000.000 đ.” |

Exact variable sets là common + hàng tương ứng, không gửi code/ttlMinutes. `overAmount=max(spent-limit,0)`, số 0 tại đúng hạn mức được mô tả “đã chạm”, không nói đã vượt một số tiền dương. Warning chỉ phát dưới limit nên remainingAmount không âm. Category/username có thể chứa ký tự HTML; tests phải chứng minh escaped trong body/title/preheader.

Copy mẫu trong template:

> Chào **{{username}}**, hôm nay **{{localDateLabel}}**, Đồng nhắc bạn cập nhật thu chi theo giờ **{{reminderTimeLabel}} ({{timeZoneLabel}})**. Bạn có thể ghi một khoản thu hoặc chi khi thuận tiện. **[Ghi thu chi]({{actionUrl}})**. Bạn đang nhận email tài chính theo lựa chọn của mình. **[Quản lý thông báo]({{settingsUrl}})**.

> Chào **{{username}}**, ngân sách **{{categoryName}}** tháng **{{monthLabel}}** đã dùng **{{usagePercent}}%**, đạt ngưỡng cảnh báo **{{thresholdPercent}}%**. Đã chi **{{spentAmount}}** trên hạn mức **{{limitAmount}}**; còn **{{remainingAmount}}**. **[Xem ngân sách]({{actionUrl}})**. Số liệu tại thời điểm thông báo; dữ liệu trong ứng dụng có thể đã thay đổi. **[Quản lý thông báo]({{settingsUrl}})**.

> Chào **{{username}}**, ngân sách **{{categoryName}}** tháng **{{monthLabel}}** đã chạm hoặc vượt hạn mức: đã chi **{{spentAmount}}** trên **{{limitAmount}}** (**{{usagePercent}}%**); phần vượt là **{{overAmount}}**. Bạn vẫn có thể ghi chi tiêu nếu ví đủ số dư. **[Xem ngân sách]({{actionUrl}})**. Số liệu tại thời điểm thông báo. **[Quản lý thông báo]({{settingsUrl}})**.

actionUrl/settingsUrl không chứa JWT/refresh token/OTP/email/credentials hoặc query kích hoạt ghi dữ liệu. Chọn HTTPS origin do deployment cấu hình và path allowlist `/app/transactions/new`, `/app/budgets/{id}`, `/app/settings/notifications`; Android App Links/landing là phần handoff phải cấu hình thật trước bật email. GET link không auto login/read/archive/unsubscribe; sau đăng nhập kiểm tra owner lại và xử lý404/archived. Nếu deployment chưa có HTTPS App Links/landing, chưa bật email production; không bịa domain/url ngoài hệ thống.

## 10. Thứ tự triển khai và Android handoff

1. **Contract review:** chốt notification API/DTO, v2 email consent, cycle/catch-up/retention/link policies; thêm OpenAPI/tài liệu mới. Không lén sửa V1 User hoặc CRUD contract để nhận field chưa chốt.
2. **Schema/inbox:** migration settings/inbox/read-state; ownership/page/count/read/read-all/delete API và PostgreSQL concurrency tests. Android bỏ n1-n3/SharedPreferences all-read, dùng Repository/ViewModel/Resource và ApiClient/session guard; group Hôm nay/Hôm qua/Cũ hơn theo timezone user, không chỉ hai list cố định.
3. **Budget events/evaluator:** Integration Agent nối append-only event hooks trong financial/settings transactions sau review lock order; durable polling/dedup/cycles/void/edit/archive/settings tests. Không HTTP/email trong hooks.
4. **Daily scheduler:** schedule persistence/claim/UTC transitions/restart; fixed Clock fixtures, multi-instance và grace tests.
5. **Email:** v2 settings consent, outbox worker/claim/retry/cancellation, ba templates và provider HTTP contract tests. Auth verification/reset tests tiếp tục pass, không đổi independence hành vi.
6. **Android/navigation:** click item gọi mark-read riêng rồi mở typed target; read failure cho retry, không mark all bằng local flag. Read-all dùng snapshotAt gần nhất; refresh page/count và Home badge sau mutation; Settings email toggle riêng với permission Android. Deep link/login/archived/VOIDED/404 và session switch cần device/backend tests.
7. **Rollout:** migration chạy trước code, producers/email disabled bằng server feature flag; deploy inbox trước, baseline budget state, bật daily/budget trong test account đã consent, cuối cùng email khi URLs/provider config được xác thực. Tắt worker không làm mất outbox; migration rollback không xóa tiền/lịch sử.

## 11. Ma trận kiểm thử và DoD

- Ownership hai user ở mọi endpoint; malformed queries/UUID/read/body; page stable tie-breaker/empty/outofrange; soft delete/read idempotent, deleted không unread hoặc sinh lại; count/page snapshot; read-all race insert/read/delete và cutoff tương lai.
- Budget fixtures 79999/80000/99999/100000/150000 với limit 100000/threshold 80; rounded 80.00 nhưng chưa warning; threshold 100; SAFE→EXCEEDED chỉ một severity; edit/void reset/re-cross; category/month moves; archived category vẫn tính usage; archived budget stops; settings off/on/baseline và timezone shift.
- Concurrent expense events/evaluator/reconciler nhiều instance không duplicate semantic notification hoặc email; rollback financial mutation không để lại event; replay POST clientRequestId không sinh notification thứ hai. Notification DB lỗi phải fail financial event append atomicity nếu durability bắt buộc, không swallow rồi báo đã bảo đảm alert.
- Daily timezone/DST gap/overlap/leap/month/year rollover; lịch đổi cùng ngày; restart trước/sau due; two schedulers; grace skip; user inactive/settings disabled; catch-up không dồn ngày; Clock cố định, không dựa giờ máy chạy test.
- Email default false, unverified/inactive/wrong owner/no recipient override; V1 PATCH không reset V2 consent; concurrent partial settings không lost update; OS permission off không khóa email; auth OTP/reset vẫn hoạt động khi finance master/email false.
- Outbox crash trước/sau claim/send/mark SENT, lease expiration và fencing; retry 429/5xx/network cùng deliveryId/body, terminal 4xx/missing template; disable/archive/void/delete/stale context/recipient đổi trước send; cutoff/expiry cancels. Không gọi provider trong transaction tài chính. Kiểm tra log không có email/body/token/provider raw exception.
- Template tests common+exact fields, username null/blank/HTML, category markup/quotes, CRLF subject, invalid URL schemes, invalid amount/percent; chưa đủ biến fail thay vì gửi `{{...}}`; shared layout/logo/footer/mobile HTML; budget đúng limit copy không báo vượt dương; toàn bộ auth email tests giữ pass.
- API/OpenAPI/Retrofit serialization, Android list/read/read-all/count/deeplink/settings/rotation/session, true backend/device smoke và rendering email preview bằng test sender. Không gửi mail thật để chứng minh unit tests.

DoD: NOT-01–10 đạt và có report pass/fail/blocked riêng cho inbox, scheduler, budget evaluator, outbox, templates, Android. Migration fresh/upgrade và PostgreSQL concurrency pass; dashboard unread count dùng cùng read-state; consent default false và auth independence verified. Không gọi hoàn thành Notifications chỉ vì settings toggle/API hoặc template tồn tại; không gọi SENT là inbox read, provider delivered hoặc exactly-once.
