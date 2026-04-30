# Dev Workflow — Quy Trình Làm Việc

> Quy trình giữa user (Xbism3), Claude (Speckit/Planning), và Copilot Pro (Implementation).

## Phân Vai

| Tool | Vai trò | Làm gì |
|------|---------|--------|
| **Claude** (Claude Code) | Speckit — Planner | specify, clarify, plan, tasks, analyze |
| **Copilot Pro** (Android Studio / VS Code) | Implementer | Đọc tasks.md → code từng task → test *(tạm thời Claude đảm nhận khi chưa có Copilot Pro)* |
| **User** (Xbism3) | Product Owner | Approve specs, review code, merge PRs |

### Tại sao tách?
- Claude mạnh về planning, architecture, spec consistency
- Copilot Pro mạnh về code generation inline, autocomplete, implement từ context
- Tránh conflict: Claude không sửa code, Copilot không sửa spec artifacts

## Flow Cho Mỗi Spec

### Bước 1: Họp trước khi start spec (Claude)
- User và Claude thảo luận về spec sắp làm
- Claude review context (project-context.md, sdd-roadmap.md, constitution.md, meeting-note.md)
- Nếu có mâu thuẫn hoặc vướng mắc → Claude hỏi user để confirm
- **Đặc biệt**: nếu spec yêu cầu thêm thư viện mới → verify 16KB page size compliance trước
- Kết quả: mọi thứ tường minh, sẵn sàng specify

### Bước 2: Specify (Claude)
- Claude chạy `/speckit.specify` với prompt chi tiết
- Speckit tạo git branch mới (`NNN-kebab-name`) + `spec.md`
- Prompt lưu vào `specify-prompts.md` (nếu có)

### Bước 3: Clarify (Claude)
- User và Claude review spec output
- Claude chạy `/speckit.clarify` nếu có ambiguities
- Iterate cho đến khi spec clean

### Bước 4: Plan & Tasks (Claude)
- Claude chạy `/speckit.plan` → `plan.md`, `research.md`, `data-model.md`
- Claude chạy `/speckit.tasks` → `tasks.md`
- Plan PHẢI nêu rõ thư viện nào được thêm, version, **16KB compliance status**
- User review plan + tasks

### Bước 5: Analyze (Claude)
- Claude chạy `/speckit.analyze` → cross-artifact consistency check
- Fix issues nếu có (clarify lại)
- Confirm mọi thứ đúng hướng

### Bước 6: Implement (Copilot Pro)
- User mở Android Studio / VS Code với Copilot Pro
- Copilot đọc `tasks.md` + `plan.md` + `spec.md` (trong branch)
- Copilot đọc `.github/copilot-instructions.md` cho project conventions
- Implement từng task theo thứ tự dependency
- User review + approve từng task

### Bước 7: Test & QA (User + Copilot)
- Copilot viết tests theo task requirements (JUnit / Compose UI Test / Espresso)
- `./gradlew testDebugUnitTest` + `./gradlew lintDebug` phải pass
- `./gradlew connectedDebugAndroidTest` cho instrumented test (nếu có)
- **Verify 16KB alignment** nếu spec thêm thư viện có `.so`:
  ```bash
  ./gradlew assembleRelease
  unzip -p app/build/outputs/apk/release/app-release.apk lib/arm64-v8a/lib*.so | \
    objdump -p - | grep LOAD | awk '{print $NF}'
  # Expected: 0x4000 hoặc lớn hơn
  ```
- User test trên device (Pixel 5+, hoặc emulator API 35/36 với 16KB page size)

### Bước 8: Cập nhật context (Claude)
- Claude cập nhật `project-context.md` với kết quả spec vừa hoàn thành
- Cập nhật `sdd-roadmap.md` (đánh dấu ✅ Done), `CLAUDE.md` nếu cần
- Document Key Decisions Log mới (nếu có)
- Xóa prompt cũ trong `specify-prompts.md`

## Copilot Context Files (sẽ tạo trong Spec 001)

```
.github/
  copilot-instructions.md              # Project context cho Copilot (architecture, patterns, conventions)
  .copilot-codegeneration-instructions.md  # Code generation rules (style, patterns to follow/avoid)
```

Copilot tự đọc các file này khi generate code. Không cần config thêm.

## Nguyên Tắc

- **Claude = Planner, Copilot = Implementer** — không overlap
- **Claude KHÔNG sửa source code** (chỉ spec artifacts + context docs)
- **Copilot KHÔNG sửa spec artifacts** (chỉ đọc để implement)
- **Mọi quyết định quan trọng phải qua user confirm**
- **Spec là source of truth** — code sinh ra từ spec, không phải ngược lại
- **Constitution là luật tối cao** — mọi spec/plan phải comply
- **16KB page size là ràng buộc cứng** — mọi thư viện có `.so` phải verify trước khi merge
- **Tiếng Việt** là ngôn ngữ giao tiếp chính
- **Roadmap spec-driven** — mỗi spec xong phải test được ngay trên app

## Workflow Progress — Upcoming

Các Spec theo thứ tự implement:

| # | Tên | Dependencies | Status |
|---|-----|-------------|--------|
| 001 | `project-init-build-config` | Constitution | ⬜ Next |
| 002 | `clean-architecture-skeleton-di` | 001 | ⬜ |
| 003 | `theme-typography-darkmode` | 002 | ⬜ |
| 004 | `localization-multi-language` | 002 | ⬜ |
| 005 | `room-database-schema` | 002 | ⬜ |
| 006 | `datastore-settings` | 002 | ⬜ |
| 007 | `webview-compose-wrapper` | 005, 006 | ⬜ |
| 008 | `navigation-controls` | 007 | ⬜ |
| 009 | `address-bar-omnibox` | 008 | ⬜ |
| 010 | `search-engine-google` | 009 | ⬜ |
| 011 | `tabs-management` | 007 | ⬜ |
| 012 | `private-incognito-mode` | 011 | ⬜ |
| 013 | `bookmarks-crud` | 005, 007 | ⬜ |
| 014 | `history-view` | 005, 007 | ⬜ |
| 015 | `downloads-manager` | 007 | ⬜ |
| 016 | `settings-screen` | 003, 004, 006 | ⬜ |
| 017 | `splash-screen` | 002 | ⬜ |
| 018 | `onboarding-flow` | 003, 004, 006, 017 | ⬜ |
| 019 | `tracker-blocker-hostlist` | 011–018 | ⬜ Optional |

> Project chưa start. Bước tiếp theo: thảo luận chi tiết Spec 001 trước khi chạy `/speckit.specify`.
