# AGENTS.md

## 1. 项目定位

Campus-Mate 是面向大学生校园学习与生活管理的 Android 小组课程项目，当前定位是本地单机 App，不是商业级长期维护产品。协作时优先保证功能可运行、可展示、可解释、可写入实验报告或课程设计文档。

本项目不追求过度工程化。新增功能应服务于课程展示闭环：课程管理、任务提醒、课表导入、专注计时、学习记录、统计展示、学习名片和学习伙伴等。除非 README 或课程要求明确提出，不要引入后端、云同步、登录体系或复杂基础设施。

## 2. 当前仓库状态

根据当前仓库内容，项目已经形成 Android 工程结构：

- 根目录存在 `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradlew`、`gradlew.bat`、`gradle/libs.versions.toml` 和 `app/` 模块。
- 这是 Kotlin Android 项目，源码主要位于 `app/src/main/java/com/example/campusmate`，文件扩展名为 `.kt`。
- 构建使用 Gradle Kotlin DSL，当前只有 `:app` 一个 Android application 模块。
- Android Gradle Plugin 版本来自 `gradle/libs.versions.toml`，当前为 `9.1.1`。
- `app/build.gradle.kts` 中配置了 `applicationId = "com.example.campusmate"`、`minSdk = 35`、`targetSdk = 36`、`compileSdk = 36.1`，Java 兼容级别为 11。
- UI 使用 XML View、AppCompat、Fragment、RecyclerView、ConstraintLayout 和 Material Components。当前没有 Jetpack Compose 依赖，也没有 Compose UI 结构。
- 本地存储使用 `SQLiteOpenHelper`、`ContentProvider`、`ContentResolver` 和 Repository 封装。当前没有 Room 依赖。
- 已使用 Jsoup 做课表 HTML 解析，使用 ZXing 相关库做二维码生成和扫码。
- 根目录已有 `README.md`，内容说明当前版本为 `V1.1-stage-9`，主闭环已跑通，NFC、天气、图片附件、学习计划、JSON 导出等仍属于后续计划。
- 当前未发现 `CONTRIBUTING.md`、`docs/`、独立设计文档或课程报告目录。
- 当前工作目录未检测到 `.git` 元数据，`git status` 无法运行；因此本地无法确认分支、提交历史或哪些文件已经被 Git 跟踪。
- 根目录存在 `local.properties`，但 `.gitignore` 已排除 `local.properties`。该文件属于本机环境文件，不应提交。

主要目录结构：

```text
app/src/main/java/com/example/campusmate
├── app                 应用配置、Application、功能开关
├── data
│   ├── db              SQLite 表结构和版本迁移
│   ├── model           课程、任务、专注、记录、名片、伙伴等数据模型
│   ├── provider        CampusMateProvider
│   └── repository      通过 ContentResolver 访问数据
├── domain
│   ├── focus           专注状态机、计时、翻转检测
│   ├── import_         课表解析、WebView HTML 提取工具
│   ├── reminder        AlarmManager、Receiver、提醒调度
│   └── statistics      热力图统计
├── ui
│   ├── buddy           学习伙伴列表与详情
│   ├── course          课程列表、编辑、详情
│   ├── dashboard       首页
│   ├── focus           专注页面和前台服务
│   ├── import_         课表导入和预览
│   ├── main            主 Activity 和底部导航
│   ├── profile         学习名片、二维码、扫码
│   ├── settings        设置页
│   ├── statistics      统计页
│   └── task            任务列表、编辑、详情
└── util                时间、数据库 Cursor、通知、权限工具
```

已存在的功能模块包括：

- 首页 Dashboard。
- 课程管理。
- 任务管理。
- AlarmManager 任务提醒、通知和开机恢复。
- 课表导入：内置 sample HTML、粘贴 HTML、Jsoup 解析、导入预览。
- WebView 课表 HTML 提取工具类，但完整 WebView 导入页面流程尚未完成。
- 番茄钟/专注模式：翻转手机检测、前台 Service 计时、暂停/继续/完成/取消。
- 基于加速度传感器的翻转检测。
- 学习记录与热力图统计。
- 设置页、通知权限入口、精确闹钟入口、勿扰模式授权入口。
- 学习名片、二维码生成、扫码添加学习伙伴、学习伙伴列表和详情。

