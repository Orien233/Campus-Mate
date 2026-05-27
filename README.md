# CampusMate 校园学习与沉浸专注助手

CampusMate 是一个面向 Android 移动应用开发课程设计的本地单机 App。项目解决学生日常学习中的课程安排、任务提醒、专注计时、学习记录和统计展示问题，核心业务闭环是：

```text
课程管理
  -> 任务管理
  -> 任务提醒
  -> 课表导入
  -> 翻转手机专注
  -> 前台服务计时
  -> 学习记录
  -> 热力图统计
  -> 设置页与演示数据
```

V1.1 在不做登录、云同步和后端服务的前提下，继续扩展本地能力。当前阶段新增校园天气卡片，使用手动城市配置、缓存和 Mock 降级，不保存账号密码，不自动定位，不上传用户隐私数据。

## 当前版本状态

- 当前版本：V1.1-stage-11
- 当前阶段：校园天气卡片
- 主闭环状态：已跑通
- 当前扩展功能状态：阶段 9-11 已完成，阶段 12-18 待实现
- 数据库版本：3
- applicationId：`com.example.campusmate`
- minSdk：35
- targetSdk：36
- compileSdk：36.1

## 已实现功能清单

- 首页 Dashboard：已完成，展示今日课程、待办任务、今日/本周学习和快捷入口。
- 课程管理：已完成，支持新增、编辑、详情、软删除、星期筛选和冲突提示。
- 任务管理：已完成，支持新增、编辑、详情、删除、完成状态切换、任务类型和优先级。
- 任务提醒：已完成，使用 AlarmManager、BroadcastReceiver 和 Notification，支持开机恢复未来提醒。
- 课表导入：已完成，支持内置 sample HTML 与粘贴 HTML 解析，WebView 提取工具类已预留。
- 专注计时：已完成，支持翻转手机检测、前台服务计时、暂停/继续/完成/取消。
- 翻转手机检测：已完成，基于加速度传感器，传感器不可用时可手动开始。
- 学习记录：已完成，专注完成后写入 FocusSession 与 StudyRecord。
- 热力图：已完成，统计页展示最近学习记录和热力图。
- 设置页：已完成，每日目标、提醒开关、沉浸模式、权限入口、演示数据和清空数据。
- 演示模式：部分完成，当前可生成 V1.0 主闭环演示数据；完整 V1.1 演示数据待阶段 18。
- 学习名片：已完成，本地保存个人资料，不需要登录。
- 二维码：已完成，使用公开 JSON 生成 QR 图片，扫码后先预览再确认添加。
- 学习伙伴：已完成，扫码确认后保存到 `study_buddies`，支持列表、详情和删除。
- NFC：已完成，复用学习名片公开 JSON，支持 NFC NDEF 写入/接收、预览确认和不支持设备降级。
- 天气：已完成，首页天气卡片、设置页城市配置、Mock/远程数据源、30 分钟缓存和失败降级。
- WebView 当前页面课表 HTML 提取增强：部分完成，已有提取工具类，完整 Activity 流程待阶段 12。
- 图片附件：待实现，阶段 13 处理。
- 学习计划：待实现，阶段 14 处理。
- 通知弱化 / 勿扰增强：部分完成，当前已有勿扰授权入口；增强沉浸和实验功能待阶段 15。
- 项目展示页 / 技术点展示页：待实现，阶段 16 处理。
- 数据导出 / 备份 JSON：待实现，阶段 17 处理。

## 待实现功能清单

