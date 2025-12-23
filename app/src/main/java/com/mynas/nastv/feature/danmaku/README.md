# Danmaku Feature Package

## 包说明
本包实现了 Android TV 弹幕功能，遵循 Strangler Fig 重构模式，与现有 UI 层完全隔离。

## 架构决策
- **包结构**：Feature Package (`com.mynas.nastv.feature.danmaku`)
- **重构模式**：Strangler Fig - 新功能独立开发，验证后完全替换旧代码
- **API 边界**：仅通过 `IDanmuController` 接口与 `VideoPlayerActivity` 通信

## 依赖规则
⚠️ **严格禁止**：
- 本包代码不得依赖 `com.mynas.nastv.ui.*` 包中的具体类
- 外部代码不得直接调用本包的内部实现类（除了 `api` 子包）

✅ **允许**：
- 依赖标准 Android SDK
- 依赖项目已有的第三方库（ExoPlayer, Gson, Coroutines）
- 通过 `IDanmuController` 接口通信

## 包结构
```
com.mynas.nastv.feature.danmaku/
├── api/                    # 公共契约接口
│   └── IDanmuController.java
├── view/                   # 渲染层
│   ├── DanmakuOverlayView.java
│   └── DanmuRenderer.java
├── model/                  # 数据模型
│   ├── DanmakuEntity.java
│   └── DanmuConfig.java
└── logic/                  # 业务逻辑
    ├── DanmuRepository.java
    ├── DanmuPresenter.java
    └── DanmuControllerImpl.java
```

## 参考文档
- [架构设计文档](file:///Users/duanhongke/android-project/nastv/_bmad-output/architecture.md)
- [产品需求文档](file:///Users/duanhongke/android-project/nastv/_bmad-output/prd.md)
- [Epic 与 Story 分解](file:///Users/duanhongke/android-project/nastv/_bmad-output/epics.md)

## 性能目标
- 冷启动耗时：< 500ms
- 渲染帧率：≥ 24fps（100条/屏负载下）
- 内存占用：< 15MB（对象池）
- 熔断恢复：< 20s

## 零崩溃策略
所有渲染错误必须被捕获并记录日志，绝不能导致主线程崩溃。
