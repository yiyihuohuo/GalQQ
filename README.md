# GalQQ 🎮

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://www.android.com/)
[![Xposed](https://img.shields.io/badge/Xposed-Framework-orange.svg)](https://github.com/rovo89/XposedBridge)

**为你的QQ增加选项，让你的QQ变成galgame！**

GalQQ 是一个基于 Xposed 框架的 Android 模块，旨在为 QQ 聊天界面添加类似 Galgame（美少女游戏）的三选项快捷回复功能。通过 AI 智能生成或本地词典提供三个不同的回复选项，让你的聊天体验更加有趣和便捷。

## ✨ 功能特性

### 🎯 核心功能
- **三选项回复系统**：在 QQ 聊天界面添加三个快捷回复选项
- **AI 智能生成**：集成 OpenAI API，智能生成符合上下文的回复建议
- **本地词典支持**：内置丰富的回复词典，无需网络也能使用
- **自定义配置**：支持自定义系统提示词、API 配置等

### 🔧 技术特性
- **LSPosed 框架支持**：基于 LSPosed 框架开发
- **QQNT 架构兼容**：支持 QQ 最新架构（QQNT）
- **模块化设计**：清晰的代码结构，易于扩展和维护

### 📱 用户界面
- **设置界面**：直观的配置界面，支持主题切换
- **AI 日志查看器**：实时监控 AI 请求和响应
- **AI 监控面板**：查看 AI 使用状态和性能
- **备份还原**：支持配置数据的备份和恢复

## 🚀 快速开始

### 环境要求
- Android 10.0+ (API 24+)
- LSPosed 框架（推荐）或 EdXposed
- QQ 9.0+ 版本

### 安装步骤

1. **下载安装包**
   - 从 [Releases](https://github.com/yiyihuohuo/GalQQ/releases) 页面下载最新版本的 APK 文件


2. **安装模块**
   - 在 LSPosed 安装器中安装生成的 APK
   - 在 LSPosed 框架中启用模块
   - 重启 QQ 应用

3. **配置模块**
   - 打开 QQ 设置中的 GalQQ 选项
   - 配置 AI API 设置（可选）
   - 启用所需功能
###  遇到问题请先查看我们的 [快速入门指南](docs/brief-guied.md)。
## 📖 使用说明

### 基本使用
1. 在 QQ 聊天界面，长按消息气泡
2. 选择出现的三个回复选项之一
3. 选中的回复会自动填入输入框

### AI 功能配置
1. 进入 GalQQ 设置界面
2. 配置 OpenAI API 密钥和端点
3. 自定义系统提示词以控制 AI 行为
4. 调整 Temperature 参数控制回复随机性

### 高级设置
- **自定义词典**：支持加载外部词典文件
- **消息过滤**：设置消息长度和类型过滤规则
- **性能优化**：调整 AI 请求频率和缓存策略

## 🏗️ 项目架构

### 技术栈
- **语言**：Java
- **依赖注入**：Dagger/Hilt（计划中）
- **网络**：OkHttp + Retrofit
- **数据存储**：MMKV + SharedPreferences
- **日志**：XposedBridge + 自定义日志系统


### 核心组件

#### Hook 系统
- **MainHook**：主 Hook 入口，负责初始化和协调
- **BaseBubbleBuilderHook**：消息气泡构建 Hook
- **MessageInterceptor**：消息拦截和处理
- **SettingsInterceptor**：设置界面注入

#### AI 系统
- **HttpAiClient**：AI API 客户端
- **AiRateLimitedQueue**：AI 请求限流队列
- **DictionaryManager**：本地词典管理
- **MessageContextManager**：消息上下文管理

#### UI 系统
- **SettingsUiFragmentHostActivity**：主设置界面
- **AiLogViewerActivity**：AI 日志查看器
- **AiMonitorActivity**：AI 监控面板

## 📊 性能优化

### 内存优化
- 使用弱引用管理 Hook 对象
- 实现对象池复用机制
- 优化图片和资源加载

### 网络优化
- AI 请求结果缓存
- 请求失败重试机制
- 网络状态监听和适配

### 电池优化
- 后台任务最小化
- 合理的唤醒策略
- 低电量模式适配

## 🔒 安全考虑

### 隐私保护
- 本地数据处理，不上传用户隐私
- API 密钥安全存储
- 敏感信息脱敏处理

### 权限管理
- 最小权限原则
- 运行时权限申请
- 权限使用透明化

## 🤝 贡献指南

### 代码规范
- 遵循 Google Java 代码规范
- 使用 Kotlin 代码约定
- 编写单元测试

### 提交规范
```
feat: 添加新功能
fix: 修复bug
docs: 文档更新
style: 代码格式调整
refactor: 代码重构
test: 测试相关
chore: 构建/工具相关
```

### 开发流程
1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

## 🐛 已知问题

- 在某些 QQ 版本上可能存在兼容性问题
- AI 回复偶尔可能出现不符合上下文的情况
- 长时间使用后可能出现内存泄漏

## 📞 支持与联系

### 问题反馈
- 提交 [Issue](https://github.com/yiyihuohuo/GalQQ/issues)
- 加入 QQ 群： 859142525
- 项目地址：https://github.com/yiyihuohuo/GalQQ

### 贡献者
感谢所有为 GalQQ 做出贡献的开发者们 ！

### 免责声明
本项目仅供学习研究使用，请勿用于商业用途。使用本模块可能违反 QQ 用户协议，请自行承担使用风险。

## 📄 许可证

本项目基于 [Apache License 2.0](LICENSE) 许可证开源。

## 🙏 致谢

- [LSPosed Framework](https://github.com/LSPosed/LSPosed) - 提供强大的 Hook 框架
- [QAuxiliary](https://github.com/cinit/QAuxiliary) - 提供架构参考和灵感
---

**⭐ 如果这个项目对你有帮助，请给我们一个 Star！**