- NFC 已实现基础 NDEF 交换；当前分享页主要面向可写 NFC 标签或兼容 NDEF 发送源，直接手机对手机 Android Beam 在当前 SDK 下不可用。
- WebView 真实教务系统课表提取尚未接入完整 UI，当前仅有 `WebViewScheduleExtractor` 工具类。
- 图片附件尚未实现，当前没有 `task_attachments` 表，也未申请图片读取权限。
- 学习计划尚未实现，当前没有 `study_plans` 表。
- NotificationListenerService 未实现，当前只提供勿扰模式授权入口。
- JSON 导出/导入尚未实现，当前不会导出本地数据文件。
- 当前版本默认关闭 Android 系统备份，不通过系统备份导出本地数据库、设置、学习名片、学习伙伴或天气缓存。
- 完整演示模式尚未实现，当前演示数据不包含完整学习伙伴、附件和计划；天气通过 Mock 卡片稳定展示。
- 头像当前只保存可选 `avatar_uri` 文本，不做相册选择；相册/拍照会在阶段 13 统一处理。

## 项目结构说明

当前真实代码结构如下：

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
│   │   ├── StudyRecord.kt
│   │   ├── StudyTask.kt
│   │   └── UserProfile.kt
│   ├── provider
│   │   └── CampusMateProvider.kt
│   └── repository
│       ├── CourseRepository.kt
│       ├── DataMaintenanceRepository.kt
│       ├── DemoDataRepository.kt
│       ├── FocusRepository.kt
│       ├── ImportLogRepository.kt
│       ├── SettingsRepository.kt
│       ├── StudyBuddyRepository.kt
│       ├── StudyRecordRepository.kt
│       ├── TaskRepository.kt
│       ├── UserProfileRepository.kt
│       └── WeatherRepository.kt
├── domain
│   ├── focus
│   │   ├── FaceDownDetector.kt
│   │   ├── FocusStateMachine.kt
│   │   └── FocusTimerEngine.kt
│   ├── import_
│   │   ├── CourseDraft.kt
│   │   ├── JsoupScheduleParser.kt
│   │   ├── ScheduleParser.kt
│   │   └── WebViewScheduleExtractor.kt
│   ├── reminder
│   │   ├── AlarmReminderScheduler.kt
│   │   ├── BootReminderReceiver.kt
│   │   ├── ReminderReceiver.kt
│   │   └── ReminderScheduler.kt
│   ├── nfc
│   │   ├── NfcCardPayload.kt
│   │   ├── NfcPayloadParser.kt
│   │   ├── NfcPayloadWriter.kt
│   │   └── NfcUtils.kt
│   ├── weather
│   │   ├── MockWeatherDataSource.kt
│   │   ├── RemoteWeatherDataSource.kt
│   │   ├── WeatherDataSource.kt
│   │   ├── WeatherParser.kt
│   │   └── WeatherResult.kt
│   └── statistics
│       └── HeatmapCalculator.kt
├── ui
│   ├── buddy
│   │   ├── BuddyAdapter.kt
│   │   ├── BuddyDetailActivity.kt
│   │   └── BuddyListActivity.kt
│   ├── course
│   ├── dashboard
│   ├── focus
│   ├── import_
│   ├── main
│   ├── nfc
│   │   ├── NfcReceiveActivity.kt
│   │   └── NfcShareActivity.kt
│   ├── profile
│   │   ├── EditProfileActivity.kt
│   │   ├── ProfileActivity.kt
│   │   ├── ProfileUiFormatter.kt
│   │   ├── ScanQrActivity.kt
│   │   └── StudyCardActivity.kt
│   ├── settings
│   ├── statistics
│   └── task
└── util
    ├── DateTimeUtils.kt
    ├── DbUtils.kt
    ├── NotificationUtils.kt
    └── PermissionUtils.kt
