# AGENTS.md

本文件是给 Codex、Cursor、Copilot Chat 等代码 Agent 使用的项目级指令。它不是 README，也不是第二份 CONTRIBUTING。人类成员的分支、提交、PR、冲突处理等协作流程见 `CONTRIBUTING.md`；本文件只规定 Agent 在本仓库中理解项目、修改代码、验证结果时必须遵守的规则。

## 1. 项目定位

Campus-Mate 是面向大学生校园学习与生活管理的 Android 小组课程项目，当前定位是本地单机 App。Agent 修改代码时优先保证：

- 可以编译；
- 可以运行；
- 可以展示；
- 关键逻辑可以解释；
- 结果可以写入课程报告或展示材料。

不要把本项目改成过度工程化的商业应用。除非 README 或明确需求要求，不要引入登录系统、后端服务、云同步、复杂架构框架或大型依赖。

## 2. 当前仓库事实

Agent 必须基于真实仓库内容工作，不得假设不存在的模块已经实现。

- 这是 Android 单模块工程，根目录包含 `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradlew`、`gradlew.bat`、`gradle/libs.versions.toml` 和 `app/`。
- 主要语言是 Kotlin，源码位于 `app/src/main/java/com/example/campusmate`。
- 构建使用 Gradle Kotlin DSL；Android Gradle Plugin 版本来自 `gradle/libs.versions.toml`，当前为 `9.1.1`。
- `app/build.gradle.kts` 当前配置：`applicationId = "com.example.campusmate"`、`minSdk = 35`、`targetSdk = 36`、`compileSdk = 36.1`。
- UI 使用 XML View、AppCompat、Fragment、RecyclerView、ConstraintLayout 和 Material Components。当前没有 Jetpack Compose。
- 本地存储使用 `SQLiteOpenHelper` + `ContentProvider` + `ContentResolver` + Repository。当前没有 Room。
- 已使用 Jsoup 做课表 HTML 解析，使用 ZXing 做二维码生成和扫码。
- `README.md` 说明当前版本为 `V1.1-stage-13`，主闭环已跑通，NFC、天气、WebView 课表导入基础页和任务图片附件已完成基础实现，学习计划、JSON 导出/备份等仍属于后续计划。
- 当前工作目录未检测到 `.git` 元数据时，Agent 不得声称已创建分支、提交或推送。
- 根目录存在 `local.properties`，但 `.gitignore` 已排除；Agent 不得提交或要求提交该文件。
- 当前版本默认不启用 Android 系统备份；不要通过系统备份导出本地数据库、设置、学习名片、学习伙伴、天气缓存或任务附件。

主要代码结构：

```text
app/src/main/java/com/example/campusmate
├── app                 应用配置、Application、功能开关
├── data
│   ├── db              SQLite 表结构和版本迁移
│   ├── model           数据模型
│   ├── provider        CampusMateProvider
│   └── repository      通过 ContentResolver 访问数据
├── domain
│   ├── focus           专注状态机、计时、翻转检测
│   ├── import_         课表解析、WebView HTML 提取工具
│   ├── reminder        AlarmManager、Receiver、提醒调度
│   └── statistics      热力图统计
├── ui                  Activity、Fragment、Adapter、页面逻辑
└── util                时间、数据库 Cursor、通知、权限工具
```

已存在模块包括：首页、课程管理、任务管理、任务提醒、课表导入、WebView 课表导入基础页、任务图片附件、专注计时、翻转检测、学习记录、热力图统计、设置页、学习名片、二维码、学习伙伴、NFC 和天气。

尚未完整实现或仍是计划方向的模块包括：学习计划、NotificationListenerService、项目展示页、JSON 数据导出/备份、真实教务系统 WebView 导入适配增强。

## 3. Agent 工作原则

- 先读代码再改代码。改动前至少查看相关 `ui`、`domain`、`data`、`res/layout`、`AndroidManifest.xml` 和 README 中对应说明。
- 小步修改，一次任务只做一类改动。不要把功能实现、UI 大改、依赖升级、重命名和格式化混在一起。
- 不做无关重构。课程项目优先稳定可演示，除非当前问题必须重构才能解决。
- 不随意更换技术栈。当前是 Kotlin + XML View + Material Components + SQLiteOpenHelper/ContentProvider。
- 不删除组员代码、文档、截图、报告材料、示例 HTML 或演示资源，除非任务明确要求。
- 当前阶段不做头像、照片资料、聊天、动态、关注、点赞、评论或社交广场；学习名片只保留轻量文本信息、二维码/NFC 公开 JSON 和本地学习伙伴列表。
- 不伪造“已测试”“已完成”。没有运行过的命令要写“未运行”；无法真机验证的功能要说明原因。
- 遇到不确定需求，先写清假设、影响文件和验收条件，再实现。
- 保持 README 中“不保存教务系统账号密码、不绕过验证码、不上传用户隐私数据”的边界。

