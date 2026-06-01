# P0-2：LLM 课表解析接入（变更说明 / 沟通文档）

本文件用于组内同步 P0-2 的实现范围、关键改动点、验收方式与已知限制，避免口头沟通遗漏。

最后更新：2026-06-01

## 目标与验收点

- 默认启用 AI 辅助解析（LLM）；当 LLM 不可用或解析失败时，自动回退本地 Jsoup 解析。
- 无论 LLM 还是本地解析，最终都必须进入 `ImportPreviewActivity` 预览确认后才能写入数据库（禁止静默写入）。
- 不保存教务系统账号、密码、Cookie；不绕过验证码。
- LLM 输出必须先经过本地校验与清洗：异常字段要丢弃或进入 warnings，不直接信任模型输出。

本地校验规则（关键）：

- `name` 非空。
- `weekday` 在 1..7。
- `startSection` / `endSection` 在项目允许范围内，且 `startSection <= endSection`。
- `startWeek <= endWeek`。
- `weekType` 合法。

## 导入闭环（新流程）

1. 用户选择导入来源：示例 HTML / 粘贴 HTML / WebView 提取。
2. 若用户选择 AI 模式且 LLM 配置可用：优先走 `LlmScheduleParseService`。
3. LLM 不可用或解析失败：提示原因，并允许一键回退到本地规则解析（Jsoup）。
4. 得到 `CourseDraft` 列表后统一进入 `ImportPreviewActivity`。
5. 用户确认后才调用 `CourseRepository` 批量写入，并记录导入日志。

## 主要改动点（P0-2 核心）

- 新增 `ScheduleParseResult`：统一承载 drafts + warnings + parserLabel + fallbackReason + sectionTimeSlots。
- 新增 `LlmCourseDraftValidator`：将 LLM JSON 转为 `CourseDraft` 并校验字段合法性。
- `LlmSchedulePromptFactory`：补充中文提示词，覆盖“星期/节次/周次/单双周”等常见表达。
- `ImportScheduleActivity` / `WebViewImportActivity`：入口统一采用“LLM 优先，失败回退本地解析”；AI 模式展示隐私提示（只提示一次）。
- `ImportPreviewActivity`：展示解析方式、回退原因、warnings 摘要；导入日志附带解析方式便于追踪来源。

## BJTU（北京交通大学）WebView 适配说明

- 入口页面（推荐从此进入再手动导航）：`https://mis.bjtu.edu.cn/`
- 说明：
  - 部分教务子页面直连会因 SSO/session 缺失提示“登录失败”。因此 WebView 导入默认预填 MIS 门户地址，用户登录后再自行进入选课/课表页，最后执行“提取并解析”。
  - `WebViewScheduleExtractor` 会轮询等待 DOM 中出现“疑似课表表格”关键文本后优先抓取该表格 `outerHTML`；超时则抓取整页 `document.documentElement.outerHTML`，以提高解析成功率。

## WebView 隐私：Cookie/登录态清理策略（严格）

目标：每次进入 WebView 导入页，都必须重新登录；不得因为上次会话残留导致“自动进入账户首页”。

触发清理时机：

- 进入 `WebViewImportActivity` 时：先清理再允许打开页面。
- 点击“打开”按钮时：先清理再 `loadUrl`（避免竞态导致偶发自动登录）。
- 用户返回/退出该 Activity 时：再次清理，确保不残留。

清理范围（best-effort）：

- Cookies：清除 persistent + session cookies，并 `flush()`。
- HTML5 Storage：`WebStorage.deleteAllData()`（localStorage / IndexedDB 等）。
- WebView 侧数据：`clearCache(true)` / `clearHistory()` / `clearFormData()` / `clearSslPreferences()`。
- WebViewDatabase：`clearHttpAuthUsernamePassword()` / `clearFormData()`。

防卡死（重要）：

- Cookie 清理回调在部分 WebView 实现上可能延迟；实现上必须“异步 + 超时兜底”，禁止任何等待式阻塞 UI（避免上次出现的卡死/ANR）。

## 修改文件清单（P0-2 核心）

- `app/src/main/java/com/example/campusmate/domain/import_/WebViewScheduleExtractor.kt`
- `app/src/main/java/com/example/campusmate/domain/import_/ScheduleParseResult.kt`
- `app/src/main/java/com/example/campusmate/domain/import_/LlmScheduleParseSettingsSource.kt`
- `app/src/main/java/com/example/campusmate/domain/import_/LlmSchedulePromptFactory.kt`
- `app/src/main/java/com/example/campusmate/domain/import_/LlmScheduleParseService.kt`
- `app/src/main/java/com/example/campusmate/domain/import_/LlmCourseDraftValidator.kt`
- `app/src/main/java/com/example/campusmate/ui/import_/ImportScheduleActivity.kt`
- `app/src/main/java/com/example/campusmate/ui/import_/WebViewImportActivity.kt`
- `app/src/main/java/com/example/campusmate/ui/import_/ImportPreviewActivity.kt`
- `app/src/main/java/com/example/campusmate/ui/main/MainActivity.kt`
- `app/src/main/res/layout/activity_import_schedule.xml`
- `app/src/main/res/layout/activity_import_preview.xml`

## 验证方式（建议）

功能验证（真机/模拟器均可；WebView 建议真机）：

- sample HTML：能进入预览页；冲突标记正常；确认后写入并记录导入日志。
- 粘贴 HTML：空输入提示正常；有效输入能进入预览页。
- WebView（BJTU）：登录进入课表页后点击“提取并解析”，能稳定进入预览页。
- WebView 隐私验收（严格）：退出/返回离开 WebView 导入页后再次进入，必须重新登录（验证未持久化 Cookie/会话）。
- AI/本地切换：无 API Key 时自动回退本地解析；LLM 失败时有明确错误提示并可回退本地解析。

构建验证：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

说明：以上命令需要本机具备可用 Android SDK/JDK（部分环境可能因缺少 JDK 21 或网络受限导致 toolchain 下载失败）。