```

资源与测试结构：

```text
app/src/main/assets/sample_schedule.html   内置课表示例
app/src/main/res/layout/                   Activity、Fragment、列表项和弹窗布局
app/src/main/res/drawable/                 Vector 图标和启动图资源
app/src/test/java/com/example/campusmate/  单元测试
app/src/androidTest/java/com/example/campusmate/ 仪器测试
```

## 模块职责说明

- `app` 层：保存应用级常量、Application 初始化和功能开关占位。
- `ui` 层：负责界面展示、用户输入、页面跳转和系统 Activity 跳转，不直接操作 `SQLiteDatabase`。
- `domain` 层：负责核心业务逻辑，如课表解析、提醒调度、专注状态机、传感器检测和热力图计算。
- `data/model` 层：定义课程、任务、专注记录、学习记录、导入日志、用户学习名片和学习伙伴数据模型。
- `data/repository` 层：封装数据访问，所有数据库读写都通过 `ContentResolver` 进入 Provider。
- `data/provider` 层：通过 `CampusMateProvider` 暴露统一数据入口，Manifest 中 `android:exported="false"`。
- `data/db` 层：负责 SQLite 表结构、数据库版本和安全迁移，表名和列名集中在 `CampusMateContract`。
- `util` 层：负责日期、通知、权限和 Cursor 读取等通用能力。
- `ui/profile`：学习名片、资料编辑、二维码生成、二维码扫描和公开 JSON 展示。
- `ui/buddy`：学习伙伴列表、详情和删除。
- `domain/nfc`：负责 NFC NDEF MIME payload 构建、解析、标签写入和设备能力判断。
- `ui/nfc`：负责 NFC 分享确认、NFC 接收预览、降级提示和确认保存学习伙伴。
- `domain/weather`：负责天气数据源抽象、Mock 数据、远程请求和 JSON 解析。
- `WeatherRepository`：负责天气缓存、30 分钟新鲜度判断、远程/缓存/Mock 降级编排。

## 模块协作模式

### A. 课程管理数据流

```text
CourseEditActivity
  -> CourseRepository
  -> ContentResolver
  -> CampusMateProvider
  -> SQLite
  -> CourseListFragment 刷新展示
```

### B. 任务提醒数据流

```text
TaskEditActivity
  -> TaskRepository 保存任务
  -> ReminderScheduler 设置 Alarm
  -> AlarmManager 到时触发
  -> ReminderReceiver
  -> NotificationUtils 显示通知
  -> 点击通知进入 TaskDetailActivity / FocusActivity
```

当前通知点击进入 `TaskDetailActivity`，任务详情页可继续进入专注流程。

### C. 专注计时数据流

```text
FocusActivity
  -> FaceDownDetector 检测翻转
  -> FocusStateMachine 切换状态
  -> FocusService 前台计时
  -> Notification 前台通知
  -> FocusRepository 保存 FocusSession
  -> StudyRecordRepository 写入 StudyRecord
  -> StatisticsFragment 聚合展示
```

### D. 课表导入数据流

```text
ImportScheduleActivity / WebViewImportActivity
  -> JsoupScheduleParser
  -> CourseDraft
  -> ImportPreviewActivity
  -> CourseRepository 批量写入
  -> ImportLog 保存导入记录
```

当前 `WebViewImportActivity` 尚未实现完整页面，阶段 12 会补齐；现阶段可用内置 HTML 和粘贴 HTML。

### E. 学习名片数据流

```text
EditProfileActivity
  -> UserProfileRepository
  -> user_profile 表
  -> StudyCardActivity 生成公开 JSON
  -> QR / NFC 分享
  -> ScanQrActivity 接收并解析
  -> 用户预览确认
  -> StudyBuddyRepository
  -> study_buddies 表
```

二维码公开 JSON 格式示例：

```json
{
  "app": "CampusMate",
  "version": 1,
  "nickname": "张三",
  "school": "北京交通大学",
  "major": "计算机科学与技术",
  "grade": "大三",
  "bio": "喜欢移动开发和 AI",
  "github": "example",
  "email": "xxx@example.com"
}
```

邮箱和手机号只有在用户打开公开开关时才会进入 JSON。二维码扫描后不会自动添加，必须在预览页点击确认。

### E2. NFC 学习名片数据流

分享：

```text
NfcShareActivity
  -> UserProfileRepository.buildPublicProfileJson()
  -> NfcPayloadWriter.createProfileMessage()
  -> 用户确认
  -> NFC foreground dispatch
  -> 写入可写 NFC 标签 / 兼容 NDEF 接收源
