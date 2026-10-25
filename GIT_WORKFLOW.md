# Git Workflow — Finance Manager Android

> Repository: [zaabinh/finance-manager-android](https://github.com/zaabinh/finance-manager-android)  
> Phạm vi: Android (Java/XML), Backend (Spring Boot), Web Admin (React).  
> Quy trình chuẩn: `feature/*` / `fix/*` / `docs/*` → `develop` → `main`.

## 1. Vai trò các nhánh

| Branch | Mục đích | Được push trực tiếp? | Cách tích hợp |
|---|---|---|---|
| `main` | Phiên bản ổn định / bàn giao | Không | Pull Request từ `develop` |
| `develop` | Nhánh phát triển và tích hợp | Không | Pull Request từ nhánh công việc |
| `feature/<scope>-<name>` | Chức năng mới | Có, trên nhánh của mình | PR vào `develop` |
| `fix/<scope>-<name>` | Sửa lỗi thông thường | Có | PR vào `develop` |
| `refactor/<scope>-<name>` | Cải tiến cấu trúc, không đổi hành vi | Có | PR vào `develop` |
| `docs/<name>` | Tài liệu | Có | PR vào `develop` |
| `chore/<name>` | Cấu hình, công cụ, bảo trì | Có | PR vào `develop` |
| `hotfix/<name>` | Lỗi khẩn cấp trên bản ổn định | Có | PR vào `main`, sau đó đồng bộ lại `develop` |

**Nguyên tắc:** Tạo nhánh công việc từ `develop`, mỗi nhánh tập trung một nhiệm vụ. Không commit trực tiếp lên `main` hay `develop`. Không dùng `git push --force` với nhánh dùng chung.

## 2. Cấu hình bảo vệ nhánh (repository admin)

Truy cập [Settings → Branches](https://github.com/zaabinh/finance-manager-android/settings/branches) → **Add classic branch protection rule**, cấu hình lần lượt cho `main` và `develop`.

| Cấu hình | `main` | `develop` |
|---|---|---|
| Branch name pattern | `main` | `develop` |
| Require a pull request before merging | Bật | Bật |
| Require approvals | 1 | 1 |
| Dismiss stale pull request approvals when new commits are pushed | Bật | Tùy chọn (đề xuất tắt khi nhóm còn nhỏ) |
| Require conversation resolution before merging | Bật | Bật |
| Require status checks to pass before merging | Bật sau khi CI có status check ổn định | Bật sau khi CI có status check ổn định |
| Do not allow bypassing the above settings | Khuyến nghị bật | Khuyến nghị bật |
| Allow force pushes | Tắt | Tắt |
| Allow deletions | Tắt | Tắt |
| Lock branch | Tắt | Tắt |

**Ghi chú:** `Require a pull request` không đồng nghĩa `Require approvals`: cần bật cả hai nếu muốn người khác review. Người tạo PR không thể tự approve PR của mình. Không đặt tên status check bắt buộc trước khi workflow thực sự chạy thành công, tránh khóa merge do check không tồn tại. Tính khả dụng của branch protection phụ thuộc chế độ public/private và gói GitHub.

### Cấu hình merge

Trong **Settings → General → Pull Requests**:

- Cho phép **Squash merging** để gộp nhiều commit của một feature thành một commit rõ nghĩa khi vào `develop`.
- Có thể giữ **Create a merge commit** cho PR `develop` → `main` nếu muốn thể hiện ranh giới release.
- Cân nhắc bật **Automatically delete head branches** sau khi merge (không dùng để xóa `develop`).
- Nếu kích hoạt `Require linear history` thì không dùng merge commit trên nhánh đó.

## 3. Quy ước đặt tên

**Branch:** `<type>/<scope>-<short-description>`, chữ thường, nối bằng dấu `-`.

Ví dụ:

- `feature/android-transaction-form`
- `feature/backend-auth-refresh`
- `feature/web-admin-dashboard`
- `fix/android-budget-progress`
- `docs/git-workflow`
- `chore/backend-flyway-config`

**Commit:** Conventional Commits — `type(scope): description`.

Các `type` thường dùng: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `build`, `ci`, `perf`.

Ví dụ:

```text
feat(android): add transaction creation form
fix(backend): handle expired refresh token
refactor(android): extract reusable category selector
ci(backend): run Maven tests on pull requests
docs(workflow): document pull request process
```

Scope tham khảo: `android`, `backend`, `web-admin`, `auth`, `budget`, `transaction`, `ocr`, `database`, `workflow`. Mô tả commit nên ngắn, ở thể mệnh lệnh, nêu hành động đã thực hiện.

## 4. Quy trình thực hiện một công việc

### 4.1. Đồng bộ develop và tạo nhánh mới

```bash
git fetch origin
git switch develop
git pull --ff-only origin develop
git switch -c feature/android-transaction-form
```

Chỉ tạo nhánh mới sau khi `develop` đã cập nhật. Nếu không có local branch `develop`, dùng `git switch --track origin/develop`.

### 4.2. Làm việc và commit

```bash
git status
git add android/
git commit -m "feat(android): add transaction creation form"
git push -u origin feature/android-transaction-form
```

Chỉ stage các file thuộc phạm vi công việc. Trước khi commit kiểm tra `git diff --staged`. Tuyệt đối không commit `.env`, khóa API, JWT secret, mật khẩu, file local cấu hình nhạy cảm hoặc thư mục build.

### 4.3. Tạo Pull Request

Trên GitHub chọn **Compare & pull request** với:

- **base:** `develop`
- **compare:** `feature/android-transaction-form`

Đặt tên PR theo Conventional Commits, ví dụ `feat(android): add transaction creation form`. Gán reviewer, mô tả thay đổi và cách kiểm thử. Dùng **Draft PR** nếu chưa sẵn sàng review.

### 4.4. Review và merge

Reviewer kiểm tra logic, ảnh hưởng API/schema, tương thích UI, kiểm thử và các thay đổi ngoài phạm vi. Tác giả xử lý comment và push commit bổ sung lên cùng branch. Khi CI (nếu có) xanh, review được approve và hội thoại đã resolve, sử dụng **Squash and merge**. Không merge PR đang có conflict hoặc build fail.

### 4.5. Dọn dẹp sau merge

```bash
git switch develop
git pull --ff-only origin develop
git branch -d feature/android-transaction-form
```

Xóa remote feature branch trên GitHub sau khi merge nếu chưa tự động xóa.

## 5. Cập nhật feature branch khi develop thay đổi

Cách đơn giản và an toàn cho nhóm — merge `develop` vào feature branch:

```bash
git fetch origin
git switch feature/android-transaction-form
git merge origin/develop
```

Nếu xảy ra conflict:

1. Mở file đang conflict và lựa chọn nội dung đúng (không giữ marker `<<<<<<<`, `=======`, `>>>>>>>`).
2. Chạy lại build/tests phù hợp.
3. `git add <file>` với từng file đã giải quyết.
4. `git commit` để hoàn tất merge (nếu Git yêu cầu).
5. `git push` để cập nhật PR.

Nếu cần hủy lần merge đang conflict: `git merge --abort`. Không dùng `git reset --hard` khi chưa chắc chắn vì có thể mất thay đổi chưa commit.

**Lưu ý:** Rebase cũng hợp lệ nhưng tránh rebase nhánh đã được nhiều người cùng làm việc. Không force push vào `main` hoặc `develop`.

## 6. Kiểm thử trước Pull Request

Chọn các kiểm tra tương ứng với thư mục đã thay đổi:

| Phạm vi | Kiểm tra tối thiểu |
|---|---|
| `android/` | Gradle build và unit tests (nếu có), kiểm tra Android Studio, chạy thử màn hình thay đổi |
| `backend/` | Maven test, xác thực API và kiểm tra migration Flyway nếu chỉnh database |
| `web-admin/` | Lint và build bằng script thực tế trong `package.json`, kiểm tra giao diện liên quan |
| Thay đổi API giữa Android và Backend | Test hợp đồng request/response và luồng end-to-end liên quan |
| `docs/` | Kiểm tra Markdown, link, câu lệnh và đường dẫn |

Có thể chạy tham khảo (tùy các script/file thực tế của dự án):

```bash
# Android, tại android/
./gradlew test
./gradlew assembleDebug

# Backend, tại backend/
./mvnw test                 # nếu có Maven Wrapper
# hoặc: mvn test

# Web Admin, tại web-admin/
npm ci
npm run build
# npm run lint              # nếu script lint tồn tại
```

Trên Windows, dùng `gradlew.bat` thay cho `./gradlew`. Không coi các lệnh trên là bằng chứng CI đã được thiết lập hoặc đang chạy thành công.

## 7. Quy trình release: develop → main

1. Đảm bảo các PR dự kiến đã merge vào `develop` và hoàn thành kiểm thử tích hợp.
2. Mở PR với **base `main`**, **compare `develop`**.
3. Mô tả thay đổi, tính năng, bug fixes, migration và kết quả test.
4. Người khác review, CI/checks đạt yêu cầu, resolve conversation.
5. Merge PR vào `main` theo chiến lược release thống nhất.
6. Gắn tag phát hành, ví dụ `v0.1.0`, sau khi xác nhận commit release đúng.

Với `hotfix`, tạo branch từ `main`, mở PR vào `main`, sau khi merge phải tạo PR đồng bộ thay đổi đó về `develop` để tránh tái xuất hiện lỗi.

## 8. Mẫu Pull Request

```markdown
## Mục tiêu
<!-- Vấn đề được giải quyết / issue liên quan -->

## Thay đổi chính
- 

## Phạm vi ảnh hưởng
- [ ] Android
- [ ] Backend
- [ ] Web Admin
- [ ] Database/API
- [ ] Docs/CI

## Kiểm thử
- [ ] Build thành công
- [ ] Test liên quan thành công
- [ ] Kiểm tra thủ công các luồng chính và trường hợp biên

## Bằng chứng
<!-- Ảnh màn hình, video, log test, API examples khi cần -->

## Checklist trước merge
- [ ] Đúng base branch (`develop`, hoặc `main` khi release/hotfix)
- [ ] Không chứa secrets, file build hoặc thay đổi ngoài phạm vi
- [ ] Đã cập nhật tài liệu nếu hành vi/API thay đổi
- [ ] Đã chọn reviewer và xử lý các review comments
```

## 9. Checklist dành cho người quản trị

- [ ] `main` và `develop` tồn tại trên remote.
- [ ] Rule `main`: bắt buộc PR + 1 approval, hạn chế force push/delete và bypass.
- [ ] Rule `develop`: bắt buộc PR + 1 approval, hạn chế force push/delete và bypass.
- [ ] CI chạy thành công rồi mới bật required status checks tương ứng.
- [ ] Test PR từ nhánh `docs/` hoặc `feature/` vào `develop`.
- [ ] Kiểm tra push trực tiếp vào `develop` bị chặn bởi rule (trong điều kiện không có quyền bypass).
- [ ] README có liên kết đến `docs/GIT_WORKFLOW.md`.

---

Nguồn cấu hình: [GitHub Docs — Managing a branch protection rule](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/managing-a-branch-protection-rule).
