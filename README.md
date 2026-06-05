# CampusMate 校园学习与沉浸专注助手

## 1. 项目定位

CampusMate 是一个 Android 移动应用开发课程项目，定位为本地单机校园学习管理 App。项目围绕大学生的日常学习场景，提供课程管理、任务管理、任务提醒、课表导入、专注计时、学习记录、统计展示、学习计划和轻量资料交换能力。

当前版本不做登录、云同步和后端服务；不提供模型服务，不内置 API Key；不上传用户隐私数据。当前阶段也不做头像、照片资料、聊天、动态、关注、点赞、评论、社交广场等强社交功能。学习名片只保留文本资料、二维码公开 JSON 和本地学习伙伴列表。

核心展示闭环：

```text
课程管理
  -> 任务管理
  -> 任务提醒
  -> 课表导入
  -> 专注计时
  -> 学习记录
  -> 热力图统计
  -> 设置页 / 演示数据
```

## 2. 当前版本状态

- 课程阶段版本：`V1.1-stage-15-p0-3`
- Gradle `versionName`：`1.0`
- 当前阶段：学习计划 + AI 辅助解析 + 定位天气协同，已合并 WebView 课表导入、任务图片附件、二维码学习名片、天气、LLM API 设置和 LLM 学习计划预览确认能力。
- 主闭环状态：课程、任务、提醒、导入、专注、记录、统计、设置页已跑通。
- 当前扩展功能状态：阶段 9-15 基础能力已进入代码；项目展示页、JSON 导出/备份和完整演示数据仍未完成。
- 数据库版本：`CampusMateDbHelper.DATABASE_VERSION = 5`
- 数据库名：`campus_mate.db`
- `applicationId`：`com.example.campusmate`
- `minSdk`：33
- `targetSdk`：36
- `compileSdk`：36 + `compileSdkMinor` 1（即 36.1）
- Android Gradle Plugin：9.1.1
- 技术栈：Kotlin + XML View + AppCompat/Fragment/RecyclerView/ConstraintLayout + Material Components。
- 本地存储：SQLiteOpenHelper + ContentProvider + ContentResolver + Repository。
- 当前不是 Jetpack Compose 项目，当前不是 Room 项目。

## 3. 已实现功能清单

状态说明：`已完成` 表示已有主流程代码；`基础完成` 表示流程可进入但仍需适配或增强；`实验功能` 表示已有代码但依赖系统授权/真机条件；`待验证` 表示需要真实设备或现场环境；`待实现` 表示代码中尚未完成。