```

接收：

```text
NfcReceiveActivity
  -> NDEF_DISCOVERED / foreground dispatch
  -> NfcPayloadParser
  -> UserProfileRepository.parsePublicProfileJson(source=NFC)
  -> 预览确认
  -> StudyBuddyRepository.addBuddy()
  -> study_buddies.source = 1
```

NFC 与二维码使用同一套公开 JSON。设备不支持 NFC 时页面显示降级提示，可继续使用二维码交换；NFC 未开启时提供系统 NFC 设置入口。当前 compile SDK 已不提供旧 Android Beam API，因此分享页以 NDEF 标签写入和兼容 NDEF 接收源为主。

### F. 天气数据流

```text
DashboardFragment
  -> WeatherRepository
  -> WeatherDataSource
  -> RemoteWeatherDataSource / MockWeatherDataSource
  -> WeatherParser
  -> weather_cache 表
  -> Dashboard 天气卡片展示
```

设置页通过 `SettingsRepository` 保存城市名和 Mock 天气开关。Dashboard 默认使用 Mock 天气；关闭 Mock 后会尝试远程请求 `wttr.in` JSON，成功后写入 `weather_cache`，30 分钟内优先使用缓存，网络失败时回退缓存或 Mock。项目不申请定位权限，不自动获取用户位置。

### G. 图片附件数据流

```text
TaskDetailActivity
  -> 相册 / 相机
  -> FileProvider / Uri
  -> TaskAttachmentRepository
  -> task_attachments 表
  -> 附件 RecyclerView 展示
```

该数据流为阶段 13 计划，当前尚未实现。

### H. 备份数据流

导出：

```text
SettingsFragment
  -> BackupExporter
  -> Repository 查询各模块数据
  -> JSON
  -> Storage Access Framework 导出文件
```

导入：

```text
SettingsFragment
  -> BackupImporter
  -> 解析 JSON
  -> 预览
  -> 用户确认
  -> Repository 追加导入