当前仍缺失或尚未完整实现的方向包括：

- NFC 贴贴交友或资料交换：README 标注为待实现，当前未见 NFC 权限、NFC Activity 或 NDEF 读写实现。
- 天气模块。
- 图片附件模块。
- 学习计划模块。
- NotificationListenerService 或更完整的勿扰增强。
- 项目展示页/技术点展示页。
- JSON 数据导出/备份。
- 独立 docs/课程报告目录。

## 3. Agent 工作原则

- 先读代码再修改。改动前至少查看相关 `ui`、`domain`、`data`、`res/layout`、`AndroidManifest.xml` 和 README 中对应说明。
- 小步修改，一次任务只做一类改动。不要把功能实现、UI 大改、依赖升级、重命名和格式化混在一起。
- 不做无关重构。课程项目优先稳定可演示，除非当前问题必须重构才能解决。
- 不随意更换技术栈。当前是 Kotlin + XML View + Material Components + SQLiteOpenHelper/ContentProvider，不要擅自迁移到 Java、Compose、Room、Hilt、后端服务或云数据库。
- 不删除组员代码、文档、截图、报告材料或演示资源。确需删除时，先说明原因和影响范围。
- 不伪造“已测试”“已完成”。没有运行过的命令要写“未运行”；无法真机验证的功能要说明原因。
- 遇到不确定需求，先写清假设、影响文件、验收条件，再动手实现。
- 课程项目优先级是：可编译 > 可运行 > 可演示 > 可解释 > 结构优雅。
- 保持 README 中“不保存教务系统账号密码、不绕过验证码、不上传用户隐私数据”的边界。

## 4. Android 开发约束

- 本仓库当前是 Kotlin 项目，不要擅自迁移为 Java，也不要新增大量 Java 代码破坏一致性。
- 本仓库当前是 XML View 项目，不要擅自迁移 Jetpack Compose。新增页面优先使用 `app/src/main/res/layout` XML、现有 Activity/Fragment 风格和 Material Components。
- 本仓库当前使用 SQLiteOpenHelper + ContentProvider + Repository，不要擅自引入 Room。新增表时必须同步更新：
  - `data/db/CampusMateContract.kt`
  - `data/db/CampusMateDbHelper.kt`
  - `data/provider/CampusMateProvider.kt`
  - 对应 `data/model` 和 `data/repository`
  - 必要的迁移逻辑和测试
- 新增 Activity、Service、BroadcastReceiver、ContentProvider、权限、传感器、NFC、通知或文件共享能力时，必须同步检查 `app/src/main/AndroidManifest.xml`。
- 新增通知或前台服务时，必须检查 Android 13+ 通知权限、前台服务类型、通知渠道和用户可见行为。
- 新增相机、相册、NFC、后台服务、精确闹钟、勿扰模式等敏感能力时，必须提供用户可理解的入口和降级方案。
- 不要硬编码个人路径、Android SDK 路径、学校账号密码、教务系统 Cookie、token、API key 或真实隐私数据。
- 不要修改 `local.properties` 来解决通用构建问题；这是每个开发者本机文件。
- 资源命名沿用现有风格：布局使用 `activity_`、`fragment_`、`item_`、`dialog_` 前缀，字符串放入 `res/values/strings.xml`。

## 5. 计划功能模块约束

### 课表导入模块

优先查看：

- `app/src/main/java/com/example/campusmate/domain/import_`
- `app/src/main/java/com/example/campusmate/ui/import_`
- `app/src/main/assets/sample_schedule.html`
- `app/src/main/java/com/example/campusmate/data/repository/CourseRepository.kt`
- `app/src/main/java/com/example/campusmate/data/repository/ImportLogRepository.kt`
- `app/src/main/res/layout/activity_import_schedule.xml`
- `app/src/main/res/layout/activity_import_preview.xml`

