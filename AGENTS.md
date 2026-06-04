# AGENTS.md

## 1. 文件用途

本文件只面向 Codex、Cursor、Copilot Chat 等 AI Agent，规定 Agent 在本仓库中理解项目、修改代码、验证结果和维护文档时必须遵守的边界。项目介绍和展示说明看 `README.md`；人类组员的分支、提交、PR 和冲突处理流程看 `CONTRIBUTING.md`。

## 2. 当前仓库事实

Agent 必须以真实文件为准，不得假设不存在的模块已经完成。

- 这是 Android 单模块工程，包含 `settings.gradle.kts`、根 `build.gradle.kts`、`gradle.properties`、`gradlew`、`gradlew.bat`、`gradle/libs.versions.toml` 和 `app/`。
- 主要语言是 Kotlin，源码位于 `app/src/main/java/com/example/campusmate`。
- UI 是 XML View + AppCompat + Fragment + RecyclerView + ConstraintLayout + Material Components；当前没有 Jetpack Compose。
- 本地存储是 `SQLiteOpenHelper` + `ContentProvider` + `ContentResolver` + Repository；当前没有 Room。
- Android Gradle Plugin 来自 `gradle/libs.versions.toml`，当前为 `9.1.1`。
- `app/build.gradle.kts` 当前配置：`applicationId = "com.example.campusmate"`、`minSdk = 33`、`targetSdk = 36`、`compileSdk = 36`、`compileSdkMinor = 1`（即 36.1）、`versionName = "1.0"`。
- `CampusMateDbHelper.DATABASE_NAME = "campus_mate.db"`，`DATABASE_VERSION = 5`。
- Manifest 当前声明 `POST_NOTIFICATIONS`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`RECEIVE_BOOT_COMPLETED`、`SCHEDULE_EXACT_ALARM`、`CAMERA`、`NFC`、`INTERNET`、`ACCESS_COARSE_LOCATION`；相机和 NFC 硬件均为 `required=false`；`allowBackup=false`。
- 已使用 Jsoup 做课表 HTML 解析，ZXing 做二维码生成和扫码，AndroidX Security Crypto 做 LLM API Key 加密存储。
- 已存在模块：Dashboard、课程管理、任务管理、任务提醒、任务 AI 网页解析预填、课表导入、WebView 课表导入基础页、专注计时、翻转检测、前台服务、学习记录、热力图统计、设置页、学习计划、任务图片附件、学习名片、二维码、学习伙伴、NFC、天气定位、通知弱化/勿扰增强、LLM API 设置与 Client 基础设施、LLM 课表解析和 LLM 学习计划预览确认。
- 待实现或待增强模块：项目展示页/技术点展示页、JSON 导出/备份、完整演示数据覆盖、真实教务系统 WebView 适配增强、学习计划按课程/考试生成和提醒、真实 AI 调用效果验证。
- 当前不做登录、云同步、后端服务、头像/照片资料、聊天、动态、关注、点赞、评论或社交广场。
- LLM 当前状态：设置页、预设、加密 Key 存储、连接测试、OpenAI-Compatible/Gemini Client、`LlmScheduleParseService`、`LlmTaskParseService` 和 `LlmPlanGenerateService` 已存在；课表导入已按设置接入 AI 优先/本地回退；任务网页解析只回填 `TaskEditActivity`；学习计划主流程已通过 `LlmPlanPreviewActivity` / `LlmWeekPlanPreviewActivity` 接入预览确认和本地规则回退。
- 当前工作目录未检测到 `.git` 元数据时，Agent 不得声称已创建分支、提交或推送。
- 根目录存在 `local.properties`，但 `.gitignore` 已排除；Agent 不得提交或要求提交该文件。
- 创建分支必须遵循 `CONTRIBUTING.md` 的命名格式，例如 `feature/xxx`、`fix/xxx`、`docs/xxx`；不要自行添加 `codex/` 等额外前缀，除非用户明确要求。

## 3. Agent 工作原则