```

该数据流为阶段 17 计划，当前尚未实现。

## 数据库设计说明

当前已创建的表：

- `courses`
  - 作用：保存课程表数据。
  - 关键字段：`name`、`teacher`、`classroom`、`weekday`、`start_section`、`end_section`、`start_week`、`end_week`、`week_type`、`color`、`is_deleted`。
  - 关系：可被 `study_tasks.course_id`、`focus_sessions.course_id`、`study_records.course_id` 关联。

- `study_tasks`
  - 作用：保存作业、实验、考试、复习、项目等学习任务。
  - 关键字段：`course_id`、`title`、`description`、`type`、`priority`、`due_at`、`remind_at`、`status`、`is_deleted`。
  - 关系：可关联 `courses._id`，也可被专注和学习记录关联。

- `focus_sessions`
  - 作用：保存一次专注计时会话。
  - 关键字段：`task_id`、`course_id`、`planned_duration_sec`、`actual_duration_sec`、`start_at`、`end_at`、`status`、`pause_count`、`interrupt_count`。
  - 关系：完成后通常生成一条 `study_records`。

- `study_records`
  - 作用：保存学习记录，供统计页和热力图聚合。
  - 关键字段：`task_id`、`course_id`、`focus_session_id`、`title`、`duration_sec`、`record_date`、`source`、`note`。
  - 关系：可关联课程、任务和专注会话。

- `import_logs`
  - 作用：保存课表导入记录。
  - 关键字段：`source_type`、`imported_count`、`skipped_count`、`conflict_count`、`created_at`、`message`。
  - 关系：记录导入行为，不直接关联课程表外键。

- `user_profile`
  - 作用：保存本机用户学习名片。
  - 关键字段：`nickname`、`school`、`major`、`grade`、`bio`、`avatar_uri`、`github`、`email`、`phone`、`show_email`、`show_phone`、`created_at`、`updated_at`。
  - 关系：`UserProfileRepository` 使用该表生成公开 JSON；不上传云端。

- `study_buddies`
  - 作用：保存扫码确认添加的学习伙伴。
  - 关键字段：`nickname`、`school`、`major`、`grade`、`bio`、`github`、`email`、`phone`、`source`、`added_at`、`note`。
  - 关系：来源 `source=0` 表示二维码，`source=1` 表示 NFC，`source=2` 预留手动添加。

- `weather_cache`
  - 作用：缓存校园天气数据。
  - 关键字段：`city`、`weather_text`、`temperature`、`humidity`、`wind`、`source`、`raw_json`、`updated_at`。
  - 关系：由 `WeatherRepository` 读写，供 Dashboard 天气卡片展示；与用户位置无关。

当前尚未创建、后续阶段计划加入的表：

- `task_attachments`
  - 作用：保存任务图片或文件附件 Uri。
  - 关键字段：`task_id`、`uri`、`type`、`title`、`created_at`。
  - 关系：阶段 13 将关联 `study_tasks._id`。

- `study_plans`
  - 作用：保存本地规则生成的学习计划。
  - 关键字段：`task_id`、`course_id`、`title`、`plan_date`、`planned_minutes`、`status`、`created_at`、`updated_at`。
  - 关系：阶段 14 将关联任务和课程。

当前 Provider Uri：

```text
content://com.example.campusmate.provider/courses
content://com.example.campusmate.provider/courses/#
content://com.example.campusmate.provider/tasks
content://com.example.campusmate.provider/tasks/#
content://com.example.campusmate.provider/focus_sessions
content://com.example.campusmate.provider/focus_sessions/#
content://com.example.campusmate.provider/study_records
content://com.example.campusmate.provider/study_records/#
content://com.example.campusmate.provider/import_logs
content://com.example.campusmate.provider/import_logs/#
content://com.example.campusmate.provider/user_profile
content://com.example.campusmate.provider/user_profile/#
content://com.example.campusmate.provider/study_buddies
content://com.example.campusmate.provider/study_buddies/#
content://com.example.campusmate.provider/weather_cache
content://com.example.campusmate.provider/weather_cache/#
```

## 权限说明

当前 Manifest 已声明：

- `POST_NOTIFICATIONS`：Android 13+ 显示任务提醒和前台服务通知。
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC`：专注计时前台服务。
- `RECEIVE_BOOT_COMPLETED`：开机后恢复未来任务提醒。
- `SCHEDULE_EXACT_ALARM`：尽量准时触发任务提醒；不可用时降级为普通时间窗口。
- `CAMERA`：阶段 9 新增，用于扫描学习名片二维码。
- `NFC`：阶段 10 新增，用于 NFC NDEF 学习名片写入和接收；设备不支持或未开启时降级到二维码。
- `INTERNET`：阶段 11 新增，用于可选远程天气请求和后续 WebView；天气默认可使用 Mock，不依赖网络。

当前通过系统设置入口引导但不强制依赖：

- `ACCESS_NOTIFICATION_POLICY`：勿扰模式授权入口，未授权不影响专注计时。

后续阶段计划涉及但当前未声明：

- `READ_MEDIA_IMAGES`：Android 13+ 阶段 13 读取图片附件。
- `READ_EXTERNAL_STORAGE`：Android 12 及以下阶段 13 读取图片附件。

## 运行方式

1. 使用 Android Studio 打开项目根目录 `E:\Zdragon\CampusMate`。
2. 等待 Gradle Sync 完成。
3. 选择 `app` 配置运行到模拟器或真机。
4. 推荐使用 Android 15/16 设备或模拟器，项目当前 `minSdk=35`。
5. NFC、传感器、通知和相机能力建议用真机测试；二维码扫描也需要可用相机。
6. 命令行构建：

```powershell
$env:GRADLE_USER_HOME='E:\Zdragon\CampusMate\.gradle-user'
$env:ANDROID_USER_HOME='E:\Zdragon\CampusMate\.android-user'
.\gradlew.bat :app:assembleDebug
```

Debug APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Mock / 演示数据：

- 进入「设置」。
- 点击「生成演示数据」。
- 当前生成课程、任务、提醒、学习记录、热力图和导入日志样例。
- 完整 V1.1 演示数据会在阶段 18 扩展。