| 模块 | 状态 | 真实实现说明 |
| --- | --- | --- |
| 首页 Dashboard | 已完成 | 展示今日课程、待办任务、今日/本周学习时长、计划完成趋势、下一节课（含教室）、天气卡片、城市来源和开始专注入口。 |
| 课程管理 | 已完成 | 支持新增、编辑、详情、软删除、按星期筛选、周课表网格展示和时间冲突提示。 |
| 任务管理 | 已完成 | 支持新增、编辑、详情、软删除、完成状态切换、类型、优先级、截止时间、提醒时间、AI 网页解析预填和多任务导入预览确认。 |
| 任务提醒 | 已完成 | `AlarmManager` + `ReminderReceiver` + 通知渠道；开机后通过 `BootReminderReceiver` 恢复未来提醒。 |
| 课表导入 | 已完成 | 支持粘贴 HTML、WebView 提取、LLM 优先/本地回退解析、导入预览、冲突标记和确认写入；界面不再提供示例 HTML 导入按钮。 |
| WebView 课表导入基础页 | 基础完成 / 待验证 | `WebViewImportActivity` 默认进入 BJTU MIS 门户，可手动登录导航并提取当前 HTML；进入、打开和退出时会尽力清理 Cookie/登录态；真实教务系统页面结构仍需现场验证。 |
| 专注计时 | 已完成 | `FocusActivity` 选择任务和时长，`FocusService` 前台计时，支持暂停、继续、完成、取消。 |
| 翻转手机专注 | 已完成 / 待真机验证 | `FaceDownDetector` 基于加速度传感器判断屏幕朝下；传感器不可用时保留手动开始。 |
| 前台服务 | 已完成 | `FocusService` 使用前台通知展示计时状态，并负责 FocusSession 和 StudyRecord 写入。 |
| 学习记录 | 已完成 | 专注完成后写入 `focus_sessions` 和 `study_records`。 |
| 热力图统计 | 已完成 | `StatisticsFragment` 展示今日/本周学习、连续学习、任务完成情况和最近学习热力图。 |
| 设置页 | 已完成 | 底部导航先进入 AI 设置、功能与权限设置、学习伙伴三个分区；功能页包含学习目标、计划生成时间范围、提醒、沉浸、天气、权限、演示数据和清空数据；AI 页保留独立能力开关。 |
| 学习计划 | 已完成 | `PlanListFragment` 支持今日/本周规则生成、AI 今日/本周预览确认、手动添加，完成状态切换、删除和详情页；生成上下文会参考课程、任务、天气、学习记录、已有计划和每日目标；普通任务避开课程时间，课程学习类计划可安排在对应课程时间。 |
| 任务图片附件 | 基础完成 | 新增/编辑任务页可先选择图片附件，保存任务后写入 `task_attachments`；详情页可继续添加、打开和删除；未接入拍照、裁剪或内置大图预览。 |
| 学习名片 | 已完成 | 本地编辑文本资料，保存到 `user_profile`，不需要登录。 |
| 二维码 | 已完成 | `StudyCardActivity` 生成公开 JSON 二维码；`ScanQrActivity` 扫码后先预览，再手动确认添加。 |
| 学习伙伴 | 已完成 | 扫码确认后写入 `study_buddies`，支持列表、详情和删除；NFC 名片交换入口已移除。 |
| 天气 | 已完成 / 待真机验证 | 手动城市配置、粗略定位辅助、`wttr.in` 远程请求、30 分钟缓存和无网缓存降级；不再提供 Mock 数据，不保存经纬度。 |
| 通知弱化 / 勿扰增强 | 实验功能 / 待真机验证 | `FocusService` 可按设置调用 `DndManager` 并启用 `NotificationFilterService` 标记；依赖用户系统授权。 |
| 演示数据 | 基础完成 | `DemoDataRepository` 生成课程、任务、学习计划、学习记录、学习名片、二维码伙伴和导入日志样例；附件仍需用户通过 SAF 选择真实图片。 |
| LLM 接口基础设施 | 已完成 | 已有设置页、加密 API Key 存储、OpenAI-Compatible/Gemini Client、连接测试、课表解析、任务网页解析预填/批量预览和学习计划生成；业务结果必须预览或回填确认。 |
| 项目展示页 / 技术点展示页 | 待实现 | 当前代码中未发现独立项目展示页。 |
| JSON 导出 / 备份 | 待实现 | 当前未实现本地 JSON 导出/导入流程。 |

## 4. 待实现与已知限制

- 项目展示页 / 技术点展示页：未实现。
- JSON 导出/备份：未实现；当前不会导出本地数据库或设置。
- Android 系统备份：Manifest 中 `android:allowBackup="false"`；`backup_rules.xml` 和 `data_extraction_rules.xml` 也排除数据库、SharedPreferences、文件和外部文件。
- 演示数据：当前生成课程、任务、学习计划、学习记录、学习名片、二维码伙伴和导入日志；附件仍需用户通过 SAF 选择真实图片。
- WebView 教务系统导入：默认入口为 `https://mis.bjtu.edu.cn/`，只提供用户手动登录并提取当前页面 HTML 的基础流程；不保存账号密码、Cookie，不绕过验证码；进入、重新打开和退出导入页时会尽力清理 Cookie、WebStorage、缓存、历史、表单和 HTTP Auth 数据；不同学校页面结构需要现场验证。
- NotificationListenerService：服务已声明，但通知访问权限必须由用户在系统设置中授权；当前设置页未看到专门跳转通知访问授权页的按钮，需真机验证。
- 勿扰模式：需要用户授予通知策略访问权限；未授权时不应影响普通专注计时。
- 天气：远程请求依赖网络；失败时只回退缓存。定位使用粗略位置反查城市，只保存城市名，不保存经纬度；权限授予/拒绝需真机验证。
- 图片附件：当前只通过 Storage Access Framework 选择图片并持久化 Uri；不申请相册读取权限，不支持拍照、裁剪、压缩或内置大图预览。
- 学习计划：已有手动添加、本地规则生成、AI 今日/本周计划预览、状态切换和详情页；按课程/考试细分生成、计划提醒和复杂编辑尚未接入。
- LLM：当前已有设置、Client、连接测试、课表解析、任务解析和学习计划主流程接入。任何 AI 结果都必须先进入预览确认页或回填表单，不能直接静默写入数据库。

