---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]
inputDocuments: ['_bmad-output/analysis/brainstorming-session-2025-12-21T22-54.md']
documentCounts:
  briefs: 0
  research: 0
  brainstorming: 1
  projectDocs: 0
workflowType: 'prd'
lastStep: 0
project_name: 'nastv'
user_name: 'Duanhongke'
date: '2025-12-21'
---

# Product Requirements Document - nastv

**Author:** Duanhongke
**Date:** 2025-12-21

## Executive Summary

**nastv** 正在进化，旨在打破 Web 端弹幕文化与客厅影院体验之间的隔阂。通过将 **只读、高性能的弹幕引擎** 集成到现有的 Android TV 客户端中，我们要为家庭用户提供一种“共享观看”的氛围，同时绝不妥协画质或操作的便捷性。本项目的核心在于将 `fnos-tv` 生态的数据无缝迁移至原生 TV 界面，优先确立渲染的平滑度和交互的极简性。

### What Makes This Special

与简单粗暴叠加文本的普通播放器不同，**nastv 的实现** 专为“后仰式”电视场景量身定制：
*   **影院优先体验 (Cinema-First):** 奉行“氛围至上”哲学，采用低干扰的默认视觉设置 (即影院模式)。
*   **视频级稳定性:** 引入无情的“视频优先”熔断机制，在低端硬件上宁可丢弃弹幕也要保全视频帧率。
*   **原生融合:** 深度整合至 OSD 菜单，尊重 D-pad 交互范式，拒绝鼠标时代的复杂操作。

## Project Classification

**Technical Type:** mobile_app (Android TV)
**Domain:** general (Media/Entertainment)
**Complexity:** medium
**Project Context:** Brownfield - extending existing system

## Success Criteria

### User Success
*   **沉浸感:** 用户报告弹幕增加了观看乐趣而非造成干扰（通过“影院模式”实现）。
*   **控制感:** 用户能在 3 秒内通过遥控器完成弹幕的开启/关闭操作。
*   **流畅度:** 视频播放全程无肉眼可见的卡顿或丢帧。

### Business Success
*   **体验闭环:** Android TV 端具备与 Web 端对齐的弹幕播放能力，填补生态缺口。
*   **用户留存:** 提升用户在大屏端观看私有库内容的市场。

### Technical Success
*   **渲染性能:** 在低性能设备上通过插值算法实现视觉上的平滑滚动。
*   **稳定性:** 熔断机制有效，在 CPU 高负载时优先保证视频帧率。
*   **兼容性:** 100% 兼容现有 `fnos-tv` 后端接口数据格式。

### Measurable Outcomes
*   **基准性能:** 在低端电视盒子（如 S905 系列）上，保证同屏至少 **10 条** 弹幕流畅显示不掉帧。
*   **启动耗时:** 弹幕引擎加载耗时 < 500ms，不拖慢视频起播速度。

## Product Scope

### MVP - Minimum Viable Product
1.  **核心渲染:** 基于 `DanmuManager` 实现基础的弹幕滚动渲染。
2.  **数据接入:** 对接 `/danmu/get` 接口，支持按视频 ID 获取数据。
3.  **影院模式:** 默认实现“低透明度、顶部区域、中等字号”的样式。
4.  **基础控制:** 在 OSD 菜单中增加“弹幕开关”。
5.  **性能熔断:** 实现基础的“视频卡顿即丢包”逻辑。

### Growth Features (Post-MVP)
1.  **高级设置:** 允许用户自定义字号、透明度、速度。
2.  **智能过滤:** 客户端根据关键词或正则屏蔽弹幕。
3.  **多用户适配:** 记忆不同用户的弹幕偏好设置。

### Vision (Future)
*   **跨屏互动:** 支持手机扫码发送弹幕到电视。
*   **AI 增强:** 本地 AI 识别画面主体，实现防遮挡（智能蒙版）。

## User Journeys

### Journey 1: Primary User - The Immersive Night
**Alex**, a 30-year-old film enthusiast, settles onto his sofa on a Friday night to watch a classic anime on **nastv**. He wants the communal feeling of bullet comments but hates when they cover the subtitles or distract from the art.

1.  **Discovery:** As the video starts, he sees a subtle "Danmaku On" indicator in the OSD.
2.  **Engagement:** He notices the comments are rendering in the top 30% of the screen with a translucent style. They feel like part of the background ambience.
3.  **Control:** When a "spoiler" warning appears, he instinctively presses the **Down** key, highlights the "Danmaku" toggle, and turns it off instantly.
4.  **Resolution:** He finishes the movie feeling he shared the experience with others without compromising his cinematic enjoyment.

### Journey 2: Edge User - Smooth Survival
**Grandma**, 60, uses an older carrier-provided Android box. She loves watching trending dramas which often have thousands of comments per minute.