约束：

- 当前可用路径是内置 sample HTML 和粘贴 HTML；WebView 提取已有 `WebViewScheduleExtractor` 工具类，但完整 UI 流程尚未接入。
- 不保存教务系统账号、密码、Cookie，不绕过验证码。
- 解析逻辑优先放在 `domain/import_`，UI 只负责输入、预览、确认和错误提示。
- 导入前要保留预览和冲突处理，不要直接静默写入课程表。

### 本地存储模块

优先查看：

- `app/src/main/java/com/example/campusmate/data/db`
- `app/src/main/java/com/example/campusmate/data/provider/CampusMateProvider.kt`
- `app/src/main/java/com/example/campusmate/data/repository`
- `app/src/main/java/com/example/campusmate/data/model`
- `app/src/androidTest/java/com/example/campusmate/RepositoryInstrumentedTest.kt`

约束：

- 当前数据库名为 `campus_mate.db`，数据库版本为 2。
- 所有数据库读写应通过 Repository 和 `ContentResolver`，不要在 UI 层直接操作 `SQLiteDatabase`。
- 新增表或字段必须考虑升级路径，不要用删除重建表的方式破坏已有演示数据。
- 软删除字段已在课程和任务中使用，涉及列表展示时要注意过滤。

### 番茄钟/专注模式模块

优先查看：

- `app/src/main/java/com/example/campusmate/domain/focus`
- `app/src/main/java/com/example/campusmate/ui/focus/FocusActivity.kt`
- `app/src/main/java/com/example/campusmate/ui/focus/FocusService.kt`
- `app/src/main/java/com/example/campusmate/data/repository/FocusRepository.kt`
- `app/src/main/java/com/example/campusmate/data/repository/StudyRecordRepository.kt`
- `app/src/test/java/com/example/campusmate/FocusStateMachineTest.kt`

约束：

- 状态切换应优先放在 `FocusStateMachine` 或相关 domain 类中，不要把核心规则散落在按钮回调里。
- 前台 Service 是计时和通知的重要边界，修改时要检查生命周期、通知更新、停止逻辑和记录写入。
- 专注完成后写入 FocusSession 和 StudyRecord，要避免重复写入。
- 传感器不可用时必须保留手动开始或手动控制的降级路径。

### 传感器模块

优先查看：

- `app/src/main/java/com/example/campusmate/domain/focus/FaceDownDetector.kt`
- `app/src/main/java/com/example/campusmate/ui/focus/FocusActivity.kt`
- `app/src/main/java/com/example/campusmate/ui/focus/FocusService.kt`

约束：

- 当前翻转检测基于加速度传感器 `Sensor.TYPE_ACCELEROMETER`。
- 注册传感器后必须在合适生命周期中注销，避免后台耗电和泄漏。
- 调整阈值或稳定时间时，要说明测试设备、测试姿态和误触发情况。
- 传感器相关功能必须真机验证，模拟器结果不能作为最终结论。

### Service/通知模块

