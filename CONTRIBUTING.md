# CONTRIBUTING.md

本文件面向 Campus-Mate 人类组员，说明分支、提交、PR、冲突处理和提交前验证方式。项目介绍看 `README.md`，AI Agent 工作边界看 `AGENTS.md`。

## 1. 协作原则

- `main` 保持可编译、可运行、可展示。
- 一个分支只做一个功能或一个修复。
- 不把功能实现、格式化、依赖升级、重命名和文档重写混在一个 PR。
- 不提交敏感文件、API Key、学校账号密码、教务系统 Cookie、`local.properties`、`build/`、`.gradle/`、`.kotlin/`、生成 APK 或临时日志。
- Android 课程项目优先稳定可演示；没有明确需求时，不新增登录、云同步、后端服务或大型依赖。
- 涉及传感器、通知、前台服务、勿扰模式、相机扫码和 WebView 导入的改动，需要写清真机或现场验证情况。

## 2. 分支命名

建议格式：

- `feature/xxx`：新功能。
- `fix/xxx`：bug 修复。
- `docs/xxx`：文档维护。
- `chore/xxx`：构建、配置、清理类工作。
- `test/xxx`：测试补充或测试修复。

项目示例：

- `feature/llm-api-settings`
- `feature/json-export`
- `fix/task-reminder-reschedule`
- `fix/focus-service-stop`
- `docs/project-docs-maintenance`

不要额外添加工具名、账号名或个人前缀；例如使用 `feature/weather-location`，不要写成 `codex/weather-location`。

## 3. 提交规范

建议使用简短前缀：

- `feat:` 新功能。
- `fix:` 修复问题。
- `docs:` 文档改动。
- `chore:` 构建、配置、脚本等维护。
- `test:` 测试改动。
- `refactor:` 不改变行为的重构。

每个 commit 只做一类事。示例：

```text
feat: add llm settings controls
fix: reschedule reminders when task reopens
docs: align project documentation and module boundaries
```

## 4. PR 描述模板

PR 描述至少包含：

```markdown
## 做了什么

## 为什么做

## 影响文件 / 模块

## 数据库是否变化

## Manifest / 权限是否变化

## 是否涉及用户隐私、API Key 或外部请求

## 测试命令和结果

## 真机验证情况

## 已知限制 / 未验证项
```

涉及数据库表或字段时，要说明 `CampusMateContract`、`CampusMateDbHelper`、`CampusMateProvider`、model、repository 和迁移逻辑是否同步。涉及权限、Activity、Service、Receiver、Provider、通知或文件访问能力时，要说明 `AndroidManifest.xml` 是否同步。

## 5. 高冲突文件清单

这些文件容易多人同时修改。动之前先同步 `main`，PR 尽量小：

- `README.md`
- `AGENTS.md`
- `CONTRIBUTING.md`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/example/campusmate/data/db/CampusMateContract.kt`
- `app/src/main/java/com/example/campusmate/data/db/CampusMateDbHelper.kt`
- `app/src/main/java/com/example/campusmate/data/provider/CampusMateProvider.kt`
- `app/src/main/java/com/example/campusmate/ui/settings/SettingsFragment.kt`
- `app/src/main/res/layout/fragment_settings.xml`
- `app/src/main/java/com/example/campusmate/ui/main/MainActivity.kt`

如果冲突涉及数据库结构、Manifest、主导航、设置页或公共 Repository，不要简单覆盖对方改动，先确认双方意图。

## 6. 功能切分建议

### 课程 / 任务

主要文件范围：`ui/course`、`ui/task`、`CourseRepository`、`TaskRepository`、课程/任务布局和字符串。避免同时改数据库 schema，除非功能确实需要。

### 课表导入

主要文件范围：`domain/import_`、`ui/import_`、`app/src/main/assets/sample_schedule.html`、`CourseRepository`、`ImportLogRepository`。LLM 解析、本地 Jsoup 解析和 WebView 提取都必须进入 `ImportPreviewActivity`，确认后才可写库；LLM 结果必须经过本地 validator 清洗，异常字段进入 warnings 或丢弃。WebView 导入不能保存账号密码、Cookie，也不能绕过验证码；修改 WebView 流程时要保留进入、打开和退出时的 Cookie/WebStorage/缓存清理，并避免阻塞 UI。BJTU 适配默认从 `https://mis.bjtu.edu.cn/` 门户进入，真实页面仍需现场验证。

