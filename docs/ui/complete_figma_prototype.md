# Complete Figma Prototype — Finance Manager Native Android

## 0. Mục tiêu chung

Hoàn thiện file Figma hiện tại thành một **UI/UX specification hoàn chỉnh** cho ứng dụng Finance Manager Native Android, theo định hướng:

- **Material Design 3 làm nền**, custom nhẹ để có nhận diện riêng.
- Phong cách **professional – minimalist**.
- **Màu xanh lá** là brand color chủ đạo.
- Giữ tối đa cấu trúc và màn hình hiện có, chỉ sửa những phần cần thiết để tăng tính nhất quán, usability và khả năng handoff cho Android.
- Chuẩn hóa toàn bộ màn hình Android theo **reference viewport 360 × 800**.
- Component, variant, state và naming dùng tiếng Anh.
- Nội dung mô tả/checklist trong tài liệu dùng tiếng Việt.
- Ưu tiên các component dễ ánh xạ sang **Native Android XML + Material Components**.

---

# 1. UI Foundation đã thống nhất

## 1.1. Reference viewport

- Frame chuẩn: `360 × 800`
- Horizontal page padding mặc định: `16dp`
- Khoảng cách chính dùng hệ:

```text
4 / 8 / 12 / 16 / 24 / 32 / 48
```

Nguyên tắc:

- Không tạo spacing ngẫu nhiên ngoài hệ trên nếu không thật sự cần.
- Các section lớn cách nhau chủ yếu `24dp`.
- Các item trong cùng nhóm dùng `8dp` hoặc `12dp`.
- Các screen phải dùng Auto Layout để không phụ thuộc vào absolute position.

---

## 1.2. Color system

### Brand Green

```text
Green/50   #F0FDF4
Green/100  #DCFCE7
Green/200  #BBF7D0
Green/300  #86EFAC
Green/400  #4ADE80
Green/500  #22C55E
Green/600  #16A34A   ← Primary
Green/700  #15803D
Green/800  #166534
Green/900  #14532D
```

### Neutral

```text
Background              #F8FAF9
Surface                 #FFFFFF
Surface Container       #F2F5F3
Surface Variant         #EAF0EC

Text Primary            #111827
Text Secondary          #667085
Text Disabled           #98A2B3

Border Default          #E4E7EC
Border Strong           #D0D5DD
```

### Semantic

```text
Income / Success        #059669
Expense / Error         #DC2626
Warning                 #D97706
Info                    #2563EB
```

### Cách sử dụng

- `Green/600` dùng cho CTA chính, active navigation, selected state, progress chính.
- Không phủ xanh toàn màn hình.
- Card mặc định dùng `Surface`.
- Background tổng thể dùng `Background`.
- Border nhẹ được ưu tiên hơn drop shadow.
- Income và Expense phải có màu semantic nhưng không phụ thuộc hoàn toàn vào màu; luôn kèm dấu `+/-`, icon hoặc label.

---

## 1.3. Typography

Ưu tiên giữ font hiện tại nếu toàn file đang dùng một font family nhất quán. Nếu font hiện tại bị trộn nhiều loại, chuẩn hóa về **Roboto** để thuận lợi khi implement Native Android.

```text
Display / Amount Large
32 / 40 — Bold

Title Large
24 / 32 — SemiBold

Title Medium
20 / 28 — SemiBold

Title Small
18 / 24 — Medium

Body Large
16 / 24 — Regular

Body Medium
14 / 20 — Regular

Label
12 / 16 — Medium
```

Quy tắc:

- Amount là nội dung quan trọng nhất trong finance app.
- Không dùng quá 6 cấp typography.
- Không dùng nhiều font weight trong cùng một card.
- Label phụ và metadata dùng `Body Medium` hoặc `Label`.
- Số tiền phải ưu tiên dễ đọc, không viết quá nhỏ.

---

## 1.4. Radius, border, elevation