1.  **Conflict:** She opens the season finale. Normally, this would crash her video player or turn it into a slideshow.
2.  **Intervention:** **nastv**'s internal monitor detects the frame rate dipping below 24fps.
3.  **Resolution:** The system silently drops 60% of the non-essential Danmaku packets. Grandma doesn't notice the missing comments; she only notices that the video plays perfectly smoothly from start to finish.

### Journey Requirements Summary
*   **OSD Integration:** Need a quick-access menu reachable via D-pad Down key.
*   **Visual Zoning:** Rendering engine must support "Top 30%" zoning.
*   **Performance Monitor:** Real-time FPS monitoring to trigger the packet-drop circuit breaker.

## mobile_app Specific Requirements (Android TV)

### Project-Type Overview
本项目是一个原生的 Android TV 应用（`mobile_app` 类型），重点在于大屏交互适配和对低版本 Android 系统的兼容性支持。

### Technical Architecture Considerations
*   **SDK Compatibility:**
    *   `minSdk`: **24** (Android 7.0) - 覆盖大多数存量电视盒子。
    *   `targetSdk`: **34** (Android 14) - 符合最新 Google Play 规范。
    *   `compileSdk`: **34**。
*   **Connectivity:**
    *   **Online Only:** 仅支持在线流媒体弹幕，不支持离线缓存/下载功能。

### TV Interaction Model
*   **Remote Control Mapping:**
    *   **D-pad Down:** 呼出播放器原生控制条 (Native Control Bar)。
    *   **Settings Access:** 在进度条旁边增加“弹幕设置”按钮，点击后弹出设置面板。
*   **Focus Management:**
    *   **Pass-through (透传):** 弹幕渲染层 (Danmaku Layer) **始终不获取焦点** (`focusable=false`, `clickable=false`)。
    *   **Overlay:** 弹幕层覆盖在 VideoView 之上，但所有按键事件应穿透至底层的 PlayerControlView。

### Performance & Device Constraints
*   **Canvas Rendering:** 使用轻量级绘制方案（或 SurfaceView），避免过度重绘 (Overdraw)。
*   **Memory Management:** 严格限制弹幕对象池大小，防止在低内存设备（1GB RAM 盒子）上 OOM。

## Functional Requirements

### Rendering Capabilities & Visuals
*   **FR-01:** 播放器必须能在视频画面之上渲染弹幕层，支持“滚动 (Scroll)”与“顶部/底部固定 (Fix)”三种类型。
*   **FR-02:** 系统必须默认启用“影院模式”预设：渲染区域限制在屏幕顶部 30%，透明度为 50%。
*   **FR-03:** 弹幕字体大小应自动适配屏幕分辨率（1080p/4k 保持视觉一致）。

### Data Integration
*   **FR-04:** 客户端必须能根据当前视频 ID 向后端 `/danmu/get` 接口请求数据。
*   **FR-05:** 客户端必须能处理后端返回的 JSON 数据格式并解析为弹幕对象。
*   **FR-06:** 播放器必须能根据视频进度时间戳 (PTS) 同步显示对应的弹幕。
*   **FR-07:** 播放器跳转进度 (Seek) 时，弹幕必须同步跳转到对应时间点。

### User Interaction & Control
*   **FR-08:** 用户在全屏播放状态下按下遥控器 `D-pad Down` 键，必须呼出带有“弹幕设置”按钮的原生控制条。
*   **FR-09:** 用户通过“弹幕设置”按钮可打开侧边栏/浮层菜单，包含“弹幕开关”。
*   **FR-10:** 用户切换弹幕开关状态后，设置应立即生效。

### Performance & Stability
*   **FR-11:** 系统必须实时监控当前渲染帧率 (FPS)。
*   **FR-12:** 当帧率连续 3 秒低于 24fps 时，系统必须自动触发熔断机制（丢弃新弹幕或清屏）。
*   **FR-13:** 弹幕加载耗时应 < 500ms（在网络正常情况下），不应阻塞视频起播。

## Non-Functional Requirements

### Performance
*   **NFR-01 (Start Time):** 弹幕引擎冷启动耗时应 < **500ms**，确保不阻塞视频首帧播放。
*   **NFR-02 (FPS):** 在 100 条/屏的高负载下，渲染帧率不低于 **24fps** (视频播放最低流畅标准)。
*   **NFR-03 (Memory):** 弹幕对象池(Object Pool) 最大内存占用限制为 **15MB**，防止在低内存设备上触发系统 OOM 杀进程。

### Reliability
*   **NFR-04 (Recovery):** 触发视频帧率熔断丢包后，当 CPU 负载回落，系统应在 **20秒内** 自动恢复弹幕显示。
*   **NFR-05 (Error Tolerance):** 后端 API 请求超时（>3000ms）或 500 错误时，客户端应静默失败，仅记录日志，不向用户展示错误提示。

### Security
*   **NFR-06 (Sanitization):** 客户端必须处理弹幕文本中的特殊字符（如换行符炸弹），防止 UI 错位或渲染异常。