- 先读代码再改代码。改动前至少查看相关 `ui`、`domain`、`data`、`res/layout`、`res/values/strings.xml`、`AndroidManifest.xml` 和 README 对应说明。
- 小步修改，一次任务只做一类改动，不混合功能实现、UI 大改、依赖升级、重命名、格式化和文档重写。
- 不做无关重构。课程项目优先稳定可编译、可运行、可展示、可解释。
- 不切换技术栈。不要把项目迁移为 Compose、Room、后端服务或云数据库。
- 可见文案优先放入 `res/values/strings.xml`，不要散落在 Kotlin 或 XML 中。
- UI 层不直接操作 `SQLiteDatabase`，数据读写走 Repository + `ContentResolver` + `CampusMateProvider`。
- 不伪造“已测试”或“已完成”。没有运行过的命令写“未运行”；无法真机验证的功能说明原因。
- 不提交敏感数据，不输出完整 API Key、Authorization Header、`x-goog-api-key`、账号密码、Cookie、token、真实手机号或批量真实邮箱。
- 不删除组员代码、文档、截图、报告材料、示例 HTML 或演示资源，除非用户明确要求。
- 保持“不保存教务系统账号密码、不绕过验证码、不上传用户隐私数据”的边界。
- 遇到不确定需求，先写清假设、影响文件和验收条件，再实现。

## 4. 禁止事项

- 不迁移 Jetpack Compose。
- 不迁移 Room。
- 不新增后端服务、登录系统、云同步、云数据库或统计 SDK。
- 不内置任何 API Key，不新增强制云服务。
- 不硬编码个人路径、Android SDK 路径、学校账号密码、教务系统 Cookie 或 token。
- 不绕过验证码，不抓取或上传用户隐私数据。
- 不保存教务系统账号、密码、Cookie。
- 不实现头像、照片资料、聊天、动态、关注、点赞、评论或社交广场。
- 不让 AI 结果直接写入数据库；涉及课程、任务、计划等业务数据时必须有预览确认。
- 不提交 `local.properties`、`build/`、`.gradle/`、`.kotlin/`、`.idea/workspace.xml`、生成 APK、临时日志或本机缓存。
- 不用删除重建表的方式破坏已有演示数据。

## 5. 模块修改导航

### 课程管理

优先读：

- `app/src/main/java/com/example/campusmate/ui/course`
- `app/src/main/java/com/example/campusmate/data/repository/CourseRepository.kt`
- `app/src/main/java/com/example/campusmate/data/model/Course.kt`
- `app/src/main/res/layout/fragment_course_list.xml`
- `app/src/main/res/layout/activity_course_edit.xml`
- `app/src/main/res/layout/activity_course_detail.xml`

边界：课程 CRUD 走 `CourseRepository`；软删除用 `is_deleted`；冲突判断保留在 repository/domain 风格代码中。修改后至少验证新增、编辑、详情、删除、星期筛选和冲突提示。

### 任务与提醒

优先读：

- `app/src/main/java/com/example/campusmate/ui/task`
- `app/src/main/java/com/example/campusmate/data/repository/TaskRepository.kt`
- `app/src/main/java/com/example/campusmate/domain/reminder`
- `app/src/main/java/com/example/campusmate/util/NotificationUtils.kt`
- `app/src/main/java/com/example/campusmate/util/PermissionUtils.kt`
- `app/src/main/AndroidManifest.xml`

边界：任务保存走 `TaskRepository`；提醒调度走 `AlarmReminderScheduler`；到点通知只在 `ReminderReceiver` 中处理当前任务状态后发出。任务 AI 网页解析只允许回填 `TaskEditActivity` 表单，用户确认保存前不能直接写数据库。修改后验证保存、编辑、AI 网页预填、完成/取消完成、删除、提醒调度、开机恢复和通知权限降级。

### 课表导入

优先读：

- `app/src/main/java/com/example/campusmate/domain/import_`
- `app/src/main/java/com/example/campusmate/ui/import_`
- `app/src/main/assets/sample_schedule.html`
- `app/src/main/java/com/example/campusmate/data/repository/CourseRepository.kt`
- `app/src/main/java/com/example/campusmate/data/repository/ImportLogRepository.kt`
- `app/src/main/res/layout/activity_import_schedule.xml`
- `app/src/main/res/layout/activity_import_preview.xml`
- `app/src/main/res/layout/activity_web_view_import.xml`

边界：解析逻辑放在 `domain/import_`；UI 只负责输入、预览、确认和错误提示。导入必须进入 `ImportPreviewActivity`，不能静默写入。WebView 不保存账号密码/Cookie，不绕过验证码。修改后验证 sample、粘贴 HTML、预览冲突、确认写入和导入日志。

### 专注 / 传感器 / 前台服务

优先读：

- `app/src/main/java/com/example/campusmate/domain/focus`
- `app/src/main/java/com/example/campusmate/ui/focus/FocusActivity.kt`
- `app/src/main/java/com/example/campusmate/ui/focus/FocusService.kt`
- `app/src/main/java/com/example/campusmate/data/repository/FocusRepository.kt`
- `app/src/main/java/com/example/campusmate/data/repository/StudyRecordRepository.kt`
- `app/src/test/java/com/example/campusmate/FocusStateMachineTest.kt`
- `app/src/main/AndroidManifest.xml`