```text
Button radius           12
Input radius            12
Card radius             16
Bottom Sheet radius     24 top corners
Dialog radius           20
Chip radius             16
FAB radius              16 hoặc full-round theo component
```

Border:

```text
Default border          1px
```

Elevation:

- Card thường: không shadow hoặc shadow rất nhẹ.
- Chỉ dùng elevation rõ cho:
  - FAB
  - Dialog
  - Bottom Sheet
  - Floating menu

---

## 1.5. Iconography

- Icon size mặc định: `24dp`
- Icon nhỏ: `20dp`
- Action icon button vẫn phải có touch target tối thiểu `48 × 48dp`
- Dùng một icon library duy nhất; hiện tại có thể tiếp tục dùng **Lucide** nếu toàn file đã theo hướng đó.
- Không trộn nhiều phong cách outline/filled không có chủ đích.

---

## 1.6. Core sizing

```text
App Bar height              56
Primary Button height       48
Secondary Button height     48
Text Input height           56
Compact Input height        48
Chip height                 32
List Item min height        56
Transaction Item            64–72
Bottom Navigation           64–72
Main FAB                     56
Quick Action FAB             44
Touch target minimum         48 × 48
```

---

# Phase 1 — Chuẩn hóa cấu trúc file Figma

## Goal

Biến file hiện tại thành một workspace có cấu trúc rõ ràng, thống nhất frame, naming và section trước khi tiếp tục sửa UI.

## Detail

### 1. Chuẩn hóa frame

- Chuyển toàn bộ Android screen về `360 × 800`.
- Các screen hiện đang `360 × 852`, `393 × 852`, `395 × 852` cần refactor về cùng viewport.
- Dùng Auto Layout thay vì phụ thuộc vào x/y tuyệt đối.
- Nội dung dài phải được thiết kế dưới dạng scrollable content thay vì kéo dài frame.

### 2. Loại bỏ iOS chrome

Hiện file Android còn các layer như:

```text
ios-signal
ios-wifi-signal
ios-battery-full
home-indicator
```

Thay bằng Android-style system bars hoặc dùng một component neutral dành cho mockup Android.

### 3. Chuẩn hóa naming

Đổi các tên lỗi hoặc thiếu nhất quán:

```text
Tracsactions
new-tracsaction
transaction-Details
Analytic
setting-sercurity-account
opt-input
Mode=Mode2
Property 1
Property 2
Collection 1
```

Theo convention:

```text
Screen/Transaction/List
Screen/Transaction/Detail
Screen/Transaction/Form

Screen/Wallet/List
Screen/Wallet/Detail

Screen/Budget/List
Screen/Budget/Detail

Navigation/Bottom
Navigation/FAB

Input/Text
Input/OTP

Card/Transaction
Card/Wallet
Card/Budget

Dialog/ConfirmDelete
```

### 4. Organize section

Giữ các section chính:

```text
Authentication
Onboarding
Home
Transactions
Wallets
Budgets
Categories
Analytics
Notifications
Settings
Components
```

Đổi `Component-assets` thành `Components`.

## Checklist

- [ ] Tất cả Android screen dùng frame `360 × 800`
- [ ] Không còn screen `393 × 852`, `395 × 852`, `360 × 852`
- [ ] Thay iOS system chrome bằng Android-style
- [ ] Fix typo trong tên screen/component
- [ ] Rename component theo cùng convention
- [ ] Không còn `Property 1`, `Property 2`, `Mode2`
- [ ] `Component-assets` đổi thành `Components`
- [ ] Các screen được đặt đúng section
- [ ] Không có duplicate screen không còn sử dụng
- [ ] Các screen chính dùng Auto Layout

---

# Phase 2 — Xây Design Tokens

## Goal

Tạo một foundation duy nhất cho color, typography, spacing và radius để toàn bộ component và screen không còn hardcode tùy ý.