优先查看：

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/campusmate/ui/focus/FocusService.kt`
- `app/src/main/java/com/example/campusmate/domain/reminder`
- `app/src/main/java/com/example/campusmate/util/NotificationUtils.kt`
- `app/src/main/java/com/example/campusmate/util/PermissionUtils.kt`
- `app/src/main/java/com/example/campusmate/ui/settings/SettingsFragment.kt`

约束：

- 当前 Manifest 已声明 `POST_NOTIFICATIONS`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`RECEIVE_BOOT_COMPLETED`、`SCHEDULE_EXACT_ALARM` 和 `CAMERA`。
- 新增或修改前台服务时，必须核对 `foregroundServiceType`、通知渠道、通知权限和停止行为。
- 任务提醒依赖 AlarmManager、BroadcastReceiver 和 Notification；修改提醒逻辑时要检查开机恢复。
- 勿扰模式相关功能只能引导用户授权，不要绕过系统限制。

### NFC 模块

优先查看：

- `app/src/main/java/com/example/campusmate/ui/profile`
- `app/src/main/java/com/example/campusmate/ui/buddy`
- `app/src/main/java/com/example/campusmate/data/model/StudyBuddy.kt`
- `app/src/main/java/com/example/campusmate/data/repository/StudyBuddyRepository.kt`
- `app/src/main/AndroidManifest.xml`

建议目录：

```text
app/src/main/java/com/example/campusmate/domain/nfc
app/src/main/java/com/example/campusmate/ui/nfc
```

约束：

- 当前 NFC 尚未实现，Manifest 也未声明 NFC 权限。实现前必须补充 `android.permission.NFC` 和必要的 intent-filter 或前台调度逻辑。
- NFC 应复用学习名片公开 JSON，不应传输账号密码、Cookie、token 或隐私默认开启的数据。
- 保存学习伙伴时应沿用 `StudyBuddy.SOURCE_NFC` 和 `StudyBuddyRepository`。
- NFC 必须真机验证，两台设备或支持读写的 NFC 测试条件要在 PR 中说明。

### UI/Material Design 模块

优先查看：

- `app/src/main/res/layout`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/strings.xml`
- 对应 `app/src/main/java/com/example/campusmate/ui/...`

约束：

- 沿用 Material Components、Toolbar、BottomNavigationView、MaterialButton、MaterialCardView、SwitchMaterial 等现有风格。
- 新增可见文案放入 `strings.xml`，不要硬编码在 Kotlin 或 XML 中。
- 新页面优先接入现有导航和设置入口，不要做孤立不可达页面。
- 课程展示优先清晰稳定，不要为了“高级感”加入难以解释或难以演示的动画和复杂视觉效果。

## 6. 构建、运行与测试命令