边界：状态切换优先放在 `FocusStateMachine`；传感器判断在 `FaceDownDetector`；计时真相和写入在 `FocusService`。注册传感器后必须在合适生命周期注销。修改后验证手动开始、翻转开始、拿起暂停、放回继续、完成写入、取消停止、前台通知和无传感器降级。传感器结论必须真机验证。

### 学习计划

优先读：

- `app/src/main/java/com/example/campusmate/ui/plan`
- `app/src/main/java/com/example/campusmate/domain/plan/StudyPlanGenerator.kt`
- `app/src/main/java/com/example/campusmate/domain/plan/LlmPlanGenerateService.kt`
- `app/src/main/java/com/example/campusmate/data/repository/StudyPlanRepository.kt`
- `app/src/main/java/com/example/campusmate/data/model/StudyPlan.kt`
- `app/src/main/res/layout/fragment_plan_list.xml`
- `app/src/main/res/layout/activity_plan_detail.xml`

边界：当前主流程包含本地规则生成器、`StudyPlanContextBuilder` 和 LLM 今日/本周预览确认入口；上下文会读取课程、任务、天气、近 7 天学习记录、已有计划和每日目标；按课程/考试生成仍是占位；LLM 结果必须先进入预览页，用户确认后才可追加或替换计划。生成 prompt 必须要求避开课程占用时间。修改后验证今日/本周生成、AI 可用/不可用回退、重复生成提示、手动添加、完成状态、删除和详情页。

### 任务附件

优先读：

- `app/src/main/java/com/example/campusmate/ui/task/TaskDetailActivity.kt`
- `app/src/main/java/com/example/campusmate/ui/task/TaskAttachmentAdapter.kt`
- `app/src/main/java/com/example/campusmate/ui/task/TaskAttachmentUiUtils.kt`
- `app/src/main/java/com/example/campusmate/data/repository/TaskAttachmentRepository.kt`
- `app/src/main/java/com/example/campusmate/data/model/TaskAttachment.kt`
- `app/src/main/res/layout/activity_task_detail.xml`
- `app/src/main/res/layout/item_task_attachment.xml`

边界：当前附件入口在任务详情页；通过 SAF 选择 `image/*`，保存 Uri，不复制原文件，不申请相册读取权限。修改后验证选择、持久化 Uri、列表展示、外部打开、删除和任务删除时附件清理。

### 学习名片 / 二维码 / NFC

优先读：

- `app/src/main/java/com/example/campusmate/ui/profile`
- `app/src/main/java/com/example/campusmate/ui/buddy`
- `app/src/main/java/com/example/campusmate/ui/nfc`
- `app/src/main/java/com/example/campusmate/domain/nfc`
- `app/src/main/java/com/example/campusmate/data/repository/UserProfileRepository.kt`
- `app/src/main/java/com/example/campusmate/data/repository/StudyBuddyRepository.kt`
- `app/src/main/java/com/example/campusmate/data/model/StudyBuddy.kt`
- `app/src/main/AndroidManifest.xml`

边界：二维码和 NFC 复用 `UserProfileRepository.buildPublicProfileJson()`；邮箱/手机号只有公开开关打开才进入 JSON；接收后必须预览确认；保存 NFC 伙伴用 `StudyBuddy.SOURCE_NFC`。修改后验证二维码生成、相机权限、扫码预览、重复提示、伙伴保存/删除、NFC unsupported/off 降级、NDEF 写入/接收。NFC 必须真机验证。

### 天气

优先读：

- `app/src/main/java/com/example/campusmate/domain/weather`
- `app/src/main/java/com/example/campusmate/data/repository/WeatherRepository.kt`
- `app/src/main/java/com/example/campusmate/ui/dashboard/DashboardFragment.kt`
- `app/src/main/java/com/example/campusmate/ui/settings/SettingsFragment.kt`
- `app/src/main/res/layout/fragment_dashboard.xml`

边界：天气使用 `ACCESS_COARSE_LOCATION`、`LocationManager` 和 `Geocoder` 辅助判断城市；只保存城市名，不保存经纬度。当前没有 Mock 数据源、Mock 开关或 Mock fallback；远程天气失败时只保留缓存降级，无缓存时显示不可用。修改后验证城市设置、首次定位引导、Dashboard 手动定位、设置页手动定位、权限拒绝、缓存新鲜度和无网降级。

### 通知弱化 / 勿扰

优先读：