## Detail

### 1. Tạo Variables

Tạo collections:

```text
Color
Spacing
Radius
```

Tối thiểu cần có:

```text
color/brand/primary
color/brand/primary-container

color/background
color/surface
color/surface-container

color/text/primary
color/text/secondary
color/text/disabled

color/border/default
color/border/strong

color/semantic/income
color/semantic/expense
color/semantic/warning
color/semantic/info
```

### 2. Tạo spacing variables

```text
space/1 = 4
space/2 = 8
space/3 = 12
space/4 = 16
space/6 = 24
space/8 = 32
space/12 = 48
```

### 3. Tạo radius variables

```text
radius/small = 8
radius/medium = 12
radius/large = 16
radius/dialog = 20
radius/sheet = 24
```

### 4. Typography styles

Tạo text styles:

```text
Type/Display
Type/Title/Large
Type/Title/Medium
Type/Title/Small
Type/Body/Large
Type/Body/Medium
Type/Label
```

### 5. Bind token vào component

Không để component chính sử dụng color hoặc spacing hardcoded nếu đã có variable tương ứng.

## Checklist

- [ ] Tạo `Color` variable collection
- [ ] Tạo Green palette
- [ ] Tạo neutral colors
- [ ] Tạo semantic colors
- [ ] Tạo spacing variables
- [ ] Tạo radius variables
- [ ] Tạo typography styles
- [ ] Primary CTA dùng `Green/600`
- [ ] Background toàn app dùng cùng token
- [ ] Card dùng cùng surface token
- [ ] Border dùng cùng token
- [ ] Income/Expense dùng semantic token
- [ ] Không còn màu xanh lá khác nhau tùy screen nếu cùng vai trò
- [ ] Không còn radius tùy ý ở các component cùng loại

---

# Phase 3 — Chuẩn hóa Core Components

## Goal

Biến các UI element đang bị lặp lại thành reusable component và làm sạch toàn bộ variant/state cho Android.

## Detail

### 1. Button

Giữ các type cần thiết:

```text
Type:
Primary
Secondary
Outline
Ghost
Danger
```

State:

```text
State:
Default
Pressed
Disabled
Loading
```

Bỏ `Hover` khỏi Android component.

Fix component hiện có đang có duplicate `State=Hover` trong Ghost.

### 2. Input

Chuẩn hóa:

```text
Input/Text

State:
Default
Focused
Filled
Error
Disabled
```

Properties:

```text
Leading Icon = True/False
Trailing Icon = True/False
Helper Text = True/False
```

### 3. OTP Input

Giữ các state:

```text
Default
Focused
Filled
Error
```

### 4. App Bar

Tạo component:

```text
Navigation/AppBar
```

Variant:

```text
Type=Root
Type=Back
Type=Action
```

### 5. Bottom Navigation

Giữ 4 tab:

```text
Home
Transactions
Analytics
Settings
```

State:

```text
Active=Home
Active=Transactions
Active=Analytics
Active=Settings
```

### 6. FAB

Giữ:

```text
State=Collapsed
State=Expanded
```

Quick actions:

```text
Photo
Pen
```

### 7. Các component cần tạo thêm

```text
Card/Transaction
Card/Wallet
Card/Budget

List/SettingItem
List/CategoryItem

Control/Segmented
Control/Chip
Control/SearchBar

Feedback/EmptyState
Feedback/ErrorState
Feedback/LoadingState

Dialog/ConfirmDelete
Sheet/Selector
Feedback/Snackbar
```

### 8. Hạn chế raw frame

Các screen đang có nhiều raw frame như Transaction List, Analytics, Settings, Budget cần được refactor sang reusable component.

## Checklist