## 5. 项目结构说明

当前真实源码结构：

```text
app/src/main/java/com/example/campusmate
├── app
│   ├── AppConfig.kt
│   ├── CampusMateApplication.kt
│   └── FeatureFlags.kt
├── data
│   ├── db
│   │   ├── CampusMateContract.kt
│   │   └── CampusMateDbHelper.kt
│   ├── model
│   │   ├── Course.kt
│   │   ├── DailyStudyStat.kt
│   │   ├── FocusSession.kt
│   │   ├── ImportLog.kt
│   │   ├── StudyBuddy.kt
│   │   ├── StudyPlan.kt
│   │   ├── StudyRecord.kt
│   │   ├── StudyTask.kt
│   │   ├── TaskAttachment.kt
│   │   ├── UserProfile.kt
│   │   └── llm
│   ├── provider
│   │   └── CampusMateProvider.kt
│   └── repository
│       ├── CourseRepository.kt
│       ├── DataMaintenanceRepository.kt
│       ├── DemoDataRepository.kt
│       ├── FocusRepository.kt
│       ├── ImportLogRepository.kt
│       ├── LlmSettingsRepository.kt
│       ├── SettingsRepository.kt
│       ├── StudyBuddyRepository.kt
│       ├── StudyPlanRepository.kt
│       ├── StudyRecordRepository.kt
│       ├── TaskAttachmentRepository.kt
│       ├── TaskRepository.kt
│       ├── UserProfileRepository.kt
│       └── WeatherRepository.kt
├── domain
│   ├── focus
│   ├── import_
│   ├── llm
│   ├── notification
│   ├── plan
│   ├── reminder
│   ├── statistics
│   └── weather
├── ui
│   ├── buddy
│   ├── common
│   ├── course
│   ├── dashboard
│   ├── focus
│   ├── import_
│   ├── main
│   ├── plan
│   ├── profile
│   ├── settings
│   ├── statistics
│   └── task
└── util
    ├── DateTimeUtils.kt
    ├── DbUtils.kt
    ├── NotificationUtils.kt
    ├── PermissionUtils.kt
    └── SystemBarsInsets.kt
```

资源与测试：

```text
app/src/main/assets/sample_schedule.html
app/src/main/res/layout
app/src/main/res/menu
app/src/main/res/values
app/src/main/res/xml
app/src/test/java/com/example/campusmate
app/src/androidTest/java/com/example/campusmate
```

## 6. 分层职责说明

- `app` 层：`Application`、`AppConfig`、`FeatureFlags`，保存应用级常量和功能开关占位。
- `ui` 层：Activity、Fragment、Adapter，负责界面展示、用户输入、页面跳转、系统入口调用和权限请求入口；不直接操作 `SQLiteDatabase`。
- `domain` 层：业务规则、HTML 解析、传感器、提醒调度、计划生成、天气、通知弱化、LLM 请求构造等逻辑。
- `data/model` 层：课程、任务、专注、学习记录、计划、附件、名片、伙伴、LLM 配置等数据对象。
- `data/repository` 层：封装 `ContentResolver`、SharedPreferences 或加密存储访问；UI 不直接访问 SQLite。
- `data/provider` 层：`CampusMateProvider`，统一 ContentProvider 边界，Manifest 中 `android:exported="false"`。
- `data/db` 层：`CampusMateContract`、`CampusMateDbHelper`，负责表结构、字段常量、Uri、数据库版本和升级迁移。
- `util` 层：日期时间、Cursor 读取、通知渠道、权限检查、系统栏适配等通用工具。