## 4. Android 技术边界

- 不要把 Kotlin 项目迁移为 Java，也不要新增大量 Java 代码破坏一致性。
- 不要把 XML View 项目迁移为 Jetpack Compose。新增页面优先使用 `app/src/main/res/layout` XML 和现有 Activity/Fragment 风格。
- 不要擅自引入 Room。新增表或字段时必须同步检查：
  - `data/db/CampusMateContract.kt`
  - `data/db/CampusMateDbHelper.kt`
  - `data/provider/CampusMateProvider.kt`
  - `data/model`
  - `data/repository`
  - 迁移逻辑和测试
- UI 层不直接操作 `SQLiteDatabase`。数据库读写通过 Repository 和 `ContentResolver`。
- 新增 Activity、Service、BroadcastReceiver、ContentProvider、权限、传感器、NFC、通知或文件共享能力时，必须同步检查 `app/src/main/AndroidManifest.xml`。
- 新增通知或前台服务时，必须检查 Android 13+ 通知权限、前台服务类型、通知渠道和用户可见行为。
- 新增相机、相册、NFC、后台服务、精确闹钟、勿扰模式等敏感能力时，必须提供用户可理解的入口和降级方案。
- 不要硬编码个人路径、Android SDK 路径、学校账号密码、教务系统 Cookie、token、API key 或真实隐私数据。
- 可见文案优先放入 `res/values/strings.xml`，不要散落在 Kotlin 或 XML 中。

## 5. 模块修改导航

### 课表导入

优先查看：

- `app/src/main/java/com/example/campusmate/domain/import_`
- `app/src/main/java/com/example/campusmate/ui/import_`
- `app/src/main/assets/sample_schedule.html`
- `app/src/main/java/com/example/campusmate/data/repository/CourseRepository.kt`
- `app/src/main/java/com/example/campusmate/data/repository/ImportLogRepository.kt`
- `app/src/main/res/layout/activity_import_schedule.xml`
- `app/src/main/res/layout/activity_import_preview.xml`

规则：

- 当前可用路径是内置 sample HTML、粘贴 HTML 和 WebView 手动打开页面后提取当前 HTML；WebView 导入不保存账号密码、Cookie，也不绕过验证码。
- 解析逻辑放在 `domain/import_`，UI 只负责输入、预览、确认和错误提示。
- 不保存教务系统账号、密码、Cookie，不绕过验证码。
- 导入前保留预览和冲突处理，不要静默写入课程表。

### LLM / AI 辅助能力

优先查看：

- `app/src/main/java/com/example/campusmate/data/model/llm`
- `app/src/main/java/com/example/campusmate/data/repository/LlmSettingsRepository.kt`
- `app/src/main/java/com/example/campusmate/domain/llm`
- `app/src/main/java/com/example/campusmate/domain/import_/LlmScheduleParseService.kt`
- `app/src/main/java/com/example/campusmate/domain/plan/LlmPlanGenerateService.kt`
- `app/src/main/java/com/example/campusmate/ui/settings/LlmSettingsUiBinder.kt`
- `app/src/main/res/layout/fragment_settings.xml`

规则：

- 后续接入课表解析时，默认策略为 `LLM_FIRST_FALLBACK_LOCAL`：优先尝试 LLM，失败或不可用时回退到本地 Jsoup 解析。
- 当前阶段只实现 LLM API 设置、加密 Key 存储、连接测试和基础生成请求；不要擅自把它接入真实课表导入按钮或 `StudyPlanGenerator` 主流程。
- 不得内置任何 API Key，不得新增后端代理服务，不得把 LLM 接入改成强制云服务。
- 不得把 API Key、Authorization Header、`x-goog-api-key` 或完整请求密钥打印到 Logcat、Toast、Snackbar、README 或测试输出。
- API Key 必须保存在本机加密存储中；如果 AndroidX Security Crypto 不可用，才可改用 Android Keystore + AES-GCM。
- 业务接入前必须保留预览确认页，不允许 AI 结果直接写入数据库。
- 预设服务商只用于填充表单，国产模型 baseUrl 和 model 必须允许用户按控制台实际配置修改。

### 本地存储

优先查看：