- [ ] Button không còn Hover state
- [ ] Ghost button có đủ Default/Pressed/Disabled/Loading
- [ ] Button height thống nhất 48
- [ ] Input height thống nhất 56 hoặc 48 cho compact
- [ ] Tạo App Bar component
- [ ] Bottom Navigation dùng cùng một component
- [ ] FAB có Collapsed/Expanded
- [ ] Tạo Transaction Item
- [ ] Tạo Wallet Card
- [ ] Tạo Budget Card
- [ ] Tạo Setting Item
- [ ] Tạo Category Item
- [ ] Tạo Segmented Control
- [ ] Tạo Chip
- [ ] Tạo Search Bar
- [ ] Tạo Empty/Error/Loading states
- [ ] Tạo Selector Bottom Sheet
- [ ] Tạo Snackbar
- [ ] Tất cả icon action có touch target >= 48dp
- [ ] Các component được dùng bằng Instance, hạn chế copy raw frame

---

# Phase 4 — Authentication & First Setup

## Goal

Hoàn thiện entry flow để user mới và user đã có tài khoản đi đúng hướng, đồng thời prototype được toàn bộ flow.

## Detail

### Authentication flow

```text
Splash
↓
Login
├── Forgot Password
│   ↓
│   Change Password
│   ↓
│   Login
│
└── Register
    ↓
    Verify Email
```

### Login routing

Không prototype Login luôn đi Setup.

Logic cần thể hiện:

```text
Existing User + setupCompleted=true
→ Home

Existing User + setupCompleted=false
→ Setup Welcome
```

### First Setup

Giữ flow hiện có:

```text
Welcome
↓
Basic Info
↓
Notifications
↓
Default Wallet & Categories
↓
Complete
↓
Home
```

### Basic Info

Chỉ giữ:

- Display name
- Currency

### Notifications

Cho phép:

- Enable notification
- Reminder time
- Budget warning

### Defaults

Hiển thị:

- Default wallet: Cash
- Default categories
- Có thể chỉnh sau trong Settings

Không thêm quá nhiều bước onboarding.

## Checklist

- [ ] Splash tự chuyển sang Login
- [ ] Login button có prototype
- [ ] Login có route đến Home
- [ ] Có route đến Setup cho account chưa setup
- [ ] Register → Verify Email hoạt động
- [ ] Forgot Password → Change Password hoạt động
- [ ] Back navigation hoạt động
- [ ] Setup có đủ 5 bước
- [ ] Setup có progress indicator nhẹ
- [ ] Basic Info không chứa field không cần thiết
- [ ] Notification setup dễ skip hoặc disable
- [ ] Default wallet/category có preview
- [ ] Complete → Home
- [ ] Input validation có Error state
- [ ] Loading button được dùng khi submit

---

# Phase 5 — Home Dashboard

## Goal

Biến Home thành màn hình tổng quan tài chính nhanh, rõ, không biến thành một Analytics dashboard thu nhỏ.

## Detail

Home chỉ cần trả lời 4 câu hỏi:

1. Tôi còn bao nhiêu tiền?
2. Tháng này thu/chi bao nhiêu?
3. Ngân sách có vấn đề không?
4. Giao dịch gần đây là gì?

### Cấu trúc đề xuất

```text
App Bar
├── Greeting
└── Notification

Total Balance
12.450.000 ₫

Wallet Summary
├── Cash
├── Bank
└── Savings

Monthly Summary
├── Income
└── Expense

Budget Progress

Recent Transactions

Bottom Navigation + FAB
```

### Wallet summary

Chỉ giữ 3 nhóm:

```text
Cash
Bank
Savings
```

Không cần hiển thị tất cả wallet trên Home.

### Home Summary

Component hiện tại có nhiều view:

```text
ThuChi
Thang
Tuan
Ngay
NganSach
```

Giảm độ phức tạp:

- Home chỉ cần `Monthly Summary`
- Day/Week/Month chi tiết chuyển sang Analytics
- Budget chỉ hiển thị progress ngắn

### Recent Transaction

Hiển thị tối đa 3–5 transaction.