## 测试方式

自动测试：

```powershell
$env:GRADLE_USER_HOME='E:\Zdragon\CampusMate\.gradle-user'
$env:ANDROID_USER_HOME='E:\Zdragon\CampusMate\.android-user'
.\gradlew.bat :app:testDebugUnitTest
```

手动测试清单：

- 添加课程，确认列表展示和详情页可打开。
- 添加任务，关联课程，设置截止时间。
- 设置 10 秒后提醒，确认通知能触发。
- 导入 `sample_schedule.html`，确认预览、冲突标记和批量写入可用。
- 开始专注并翻转手机，确认计时开始、拿起暂停、完成后写入记录。
- 完成专注后进入统计页，查看今日时长和热力图。
- 进入设置页，打开学习名片入口。
- 编辑个人资料，保存昵称、学校、专业、年级、简介、GitHub、邮箱和手机号。
- 生成学习名片二维码，确认公开 JSON 不包含未授权公开的邮箱和手机号。
- 使用另一台设备或截图扫码，确认扫码后出现预览页。
- 在预览页点击确认添加，确认学习伙伴列表出现该伙伴。
- 打开学习伙伴详情，确认可以查看来源和删除。
- 拒绝相机权限时，扫码页应提示权限不可用且不崩溃。
- 打开学习名片页的 NFC 分享入口，确认不支持 NFC 的设备显示二维码降级提示。
- NFC 未开启时，确认页面提供系统 NFC 设置入口。
- 支持 NFC 的真机上进入分享页，确认公开 JSON 与二维码名片一致，并可尝试写入可写 NFC 标签。
- 使用接收页读取 CampusMate NDEF payload，确认出现预览页且必须手动确认后才保存学习伙伴。
- 在设置页修改天气城市，例如北京、上海、天津或 Tokyo。
- 首页查看天气卡片，确认城市、天气、温度、湿度、风和更新时间展示正常。
- 点击刷新天气，确认不会阻塞页面。
- 打开 Mock 天气开关时，确认无网络也能稳定展示 Mock 数据。
- 关闭 Mock 天气后，确认网络失败时回退缓存或 Mock，不崩溃。
- 拍照附件保存将在阶段 13 测试。
- JSON 导出导入将在阶段 17 测试。

## Changelog

### V1.1-stage-11 - 校园天气卡片

已完成：

- 新增 `weather_cache` 表，数据库版本从 2 升级到 3，迁移只创建新表和索引。
- 扩展 `CampusMateContract` 和 `CampusMateProvider`，新增 `weather_cache` Uri。
- 新增 `domain/weather`：`WeatherDataSource`、`RemoteWeatherDataSource`、`MockWeatherDataSource`、`WeatherParser`、`WeatherResult`。
- 新增 `WeatherRepository`，负责缓存读取、缓存写入、30 分钟新鲜度判断和远程/缓存/Mock 降级。
- Dashboard 增加校园天气卡片和手动刷新按钮。
- Settings 增加城市配置和 Mock 天气开关。
- Manifest 新增 `INTERNET` 权限；不申请定位权限。
- 新增 `WeatherParserInstrumentedTest`。
- README 更新到 V1.1-stage-11。

待完成：

- WebView 当前页面课表 HTML 提取完整 UI。
- 任务图片附件。
- 本地规则式学习计划。
- 专注期间通知弱化 / 勿扰增强。
- 项目技术点展示页。
- JSON 导出 / 导入备份。
- 完整演示模式增强。

### V1.1-stage-10 - NFC 贴贴交友 / NFC 学习名片

已完成：