- `app/src/main/java/com/example/campusmate/data/db`
- `app/src/main/java/com/example/campusmate/data/provider/CampusMateProvider.kt`
- `app/src/main/java/com/example/campusmate/data/repository`
- `app/src/main/java/com/example/campusmate/data/model`
- `app/src/androidTest/java/com/example/campusmate/RepositoryInstrumentedTest.kt`

规则：

- 当前数据库名为 `campus_mate.db`，数据库版本为 5。
- 新增表或字段必须考虑升级路径，不要用删除重建表的方式破坏已有演示数据。
- 课程和任务已有软删除字段，列表查询和统计逻辑要正确过滤。

### 番茄钟/专注模式

优先查看：

- `app/src/main/java/com/example/campusmate/domain/focus`
- `app/src/main/java/com/example/campusmate/ui/focus/FocusActivity.kt`
- `app/src/main/java/com/example/campusmate/ui/focus/FocusService.kt`
- `app/src/main/java/com/example/campusmate/data/repository/FocusRepository.kt`
- `app/src/main/java/com/example/campusmate/data/repository/StudyRecordRepository.kt`
- `app/src/test/java/com/example/campusmate/FocusStateMachineTest.kt`

规则：

- 状态切换优先放在 `FocusStateMachine` 或 domain 类，不要散落在按钮回调里。
- `FocusService` 是计时和通知的重要边界，修改时要检查生命周期、通知更新、停止逻辑和记录写入。
- 专注完成后写入 FocusSession 和 StudyRecord，要避免重复写入。
- 传感器不可用时保留手动开始或手动控制降级路径。

### 传感器

优先查看：

- `app/src/main/java/com/example/campusmate/domain/focus/FaceDownDetector.kt`
- `app/src/main/java/com/example/campusmate/ui/focus/FocusActivity.kt`
- `app/src/main/java/com/example/campusmate/ui/focus/FocusService.kt`

规则：

- 当前翻转检测基于 `Sensor.TYPE_ACCELEROMETER`。
- 注册传感器后必须在合适生命周期中注销，避免后台耗电和泄漏。
- 调整阈值或稳定时间时，要说明测试设备、测试姿态和误触发情况。
- 传感器相关功能必须真机验证，模拟器结果不能作为最终结论。

### Service/通知

优先查看：

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/campusmate/ui/focus/FocusService.kt`
- `app/src/main/java/com/example/campusmate/domain/reminder`
- `app/src/main/java/com/example/campusmate/util/NotificationUtils.kt`
- `app/src/main/java/com/example/campusmate/util/PermissionUtils.kt`
- `app/src/main/java/com/example/campusmate/ui/settings/SettingsFragment.kt`

规则：

- 当前 Manifest 已声明 `POST_NOTIFICATIONS`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`RECEIVE_BOOT_COMPLETED`、`SCHEDULE_EXACT_ALARM`、`CAMERA`、`NFC` 和 `INTERNET`，相机与 NFC 硬件均按 `required=false` 处理。
- 新增或修改前台服务时，必须核对 `foregroundServiceType`、通知渠道、通知权限和停止行为。
- 任务提醒依赖 AlarmManager、BroadcastReceiver 和 Notification；修改提醒逻辑时要检查开机恢复。
- 勿扰模式相关功能只能引导用户授权，不要绕过系统限制。

### NFC

优先查看：

- `app/src/main/java/com/example/campusmate/ui/profile`
- `app/src/main/java/com/example/campusmate/ui/buddy`
- `app/src/main/java/com/example/campusmate/data/model/StudyBuddy.kt`
- `app/src/main/java/com/example/campusmate/data/repository/StudyBuddyRepository.kt`
- `app/src/main/AndroidManifest.xml`

相关目录：

```text
app/src/main/java/com/example/campusmate/domain/nfc
app/src/main/java/com/example/campusmate/ui/nfc
```

规则：

- 当前 NFC 已完成基础 NDEF 写入、接收、预览确认和设备不支持/未开启时的降级提示；Manifest 已声明 `android.permission.NFC` 和 `android.hardware.nfc required=false`。
- 修改 NFC 时必须继续检查 intent-filter、前台调度逻辑和设备降级提示。
- NFC 应复用学习名片公开 JSON，不应传输账号密码、Cookie、token 或隐私默认开启的数据。
- 保存学习伙伴时沿用 `StudyBuddy.SOURCE_NFC` 和 `StudyBuddyRepository`。
- NFC 必须真机验证，并在结果说明中写明设备条件。

### 天气

优先查看：

- `app/src/main/java/com/example/campusmate/domain/weather`
- `app/src/main/java/com/example/campusmate/data/repository/WeatherRepository.kt`
- `app/src/main/java/com/example/campusmate/ui/dashboard/DashboardFragment.kt`
- `app/src/main/java/com/example/campusmate/ui/settings/SettingsFragment.kt`
- `app/src/main/res/layout/fragment_dashboard.xml`

规则：

- 当前天气已完成首页天气卡片、城市配置、Mock/远程数据源、缓存和失败降级。
- 天气不申请定位权限，不自动获取用户位置，不上传隐私数据。
- 默认 Mock 天气用于稳定演示；远程天气失败时要保留缓存或 Mock 降级。

### UI/Material Design

优先查看：

- `app/src/main/res/layout`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/strings.xml`
- 对应 `app/src/main/java/com/example/campusmate/ui/...`