CTA:

```text
View all
```

## Checklist

- [ ] Home fit tốt trong 1 screen cơ bản
- [ ] Không cần scroll quá nhiều
- [ ] Total Balance là visual hierarchy cao nhất
- [ ] Wallet summary chỉ có Cash/Bank/Savings
- [ ] Monthly income/expense hiển thị rõ
- [ ] Budget progress hiển thị ngắn gọn
- [ ] Recent Transactions dùng Card/Transaction
- [ ] Có `View all`
- [ ] Notification icon dùng App Bar
- [ ] Bottom Navigation đúng Active=Home
- [ ] FAB dùng component chuẩn
- [ ] Không có chart phức tạp trên Home
- [ ] Màu xanh chỉ dùng để nhấn CTA/brand, không phủ card hàng loạt

---

# Phase 6 — Transaction & OCR Flow

## Goal

Tối ưu tác vụ quan trọng nhất của app: tạo, xem, sửa và nhập giao dịch nhanh bằng OCR.

## Detail

### Add Transaction

Flow:

```text
Home / Transactions
↓
FAB
├── Pen
│   ↓
│   Transaction Form
│
└── Photo
    ↓
    Import / Camera
    ↓
    OCR Review
```

### Transaction Form

Ưu tiên order:

```text
Expense | Income

Amount

Category

Wallet

Date

Note

More Options
```

Không đưa các field phụ lên top.

### Edit Transaction

Không tạo design hoàn toàn mới.

Dùng cùng component:

```text
Transaction Form
Mode=Add
Mode=Edit
```

### Transaction Detail

Cần:

- Amount
- Type
- Category
- Wallet
- Date
- Note
- Edit
- Delete

### Delete

Dùng `Dialog/ConfirmDelete`.

### OCR Review

Hiển thị field đã detect:

```text
Amount
Merchant
Category
Date
```

Field confidence thấp:

- highlight nhẹ
- có helper text `Please check this field`

Không auto-save OCR transaction trước khi review.

### Transaction List

Nên có:

- Search
- Filter
- Date/month grouping
- Expense/Income visual distinction
- Empty State

Không cần tạo một screen Search riêng; dùng SearchBar và Filter Bottom Sheet.

## Checklist

- [ ] FAB Pen → Transaction Form
- [ ] FAB Photo → OCR flow
- [ ] Transaction Form có Expense/Income segmented control
- [ ] Amount là field nổi bật nhất
- [ ] Category selector dùng Bottom Sheet
- [ ] Wallet selector dùng Bottom Sheet
- [ ] Date có default Today
- [ ] Note là optional
- [ ] Add/Edit dùng cùng component
- [ ] Transaction Detail có Edit
- [ ] Transaction Detail có Delete
- [ ] Delete mở Confirm Dialog
- [ ] OCR Review có Amount
- [ ] OCR Review có Merchant
- [ ] OCR Review có Category
- [ ] OCR Review có Date
- [ ] Field confidence thấp có visual warning
- [ ] OCR phải Review trước Save
- [ ] Transaction List có Search
- [ ] Transaction List có Filter
- [ ] Có Empty State khi chưa có transaction
- [ ] Có No Result state khi search không có kết quả
- [ ] Save thành công có Snackbar
- [ ] Bottom Navigation đúng Active=Transactions

---

# Phase 7 — Wallets & Categories

## Goal

Chuẩn hóa quản lý ví và danh mục thành các flow đơn giản, reusable và dễ implement.

## Detail

## Wallets

### Wallet List

Dùng `Card/Wallet`.

Mỗi card chỉ cần:

```text
Wallet Name
Balance
Type
Icon
```

Types:

```text
Cash
Bank
Savings
```

### Wallet Detail

Hiển thị:

- Current Balance
- Wallet Type
- Recent Transactions
- Edit
- Delete

### Wallet Form

Giữ component hiện có:

```text
Mode=Add
Mode=Edit
```

Không tạo hai screen khác nhau.

## Categories

Gộp Income và Expense vào một screen logic.

```text
Categories

[ Expense ] [ Income ]

Category List
```

Dùng segmented control.

### Add Category

Cần:

- Name
- Icon
- Type
- Color nếu thật sự cần

Không bắt buộc user chọn custom color nếu muốn giữ UI minimalist.

## Checklist

- [ ] Wallet List dùng cùng Wallet Card component
- [ ] Wallet type chỉ Cash/Bank/Savings trong scope hiện tại
- [ ] Wallet Detail có recent transactions
- [ ] Wallet Form dùng Mode=Add/Edit
- [ ] Delete Wallet dùng Confirm Dialog
- [ ] Wallet không dùng decorative banking-card style quá mức
- [ ] Category Expense/Income gộp bằng segmented control
- [ ] Không cần hai UX flow hoàn toàn riêng
- [ ] Add Category dùng cùng form
- [ ] Delete Category dùng Confirm Dialog
- [ ] Category item dùng component
- [ ] Có Empty State phù hợp
- [ ] Snackbar xuất hiện sau Add/Edit/Delete thành công

---

# Phase 8 — Budget & Analytics

## Goal

Hoàn thiện chức năng theo dõi ngân sách và phân tích tài chính nhưng giữ màn hình gọn, tránh chart overload.

## Detail

## Budget

### Budget List

Mỗi Budget Card hiển thị:

```text
Category
Used / Limit
Progress
Remaining
Period
```

Màu progress:

- Normal → Primary Green
- Near limit → Warning
- Exceeded → Expense/Error

### Budget Detail

Hiển thị:

- Limit
- Used
- Remaining
- Progress
- Related Transactions
- Edit
- Delete

### Budget Form

Dùng chung:

```text
Mode=Add
Mode=Edit
```

Fix variant hiện tại `Mode=Mode2`.

## Analytics

Một screen chỉ nên có:

- Period selector
- Income/Expense segmented control
- Total
- 1 primary chart
- Category breakdown
- Comparison với period trước

Không đặt nhiều chart cùng lúc.

### Period

Có thể dùng:

```text
Day
Week
Month
```

Nhưng default nên là `Month`.

## Checklist

- [ ] Budget Card dùng component
- [ ] Progress bar có semantic states
- [ ] Add/Edit Budget dùng cùng form
- [ ] `Mode=Mode2` được rename thành `Mode=Edit`
- [ ] Budget Detail có related transactions
- [ ] Delete Budget dùng Confirm Dialog
- [ ] Analytics mặc định Month
- [ ] Có Expense/Income segmented control
- [ ] Một primary chart trên một viewport
- [ ] Category breakdown dễ đọc
- [ ] Không dùng quá nhiều màu cho chart
- [ ] Period comparison ngắn gọn
- [ ] Analytics có Empty State khi chưa đủ dữ liệu
- [ ] Bottom Navigation đúng Active=Analytics

---

# Phase 9 — Settings, Notifications, System States & Accessibility

## Goal

Hoàn thiện các màn hình hỗ trợ, trạng thái hệ thống và accessibility để prototype có cảm giác như một sản phẩm thực tế.

## Detail

## Settings

Giữ các nhóm:

```text
Profile
Preferences
Notifications
Security & Account
About
```

Dùng `List/SettingItem`.

Không thiết kế mỗi item theo một layout khác nhau.

### Settings/Profile

- Display name
- Currency
- Avatar nếu cần
- Save

### Notifications

- Reminder toggle
- Reminder time
- Budget warning
- Transaction notification nếu scope cần

### Security & Account

- Change Password
- Logout
- Delete Account nếu requirement có

Delete Account phải có confirm destructive action rõ.

## Notification Center

Giữ:

```text
Notification List
Empty Notification
```

Bổ sung interaction:

- Back
- Mark all as read
- Tap notification
- Read/unread state

## Global System States

Tạo reusable:

```text
Loading
Empty
Error
Offline
No Search Result
```

Không vẽ một component hoàn toàn mới cho từng feature nếu chỉ khác text/icon.

## Accessibility

- Touch target >= 48 × 48
- Contrast rõ
- Không chỉ dùng màu để biểu thị Income/Expense
- Label rõ cho icon-only action
- Font nhỏ nhất không dưới 12
- Body text chủ yếu >= 14
- Disabled state vẫn đọc được

## Checklist

- [ ] Settings dùng Setting Item component
- [ ] Settings chia nhóm rõ
- [ ] Profile screen consistent với Input component
- [ ] Notification settings dùng Switch chuẩn
- [ ] Change Password reuse Authentication component
- [ ] Logout có confirm nếu cần
- [ ] Delete Account có destructive confirmation
- [ ] Notification List có read/unread state
- [ ] Mark all as read có interaction
- [ ] Empty Notification dùng EmptyState
- [ ] Tạo Loading State
- [ ] Tạo Error State
- [ ] Tạo Offline State
- [ ] Tạo No Result State
- [ ] Touch target >= 48dp
- [ ] Text contrast đạt mức dễ đọc
- [ ] Income/Expense không chỉ phân biệt bằng màu
- [ ] Không có text quan trọng quá nhỏ
- [ ] Bottom Navigation đúng Active=Settings

---

# Phase 10 — Complete Prototype, QA & Android Handoff

## Goal

Nối toàn bộ màn hình thành một prototype có thể demo end-to-end và biến Figma thành tài liệu handoff đủ rõ để team Android implement mà không phải tự đoán UI.

## Detail

## 1. Prototype main flows

### Flow A — New User

```text
Splash
↓
Register
↓
Verify Email
↓
Setup Welcome
↓
Basic Info
↓
Notifications
↓
Defaults
↓
Complete
↓
Home
```

### Flow B — Existing User

```text
Splash
↓
Login
↓
Home
```

### Flow C — Add Transaction

```text
Home
↓
FAB
↓
Pen
↓
Transaction Form
↓
Save
↓
Transaction Detail
```

### Flow D — OCR Transaction

```text
Home
↓
FAB
↓
Photo
↓
Import / Camera
↓
OCR Review
↓
Save
↓
Transaction Detail
```

### Flow E — Wallet

```text
Home / Settings
↓
Wallet List
↓
Wallet Detail
├── Edit
└── Delete
```

### Flow F — Budget

```text
Budget List
↓
Budget Detail
├── Edit
└── Delete
```

### Flow G — Navigation

```text
Home
↔ Transactions
↔ Analytics
↔ Settings
```

## 2. Interaction rules

Prototype tối thiểu phải hỗ trợ:

```text
Tap
Back
Bottom Navigation
FAB Expand/Collapse
Dialog Open/Close
Bottom Sheet Open/Close
Segmented Control
Toggle
Form Submit
Delete Confirm
Snackbar Feedback
```

Không cần prototype keyboard thật hoặc mọi text input animation.

## 3. Component QA

Kiểm tra:

- instance không detach nếu không cần
- variants naming đúng
- không duplicate component
- không dùng raw color nếu có token
- không có frame lệch viewport
- component resize đúng
- Auto Layout không bị break

## 4. Screen QA

Mỗi screen cần test:

```text
Default
Loading
Empty nếu có
Error nếu có
Long text
Large amount
```

Ví dụ amount:

```text
9.999.999.999 ₫
```

không được tràn layout.

## 5. Android handoff

Mỗi component trong Figma phải có mapping gần với Android:

```text
Button              → MaterialButton
Input               → TextInputLayout + TextInputEditText
App Bar             → MaterialToolbar
Bottom Navigation   → BottomNavigationView
FAB                  → FloatingActionButton
Dialog               → MaterialAlertDialog
Bottom Sheet         → BottomSheetDialog
Chip                 → Chip
Segmented Control    → MaterialButtonToggleGroup
Snackbar             → Snackbar
Switch               → MaterialSwitch
```

Không ép design theo những pattern quá khó implement bằng XML.

## Checklist

- [ ] Prototype New User chạy end-to-end
- [ ] Prototype Existing User chạy end-to-end
- [ ] Add Transaction chạy end-to-end
- [ ] OCR Transaction chạy end-to-end
- [ ] Wallet flow chạy end-to-end
- [ ] Budget flow chạy end-to-end
- [ ] Bottom Navigation hoạt động
- [ ] FAB Expand/Collapse hoạt động
- [ ] Dialog hoạt động
- [ ] Bottom Sheet hoạt động
- [ ] Segmented Control hoạt động
- [ ] Snackbar được minh họa
- [ ] Không có dead-end screen
- [ ] Back navigation hợp lý
- [ ] Không có component duplicate
- [ ] Không còn hardcoded color chính
- [ ] Không còn viewport inconsistent
- [ ] Không còn iOS system bar
- [ ] Không còn typo trong component/screen naming
- [ ] Amount lớn không overflow
- [ ] Long label không phá layout
- [ ] Các screen chính dùng reusable component
- [ ] Các component chính có đủ state
- [ ] Figma component có mapping rõ sang Android Material Component
- [ ] Prototype đủ ổn để dùng demo đồ án

---

# Component Inventory cuối cùng

Sau Phase 10, file nên có tối thiểu các component sau:

```text
Navigation/AppBar
Navigation/Bottom
Navigation/FAB

Button
Input/Text
Input/OTP

Control/Checkbox
Control/Switch
Control/Chip
Control/Segmented
Control/SearchBar

Card/Transaction
Card/Wallet
Card/Budget
Card/Notification

List/CategoryItem
List/SettingItem

Analysis/FinancialSummary
Analysis/CategoryBreakdown

Feedback/Badge
Feedback/Loading
Feedback/EmptyState
Feedback/ErrorState
Feedback/Snackbar

Dialog/ConfirmDelete
Sheet/Selector

System/StatusBar
```

---

# Screen Inventory cuối cùng

```text
Authentication
├── Splash
├── Login
├── Register
├── Verify Email
├── Forgot Password
└── Change Password

Onboarding
├── Welcome
├── Basic Info
├── Notifications
├── Defaults
└── Complete

Home
└── Dashboard

Transactions
├── List
├── Detail
├── Form / Add
├── Form / Edit
├── Quick Import
└── OCR Review

Wallets
├── List
├── Detail
└── Form / Add/Edit

Budgets
├── List
├── Detail
└── Form / Add/Edit

Categories
├── List / Expense-Income
└── Form / Add/Edit

Analytics
└── Overview

Notifications
├── List
└── Empty

Settings
├── Profile
├── Notifications
└── Security & Account
```

---

# Nguyên tắc cuối cùng để tránh redesign quá mức

Trong toàn bộ roadmap, ưu tiên theo thứ tự:

```text
Reuse
↓
Refactor
↓
Standardize
↓
Add missing state
↓
Add new screen only when absolutely necessary
```

Không redesign một screen chỉ vì muốn “đẹp hơn”.

Chỉ sửa khi ít nhất một trong các điều sau xảy ra:

- Component không nhất quán
- Flow bị dead-end
- Thiếu state quan trọng
- Visual hierarchy chưa rõ
- Khó implement trên Native Android
- Layout không responsive
- Có duplicate UI
- Không đáp ứng accessibility cơ bản

Mục tiêu cuối cùng không phải tạo một file Figma phức tạp hơn, mà là tạo một prototype **gọn, nhất quán, dễ hiểu, dễ demo và dễ chuyển thành code Android**.