- 新增 `domain/nfc` 模块：`NfcCardPayload`、`NfcPayloadParser`、`NfcPayloadWriter`、`NfcUtils`。
- 新增 `ui/nfc` 页面：`NfcShareActivity`、`NfcReceiveActivity`。
- NFC 使用与二维码相同的公开 JSON，不新增隐私字段，不保存账号密码。
- 新增 `android.permission.NFC` 和 `android.hardware.nfc required=false`。
- `NfcReceiveActivity` 增加 `NDEF_DISCOVERED` MIME intent-filter：`application/vnd.campusmate.profile`。
- 学习名片页增加 NFC 分享和 NFC 接收入口。
- NFC 接收后先展示预览，用户确认后才保存到 `study_buddies`，并写入 `source=1`。
- 不支持 NFC 或 NFC 未开启时显示降级提示，可继续使用二维码交换。
- README 更新到 V1.1-stage-10。

待完成：

- WebView 当前页面课表 HTML 提取完整 UI。
- 任务图片附件。
- 本地规则式学习计划。
- 专注期间通知弱化 / 勿扰增强。
- 项目技术点展示页。
- JSON 导出 / 导入备份。
- 完整演示模式增强。

### V1.1-stage-9 - 学习名片与二维码

已完成：

- 新增 `user_profile` 表。
- 新增 `study_buddies` 表。
- 数据库版本从 1 升级到 2，迁移只创建新表，不删除 V1.0 数据。
- 扩展 `CampusMateContract` 和 `CampusMateProvider`，新增学习名片和学习伙伴 Uri。
- 新增 `UserProfileRepository`，负责资料保存、公开 JSON 构建和 JSON 解析。
- 新增 `StudyBuddyRepository`，负责伙伴添加、查询、详情、删除和重复提示查询。
- 新增个人资料页、编辑页、二维码名片页、扫码页、伙伴列表页、伙伴详情页。
- 设置页增加学习名片和学习伙伴入口。
- 新增 `CAMERA` 权限和 ZXing Android Embedded 依赖。
- 更新 README 到 V1.1-stage-9。

待完成：

- NFC 名片交换。
- WebView 当前页面课表 HTML 提取完整 UI。
- 任务图片附件。
- 本地规则式学习计划。
- 专注期间通知弱化 / 勿扰增强。
- 项目技术点展示页。
- JSON 导出 / 导入备份。
- 完整演示模式增强。

### V1.0-stage-1 到 V1.0-stage-8 - 主闭环

已完成：

- 项目基础架构、Material 主题、主导航。
- SQLite + ContentProvider + Repository。
- 课程管理、任务管理、任务提醒。
- HTML 课表导入和导入预览。
- 翻转手机专注、前台服务计时。
- 学习记录、统计页和热力图。
- 设置页、权限入口、演示数据和清空数据。

## 已知问题

- 部分 Android 13+ 设备需要手动授予通知权限，否则任务提醒通知不会显示。
- 二维码扫描需要相机权限和可用相机；模拟器摄像头不可用时只能测试二维码生成。
- 头像当前只保存 `avatar_uri` 文本，不做相册选择和图片裁剪。
- NFC 功能需要支持 NFC 的真机和可写 NFC 标签或兼容 NDEF 发送源测试；当前环境未做真机贴近验证。
- 当前 compile SDK 已移除旧 Android Beam 手机对手机推送 API，NFC 分享页采用 NDEF 标签写入方式，接收页可解析标准 CampusMate NDEF payload。
- WebView 解析真实教务系统依赖页面结构，当前完整增强流程尚未实现。
- 天气默认使用 Mock 数据保证演示稳定；远程天气依赖网络，失败时会回退缓存或 Mock。
- NotificationListenerService 属于后续实验功能，当前未实现且默认不读取其他 App 通知。
- 数据导入当前尚未实现；未来阶段计划采用追加策略，不默认覆盖已有数据。

## 下一阶段目标

下一阶段目标：

- 阶段名称：V1.1-stage-12 WebView 课表导入增强。
- 准备实现的功能：WebView 手动登录教务系统、用户确认后提取当前页面 HTML、交给 JsoupScheduleParser、解析失败降级到粘贴 HTML / sample HTML / 手动添加。
- 预期修改的模块：`ui/import_`、`domain/import_`、`AndroidManifest.xml`、README。
- 可能涉及的权限：`android.permission.INTERNET`。