根据当前 Gradle 工程，Windows PowerShell 下建议命令：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat connectedAndroidTest
.\gradlew.bat lint
```

macOS/Linux 下对应命令：

```bash
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest
./gradlew lint
```

说明：

- `assembleDebug` 用于编译 Debug APK。
- `test` 用于运行 JVM 单元测试，例如 `ScheduleParserTest`、`FocusStateMachineTest`、`HeatmapCalculatorTest`。
- `connectedAndroidTest` 需要连接模拟器或真机，用于运行仪器测试，例如 Repository 相关测试。
- `lint` 用于 Android 静态检查。
- Agent 不得在未执行命令时写“测试通过”。如果因为没有设备、SDK、网络或权限导致无法运行，要在最终回复或 PR 描述中明确说明。

## 7. Git 协作规范

- `main` 分支保持稳定，能编译、能展示。
- 每个功能从 `main` 新建分支，建议命名为 `feature/schedule-import-webview`、`feature/nfc-card-share`、`fix/focus-service-stop` 等。
- 不直接向 `main` 提交未完成代码。
- 每次提交只做一类改动，例如“新增 NFC 解析 domain 层”或“修复课表解析星期识别”。
- PR 描述必须说明：
  - 做了什么；
  - 影响哪些模块；
  - 怎么测试；
  - 有哪些未验证项或已知限制；
  - 是否涉及权限、Manifest、数据库迁移或用户数据。
- 处理冲突时不要简单覆盖别人代码。先读双方改动意图，再保留必要逻辑。
- 当前工作目录没有 `.git` 元数据时，Agent 不能假装已经创建分支、提交或推送。

## 8. Vibe Coding 使用规范

允许使用 AI 快速生成：

- Activity/Fragment 页面骨架。
- XML 布局初稿。
- 数据类、Repository 方法、Contract 常量。
- DAO 风格的访问封装，但本项目实际应落在现有 Repository/ContentProvider 体系内。
- Jsoup 解析规则、JSON 工具类、格式化工具。
- 单元测试样例、PR 风险清单、课程报告技术说明草稿。

不允许：

- 不理解 AI 生成代码就直接合并。
- 让 AI 大范围重构项目结构但没有明确验收标准。
- 接受无法编译、无法解释、无法展示的“看起来完整”的代码。
- 用 AI 编造已测试结论、真机结果、课程要求或用户数据。

AI 生成代码合并前必须人工完成：

- 阅读关键路径代码，确认没有删除已有功能。
- 运行相关构建或测试命令；无法运行时写明原因。
- 对 UI 功能进行实际点击验证，课程展示功能建议保留截图材料。
- 对传感器、NFC、通知权限、前台服务、精确闹钟、勿扰模式等功能进行真机验证。
- 对数据库迁移和演示数据进行一次旧数据兼容检查。

每次让 Agent 改代码时，建议提供：

- 目标：要实现或修复什么。
- 上下文：相关页面、文件、README 阶段或课程要求。
- 约束：不能改哪些技术栈、不能引入哪些依赖、需要兼容哪些已实现功能。
- 验收条件：编译命令、测试命令、页面操作路径、真机验证要求。

需求很模糊时，先让 Agent 阅读项目并制定计划，不要直接实现。

适合本项目的 Codex 提示词模板：

```text
请先阅读 CampusMate 当前代码，不要急着写代码。目标是在现有 XML View + Kotlin + SQLiteOpenHelper 架构下实现【小功能名称】。请先列出会修改的文件、数据流和验收条件，确认后再小步实现。
```

```text
请修复【bug 描述】。优先查看【相关 Activity/Repository/domain 文件】。不要重构无关模块，不要更换技术栈。完成后请说明根因、修改点、运行过的测试命令和未验证风险。
```

```text
请阅读项目并为【NFC/课表导入/专注模式】制定实现计划。要求区分当前已经存在的代码、README 中的计划功能和你建议新增的目录，不要直接写代码。
```

```text
请根据当前实现生成课程报告中的技术说明，主题是【SQLite 本地存储/前台 Service 专注计时/Jsoup 课表解析】。只基于仓库已有代码描述，不要夸大功能，不要写不存在的模块。
```

```text
请以代码审查视角检查这个 PR 的风险。重点看 AndroidManifest 权限、数据库迁移、通知/Service 生命周期、真机验证缺口和是否破坏已有课程展示闭环。请按严重程度列出问题。
```

## 9. 禁止事项

- 不提交 `local.properties`。
- 不提交 `build/`、`.gradle/`、`.kotlin/`、`.idea/workspace.xml`、生成 APK、临时日志或本机缓存。
- 不提交学校账号密码、教务系统 Cookie、token、API key、真实手机号、真实邮箱批量数据或其他敏感文件。
- 不引入大型依赖、后端服务、云数据库、登录系统或支付/统计 SDK，除非 README 或课程要求明确要求。
- 不为了“看起来高级”加入无法解释、无法运行或无法现场演示的技术。
- 不删除 README、文档、截图、报告材料、示例 HTML 或演示数据，除非任务明确要求。
- 不绕过验证码，不抓取或上传用户隐私数据。
- 不在 UI 层直接拼接复杂 SQL 或直接操作底层数据库。
- 不把课程项目写成需要复杂环境才能启动的系统。

## 10. 完成标准

一个任务完成前应满足：

- 代码可以编译，至少尝试运行 `assembleDebug`；无法运行时说明原因。
- 相关页面或功能可以通过明确路径打开和操作。
- 新增权限、Activity、Service、Receiver、Provider、NFC、通知或文件访问能力时，`AndroidManifest.xml` 配置完整。
- 数据库变更包含 Contract、DbHelper、Provider、Repository 和迁移说明。
- 关键业务逻辑有必要说明，复杂规则优先放在 domain 层并补充测试。
- PR 或最终说明中写明测试结果、真机验证情况和未验证项。
- 如果无法测试，必须明确说明原因，例如“没有 NFC 真机”“没有连接 Android 设备”“本地 SDK 缺失”。