### 专注 / 通知 / 传感器

主要文件范围：`domain/focus`、`ui/focus`、`domain/notification`、`domain/reminder`、`NotificationUtils`、`PermissionUtils`、Manifest。传感器、前台服务、勿扰和通知访问必须真机验证。

### 学习计划

主要文件范围：`domain/plan`、`ui/plan`、`StudyPlanRepository`、`StudyPlan`、`study_plans` 相关布局和字符串。当前 LLM 计划生成已接入今日/本周预览确认；生成上下文包含课程、任务、天气、学习记录和已有计划。改动时必须保留预览/确认边界，并要求生成结果避开课程时间。

### 任务附件

主要文件范围：`TaskDetailActivity`、`TaskAttachmentRepository`、`TaskAttachmentAdapter`、`TaskAttachmentUiUtils`、`task_attachments` 表和相关布局。当前附件使用 SAF Uri，不做拍照、裁剪或头像资料。

### 学习名片 / 二维码

主要文件范围：`ui/profile`、`ui/buddy`、`UserProfileRepository`、`StudyBuddyRepository`。二维码使用公开 JSON，保存学习伙伴前必须预览确认。NFC 名片交换入口已移除，不要重新引入。

### 天气

主要文件范围：`domain/weather`、`WeatherRepository`、`DashboardFragment`、`SettingsFragment`、`fragment_dashboard.xml`。天气使用 `ACCESS_COARSE_LOCATION` 辅助判断城市，只保存城市名，不保存经纬度；不再保留 Mock 数据或 Mock 开关。权限授予/拒绝、定位失败、远程请求失败和缓存降级都需要说明验证情况。

### LLM

主要文件范围：`domain/llm`、`data/model/llm`、`LlmSettingsRepository`、`LlmSettingsUiBinder`、`fragment_settings.xml`，以及具体业务解析服务。当前已接入课表解析、任务网页解析预填和学习计划生成。不内置 Key，不提供后端代理，不输出完整 API Key。业务接入前要说明哪些用户数据会发给用户选择的模型服务商；课程、任务、计划等 AI 结果必须先预览或回填确认，不能静默写库。

### 文档

`README.md`、`CONTRIBUTING.md`、`AGENTS.md` 用单独文档分支维护，不和功能 PR 混合。

## 7. 提交前检查

普通代码 PR 至少运行：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

真机相关检查按改动范围选择：

- 翻转手机专注。
- 通知权限授予/拒绝。
- 前台服务通知与停止行为。
- 勿扰授权。
- NotificationListenerService 通知访问授权。
- 相机扫码和拒绝相机权限。
- SAF 图片附件选择、打开、删除。
- WebView 导入真实教务系统页面。
- WebView 导入退出后再次进入需重新登录，确认 Cookie/会话未持久化。
- 课表导入 AI/本地切换：无 API Key 和 LLM 失败时均能回退本地解析，并进入预览确认。

无法真机验证时，PR 里必须明确写“未验证”和原因。

## 8. AI Agent 使用规则

- Agent 生成代码必须人工 review 后再合并。
- Agent 未运行的测试不能写成“已通过”。
- 给 Agent 任务时必须提供目标、约束、文件范围和验收条件。
- Agent 不得处理真实 API Key、账号密码、Cookie、token、真实手机号或批量真实邮箱等敏感数据。
- 让 Agent 改文档时，要要求它先核对真实代码状态，不要只抄旧 README。