## 7. 功能逻辑闭环与文件关系

### A. 课程管理闭环

```text
CourseEditActivity
  -> CourseRepository
  -> ContentResolver
  -> CampusMateProvider
  -> courses 表
  -> CourseListFragment / CourseDetailActivity 展示
```

- `CourseEditActivity`：负责课程输入、基础校验和冲突确认，不直接写 SQLite。
- `CourseRepository`：负责课程新增、更新、软删除、查询和时间冲突判断。
- `CampusMateProvider`：统一处理 `courses` Uri 的 query/insert/update/delete。
- `CampusMateContract.Courses`：定义表名、字段、Uri、单双周常量。
- `CampusMateDbHelper`：创建 `courses` 表和索引，维护数据库版本。

### B. 任务管理与提醒闭环

```text
TaskEditActivity
  -> TaskRepository
  -> AlarmReminderScheduler / ReminderScheduler
  -> AlarmManager
  -> ReminderReceiver
  -> NotificationUtils
  -> TaskDetailActivity
```

- `TaskRepository`：保存任务、软删除任务、切换完成状态和按截止时间查询。
- `TaskEditActivity`：保存任务后取消旧提醒，并在提醒开关、通知权限和时间有效时重新调度提醒。
- `TaskReminderPolicy`：在任务完成/重新打开时判断是否取消或恢复提醒。
- `AlarmReminderScheduler`：封装精确闹钟和普通时间窗口降级。
- `ReminderReceiver`：只处理到点通知，读取当前任务状态后发通知，不承担复杂业务。
- `NotificationUtils`：负责任务提醒和前台服务通知渠道、通知 id 和通知可用性检查。
- 当前通知点击进入 `TaskDetailActivity`；`FocusActivity` 是独立专注入口，可在专注页选择待办任务。

### C. 课表导入闭环

```text
ImportScheduleActivity / WebViewImportActivity
  -> JsoupScheduleParser / WebViewScheduleExtractor
  -> CourseDraft
  -> ImportPreviewActivity
  -> CourseRepository
  -> import_logs 表
```

- 粘贴 HTML：用户粘贴 HTML 后交给 `JsoupScheduleParser`。
- WebView 当前页：`WebViewImportActivity` 让用户手动打开网页，`WebViewScheduleExtractor` 只用 `evaluateJavascript` 提取当前页面 HTML。
- BJTU 场景：WebView 默认预填 `https://mis.bjtu.edu.cn/`，避免直接打开教务子页面时因 SSO/session 缺失导致登录失败；用户登录后自行进入课表页再提取。
- 隐私清理：进入 `WebViewImportActivity`、点击打开页面和退出 Activity 时都会尽力清理 Cookie、WebStorage、缓存、历史、表单、SSL 偏好和 HTTP Auth 数据；清理过程采用异步和超时兜底，不能阻塞 UI。
- `WebViewScheduleExtractor`：会等待 DOM 中出现疑似课表表格后优先抓取表格 `outerHTML`，超时则抓取整页 `document.documentElement.outerHTML`。
- `CourseDraft`：导入预览前的课程候选对象。
- `ImportPreviewActivity`：做冲突标记、默认勾选和用户确认，不允许静默写入。
- `CourseRepository`：确认后批量写入课程。
- `ImportLogRepository` / `import_logs`：记录来源、导入数、跳过数、冲突数和消息。
- WebView 流程不保存教务系统账号、密码、Cookie，不绕过验证码。
- `ScheduleParseResult`：统一承载课程草稿、warnings、解析方式、回退原因和节次时间。
- `LlmScheduleParseService` 已接入粘贴 HTML 和 WebView 导入流程；AI 可用时优先解析，失败时可回退本地 Jsoup 规则，所有结果仍进入 `ImportPreviewActivity`。
- `LlmCourseDraftValidator` 会将 LLM JSON 转为 `CourseDraft` 并做本地校验，课程名不能为空，星期必须在 1..7，开始/结束节次和周次必须合法，单双周类型必须合法；异常字段会丢弃或进入 warnings，不能直接信任模型输出。
- LLM 与本地 fallback 已拓展地点、楼宇、教室、教师、周次和单双周解析；真实教务系统页面仍需现场验证。

