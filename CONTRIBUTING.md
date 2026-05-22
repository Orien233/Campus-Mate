# CONTRIBUTING.md

本文件面向 Campus-Mate 小组成员，说明人类协作流程。AI Agent 的代码修改规则见 `AGENTS.md`。

## 分支

- `main` 保持稳定，应该能编译、能展示。
- 每个功能或修复从 `main` 新建分支，例如 `feature/nfc-card-share`、`feature/webview-schedule-import`、`fix/focus-service-stop`。
- 不直接向 `main` 提交未完成代码。

## 提交

- 每次提交只做一类改动，避免把功能、格式化、依赖升级和文档修改混在一起。
- 提交信息简洁说明做了什么，例如 `Add QR buddy duplicate hint` 或 `Fix focus session duplicate write`。
- 不提交 `local.properties`、`build/`、`.gradle/`、`.kotlin/`、生成 APK、临时日志或敏感文件。

## PR 描述

PR 至少说明：

- 做了什么；
- 影响哪些模块；
- 怎么测试；
- 是否涉及权限、Manifest、数据库迁移或用户数据；
- 有哪些未验证项或已知限制。

涉及传感器、NFC、通知权限、前台 Service、精确闹钟、勿扰模式的 PR，应写明真机验证设备和结果。无法真机验证时要明确说明。

## 冲突处理

- 处理冲突前先读双方改动意图，不要简单覆盖别人代码。
- 如果冲突涉及数据库结构、Manifest、导航入口或公共 Repository，先和相关成员确认。
- 不删除文档、截图、报告材料、示例 HTML 或演示数据，除非任务明确要求。

## 使用 AI Agent

- 给 Agent 任务时同时提供目标、上下文、约束和验收条件。
- Agent 生成的代码必须由人类阅读和运行验证后再合并。
- Agent 未实际运行的测试，不能在 PR 中写成“已通过”。