- `app/src/main/java/com/example/campusmate/domain/notification/DndManager.kt`
- `app/src/main/java/com/example/campusmate/domain/notification/NotificationFilterService.kt`
- `app/src/main/java/com/example/campusmate/ui/focus/FocusService.kt`
- `app/src/main/java/com/example/campusmate/ui/settings/SettingsFragment.kt`
- `app/src/main/java/com/example/campusmate/data/repository/SettingsRepository.kt`
- `app/src/main/AndroidManifest.xml`

边界：勿扰和通知访问只能由用户授权，不能绕过系统限制。`NotificationFilterService` 需要系统通知访问权限；`DndManager` 需要通知策略访问权限。修改后验证授权入口、未授权降级、专注开始启用、专注结束恢复和服务销毁清理。

### LLM / AI 辅助能力

优先读：

- `app/src/main/java/com/example/campusmate/data/model/llm`
- `app/src/main/java/com/example/campusmate/data/repository/LlmSettingsRepository.kt`
- `app/src/main/java/com/example/campusmate/domain/llm`
- `app/src/main/java/com/example/campusmate/domain/import_/LlmScheduleParseService.kt`
- `app/src/main/java/com/example/campusmate/domain/plan/LlmPlanGenerateService.kt`
- `app/src/main/java/com/example/campusmate/ui/settings/LlmSettingsUiBinder.kt`
- `app/src/main/res/layout/fragment_settings.xml`

边界：

- 用户自带 API Key，App 不提供模型服务，不内置 Key，不提供后端代理。
- API Key 必须保存在本机加密存储中；不得写入 Logcat、Toast、Snackbar、README 或测试输出。
- 预设服务商只用于填充表单，baseUrl、model 和 Header 类型必须允许用户修改。
- 当前只实现设置、Key 存储、连接测试和基础生成请求。
- 课表解析已接入 `LLM_FIRST_FALLBACK_LOCAL` 类似策略；业务接入仍必须进入导入预览。
- 任务网页解析已接入 `TaskWebViewParseActivity`，只回填 `TaskEditActivity`，用户保存前必须可检查。
- 计划生成已接入 LLM 预览确认；修改时不能绕过用户确认，不能让 AI 结果直接静默写数据库。

### 数据库 / Provider

优先读：

- `app/src/main/java/com/example/campusmate/data/db/CampusMateContract.kt`
- `app/src/main/java/com/example/campusmate/data/db/CampusMateDbHelper.kt`
- `app/src/main/java/com/example/campusmate/data/provider/CampusMateProvider.kt`
- `app/src/main/java/com/example/campusmate/data/model`
- `app/src/main/java/com/example/campusmate/data/repository`
- `app/src/androidTest/java/com/example/campusmate/RepositoryInstrumentedTest.kt`

新增表或字段时必须同步：

- Contract 表名、字段、Uri 和常量。
- DbHelper 建表、索引、版本号和 `onUpgrade`。
- Provider UriMatcher、类型、表映射、collection Uri。
- model 和 repository。
- 迁移路径和测试。

### Manifest / 权限

新增或修改 Activity、Service、BroadcastReceiver、ContentProvider、权限、feature、前台服务、NFC、通知、相机、文件访问能力时，必须同步检查 `app/src/main/AndroidManifest.xml`。前台服务还要核对 `foregroundServiceType`、通知渠道、通知权限和停止行为。

## 6. 文档维护规则

- 代码实现变化后必须同步 `README.md` 的功能清单、结构、闭环、数据库、权限和已知限制。
- 人类协作流程变化后同步 `CONTRIBUTING.md`。
- Agent 工作边界、模块导航、禁止事项或验证要求变化后同步 `AGENTS.md`。
- 三个文档中的版本号、数据库版本、applicationId、SDK、已实现功能和待实现功能不得互相冲突。
- 不确定时以真实代码为准；如果无法确认，写“基础完成，需验证”或“待验证”，不要编造。
- 文档 PR 不要混入 Kotlin、XML、Gradle、Manifest 或资源改动，除非用户明确要求。

## 7. 验证要求

常规命令：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

需要设备或现场环境的验证项：

- `connectedAndroidTest`
- NFC 写入/接收。
- 加速度传感器翻转检测。
- Android 13+ 通知权限。
- 前台服务通知和停止行为。
- 精确闹钟授权/降级。
- 勿扰模式授权。
- NotificationListenerService 通知访问授权。
- 相机扫码。
- SAF 图片附件。
- 真实教务系统 WebView 导入。

无法运行时必须在最终回复和 PR 中写明原因，例如“本地 SDK 缺失”“没有连接 Android 设备”“没有 NFC 真机”“未提供真实教务系统页面”。未运行的命令不得写成通过。