### D. 专注计时闭环

```text
FocusActivity
  -> FaceDownDetector
  -> FocusStateMachine
  -> FocusService
  -> 前台通知
  -> FocusRepository
  -> StudyRecordRepository
  -> StatisticsFragment
```

- `FaceDownDetector`：只做加速度传感器稳定屏幕朝下/拿起判断。
- `FocusStateMachine`：只做合法状态流转。
- `FocusActivity`：负责任务选择、时长选择、手动开始/暂停/完成入口和服务状态展示。
- `FocusService`：负责前台计时、通知更新、传感器暂停/恢复、勿扰/过滤标记和最终记录写入。
- `FocusRepository`：写入和更新 `focus_sessions`。
- `StudyRecordRepository`：专注完成后写入 `study_records`。
- `StatisticsFragment`：读取学习记录并展示统计和热力图。

### E. 学习计划闭环

```text
PlanListFragment
  -> LlmPlanPreviewActivity (AI 生成入口)
  -> LlmWeekPlanPreviewActivity (AI 本周预览)
  -> StudyPlanContextBuilder
  -> StudyPlanGenerator (本地规则生成)
  -> CourseRepository / TaskRepository / WeatherRepository / StudyRecordRepository
  -> StudyPlanRepository
  -> study_plans 表
  -> PlanAdapter / PlanDetailActivity 展示
```

- `LlmPlanPreviewActivity`：AI 生成计划预览页，调用 LLM 生成计划后展示预览，用户可选择「追加到现有」或「替换当日」；失败时可回退本地规则生成。
- `LlmWeekPlanPreviewActivity`：本周计划预览页，逐日生成并展示，用户确认后才写入。
- `LlmPlanGenerateService`：构造 LLM 请求和可用性判断。
- `LlmPlanValidator`：解析 LLM 返回的 JSON，校验计划时间、时长等合法性。
- `StudyPlanContextBuilder`：统一构建计划上下文，包含当天课程、课程占用时间、待办任务、天气缓存、近 7 天学习记录、已有计划和每日目标。
- `StudyPlanGenerator`：本地规则生成器，基于统一上下文生成每日/每周计划，作为 AI 不可用时的回退方案。
- `StudyPlanRepository`：负责计划批量写入、按日期查询、状态更新、删除和详情查询。
- `PlanListFragment`：触发今日/本周生成、AI 生成今日、手动添加、切换完成状态和删除。
- `PlanAdapter` / `PlanDetailActivity`：展示列表和详情。
- AI 生成结果必须先在预览页确认，不能直接写入数据库；Prompt 中明确要求避开课程占用时间和已有计划时间。
- 按课程生成、按考试生成按钮目前显示后续阶段占位提示。

### F. 任务图片附件闭环

```text
TaskDetailActivity
  -> Storage Access Framework
  -> TaskAttachmentRepository
  -> task_attachments 表
  -> TaskAttachmentAdapter 展示
  -> ACTION_VIEW 打开
```

- `TaskEditActivity` 可先选择图片附件，保存任务成功后写入附件记录；`TaskDetailActivity` 负责完整附件展示、继续添加、打开和删除。
- SAF 选择限制为 `image/*`，应用保存 Uri、MIME type、显示名和创建时间。
- 代码会尽力调用 `takePersistableUriPermission`，但不复制图片原文件。
- 打开附件使用外部 `ACTION_VIEW`，没有内置图片大图预览。
- 当前不做头像、照片资料、拍照、裁剪或复杂文件管理。