规则：

- 沿用 Material Components、Toolbar、BottomNavigationView、MaterialButton、MaterialCardView、SwitchMaterial 等现有风格。
- 新页面优先接入现有导航和设置入口，不要做孤立不可达页面。
- 课程展示优先清晰稳定，不要为了“高级感”加入难以解释或难以演示的动画和复杂视觉效果。

## 6. 构建、运行与测试

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat connectedAndroidTest
.\gradlew.bat lint
```

macOS/Linux：

```bash
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest
./gradlew lint
```

Agent 规则：

- `assembleDebug` 用于确认 Debug APK 可编译。
- `test` 用于 JVM 单元测试，例如 `ScheduleParserTest`、`FocusStateMachineTest`、`HeatmapCalculatorTest`。
- `connectedAndroidTest` 需要模拟器或真机。
- 涉及传感器、NFC、通知权限、前台服务、精确闹钟、勿扰模式的功能，最终结论必须来自真机验证；无法真机验证时必须明说。
- 未运行的命令不得写成“通过”。

## 7. 禁止事项

- 不提交或修改 `local.properties` 来解决通用问题。
- 不提交 `build/`、`.gradle/`、`.kotlin/`、`.idea/workspace.xml`、生成 APK、临时日志或本机缓存。
- 不提交学校账号密码、教务系统 Cookie、token、API key、真实手机号、真实邮箱批量数据或其他敏感文件。
- 不引入大型依赖、后端服务、云数据库、登录系统或统计 SDK，除非 README 或明确需求要求。
- 不为了“看起来高级”加入无法解释、无法运行或无法现场演示的技术。
- 不删除文档、截图、报告材料、示例 HTML 或演示数据，除非任务明确要求。
- 不绕过验证码，不抓取或上传用户隐私数据。
- 不在 UI 层直接拼接复杂 SQL 或直接操作底层数据库。

## 8. 任务完成标准

Agent 完成任务前应确认：

- 代码可以编译，至少尝试运行 `assembleDebug`；无法运行时说明原因。
- 相关页面或功能可以通过明确路径打开和操作。
- 新增权限、Activity、Service、Receiver、Provider、NFC、通知或文件访问能力时，`AndroidManifest.xml` 配置完整。
- 数据库变更包含 Contract、DbHelper、Provider、Repository 和迁移说明。
- 关键业务逻辑放在合适层级，复杂规则优先放在 domain 层并补充测试。
- 最终回复写明修改文件、验证命令、未验证项和风险。
- 如果无法测试，必须明确说明原因，例如“没有 NFC 真机”“没有连接 Android 设备”“本地 SDK 缺失”。

## 9. 给使用 Agent 的人类成员

这部分只保留最小说明，帮助人类成员正确给 Agent 下任务。完整协作流程见 `CONTRIBUTING.md`。

- 给 Agent 任务时请说明目标、相关页面或模块、不能改的边界、验收条件。
- 需求模糊时先让 Agent 阅读项目并制定计划，不要直接要求实现。
- AI 生成代码必须由人类阅读、运行和截图验证后再合并。
- 传感器、NFC、通知权限、前台服务等功能必须真机验证。

示例提示词：

```text
请先阅读 CampusMate 当前代码，不要急着写代码。目标是在现有 XML View + Kotlin + SQLiteOpenHelper 架构下实现【功能】。请先列出会修改的文件、数据流和验收条件。
```

```text
请修复【bug】。优先查看【相关文件】。不要重构无关模块，不要更换技术栈。完成后说明根因、修改点、测试命令和未验证风险。
```

```text
请以代码审查视角检查这个改动。重点看 AndroidManifest、数据库迁移、通知/Service 生命周期、真机验证缺口和是否破坏已有展示闭环。
```