### G. 学习名片 / 二维码 / 学习伙伴闭环

```text
EditProfileActivity
  -> UserProfileRepository
  -> user_profile 表
  -> StudyCardActivity 生成公开 JSON
  -> QR
  -> ScanQrActivity
  -> 预览确认
  -> StudyBuddyRepository
  -> study_buddies 表
```

- `EditProfileActivity`：编辑昵称、学校、专业、年级、简介、GitHub、邮箱、手机号和公开开关。
- `UserProfileRepository.buildPublicProfileJson()`：生成公开 JSON；邮箱/手机号只有用户打开公开开关才进入 JSON。
- `StudyCardActivity`：用 ZXing 生成二维码并显示公开 JSON。
- `ScanQrActivity`：扫码后解析为 `StudyBuddy`，先展示预览，用户确认后保存。
- `StudyBuddyRepository`：写入 `study_buddies`；来源 `0=QR`、`1=历史保留值`、`2=手动预留`。
- 当前不做头像、聊天、关注、动态或社交广场。

### H. 天气闭环

```text
DashboardFragment
  -> WeatherLocationResolver
  -> WeatherRepository
  -> WeatherDataSource
  -> RemoteWeatherDataSource
  -> WeatherParser
  -> weather_cache 表
  -> Dashboard 天气卡片
```

- `SettingsRepository` 保存天气城市、城市更新时间和定位引导状态，默认城市为北京。
- `WeatherLocationResolver` 使用 `LocationManager` + `Geocoder` 将粗略位置反查为城市名，只保存城市名，不保存经纬度。
- `WeatherRepository` 优先使用 30 分钟内新鲜缓存；强制刷新或缓存过期时再取数据。
- `RemoteWeatherDataSource` 当前远程源为 `wttr.in` JSON；失败时只回退缓存，无缓存时显示不可用提示。
- Dashboard 首次天气卡片会引导粗略定位授权；Dashboard 和设置页都支持手动重新定位。

### I. 通知弱化 / 勿扰增强闭环

```text
SettingsFragment
  -> SettingsRepository
  -> FocusActivity / FocusService
  -> DndManager
  -> NotificationFilterService
  -> 系统授权
```

- `SettingsFragment` 提供专注勿扰和通知过滤开关。
- `SettingsRepository` 保存开关状态。
- `FocusService` 开始专注时检查开关：勿扰开启则调用 `DndManager.enableDnd()`；通知过滤开启则设置 `NotificationFilterService.isFocusModeActive = true`。
- `DndManager` 只能在用户已授予通知策略访问权限后切换勿扰，不绕过系统限制。
- `NotificationFilterService` 需要用户在系统通知访问设置中授权，否则无法过滤通知。
- 该模块必须真机验证；模拟器结果不能作为最终结论。

### J. 数据维护 / 演示数据闭环

```text
SettingsFragment
  -> DataMaintenanceRepository
  -> ContentResolver / CampusMateProvider
  -> 清空本地数据
```

```text
SettingsFragment
  -> DemoDataRepository
  -> CourseRepository / TaskRepository / StudyRecordRepository / ImportLogRepository
  -> Dashboard / 任务 / 统计可展示
```

- 清空范围：课程、任务、学习计划、任务附件、专注记录、学习统计、导入日志、学习名片、学习伙伴和天气缓存。
- 清空和重新生成演示数据都会先取消已调度任务提醒。
- 清空本地数据不可撤销。
- 当前演示数据覆盖课程、任务、学习计划、学习记录、热力图、学习名片、二维码伙伴和导入日志；图片附件仍需通过 SAF 选择真实文件。

### K. LLM 接口闭环

```text
SettingsFragment
  -> LlmSettingsUiBinder
  -> LlmSettingsRepository
  -> 加密保存 API Key
  -> LlmClientFactory
  -> OpenAiCompatibleLlmClient / GeminiLlmClient
  -> 用户选择的模型服务商
```

- App 不提供模型服务、不内置 API Key、不提供后端代理。
- 用户在设置页自行填写 API Key、Base URL、Model、Header 类型和生成参数。
- API Key 保存在本机 `EncryptedSharedPreferences` 中；普通配置保存在 SharedPreferences 中。
- 连接测试从手机直接请求用户选择的模型服务商。
- 错误详情会通过 `LlmHttpUtils` 屏蔽当前 API Key，避免完整密钥出现在 UI 或测试输出中。
- 当前支持 OpenAI-Compatible 和 Gemini 两类客户端；预设只用于填表，用户可以按控制台实际配置修改。
- 当前已把 LLM 接入课表导入、任务网页解析和学习计划主流程：课表进入导入预览，任务可回填编辑表单或进入多任务导入预览，计划进入预览确认；失败时可回退本地规则或提示用户配置。
- AI 解析字段已拓展到地点别名（`location`、`venue`、`campus`、`building`、`room` 等）、教师别名、周次和单双周；仍需用户在预览页或表单里确认。

## 8. 数据库设计说明

数据库版本以 `CampusMateDbHelper.DATABASE_VERSION` 为准，当前为 5。LLM 设置当前不使用数据库表。

| 表 | 作用 | 关键字段 | 读写 Repository | 关系 |
| --- | --- | --- | --- | --- |
| `courses` | 课程表数据 | `name`、`teacher`、`classroom`、`weekday`、`start_section`、`end_section`、`start_week`、`end_week`、`week_type`、`color`、`is_deleted` | `CourseRepository` | 可被任务、专注会话和学习记录通过 `course_id` 关联。 |
| `study_tasks` | 作业、实验、考试、复习、项目等任务 | `course_id`、`title`、`description`、`type`、`priority`、`due_at`、`remind_at`、`status`、`is_deleted` | `TaskRepository` | 可关联课程，也可被专注和学习记录关联。 |
| `focus_sessions` | 一次专注计时会话 | `task_id`、`course_id`、`planned_duration_sec`、`actual_duration_sec`、`start_at`、`end_at`、`status`、`pause_count`、`interrupt_count` | `FocusRepository` | 完成后通常生成一条 `study_records`。 |
| `study_records` | 学习记录和统计来源 | `task_id`、`course_id`、`focus_session_id`、`title`、`duration_sec`、`record_date`、`source`、`note` | `StudyRecordRepository` | 供 `StatisticsFragment` 和热力图聚合。 |
| `import_logs` | 课表导入记录 | `source_type`、`imported_count`、`skipped_count`、`conflict_count`、`created_at`、`message` | `ImportLogRepository` | 记录导入行为，不直接外键关联课程。 |
| `user_profile` | 本机学习名片 | `nickname`、`school`、`major`、`grade`、`bio`、`avatar_uri`、`github`、`email`、`phone`、`show_email`、`show_phone` | `UserProfileRepository` | 用于生成公开 JSON，不上传云端。 |
| `study_buddies` | 二维码确认添加的学习伙伴 | `nickname`、`school`、`major`、`grade`、`bio`、`github`、`email`、`phone`、`source`、`added_at`、`note` | `StudyBuddyRepository` | `source=0` 二维码，`source=1` 历史保留值，`source=2` 手动预留。 |
| `weather_cache` | 天气缓存 | `city`、`weather_text`、`temperature`、`humidity`、`wind`、`source`、`raw_json`、`updated_at` | `WeatherRepository` | Dashboard 天气卡片读取；只缓存城市天气，不保存经纬度。 |
| `study_plans` | 学习计划 | `title`、`plan_date`、`planned_minutes`、`actual_minutes`、`start_time`、`end_time`、`type`、`status`、`source_type` | `StudyPlanRepository` | 由本地规则生成器、AI 预览确认或手动添加写入，供计划列表和详情展示。 |
| `task_attachments` | 任务图片附件 Uri | `task_id`、`uri`、`mime_type`、`title`、`created_at` | `TaskAttachmentRepository` | 按 `task_id` 关联任务详情页。 |

Provider Uri 均以 `content://com.example.campusmate.provider/` 开头，当前包含：`courses`、`tasks`、`focus_sessions`、`study_records`、`import_logs`、`user_profile`、`study_buddies`、`weather_cache`、`study_plans`、`task_attachments` 及各自 `/#` item Uri。

## 9. 权限说明

Manifest 当前声明：

| 权限 / feature | 用途 | 授权方式与降级 |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | Android 13+ 任务提醒和前台服务通知 | 运行时授权；拒绝后应用不崩溃，但通知不显示。 |
| `FOREGROUND_SERVICE` | 前台服务基础权限 | 用于 `FocusService`。 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 前台服务类型权限 | `FocusService` 声明 `foregroundServiceType="dataSync"`。 |
| `RECEIVE_BOOT_COMPLETED` | 开机恢复未来提醒 | `BootReminderReceiver` 处理。 |
| `SCHEDULE_EXACT_ALARM` | 尽量准时触发任务提醒 | 不可用时 `AlarmReminderScheduler` 降级到 `setWindow`。 |
| `CAMERA` | 二维码扫描 | 运行时授权；拒绝后扫码不可用，二维码生成仍可用。 |
| `ACCESS_NOTIFICATION_POLICY` | 专注时可按用户授权切换勿扰模式 | 需要用户在系统通知策略访问页授权；未授权不影响普通专注计时。 |
| `INTERNET` | 远程天气、WebView 导入、LLM 连接测试和 AI 请求 | 无网络时天气可回退缓存；LLM 测试失败不影响本地功能。 |
| `ACCESS_COARSE_LOCATION` | 根据粗略位置辅助判断天气城市 | 运行时授权；拒绝后继续使用手动城市和天气缓存。 |
| `android.hardware.camera required=false` | 相机硬件声明 | 没有相机时扫码不可用。 |

Manifest 当前未声明相册读取权限。图片附件通过 SAF 选择图片，不需要 `READ_MEDIA_IMAGES` 或 `READ_EXTERNAL_STORAGE`。定位仅用于反查天气城市，代码不保存经纬度。

勿扰模式和通知访问属于系统设置授权：应用只能引导或提示用户授权，不能绕过系统限制；相关功能需要真机验证。

## 10. 运行方式与测试方式

Android Studio：

1. 打开仓库根目录。
2. 等待 Gradle Sync 完成。
3. 选择 `app` 配置运行到 Android 13+ 模拟器或真机。
4. 传感器、通知权限、相机扫码、勿扰和通知访问必须用真机做最终验证。

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat connectedAndroidTest
```

Debug APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

当前已有 JVM/仪器测试文件包括 `ScheduleParserTest`、`LlmTaskDraftValidatorTest`、`StudyPlanContextBuilderTest`、`FocusStateMachineTest`、`HeatmapCalculatorTest`、`TaskReminderPolicyTest`、`LlmProviderPresetsTest`、`LlmSettingsRepositoryTest`、`OpenAiCompatibleRequestBuilderTest`、`GeminiRequestBuilderTest`、`RepositoryInstrumentedTest`、`WeatherParserInstrumentedTest` 等。

真机验证清单：

- 通知权限授予/拒绝下的任务提醒。
- 精确闹钟不可用时的提醒降级。
- 翻转手机开始、拿起暂停、放回继续。
- `FocusService` 前台通知、完成写入和取消行为。
- 勿扰授权和通知访问授权后的专注弱化效果。
- 相机扫码和拒绝权限降级。
- SAF 图片附件选择、持久 Uri、外部打开和删除。
- WebView 打开真实教务系统页面并提取当前 HTML。
- WebView 导入离开后再次进入必须重新登录，验证未持久化 Cookie/会话；BJTU 场景需从 MIS 门户登录后进入课表页再提取。
- 课表导入 AI/本地切换：无 API Key 时回退本地解析，LLM 失败时提示原因并可进入本地解析，最终都必须进入预览页。
- Android 粗略定位权限授予/拒绝、天气城市反查、远程天气有网/无网缓存降级。

本 README 只说明可运行命令和验证项；是否已通过以本次实际执行结果为准。
